"""Parse COBIP's PostgreSQL DDL into an auditable ERD model.

This parser is intentionally scoped to the syntax used in schema.sql. It fails
when a table column, referenced key, or foreign-key component cannot be read.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parent


@dataclass
class Column:
    name: str
    data_type: str
    nullable: bool
    default: str | None = None
    identity: bool = False
    primary_key: bool = False
    unique_single: bool = False
    unique_group: bool = False


@dataclass
class UniqueKey:
    name: str
    columns: list[str]
    primary: bool = False
    partial: bool = False
    expression: bool = False


@dataclass
class ForeignKey:
    name: str
    child_table: str
    child_columns: list[str]
    parent_table: str
    parent_columns: list[str]
    child_nullable: bool = False
    child_unique: bool = False
    parent_key: str = ""

    @property
    def parent_end(self) -> str:
        return "0..1" if self.child_nullable else "1"

    @property
    def child_end(self) -> str:
        return "0..1" if self.child_unique else "0..N"


@dataclass
class Table:
    name: str
    columns: list[Column] = field(default_factory=list)
    unique_keys: list[UniqueKey] = field(default_factory=list)


def matching_paren(source: str, start: int) -> int:
    assert source[start] == "("
    depth = 0
    quoted = False
    i = start
    while i < len(source):
        char = source[i]
        if char == "'":
            if quoted and i + 1 < len(source) and source[i + 1] == "'":
                i += 2
                continue
            quoted = not quoted
        elif not quoted:
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    raise ValueError("Unclosed parenthesis in SQL")


def split_top_level(source: str) -> list[str]:
    parts = []
    begin = 0
    depth = 0
    quoted = False
    i = 0
    while i < len(source):
        char = source[i]
        if char == "'":
            if quoted and i + 1 < len(source) and source[i + 1] == "'":
                i += 2
                continue
            quoted = not quoted
        elif not quoted:
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            elif char == "," and depth == 0:
                parts.append(source[begin:i].strip())
                begin = i + 1
        i += 1
    parts.append(source[begin:].strip())
    return [part for part in parts if part]


def names(value: str) -> list[str]:
    result = [part.strip() for part in split_top_level(value)]
    if not all(re.fullmatch(r"[a-z_][a-z_0-9]*", part, re.I) for part in result):
        raise ValueError(f"Expected column names: {value}")
    return result


def parse(sql: str) -> tuple[dict[str, Table], list[ForeignKey]]:
    sql = re.sub(r"--[^\n]*", "", sql)
    tables: dict[str, Table] = {}
    fks: list[ForeignKey] = []

    for match in re.finditer(r"\bCREATE\s+TABLE\s+(\w+)\s*\(", sql, re.I):
        table_name = match.group(1)
        open_at = match.end() - 1
        body = sql[open_at + 1:matching_paren(sql, open_at)]
        table = Table(table_name)
        tables[table_name] = table
        for item in split_top_level(body):
            named = re.match(r"CONSTRAINT\s+(\w+)\s+(.+)", item, re.I | re.S)
            constraint_name = named.group(1) if named else ""
            definition = named.group(2).strip() if named else item
            pk = re.match(r"PRIMARY\s+KEY\s*\(([^)]*)\)", definition, re.I | re.S)
            uq = re.match(r"UNIQUE(?:\s+NULLS\s+NOT\s+DISTINCT)?\s*\(([^)]*)\)", definition, re.I | re.S)
            fk = re.match(
                r"FOREIGN\s+KEY\s*\(([^)]*)\)\s+REFERENCES\s+(\w+)\s*\(([^)]*)\)",
                definition, re.I | re.S,
            )
            if pk:
                table.unique_keys.append(UniqueKey(constraint_name or f"{table_name}_pkey", names(pk.group(1)), primary=True))
                continue
            if uq:
                table.unique_keys.append(UniqueKey(constraint_name, names(uq.group(1))))
                continue
            if fk:
                fks.append(ForeignKey(constraint_name, table_name, names(fk.group(1)), fk.group(2), names(fk.group(3))))
                continue
            if re.match(r"CHECK\s*\(", definition, re.I):
                continue
            col = re.match(
                r"(\w+)\s+(bigint|integer|smallint|boolean|text|jsonb|timestamptz|varchar\(\d+\)|numeric\(\d+,\s*\d+\))(.*)",
                item, re.I | re.S,
            )
            if not col:
                raise ValueError(f"Unparsed item in {table_name}: {item}")
            col_name, data_type, tail = col.groups()
            inline_pk = bool(re.search(r"\bPRIMARY\s+KEY\b", tail, re.I))
            inline_uq = bool(re.search(r"\bUNIQUE\b", tail, re.I))
            without_identity = re.sub(r"\bGENERATED\s+BY\s+DEFAULT\s+AS\s+IDENTITY\b", "", tail, flags=re.I)
            default = re.search(r"\bDEFAULT\s+('(?:''|[^'])*'|[A-Za-z_][\w]*(?:\([^)]*\))?|\d+)", without_identity, re.I)
            table.columns.append(Column(
                col_name, data_type.upper().replace(" ", ""),
                nullable=not (inline_pk or bool(re.search(r"\bNOT\s+NULL\b", tail, re.I))),
                default=default.group(1) if default else None,
                identity=bool(re.search(r"\bGENERATED\s+BY\s+DEFAULT\s+AS\s+IDENTITY\b", tail, re.I)),
                primary_key=inline_pk,
                unique_single=inline_uq and not inline_pk,
            ))
            if inline_pk:
                table.unique_keys.append(UniqueKey(f"{table_name}_pkey", [col_name], primary=True))
            if inline_uq:
                table.unique_keys.append(UniqueKey(f"{table_name}_{col_name}_key", [col_name]))
            inline_fk = re.search(r"\bREFERENCES\s+(\w+)\s*\(([^)]*)\)", tail, re.I | re.S)
            if inline_fk:
                fks.append(ForeignKey(f"{table_name}_{col_name}_fkey", table_name, [col_name], inline_fk.group(1), names(inline_fk.group(2))))

    for match in re.finditer(
        r"\bALTER\s+TABLE\s+(\w+)\s+ADD\s+CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\(([^)]*)\)\s+REFERENCES\s+(\w+)\s*\(([^)]*)\)",
        sql, re.I | re.S,
    ):
        table_name, constraint_name, child_cols, parent_table, parent_cols = match.groups()
        fks.append(ForeignKey(constraint_name, table_name, names(child_cols), parent_table, names(parent_cols)))

    for match in re.finditer(r"\bCREATE\s+UNIQUE\s+INDEX\s+(\w+)\s+ON\s+(\w+)\s*\(", sql, re.I):
        index_name, table_name = match.group(1), match.group(2)
        opened = match.end() - 1
        closed = matching_paren(sql, opened)
        columns_text = sql[opened + 1:closed]
        rest = sql[closed + 1:sql.index(";", closed)]
        expression = bool(re.search(r"[()]", columns_text))
        columns = [part.strip() for part in split_top_level(columns_text)]
        tables[table_name].unique_keys.append(UniqueKey(
            index_name, columns, partial=bool(re.search(r"\bWHERE\b", rest, re.I)), expression=expression,
        ))

    for table in tables.values():
        colmap = {col.name: col for col in table.columns}
        for key in table.unique_keys:
            if key.expression:
                for expression in key.columns:
                    referenced = re.fullmatch(r"lower\((\w+)\)", expression, re.I)
                    if referenced and referenced.group(1) in colmap:
                        colmap[referenced.group(1)].unique_group = True
                continue
            if not set(key.columns) <= set(colmap):
                raise ValueError(f"Unknown UNIQUE column: {table.name}.{key.columns}")
            for name in key.columns:
                col = colmap[name]
                if key.primary:
                    col.primary_key = True
                    col.nullable = False
                elif len(key.columns) == 1 and not key.partial:
                    col.unique_single = True
                else:
                    col.unique_group = True

    for fk in fks:
        child, parent = tables[fk.child_table], tables[fk.parent_table]
        childmap = {col.name: col for col in child.columns}
        parentmap = {col.name: col for col in parent.columns}
        if len(fk.child_columns) != len(fk.parent_columns):
            raise ValueError(f"FK arity mismatch: {fk.name}")
        if not set(fk.child_columns) <= set(childmap) or not set(fk.parent_columns) <= set(parentmap):
            raise ValueError(f"Unknown FK column: {fk.name}")
        fk.child_nullable = any(childmap[name].nullable for name in fk.child_columns)
        fk.child_unique = any(
            not key.partial and not key.expression and set(key.columns) <= set(fk.child_columns)
            for key in child.unique_keys
        )
        target = next((
            key for key in parent.unique_keys
            if not key.partial and not key.expression and key.columns == fk.parent_columns
        ), None)
        if target is None:
            raise ValueError(f"FK target is not a declared PK/UNIQUE key: {fk.name}")
        fk.parent_key = "PK" if target.primary else "UNIQUE"

    if len({fk.name for fk in fks}) != len(fks):
        raise ValueError("Duplicate FK constraint names")
    return tables, fks


def write_catalog(tables: dict[str, Table], fks: list[ForeignKey]) -> None:
    model = {
        "source": "schema.sql",
        "tables": {name: asdict(table) for name, table in tables.items()},
        "foreign_keys": [asdict(fk) | {"parent_end": fk.parent_end, "child_end": fk.child_end} for fk in fks],
    }
    (ROOT / "erd-schema.json").write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = [
        "# COBIP SQL 외래 키 관계 목록", "",
        "`schema.sql`의 실제 `REFERENCES` / `FOREIGN KEY` 정의에서 추출했다. 한 줄은 FK 제약 하나이며 복합 FK는 컬럼 쌍을 모두 표시한다.", "",
        "표기: 부모 쪽 `1`은 자식 행에 부모가 필수, `0..1`은 FK가 NULL일 수 있음을 뜻한다. 자식 쪽은 이 부모에 연결된 행이 없을 수도 있으므로 최소 0이다.", "",
        f"테이블 {len(tables)}개 · 컬럼 {sum(len(t.columns) for t in tables.values())}개 · 외래 키 제약 {len(fks)}개", "",
    ]
    for index, fk in enumerate(fks, 1):
        parent = f"{fk.parent_table}.({', '.join(fk.parent_columns)})"
        child = f"{fk.child_table}.({', '.join(fk.child_columns)})"
        lines.append(
            f"{index}. `{parent}` ({fk.parent_key}) `{fk.parent_end}` ─ `{fk.child_end}` "
            f"`{child}` (FK: `{fk.name}`; {'NULL 가능' if fk.child_nullable else 'NOT NULL'}; "
            f"{'자식 FK UNIQUE' if fk.child_unique else '자식 FK 비고유'})"
        )
    lines.extend(["", "## UNIQUE 인덱스/제약", ""])
    for table in tables.values():
        for key in table.unique_keys:
            flags = ", ".join(
                tag for condition, tag in [(key.primary, "PK"), (key.partial, "부분 인덱스"), (key.expression, "표현식")]
                if condition
            )
            lines.append(f"- `{table.name}.{key.name}`: `({', '.join(key.columns)})`" + (f" — {flags}" if flags else ""))
    (ROOT / "erd-relationships.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    tables, fks = parse((ROOT / "schema.sql").read_text(encoding="utf-8"))
    write_catalog(tables, fks)
    print(f"Parsed {len(tables)} tables, {sum(len(t.columns) for t in tables.values())} columns, {len(fks)} FK constraints")
