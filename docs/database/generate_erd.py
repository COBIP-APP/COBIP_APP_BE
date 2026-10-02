"""Render the current schema.sql as an ERDCloud-inspired SVG and PNG.

Run from any directory: python generate_erd.py
The SQL file remains the source of truth for table/column names and types.
"""

from __future__ import annotations

from html import escape
import heapq
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from erd_model import parse, write_catalog


ROOT = Path(__file__).resolve().parent
SQL = (ROOT / "schema.sql").read_text(encoding="utf-8")
W, H = 4700, 2910
CARD_W, HEADER_H, COLUMN_H, ROW_H = 1030, 55, 40, 37

FONT = r"C:\Windows\Fonts\malgun.ttf"
FONT_BOLD = r"C:\Windows\Fonts\malgunbd.ttf"
MONO = r"C:\Windows\Fonts\consola.ttf"

FONT_TITLE = ImageFont.truetype(FONT_BOLD, 46)
FONT_SUBTITLE = ImageFont.truetype(FONT, 23)
FONT_TABLE = ImageFont.truetype(MONO, 27)
FONT_KO = ImageFont.truetype(FONT_BOLD, 25)
FONT_COLUMN = ImageFont.truetype(FONT_BOLD, 19)
FONT_NAME = ImageFont.truetype(MONO, 22)
FONT_TYPE = ImageFont.truetype(MONO, 19)
FONT_DESC = ImageFont.truetype(FONT, 21)
FONT_BADGE = ImageFont.truetype(MONO, 16)
FONT_LEGEND = ImageFont.truetype(FONT, 20)
FONT_SMALL = ImageFont.truetype(MONO, 15)

COLORS = {
    "bg": "#202224", "card": "#252728", "card_alt": "#292b2c",
    "border": "#555b5e", "grid": "#45484a", "header": "#3d2d20",
    "header_edge": "#9a6336", "column": "#302a26", "white": "#eef0f0",
    "text": "#d7dbdc", "muted": "#9aa0a3", "type": "#bdc3c4",
    "pk": "#efbf51", "fk": "#62c2cc", "key": "#c98ba9",
    "line": "#698b91", "line_alt": "#916d7c", "line_bg": "#34383a",
}

KOREAN = {
    "users": "사용자", "languages": "언어", "categories": "카테고리",
    "templates": "학습 템플릿", "template_sections": "학습 목차",
    "questions": "문제", "media_files": "이미지 파일",
    "submissions": "제출 기록", "user_progress": "학습 진행",
    "template_bookmarks": "즐겨찾기",
}

