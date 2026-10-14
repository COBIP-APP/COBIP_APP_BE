-- COBIA Python 입문 콘텐츠. Flyway migration이 아닌 선택적 콘텐츠 시드입니다.
-- 관리자 계정이 생성된 로컬 DB에서 psql -v ON_ERROR_STOP=1 -f 로 한 번 실행합니다.
-- 동일한 Python 챕터가 이미 있으면 아무 데이터도 추가하지 않습니다.
BEGIN;

DO $seed$
DECLARE
    admin_id bigint;
    grammar_id bigint;
    python_id bigint;
    chapter_id bigint;
    root1 bigint;
    root2 bigint;
    root3 bigint;
    root4 bigint;
    section11 bigint;
    section12 bigint;
    section21 bigint;
    section22 bigint;
    section31 bigint;
    section32 bigint;
    section41 bigint;
    section42 bigint;
BEGIN
    SELECT user_id INTO admin_id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY user_id LIMIT 1;
    SELECT category_id INTO grammar_id FROM categories WHERE code = 'GRAMMAR';
    SELECT language_id INTO python_id FROM languages WHERE code = 'PYTHON';
    IF admin_id IS NULL OR grammar_id IS NULL OR python_id IS NULL THEN
        RAISE EXCEPTION 'Active admin, GRAMMAR category and PYTHON language are required';
    END IF;
    IF EXISTS (
        SELECT 1 FROM templates
        WHERE category_id = grammar_id AND language_id = python_id
          AND title = 'Python 변수와 자료형' AND deleted_at IS NULL
    ) THEN
        RAISE NOTICE 'Python 변수와 자료형 already exists; skipped';
        RETURN;
    END IF;

    INSERT INTO templates (category_id, language_id, created_by, title, summary, description,
                           difficulty, display_order, is_published)
    VALUES (grammar_id, python_id, admin_id, 'Python 변수와 자료형',
            '변수에 값을 담는 법부터 숫자·불리언·문자열·컬렉션까지, 예제와 출력 예측 문제로 배우는 Python 첫걸음.',
            'Python에서 값에 이름을 붙이는 방법과 자주 쓰는 자료형을 차근차근 익힙니다. 각 개요를 읽고 1-1, 1-2 같은 하위 목차에서 예제를 따라가세요. 하위 목차마다 객관식 두 문제와 출력 결과를 적는 문제 한 개가 있습니다.',
            'EASY',
            (SELECT COALESCE(MAX(display_order), 0) + 1 FROM templates
             WHERE category_id = grammar_id AND language_id = python_id AND deleted_at IS NULL),
            true)
    RETURNING template_id INTO chapter_id;

    INSERT INTO template_sections (template_id, title, body, display_order)
    VALUES (chapter_id, '1. 값에 이름 붙이기', $md$
## 이 장에서 배울 것

프로그램은 **값을 저장하고, 바꾸고, 다시 읽는 일**을 반복합니다. Python의 변수는 값에 붙여 둔 이름입니다. `score = 80`이라고 적으면 이후에 `score`라는 이름으로 80을 사용할 수 있습니다.

처음에는 `=`를 수학의 등호처럼 읽기 쉽지만, 여기서는 오른쪽 값을 계산해 왼쪽 이름에 연결하는 **대입**을 뜻합니다. 왼쪽과 오른쪽이 언제나 같다는 선언은 아닙니다.

- **1-1**에서는 변수 이름과 대입·재대입을 살펴봅니다.
- **1-2**에서는 `type()`으로 값의 자료형을 확인하고 필요한 경우 변환합니다.

> 코드를 읽을 때는 “오른쪽을 계산한 뒤 왼쪽에 넣는다”는 순서를 기억하세요.
$md$, 1)
    RETURNING section_id INTO root1;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root1, '1-1. 변수와 대입', $md$
## 변수는 값을 가리키는 이름

`name = "COBIA"`는 문자열에 `name`이라는 이름을 붙입니다. 다음 줄에서 `print(name)`을 실행하면 그 값을 읽어 화면에 표시합니다. Python은 처음 변수를 사용할 때 `int name`처럼 자료형을 앞에 적지 않습니다.

### 한 줄씩 읽어 보기

1. `visits = 1`로 첫 방문 횟수를 저장합니다.
2. `visits = visits + 1`은 **기존 값에 1을 더한 결과**를 다시 저장합니다.
3. 마지막 `print(visits)`는 `2`를 출력합니다.

변수 이름은 의미를 드러내게 적는 편이 좋습니다. `user_name`, `total_score`처럼 단어 사이에 밑줄을 쓰면 읽기 쉽습니다. 이름은 숫자로 시작할 수 없고, `class` 같은 Python 예약어도 사용할 수 없습니다. 영문 대소문자를 구분하므로 `score`와 `Score`는 서로 다른 이름입니다.

### 기억할 점

- `=`: 오른쪽 값을 왼쪽 변수에 대입합니다.
- `==`: 두 값이 같은지 비교합니다. 비교는 뒤의 조건문 챕터에서 배웁니다.
- 아직 값을 넣지 않은 이름을 읽으면 오류가 납니다.

> **직접 확인:** 예제의 첫 번째 `visits` 값을 5로 바꾸고 마지막 출력값을 예상해 보세요.
$md$, $py$name = "COBIA"
visits = 1
print(name)
print(visits)

visits = visits + 1
print(visits)

user_name = "Mina"
print(user_name)$py$, 1)
    RETURNING section_id INTO section11;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root1, '1-2. 자료형 확인과 변환', $md$
## 같은 모양이어도 자료형은 다릅니다

`42`는 정수 `int`이고, `"42"`는 글자 두 개로 이루어진 문자열 `str`입니다. 화면에는 비슷하게 보이지만 `42 + 3`은 45가 되고 `"42" + "3"`은 `"423"`이 됩니다. 어떤 값을 다루는지 모르겠다면 `type(값)`으로 확인할 수 있습니다.

### 필요한 순간에 변환하기

- `int("42")` → 정수 42
- `float("3.5")` → 실수 3.5
- `str(42)` → 문자열 `"42"`

입력창에서 받은 값은 보통 문자열이므로 계산하려면 먼저 변환해야 합니다. 다만 `int("사십이")`처럼 정수로 해석할 수 없는 문자열은 오류가 납니다. 변환이 항상 성공한다고 가정하지 마세요.

Python에서는 변수에 다른 자료형의 값을 다시 대입할 수 있습니다. 그렇다고 한 변수에 서로 다른 의미의 값을 계속 섞어 넣으면 코드를 이해하기 어려워집니다. `score_text`와 `score`처럼 **역할이 다른 값은 이름도 나누는 것**이 좋습니다.

> **직접 확인:** `"85" + "5"`와 `int("85") + 5`의 결과를 각각 예상해 보세요.
$md$, $py$score_text = "85"
score = int(score_text)
print(type(score_text))
print(type(score))
print(score + 5)

price = 3.5
print(str(price) + "원")$py$, 2)
    RETURNING section_id INTO section12;

    INSERT INTO template_sections (template_id, title, body, display_order)
    VALUES (chapter_id, '2. 숫자와 참·거짓', $md$
## 계산과 판단의 재료

Python의 대표적인 숫자 자료형은 정수 `int`와 실수 `float`입니다. 숫자는 더하고 나누는 데 쓰고, 비교의 결과는 `True` 또는 `False`인 `bool` 값으로 받습니다.

예를 들어 `score >= 60`은 점수가 60 이상인지 판단한 결과를 줍니다. 아직 `if`를 쓰지 않아도 `print(score >= 60)`으로 결과를 바로 확인할 수 있습니다.

- **2-1**에서 나눗셈·몫·나머지의 차이를 배웁니다.
- **2-2**에서 불리언 값과 “값이 없음”을 나타내는 `None`을 구분합니다.

> 숫자처럼 보이는 문자열은 계산 전에 변환해야 한다는 점도 다시 떠올려 보세요.
$md$, 2)
    RETURNING section_id INTO root2;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root2, '2-1. 숫자와 연산자', $md$
## `/`, `//`, `%`는 서로 다릅니다

`7 / 2`는 일반 나눗셈으로 `3.5`를 줍니다. `7 // 2`는 몫을 구해 `3`을 줍니다. `7 % 2`는 나머지인 `1`을 줍니다. 짝수·홀수 판단이나 반복되는 순서를 계산할 때 나머지 연산이 자주 쓰입니다.