# Every physical column has one short logical description.
DESCRIPTIONS = {
    "users": {
        "user_id": "사용자 ID", "email": "이메일", "nickname": "닉네임",
        "password_hash": "비밀번호 해시", "role": "권한", "status": "계정 상태",
        "email_verified": "이메일 인증 여부", "service_terms_agreed_at": "서비스 약관 동의 시각",
        "privacy_terms_agreed_at": "개인정보 동의 시각", "profile_media_id": "프로필 이미지 ID",
        "created_at": "가입 시각", "updated_at": "수정 시각",
    },
    "languages": {
        "language_id": "언어 ID", "code": "언어 코드", "name": "언어 이름",
        "display_order": "표시 순서", "is_active": "선택 가능 여부",
    },
    "categories": {
        "category_id": "카테고리 ID", "parent_category_id": "상위 카테고리 ID",
        "code": "카테고리 코드", "name": "표시 이름",
        "display_order": "표시 순서", "is_active": "선택 가능 여부",
    },
    "templates": {
        "template_id": "템플릿 ID", "category_id": "소속 카테고리 ID",
        "language_id": "언어 ID (선택)", "created_by": "등록 관리자 ID",
        "title": "제목", "summary": "목록 요약", "description": "상세 소개",
        "difficulty": "난이도", "display_order": "표시 순서",
        "is_published": "공개 여부", "created_at": "생성 시각",
        "updated_at": "수정 시각",
    },
    "template_sections": {
        "section_id": "목차 항목 ID", "template_id": "소속 템플릿 ID",
        "parent_section_id": "상위 항목 ID", "title": "항목 제목",
        "body": "학습 설명", "example_code": "예제 코드 (선택)",
        "display_order": "목차 순서",
    },
    "questions": {
        "question_id": "문제 ID", "category_id": "문제 카테고리 ID",
        "related_template_id": "연결 템플릿 ID", "related_section_id": "연결 목차 항목 ID",
        "created_by": "등록 관리자 ID", "language_id": "언어 ID (선택)",
        "question_type": "문제 유형", "prompt": "문제 지문",
        "code_snippet": "제시 코드", "choice_options": "객관식 보기 JSON",
        "correct_choice_key": "객관식 정답 키", "reference_answer": "모범 답안",
        "grading_criteria": "평가 기준 JSON", "explanation": "해설",
        "difficulty": "난이도", "pass_score": "통과 점수",
        "display_order": "표시 순서", "is_published": "공개 여부",
        "created_at": "생성 시각", "updated_at": "수정 시각",
    },
    "media_files": {
        "media_id": "이미지 ID", "uploaded_by": "업로드 사용자 ID",
        "template_id": "템플릿 ID (선택)", "section_id": "목차 항목 ID (선택)",
        "question_id": "문제 ID (선택)", "media_type": "이미지 용도",
        "object_key": "S3 객체 키", "original_name": "원본 파일명",
        "content_type": "MIME 유형", "file_size": "파일 크기(Byte)",
        "alt_text": "대체 텍스트", "display_order": "표시 순서",
        "created_at": "업로드 시각",
    },
    "submissions": {
        "submission_id": "제출 ID", "user_id": "제출 사용자 ID",
        "question_id": "문제 ID", "selected_choice_key": "선택한 보기 키",
        "answer_text": "서술형 답안", "source_code": "제출 코드",
        "grading_status": "채점 상태", "grading_method": "채점 방식",
        "score": "점수", "feedback": "피드백",
        "criteria_results": "기준별 결과 JSON", "ai_model_version": "AI 모델 버전",
        "submitted_at": "제출 시각", "graded_at": "채점 시각",
    },
    "user_progress": {
        "user_id": "사용자 ID", "template_id": "템플릿 ID",
        "last_section_id": "마지막 학습 항목 ID",
        "started_at": "시작 시각", "last_studied_at": "마지막 학습 시각",
        "completed_at": "완료 시각",
    },
    "template_bookmarks": {
        "user_id": "사용자 ID", "template_id": "템플릿 ID",
        "created_at": "즐겨찾기 시각",
    },
}

POSITIONS = {
    "users": (100, 210), "languages": (1470, 210), "categories": (3550, 210),
    "media_files": (100, 830), "templates": (1500, 810),
    "questions": (3550, 830),
    "template_bookmarks": (100, 2220), "user_progress": (1350, 2190),
    "template_sections": (2470, 2160), "submissions": (3550, 2130),
}

MODEL, RELATIONS = parse(SQL)
write_catalog(MODEL, RELATIONS)
TABLES = {name: [(col.name, col.data_type) for col in table.columns] for name, table in MODEL.items()}
assert set(TABLES) == set(DESCRIPTIONS) == set(POSITIONS)
for table, cols in TABLES.items():
    assert {name for name, _ in cols} == set(DESCRIPTIONS[table]), (table, cols)

COL = {(table, col.name): col for table, model in MODEL.items() for col in model.columns}
FK = {(fk.child_table, name) for fk in RELATIONS for name in fk.child_columns}

image = Image.new("RGB", (W, H), COLORS["bg"])
draw = ImageDraw.Draw(image)
svg: list[str] = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
    '<style>text{font-family:Malgun Gothic,Arial,sans-serif}</style>',
]


def rect(box, fill, outline=None, width=1, radius=0):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)
    x1, y1, x2, y2 = box
    svg.append(
        f'<rect x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" '
        f'rx="{radius}" fill="{fill}" stroke="{outline or "none"}" stroke-width="{width}"/>'
    )


def line(points, color, width=2, dash=None):
    draw.line(points, fill=color, width=width, joint="curve")
    coords = " ".join(f"{x},{y}" for x, y in points)
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    svg.append(f'<polyline points="{coords}" fill="none" stroke="{color}" stroke-width="{width}"{dash_attr}/>')


def circle(x, y, r, fill, outline=None, width=1):
    draw.ellipse((x-r, y-r, x+r, y+r), fill=fill, outline=outline, width=width)
    svg.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}" stroke="{outline or "none"}" stroke-width="{width}"/>')


def text_at(x, y, value, font, fill, *, anchor="la", family="Malgun Gothic"):
    draw.text((x, y), value, font=font, fill=fill, anchor=anchor)
    # Pillow anchor 'la' is left/ascender. SVG dominant-baseline approximates it.
    svg_anchor = "end" if anchor[0] == "r" else "middle" if anchor[0] == "m" else "start"
    size = font.size
    svg.append(
        f'<text x="{x}" y="{y+size}" font-family="{family}" font-size="{size}" '
        f'fill="{fill}" text-anchor="{svg_anchor}">{escape(value)}</text>'
    )


def row_y(table: str, col: str) -> int:
    index = [name for name, _ in TABLES[table]].index(col)
    return POSITIONS[table][1] + HEADER_H + COLUMN_H + index * ROW_H + ROW_H // 2


def card_box(table: str):
    x, y = POSITIONS[table]
    return x, y, x + CARD_W, y + HEADER_H + COLUMN_H + len(TABLES[table]) * ROW_H