### 자주 쓰는 연산

- `+`, `-`, `*`: 더하기, 빼기, 곱하기
- `/`: 나눗셈 결과
- `//`: 몫에 해당하는 값
- `%`: 나머지
- `**`: 거듭제곱, 예를 들어 `2 ** 3`은 8

괄호를 사용하면 계산 순서를 분명하게 만들 수 있습니다. `2 + 3 * 4`는 14이지만 `(2 + 3) * 4`는 20입니다. `int`와 `float`을 함께 계산하면 결과가 실수가 될 수 있으니 `type()`으로 확인해 보세요.

> **직접 확인:** 17개의 사탕을 5명에게 똑같이 나누면 한 명당 몇 개이고 몇 개가 남을까요? `//`와 `%`로 각각 계산해 보세요.
$md$, $py$candies = 17
people = 5

print(candies / people)
print(candies // people)
print(candies % people)

total = 2 + 3 * 4
grouped = (2 + 3) * 4
print(total, grouped)$py$, 1)
    RETURNING section_id INTO section21;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root2, '2-2. 불리언과 None', $md$
## 판단 결과는 `bool`

비교식 `score >= 60`의 결과는 `True` 또는 `False`입니다. 두 단어는 첫 글자가 **대문자**이며 따옴표를 붙이지 않습니다. `"True"`는 불리언이 아니라 문자열입니다.

`bool()`로 값이 참처럼 취급되는지도 확인할 수 있습니다. 숫자 `0`, 빈 문자열 `""`, 빈 리스트 `[]`는 거짓처럼 취급되고, `"0"`처럼 비어 있지 않은 문자열은 참처럼 취급됩니다. 이 차이는 조건문에서 실수를 줄이는 데 중요합니다.

### 값이 아직 없다면

`None`은 “지금 연결된 값이 없다”는 뜻으로 자주 사용합니다. `0`, `False`, `""`와 같은 값이 아닙니다. 값이 `None`인지 확인할 때는 `value is None`을 사용합니다.

> **주의:** `bool("False")`도 문자열이 비어 있지 않으므로 `True`입니다. 글자의 뜻을 읽어서 참·거짓으로 바꾸는 함수가 아닙니다.
$md$, $py$score = 65
passed = score >= 60
print(passed)
print(bool(0))
print(bool("0"))

result = None
print(result is None)$py$, 2)
    RETURNING section_id INTO section22;

    INSERT INTO template_sections (template_id, title, body, display_order)
    VALUES (chapter_id, '3. 문자열 다루기', $md$
## 글자를 저장하고 보여주기

문자열 `str`은 이름, 안내 문구, 사용자가 입력한 값처럼 글자로 다루는 데이터를 표현합니다. 따옴표로 둘러싼 `"123"`은 숫자가 아니라 문자열입니다.

문자열은 각 문자에 위치가 있어 필요한 부분을 꺼낼 수 있고, 여러 값을 보기 좋은 문장으로 조합할 수도 있습니다.

- **3-1**에서 인덱스와 슬라이싱을 살펴봅니다.
- **3-2**에서 f-string과 기본 문자열 메서드를 연습합니다.

> 문자열을 편집할 때는 원본이 바뀌는지, 새로운 문자열이 만들어지는지도 확인해 보세요.
$md$, 3)
    RETURNING section_id INTO root3;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root3, '3-1. 인덱스와 슬라이싱', $md$
## 첫 번째 문자의 위치는 0

`text = "Python"`이라면 `text[0]`은 `P`, `text[1]`은 `y`입니다. 음수 인덱스는 뒤에서부터 세므로 `text[-1]`은 `n`입니다. `len(text)`는 문자열의 길이인 6을 줍니다.

### 일부만 꺼내기

`text[1:4]`는 **1번 위치부터 4번 직전까지** 가져오므로 `yth`입니다. 끝 위치는 결과에 포함되지 않습니다. 앞을 생략한 `text[:2]`는 `Py`, 뒤를 생략한 `text[2:]`는 `thon`입니다.