STEP, PORT, CLEARANCE = 25, 75, 18
GRID_MAX_X, GRID_MAX_Y = W // STEP, H // STEP
BLOCKED = set()
for table in TABLES:
    left, top, right, bottom = card_box(table)
    for gx in range(max(1, (left-CLEARANCE)//STEP), min(GRID_MAX_X, (right+CLEARANCE)//STEP+2)):
        for gy in range(max(1, (top-CLEARANCE)//STEP), min(GRID_MAX_Y, (bottom+CLEARANCE)//STEP+2)):
            px, py = gx*STEP, gy*STEP
            if left-CLEARANCE < px < right+CLEARANCE and top-CLEARANCE < py < bottom+CLEARANCE:
                BLOCKED.add((gx, gy))
for gx in range(1, GRID_MAX_X):
    for gy in range(1, GRID_MAX_Y):
        if gy*STEP < 150 or gy*STEP > 2775:
            BLOCKED.add((gx, gy))


def compress(points):
    result = []
    for point in points:
        if result and point == result[-1]:
            continue
        result.append(point)
        while len(result) >= 3:
            a, b, c = result[-3:]
            if (a[0] == b[0] == c[0]) or (a[1] == b[1] == c[1]):
                result.pop(-2)
            else:
                break
    return result


def through_card(points):
    for a, b in zip(points, points[1:]):
        for table in TABLES:
            left, top, right, bottom = card_box(table)
            if a[1] == b[1] and top < a[1] < bottom:
                if max(min(a[0], b[0]), left) < min(max(a[0], b[0]), right):
                    return table
            elif a[0] == b[0] and left < a[0] < right:
                if max(min(a[1], b[1]), top) < min(max(a[1], b[1]), bottom):
                    return table
            elif a[0] != b[0] and a[1] != b[1]:
                raise ValueError(f"Diagonal segment: {a} -> {b}")
    return None


def grid_edge(a, b):
    return (a, b) if a <= b else (b, a)


def astar(start, goal, occupied_edges, occupied_nodes):
    # Direction is part of the search state so turns can be penalized.
    moves = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    first = (*start, -1)
    heap = [(abs(start[0]-goal[0]) + abs(start[1]-goal[1]), 0.0, first)]
    best = {first: 0.0}
    previous = {}
    while heap:
        _, cost, state = heapq.heappop(heap)
        if cost != best.get(state):
            continue
        x, y, previous_direction = state
        if (x, y) == goal:
            nodes = []
            while state in previous:
                nodes.append(state[:2])
                state = previous[state]
            nodes.append(start)
            nodes.reverse()
            return nodes, cost
        for direction, (dx, dy) in enumerate(moves):
            nx, ny = x+dx, y+dy
            if not (1 <= nx < GRID_MAX_X and 1 <= ny < GRID_MAX_Y):
                continue
            if (nx, ny) in BLOCKED:
                continue
            edge = grid_edge((x, y), (nx, ny))
            step_cost = 1.0
            if previous_direction != -1 and previous_direction != direction:
                step_cost += 0.7
            if edge in occupied_edges:
                step_cost += 10.0
            if (nx, ny) in occupied_nodes:
                step_cost += 3.0
            new_cost = cost + step_cost
            new_state = (nx, ny, direction)
            if new_cost >= best.get(new_state, float("inf")):
                continue
            best[new_state] = new_cost
            previous[new_state] = state
            heuristic = abs(nx-goal[0]) + abs(ny-goal[1])
            heapq.heappush(heap, (new_cost+heuristic, new_cost, new_state))
    return None


def endpoint(table, column, side):
    left, _, right, _ = card_box(table)
    y = row_y(table, column)
    sign = -1 if side == "left" else 1
    edge = (left if sign < 0 else right, y)
    port = (edge[0] + sign*PORT, y)
    grid = (round(port[0]/STEP), round(port[1]/STEP))
    gridpoint = (grid[0]*STEP, grid[1]*STEP)
    return edge, port, grid, gridpoint


def route_interference(points, prior_routes):
    crossings, overlap = 0, 0
    for previous in prior_routes:
        for a, b in zip(points, points[1:]):
            horizontal = a[1] == b[1]
            for c, d in zip(previous, previous[1:]):
                other_horizontal = c[1] == d[1]
                if horizontal != other_horizontal:
                    h1, h2, v1, v2 = (a, b, c, d) if horizontal else (c, d, a, b)
                    if min(h1[0], h2[0]) < v1[0] < max(h1[0], h2[0]) and min(v1[1], v2[1]) < h1[1] < max(v1[1], v2[1]):
                        crossings += 1
                elif horizontal and a[1] == c[1]:
                    overlap += max(0, min(max(a[0], b[0]), max(c[0], d[0])) - max(min(a[0], b[0]), min(c[0], d[0])))
                elif not horizontal and a[0] == c[0]:
                    overlap += max(0, min(max(a[1], b[1]), max(c[1], d[1])) - max(min(a[1], b[1]), min(c[1], d[1])))
    return crossings, overlap


def route(fk, occupied_edges, occupied_nodes, prior_routes):
    options = [(a, b) for a in ("left", "right") for b in ("left", "right")]
    if fk.child_table == fk.parent_table:
        options = [("left", "left"), ("right", "right")]
    best_route = None
    for child_side, parent_side in options:
        start, start_port, start_grid, start_point = endpoint(fk.child_table, fk.child_columns[0], child_side)
        end, end_port, end_grid, end_point = endpoint(fk.parent_table, fk.parent_columns[0], parent_side)
        if start_grid in BLOCKED or end_grid in BLOCKED:
            continue
        pre = [start, start_port, (start_port[0], start_point[1]), start_point]
        post = [end_point, (end_port[0], end_point[1]), end_port, end]
        if through_card(pre) or through_card(post):
            continue
        found = astar(start_grid, end_grid, occupied_edges, occupied_nodes)
        if found is None:
            continue
        grid_nodes, cost = found
        points = compress(pre + [(x*STEP, y*STEP) for x, y in grid_nodes] + post)
        hit = through_card(points)
        if hit:
            continue
        crossings, overlap = route_interference(points, prior_routes)
        score = cost + crossings*35 + overlap/10 + (len(points)-2)*0.2
        if best_route is None or score < best_route[0]:
            best_route = (score, points, grid_nodes, child_side, parent_side)
    if best_route is None:
        raise ValueError(f"No obstacle-free route for FK {fk.name}")
    _, points, grid_nodes, child_side, parent_side = best_route
    for a, b in zip(grid_nodes, grid_nodes[1:]):
        occupied_edges.add(grid_edge(a, b))
    occupied_nodes.update(grid_nodes)
    return points, child_side, parent_side


def cardinality_marker(point, side, cardinality, color):
    x, y = point
    sign = -1 if side == "left" else 1
    def bar(distance):
        bx = x + sign*distance
        line([(bx, y-12), (bx, y+12)], color, 3)
    if cardinality == "1":
        bar(11)
        bar(21)
    elif cardinality == "0..1":
        bar(11)
        circle(x+sign*31, y, 7, COLORS["bg"], color, 3)
    elif cardinality == "0..N":
        root = (x+sign*12, y)
        line([root, (x+sign*27, y-12)], color, 3)
        line([root, (x+sign*27, y+12)], color, 3)
        circle(x+sign*41, y, 7, COLORS["bg"], color, 3)
    else:
        raise ValueError(cardinality)


for first, table in enumerate(TABLES):
    a = card_box(table)
    for other in list(TABLES)[first+1:]:
        b = card_box(other)
        if max(a[0], b[0]) < min(a[2], b[2]) and max(a[1], b[1]) < min(a[3], b[3]):
            raise ValueError(f"Cards overlap: {table}, {other}")

rect((0, 0, W, H), COLORS["bg"])
text_at(100, 34, "COBIP  ·  PostgreSQL ERD", FONT_TITLE, COLORS["white"])
text_at(102, 93, "schema.sql 기준  ·  10 tables  ·  98 columns  ·  23 FK constraints", FONT_SUBTITLE, COLORS["muted"])
text_at(3550, 63, "P PK   F FK   U UNIQUE   N NOT NULL", FONT_LEGEND, COLORS["muted"])

# SQL-derived FK constraints, including every component of composite FKs.
occupied_edges, occupied_nodes = set(), set()
routes = []
prior_paths = []
ROUTE_PRIORITY = [
    "templates_created_by_fkey", "user_progress_last_section_fk",
    "categories_parent_category_id_fkey", "template_bookmarks_user_id_fkey",
    "templates_language_id_fkey", "questions_category_id_fkey",
    "questions_section_template_fk", "template_sections_template_id_fkey",
    "submissions_user_id_fkey", "media_files_uploaded_by_fkey",
    "template_sections_parent_fk", "templates_category_id_fkey",
    "media_files_question_id_fkey", "questions_related_template_id_fkey",
    "users_profile_media_fk", "template_bookmarks_template_id_fkey",
    "user_progress_template_id_fkey", "questions_created_by_fkey",
    "media_files_section_id_fkey", "media_files_template_id_fkey",
    "questions_language_id_fkey", "submissions_question_id_fkey",
    "user_progress_user_id_fkey",
]
rank = {name: index for index, name in enumerate(ROUTE_PRIORITY)}
# Priority affects drawing order only; relationships always come from parsed SQL.
ordered = sorted(enumerate(RELATIONS, 1), key=lambda item: rank.get(item[1].name, 999))
for seq, fk in ordered:
    color = COLORS["line"] if seq % 2 == 0 else COLORS["line_alt"]
    points, child_side, parent_side = route(fk, occupied_edges, occupied_nodes, prior_paths)
    if through_card(points):
        raise ValueError(f"Route crosses a card: {fk.name}")
    routes.append({"id": seq, "fk": fk.name, "points": points,
                   "child_side": child_side, "parent_side": parent_side})
    prior_paths.append(points)
    line(points, color, width=3)
    cardinality_marker(points[0], child_side, fk.child_end, color)
    cardinality_marker(points[-1], parent_side, fk.parent_end, color)
    if len(fk.child_columns) > 1:
        label = f"R{seq:02d} · FK({len(fk.child_columns)})"
        lx = points[0][0] + (-92 if child_side == "left" else 35)
        ly = points[0][1] - 29
        text_at(lx, ly, label, FONT_SMALL, COLORS["white"], family="Consolas")

for table, cols in TABLES.items():
    x, y, right, bottom = card_box(table)
    rect((x+6, y+7, right+6, bottom+7), "#17191a", radius=4)
    rect((x, y, right, bottom), COLORS["card"], COLORS["border"], 2, 4)
    rect((x+1, y+1, right-1, y+HEADER_H), COLORS["header"])
    rect((x+1, y+HEADER_H-4, right-1, y+HEADER_H), COLORS["header_edge"])
    text_at(x+19, y+10, table, FONT_TABLE, COLORS["white"], family="Consolas")
    text_at(right-20, y+12, KOREAN[table], FONT_KO, COLORS["white"], anchor="ra")

    col_top = y + HEADER_H
    rect((x+1, col_top, right-1, col_top+COLUMN_H), COLORS["column"])
    for xpos, label in [(x+18, "FLAGS"), (x+140, "COLUMN"), (x+500, "TYPE"), (x+690, "설명")]:
        text_at(xpos, col_top+8, label, FONT_COLUMN, COLORS["muted"])

    for index, (name, typ) in enumerate(cols):
        col = COL[(table, name)]
        ry = col_top + COLUMN_H + index * ROW_H
        if index % 2:
            rect((x+1, ry, right-1, ry+ROW_H), COLORS["card_alt"])
        if col.primary_key or (table, name) in FK:
            rect((x+1, ry, right-1, ry+ROW_H), "#35302b" if col.primary_key else "#2c3435")
        line([(x+1, ry+ROW_H), (right-1, ry+ROW_H)], COLORS["grid"], 1)

        flags = []
        if col.primary_key: flags.append(("P", COLORS["pk"]))
        if (table, name) in FK: flags.append(("F", COLORS["fk"]))
        if col.unique_single and not col.primary_key: flags.append(("U", "#a6c987"))
        if col.unique_group and not col.primary_key: flags.append(("G", "#a6c987"))
        if not col.nullable: flags.append(("N", "#9ab0c3"))
        if col.identity: flags.append(("A", "#d4a574"))
        if col.default is not None: flags.append(("D", "#aaa6cb"))
        for badge_index, (badge, badge_color) in enumerate(flags):
            bx = x + 9 + badge_index * 21
            rect((bx, ry+10, bx+18, ry+28), badge_color, radius=2)
            text_at(bx+4, ry+9, badge, FONT_SMALL, "#172124", family="Consolas")

        text_at(x+140, ry+5, name, FONT_NAME, COLORS["white"], family="Consolas")
        text_at(x+500, ry+6, typ, FONT_TYPE, COLORS["type"], family="Consolas")
        text_at(x+690, ry+6, DESCRIPTIONS[table][name], FONT_DESC, COLORS["text"])

    for xpos in (x+132, x+486, x+678):
        line([(xpos, col_top), (xpos, bottom)], COLORS["grid"], 1)

text_at(100, 2805, "관계선은 SQL의 FK 제약 1개당 1개  ·  복합 FK는 R번호와 컬럼 수 표시  ·  G 복합/부분/표현식 UNIQUE  ·  A IDENTITY  ·  D DEFAULT", FONT_LEGEND, COLORS["muted"])

svg.append("</svg>")
(ROOT / "cobip-erd.svg").write_text("\n".join(svg), encoding="utf-8")
image.save(ROOT / "cobip-erd.png", optimize=True)
(ROOT / "erd-routes.json").write_text(json.dumps(routes, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print(f"Rendered {len(TABLES)} tables, {sum(map(len, TABLES.values()))} columns, {len(RELATIONS)} FK constraints; all routes clear of cards")