존재하지 않는 위치 하나를 `text[99]`처럼 직접 읽으면 오류가 나지만, 범위를 자르는 슬라이스는 가능한 범위까지만 가져옵니다. 문자열의 문자를 `text[0] = "J"`처럼 직접 바꿀 수는 없습니다. 필요한 부분을 이어 붙여 새로운 문자열을 만듭니다.

> **직접 확인:** `"COBIA"[1:4]`의 결과를 먼저 예상한 뒤 실행해 보세요.
$md$, $py$text = "Python"
print(text[0])
print(text[-1])
print(text[1:4])
print(text[:2])
print(len(text))

word = "COBIA"
print(word[1:4])$py$, 1)
    RETURNING section_id INTO section31;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root3, '3-2. f-string과 문자열 메서드', $md$
## 값을 문장에 자연스럽게 넣기

이름과 나이를 문장으로 표시하고 싶다면 `f"{name}은 {age}살"`처럼 문자열 앞에 `f`를 붙입니다. 중괄호 안의 변수 값이 문자열에 들어갑니다. `age`가 정수여도 f-string 안에서는 표시할 문자로 바뀌므로 직접 `str(age)`를 이어 붙일 필요가 없습니다.

### 자주 쓰는 메서드

- `text.strip()`: 문자열 **양쪽 끝**의 공백 제거
- `text.upper()`: 영문 대문자로 바꾼 새 문자열
- `text.lower()`: 영문 소문자로 바꾼 새 문자열
- `text.replace("A", "B")`: 일치하는 부분을 바꾼 새 문자열

문자열은 수정 가능한 자료형이 아닙니다. `clean = text.strip()`처럼 **반환된 새 문자열을 변수에 저장**해야 결과를 계속 사용할 수 있습니다. `strip()`은 문자열 중간의 공백까지 모두 지우지는 않습니다.

> **직접 확인:** 예제에서 `name.strip()`을 제거하면 첫 번째 출력의 공백이 어떻게 달라질지 생각해 보세요.
$md$, $py$name = "  Mina  "
age = 20
clean_name = name.strip()

message = f"안녕하세요, {clean_name}님!"
print(message)
print(f"{clean_name}은 {age}살입니다.")
print(clean_name.upper())
print(name)$py$, 2)
    RETURNING section_id INTO section32;

    INSERT INTO template_sections (template_id, title, body, display_order)
    VALUES (chapter_id, '4. 여러 값을 함께 보관하기', $md$
## 하나의 값에서 여러 값으로

변수마다 값을 하나씩 담을 수도 있지만, 관련된 값이 많아지면 묶어서 다루는 편이 편리합니다. Python은 순서가 있는 `list`와 `tuple`, 키로 값을 찾는 `dict`, 중복을 제거하는 `set`을 제공합니다.

- **4-1**에서 리스트와 튜플의 차이를 배웁니다.
- **4-2**에서 딕셔너리와 집합을 다룹니다.

어떤 자료형이든 “순서가 필요한가?”, “값을 바꿀 것인가?”, “키로 찾을 것인가?”, “중복을 없앨 것인가?”를 먼저 생각하면 선택이 쉬워집니다.

> 컬렉션 안의 개별 값도 앞에서 배운 숫자·문자열·불리언일 수 있습니다.
$md$, 4)
    RETURNING section_id INTO root4;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root4, '4-1. 리스트와 튜플', $md$
## 순서가 있는 값 묶음

리스트는 대괄호로 만듭니다. `scores = [80, 90, 100]`에서 `scores[0]`은 80이고 `len(scores)`는 3입니다. `append()`로 끝에 값을 더하거나 `scores[0] = 85`처럼 기존 항목을 바꿀 수 있습니다.

튜플은 보통 소괄호로 적습니다. `point = (3, 5)`처럼 생성한 뒤에는 `point[0] = 4`로 항목을 바꿀 수 없습니다. **리스트는 변경 가능, 튜플은 항목을 직접 변경할 수 없음**이 핵심 차이입니다.

### 헷갈리기 쉬운 대입

`other = scores`는 리스트를 복사하지 않고 **같은 리스트를 함께 가리킵니다**. 한쪽에서 `append()`하면 다른 이름으로 읽을 때도 바뀐 값이 보입니다. 별도 리스트가 필요할 때는 `scores.copy()`처럼 복사해야 합니다.

> **직접 확인:** `values = [1, 2]` 다음에 `values.append(3)`을 실행하면 `len(values)`는 몇일까요?
$md$, $py$scores = [80, 90, 100]
print(scores[0])
scores.append(95)
print(len(scores))

other = scores
other[0] = 85
print(scores[0])

point = (3, 5)
print(point[1])$py$, 1)
    RETURNING section_id INTO section41;

    INSERT INTO template_sections (template_id, parent_section_id, title, body, example_code, display_order)
    VALUES (chapter_id, root4, '4-2. 딕셔너리와 집합', $md$
## 키로 찾기: 딕셔너리

딕셔너리는 `{"name": "Mina", "score": 90}`처럼 **키와 값의 쌍**으로 데이터를 보관합니다. `user["name"]`으로 키에 연결된 값을 읽고, `user["score"] = 95`로 값을 바꿀 수 있습니다. 없는 키를 `user["age"]`처럼 바로 읽으면 오류가 나므로, 기본값이 필요할 때는 `user.get("age", 0)`을 활용합니다.

## 중복 없이 모으기: 집합

집합 `set`은 중복된 값을 하나로 합칩니다. `set([1, 1, 2])`에는 1과 2만 남아 길이가 2입니다. 집합은 순서를 보장하지 않으므로 `items[0]` 같은 위치 접근에는 맞지 않습니다. 빈 집합은 `set()`으로 만들고, 빈 `{}`는 딕셔너리입니다.

### 어떤 상황에 쓸까요?

- 사용자 정보의 필드 이름으로 값을 찾기 → `dict`
- 태그 목록에서 같은 항목을 제거하기 → `set`

> **직접 확인:** `len(set(["A", "B", "A"]))`의 값을 예상해 보세요.
$md$, $py$user = {"name": "Mina", "score": 90}
print(user["name"])
user["score"] = 95
print(user.get("age", 0))

tags = ["Python", "AI", "Python"]
unique_tags = set(tags)
print(len(unique_tags))$py$, 2)
    RETURNING section_id INTO section42;

    INSERT INTO questions (category_id, related_template_id, related_section_id, created_by,
                           language_id, question_type, prompt, code_snippet, choice_options,
                           correct_choice_key, reference_answer, explanation, difficulty,
                           pass_score, display_order, is_published)
    SELECT grammar_id, chapter_id, q.section_id, admin_id, python_id, q.question_type,
           q.prompt, q.code_snippet, q.options::jsonb, q.correct_choice_key,
           q.reference_answer, q.explanation, 'EASY', 70, q.display_order, true
    FROM (VALUES
        (section11, 1, 'MULTIPLE_CHOICE', 'Python 변수 이름으로 사용할 수 있는 것은 무엇인가요?', NULL, $json$[{"key":"1","text":"2score"},{"key":"2","text":"user_name"},{"key":"3","text":"class"},{"key":"4","text":"first-name"}]$json$, '2', NULL, '변수 이름은 숫자로 시작할 수 없고 예약어를 사용할 수 없습니다. 단어를 연결할 때 밑줄을 사용할 수 있습니다.'),
        (section11, 2, 'MULTIPLE_CHOICE', '`score = 85`의 뜻으로 가장 알맞은 것은 무엇인가요?', NULL, $json$[{"key":"1","text":"score가 언제나 85와 같다는 조건"},{"key":"2","text":"85를 출력하는 명령"},{"key":"3","text":"85를 score라는 이름에 대입"},{"key":"4","text":"score와 85를 비교"}]$json$, '3', NULL, '대입 연산자 =는 오른쪽 값을 왼쪽 이름에 연결합니다. 값을 비교할 때는 ==를 사용합니다.'),
        (section11, 3, 'CODE_EXPLANATION', '다음 Python 코드를 실행하면 마지막에 무엇이 출력되나요? 숫자만 입력하세요.', $py$x = 10
x = x + 5
print(x)$py$, NULL, NULL, '15', '기존 x 값 10에 5를 더한 15를 다시 x에 대입합니다.'),

        (section12, 1, 'MULTIPLE_CHOICE', '`type("42")`의 결과가 나타내는 자료형은 무엇인가요?', NULL, $json$[{"key":"1","text":"int"},{"key":"2","text":"float"},{"key":"3","text":"str"},{"key":"4","text":"bool"}]$json$, '3', NULL, '따옴표로 감싼 42는 숫자가 아니라 문자열이며 자료형은 str입니다.'),
        (section12, 2, 'MULTIPLE_CHOICE', '문자열 `"42"`를 정수 42로 바꾸는 표현은 무엇인가요?', NULL, $json$[{"key":"1","text":"str(\"42\")"},{"key":"2","text":"int(\"42\")"},{"key":"3","text":"float(42)"},{"key":"4","text":"type(42)"}]$json$, '2', NULL, 'int("42")를 사용하면 정수로 해석 가능한 문자열을 정수로 변환합니다.'),
        (section12, 3, 'CODE_EXPLANATION', '다음 코드의 출력 결과를 숫자만 입력하세요.', $py$print(int("7") + 3)$py$, NULL, NULL, '10', '문자열 "7"을 정수 7로 변환한 뒤 3을 더합니다.'),

        (section21, 1, 'MULTIPLE_CHOICE', 'Python에서 `7 / 2`의 결과는 무엇인가요?', NULL, $json$[{"key":"1","text":"3"},{"key":"2","text":"3.5"},{"key":"3","text":"1"},{"key":"4","text":"오류"}]$json$, '2', NULL, '/는 일반 나눗셈이며 결과는 3.5입니다.'),
        (section21, 2, 'MULTIPLE_CHOICE', 'Python에서 `7 // 2`의 결과는 무엇인가요?', NULL, $json$[{"key":"1","text":"3"},{"key":"2","text":"3.5"},{"key":"3","text":"1"},{"key":"4","text":"14"}]$json$, '1', NULL, '//는 몫에 해당하는 값을 구합니다. 두 양의 정수 7과 2의 몫은 3입니다.'),
        (section21, 3, 'CODE_EXPLANATION', '다음 코드의 출력 결과를 숫자만 입력하세요.', $py$print(10 % 3)$py$, NULL, NULL, '1', '10을 3으로 나눈 나머지는 1입니다.'),

        (section22, 1, 'MULTIPLE_CHOICE', '`bool([])`의 결과는 무엇인가요?', NULL, $json$[{"key":"1","text":"True"},{"key":"2","text":"False"},{"key":"3","text":"None"},{"key":"4","text":"오류"}]$json$, '2', NULL, '빈 리스트는 거짓처럼 취급되므로 bool([])은 False입니다.'),
        (section22, 2, 'MULTIPLE_CHOICE', '값이 `None`인지 확인할 때 가장 알맞은 표현은 무엇인가요?', NULL, $json$[{"key":"1","text":"value = None"},{"key":"2","text":"value is None"},{"key":"3","text":"value + None"},{"key":"4","text":"bool(None) is None"}]$json$, '2', NULL, 'None과 동일한 객체인지 확인할 때 is None을 사용합니다.'),
        (section22, 3, 'CODE_EXPLANATION', '다음 코드의 출력 결과를 대소문자까지 맞춰 입력하세요.', $py$print(bool("0"))$py$, NULL, NULL, 'True', '"0"은 비어 있지 않은 문자열이므로 참처럼 취급됩니다.'),

        (section31, 1, 'MULTIPLE_CHOICE', '`"Python"`의 첫 번째 문자 인덱스는 무엇인가요?', NULL, $json$[{"key":"1","text":"0"},{"key":"2","text":"1"},{"key":"3","text":"-2"},{"key":"4","text":"6"}]$json$, '1', NULL, 'Python을 포함한 시퀀스의 첫 위치는 0입니다.'),
        (section31, 2, 'MULTIPLE_CHOICE', '`text[1:4]`가 가져오는 범위는 무엇인가요?', NULL, $json$[{"key":"1","text":"1부터 4까지 포함"},{"key":"2","text":"1부터 4 직전까지"},{"key":"3","text":"0부터 4까지 포함"},{"key":"4","text":"4번째 문자 하나"}]$json$, '2', NULL, '슬라이스의 끝 인덱스는 결과에 포함되지 않습니다.'),
        (section31, 3, 'CODE_EXPLANATION', '다음 코드의 출력 결과를 따옴표 없이 입력하세요.', $py$print("Python"[1:4])$py$, NULL, NULL, 'yth', '인덱스 1, 2, 3에 있는 y, t, h를 가져옵니다.'),

        (section32, 1, 'MULTIPLE_CHOICE', '변수 `name`을 문자열 안에 넣는 f-string은 무엇인가요?', NULL, $json$[{"key":"1","text":"\"Hi, name\""},{"key":"2","text":"f\"Hi, {name}\""},{"key":"3","text":"str\"Hi, name\""},{"key":"4","text":"f(Hi, name)"}]$json$, '2', NULL, '문자열 앞에 f를 붙이고 중괄호 안에 변수를 적습니다.'),
        (section32, 2, 'MULTIPLE_CHOICE', '`"  hi  ".strip()`의 결과는 무엇인가요?', NULL, $json$[{"key":"1","text":"\"  hi  \""},{"key":"2","text":"\"hi\""},{"key":"3","text":"\"HI\""},{"key":"4","text":"\"h i\""}]$json$, '2', NULL, 'strip()은 문자열 양쪽 끝의 공백을 제거한 새 문자열을 돌려줍니다.'),
        (section32, 3, 'CODE_EXPLANATION', '다음 코드의 출력 결과를 그대로 입력하세요.', $py$name = "Mina"
print(f"Hi, {name}")$py$, NULL, NULL, 'Hi, Mina', 'f-string의 {name} 자리에 Mina가 들어갑니다.'),

        (section41, 1, 'MULTIPLE_CHOICE', '항목을 추가하거나 수정할 수 있는 자료형은 무엇인가요?', NULL, $json$[{"key":"1","text":"tuple"},{"key":"2","text":"list"},{"key":"3","text":"str"},{"key":"4","text":"int"}]$json$, '2', NULL, '리스트는 변경 가능하며 append()로 항목을 추가할 수 있습니다.'),
        (section41, 2, 'MULTIPLE_CHOICE', '`point = (3, 5)`에 대해 올바른 설명은 무엇인가요?', NULL, $json$[{"key":"1","text":"point[0] = 4로 값을 바꿀 수 있다"},{"key":"2","text":"항목이 없는 딕셔너리다"},{"key":"3","text":"튜플이므로 항목을 직접 변경할 수 없다"},{"key":"4","text":"append()로 값을 더할 수 있다"}]$json$, '3', NULL, '튜플은 항목에 새 값을 대입해 직접 변경할 수 없습니다.'),
        (section41, 3, 'CODE_EXPLANATION', '다음 코드의 출력 결과를 숫자만 입력하세요.', $py$values = [1, 2]
values.append(3)
print(len(values))$py$, NULL, NULL, '3', 'append(3)으로 세 번째 항목이 추가되어 길이는 3입니다.'),

        (section42, 1, 'MULTIPLE_CHOICE', '딕셔너리 `{"name": "Mina"}`에서 `"name"`은 무엇인가요?', NULL, $json$[{"key":"1","text":"키"},{"key":"2","text":"인덱스"},{"key":"3","text":"리스트 항목"},{"key":"4","text":"집합 원소"}]$json$, '1', NULL, '딕셔너리는 키와 값의 쌍으로 데이터를 보관합니다.'),
        (section42, 2, 'MULTIPLE_CHOICE', '`set([1, 1, 2])`에 대한 설명으로 옳은 것은 무엇인가요?', NULL, $json$[{"key":"1","text":"길이는 3이다"},{"key":"2","text":"중복된 1이 제거되어 길이는 2이다"},{"key":"3","text":"첫 항목을 [0]으로 읽는다"},{"key":"4","text":"빈 딕셔너리가 된다"}]$json$, '2', NULL, '집합은 중복을 제거하므로 1과 2만 남습니다.'),
        (section42, 3, 'CODE_EXPLANATION', '다음 코드의 출력 결과를 숫자만 입력하세요.', $py$print(len(set([1, 1, 2])))$py$, NULL, NULL, '2', '중복된 1이 제거되어 서로 다른 원소는 두 개입니다.')
    ) AS q(section_id, display_order, question_type, prompt, code_snippet,
           options, correct_choice_key, reference_answer, explanation);

    RAISE NOTICE 'Created Python template % with 12 sections and 24 questions', chapter_id;
END
$seed$;

COMMIT;
