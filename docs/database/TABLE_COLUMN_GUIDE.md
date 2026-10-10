# 앱 DB 테이블·컬럼 설명

기준: [`V1__initial_schema.sql`](../../src/main/resources/db/migration/V1__initial_schema.sql). PostgreSQL 16 초기 스키마의 **10개 테이블·98개 컬럼**을 설명한다. 이 문서는 구현 이해를 돕는 자료이고, 실제 제약과 기본값은 SQL이 기준이다. `PK`는 행의 식별자, `FK`는 다른 테이블을 참조하는 값, `NULL 가능`은 비워 둘 수 있다는 뜻이다.

## 먼저 보는 데이터 흐름

`languages`의 Java·Python·JavaScript와 `categories`의 문법·실무·코딩문제는 목록의 분류다. `templates` 한 행이 학습 카드이자 챕터 한 개이며, `template_sections`가 그 안의 1·1-1·1-2 같은 목차와 설명이다. `questions`는 목차에 붙이거나 독립 문제로 둘 수 있다. 사용자가 문제를 다시 풀 때마다 `submissions`에 새 행을 만들고, 챕터의 현재 위치와 완료 여부는 `user_progress` 한 행에 갱신한다.

초기 데이터에는 언어 3개와 문법·실무·코딩문제 및 실무 하위 분류만 있다. **템플릿·목차·문제·회원은 자동 생성되지 않는다.** 따라서 학습 조회 API를 만들더라도 관리자가 자료를 등록하기 전에는 목록이 비어 있다.

## 1. `users` — 회원

로그인 계정, 권한, 이메일 인증 및 필수 약관 동의 기록이다. A 담당 영역이며, B의 학습·제출 기록이 `user_id`로 이 테이블을 참조한다.

- `user_id` (PK): 회원 한 명을 식별하는 ID.
- `email`: 로그인 이메일. 대소문자를 구분하지 않는 중복 금지 인덱스가 있다.
- `nickname`: 앱에 표시할 닉네임. 중복할 수 없다.
- `password_hash`: 해시된 비밀번호. 비밀번호 원문을 저장하지 않는다.
- `role`: `USER` 또는 `ADMIN` 권한. 기본값은 `USER`.
- `status`: `ACTIVE`·`SUSPENDED`·`WITHDRAWN` 계정 상태. 기본값은 `ACTIVE`.
- `email_verified`: 이메일 인증 완료 여부. 기본값은 `false`.
- `service_terms_agreed_at`: 서비스 이용약관에 동의한 시각. 가입 시 필수로 기록한다.
- `privacy_terms_agreed_at`: 개인정보 관련 필수 문서에 동의한 시각. 가입 시 필수로 기록한다.
- `profile_media_id` (FK, NULL 가능): 현재 프로필 이미지의 `media_files.media_id`. 한 이미지가 여러 회원의 프로필로 지정되지 않게 `UNIQUE`다.
- `created_at`: 회원 행 생성 시각.
- `updated_at`: 회원 정보의 마지막 수정 시각. 수정 API에서 갱신해야 한다.

이메일 인증번호와 JWT 관련 임시 값은 이 SQL에 없다. 계획대로 Redis에 만료 시간을 두고 관리할 영역이다.

## 2. `languages` — 프로그래밍 언어

문법 화면 상단의 언어 선택지다. 관리자가 언어를 추가할 수 있도록 별도 테이블로 뒀다. 실무의 캐시·DB·네트워크 같은 주제는 이 테이블이 아니라 `categories`에 둔다.

- `language_id` (PK): 언어 ID.
- `code`: 내부 식별 코드. 초기값은 `JAVA`, `PYTHON`, `JAVASCRIPT`이며 중복할 수 없다.
- `name`: 화면 표시 이름. 초기값은 Java, Python, JavaScript이며 중복할 수 없다.
- `display_order`: 언어 선택지 순서. 양수이며 중복할 수 없다.
- `is_active`: 언어 선택지 사용 여부. 기본값은 `true`. 하단 메뉴 전체의 노출 여부가 아니다.

## 3. `categories` — 앱 영역과 실무 주제

최상위에는 `GRAMMAR`(문법), `PRACTICAL`(실무), `CODING_PROBLEM`(코딩문제)가 있다. 실무 아래에는 캐시·DB·네트워크·로그가 초기 등록된다. 화면 하단 메뉴 자체를 제어하는 테이블은 아니다.

- `category_id` (PK): 분류 ID.
- `parent_category_id` (FK, NULL 가능): 상위 `categories.category_id`. 최상위 영역은 비우고, 실무 하위 주제는 실무 분류 ID를 넣는다.
- `code`: 내부 식별 코드. 예: `PRACTICAL_CACHE`. 중복할 수 없다.
- `name`: 화면 표시 이름. 예: `캐시`.
- `display_order`: 같은 단계의 분류 간 표시 순서. 양수다.
- `is_active`: 이 분류를 선택지로 사용할지 여부. 기본값은 `true`.

같은 부모 아래에서는 이름과 표시 순서를 중복할 수 없다. 자기 자신을 부모로 지정하는 것도 막는다. 더 깊은 순환 관계 검사는 이 제약만으로는 해결되지 않는다.

## 4. `templates` — 학습 카드/챕터

문법의 `Java 조건문` 또는 실무의 `캐시 관리와 Redis 활용` 같은 카드 한 개다. **기존 웹의 프로젝트 제작 템플릿과는 의미가 다르다.** 상세 목차·본문은 `template_sections`에 둔다.

- `template_id` (PK): 챕터 ID.
- `category_id` (FK): 소속 `categories.category_id`. 문법이면 문법 영역, 실무이면 캐시 같은 실무 하위 분류를 연결한다.
- `language_id` (FK, NULL 가능): 소속 `languages.language_id`. Java·Python·JavaScript 등 언어별 챕터에 사용하고, 언어와 관계없는 실무 챕터는 비울 수 있다.
- `created_by` (FK): 등록한 관리자 계정의 `users.user_id`. 관리자 여부는 API에서 확인해야 한다.
- `title`: 카드·챕터 제목.
- `summary`: 목록 카드에 보여줄 짧은 소개.
- `description`: 챕터 상세 소개 또는 개요. 목차별 본문은 아니다.
- `difficulty`: `EASY`·`NORMAL`·`HARD` 난이도. 기본값은 `EASY`.
- `display_order`: 같은 분류·언어 조합에서 챕터 표시 순서. 양수이고 조합 안에서 중복할 수 없다.
- `is_published`: 사용자에게 공개할지 여부. 기본값은 `false`; 조회 API는 공개된 챕터만 반환해야 한다.
- `created_at`: 챕터 등록 시각.
- `updated_at`: 챕터 마지막 수정 시각.

SQL은 `category_id`와 `language_id`의 조합 자체가 업무적으로 적절한지까지 검사하지 않는다. 예를 들어 문법 챕터에 언어를 요구하는 규칙은 등록 API에서 확인해야 한다.

## 5. `template_sections` — 챕터 안의 목차와 내용

`1. 캐시가 필요한 상황`과 그 아래 `1-1. 적용 기준` 같은 목차 항목이다. 번호 문자열을 저장하는 것이 아니라 부모 관계와 순서로 화면에서 번호를 만든다.

- `section_id` (PK): 목차 항목 ID.
- `template_id` (FK): 이 항목이 속한 `templates.template_id`.
- `parent_section_id` (FK, NULL 가능): 상위 목차 항목의 `section_id`. 1·2 같은 상위 항목은 비우고, 1-1은 1의 ID를 넣는다.
- `title`: 목차 항목 제목.
- `body` (NULL 가능): 사용자가 읽는 설명 본문. 제목만 있는 상위 항목이면 비울 수 있다.
- `example_code` (NULL 가능): 이 항목에서 별도로 보여줄 예제 코드. 예제가 없으면 비운다.
- `display_order`: 같은 부모 아래에서의 순서. 양수이며 중복할 수 없다.

부모 목차는 반드시 **같은 템플릿**에 속해야 한다. `example_code`는 템플릿 전체의 예제가 아니라 이 목차 항목의 선택 입력 예제다.

## 6. `questions` — 문제 원본

객관식·코드 해석·코드 작성 문제를 한 테이블에 둔다. 문제은행의 독립 문제도, 특정 챕터나 1-1 목차에 붙는 문제도 표현할 수 있다.

- `question_id` (PK): 문제 ID.
- `category_id` (FK): 문제가 속한 `categories.category_id`.
- `related_template_id` (FK, NULL 가능): 관련 학습 챕터 ID. 독립 문제면 비울 수 있다.
- `related_section_id` (FK, NULL 가능): 문제가 연결된 목차 항목 ID. 독립 문제 또는 챕터 전체에만 관련되면 비운다.
- `created_by` (FK): 문제를 등록한 관리자 계정 ID. 관리자 여부는 API에서 확인해야 한다.
- `language_id` (FK, NULL 가능): 문제의 프로그래밍 언어. 언어와 관계없는 문제는 비울 수 있다.
- `question_type`: `MULTIPLE_CHOICE`(객관식), `CODE_EXPLANATION`(코드 해석), `CODE_WRITING`(코드 작성) 중 하나.
- `prompt`: 사용자에게 보여줄 문제 지문.
- `code_snippet` (NULL 가능): 코드 해석형 등에서 읽고 분석할 제시 코드.
- `choice_options` (NULL 가능): 객관식 보기 목록을 담는 JSON 배열. 보기 객체의 세부 키 형식은 API 구현 시 확정한다.
- `correct_choice_key` (NULL 가능): 객관식 정답 보기의 키. 정답을 공개하는 API가 아니면 응답에서 제외한다.
- `reference_answer` (NULL 가능): 코드 해석·작성형의 참고 답안. 일반 사용자에게 제출 전 노출하지 않는다.
- `grading_criteria` (NULL 가능): AI 채점 기준 목록을 담는 JSON 배열. AI가 추출하더라도 공개 전 관리자 검토를 권장한다.
- `explanation` (NULL 가능): 풀이 후 보여줄 해설.
- `difficulty`: `EASY`·`NORMAL`·`HARD` 난이도. 기본값은 `EASY`.
- `pass_score`: 통과 기준 점수. 0~100, 기본값은 70.
- `display_order`: 목차 안 또는 문제 목록에서의 표시 순서. 양수다.
- `is_published`: 사용자에게 공개할지 여부. 기본값은 `false`.
- `created_at`: 문제 등록 시각.
- `updated_at`: 문제 마지막 수정 시각.

`related_section_id`가 있으면 `related_template_id`도 필요하고, 두 ID가 같은 템플릿을 가리켜야 한다. 다만 문제 유형별 필수값(예: 객관식 보기·정답)은 SQL이 모두 강제하지 않으므로 문제 등록·공개 API에서 확인해야 한다.

## 7. `media_files` — 이미지의 저장 정보

이미지 바이너리 자체는 PostgreSQL이 아니라 S3에 보관하고, 이 테이블에는 파일 메타데이터와 어느 화면에 붙는지를 기록한다. 현재 기본 서버에 S3 업로드 API가 구현됐다는 뜻은 아니다.

- `media_id` (PK): 이미지 메타데이터 ID.
- `uploaded_by` (FK): 업로드한 `users.user_id`. 학습·문제 이미지 업로드는 API에서 관리자 권한을 확인해야 한다.
- `template_id` (FK, NULL 가능): 챕터 카드·상세에 붙는 이미지의 대상 ID.
- `section_id` (FK, NULL 가능): 특정 목차 항목에 붙는 이미지의 대상 ID.
- `question_id` (FK, NULL 가능): 문제에 붙는 이미지의 대상 ID.
- `media_type`: `PROFILE`·`TEMPLATE`·`SECTION`·`QUESTION` 중 용도.
- `object_key`: S3 객체를 찾는 고유 키. 전체 URL이 아니다.
- `original_name`: 사용자가 업로드한 원래 파일명.
- `content_type`: 파일의 MIME 유형. 예: `image/png`.
- `file_size`: 바이트 단위 크기. 0보다 커야 한다.
- `alt_text` (NULL 가능): 이미지를 설명하는 대체 문구.
- `display_order`: 한 대상에 이미지가 여러 장일 때 표시 순서. 기본값은 0.
- `created_at`: 메타데이터 생성 시각.

프로필 이미지는 대상 FK 셋을 모두 비우고 `users.profile_media_id`로 선택한다. 나머지 유형은 자기 대상 FK 하나만 채워야 한다는 제약이 있다. 프로필 이미지가 해당 사용자가 업로드한 파일인지, 파일 유형·크기가 허용 범위인지 등은 API에서 추가 검사해야 한다.

## 8. `submissions` — 사용자 답안·채점 결과

사용자가 **제출할 때마다 새 행**을 만든다. 같은 문제의 이전 오답도 DB에 남고, 화면에서 최신 결과만 보여줄지는 API·UI에서 정한다.

- `submission_id` (PK): 제출 기록 ID.
- `user_id` (FK): 제출한 회원 ID.
- `question_id` (FK): 푼 문제 ID.
- `selected_choice_key` (NULL 가능): 객관식에서 선택한 보기 키.
- `answer_text` (NULL 가능): 코드 해석 등 글로 작성한 답안.
- `source_code` (NULL 가능): 코드 작성형에서 제출한 소스 코드.
- `grading_status`: `PENDING`(대기)·`COMPLETED`(완료)·`FAILED`(채점 실패). 기본값은 `PENDING`.
- `grading_method` (NULL 가능): `DB`(정답 비교)·`AI`(AI 채점)·`RULE_FALLBACK`(규칙 기반 대체 채점).
- `score` (NULL 가능): 0~100점의 최종 점수. 채점 완료 전에는 비울 수 있다.
- `feedback` (NULL 가능): 사용자에게 전달할 채점 의견.
- `criteria_results` (NULL 가능): 평가 기준별 판단 결과를 담는 JSON 배열. 세부 구조는 AI 응답 규격과 맞춰야 한다.
- `ai_model_version` (NULL 가능): AI 채점을 사용했을 때 모델 또는 채점기 버전 기록.
- `submitted_at`: 사용자가 답을 제출한 시각.
- `graded_at` (NULL 가능): 채점이 끝난 시각.

답안 세 칸(`selected_choice_key`, `answer_text`, `source_code`) 중 **정확히 하나만** 채워야 한다. `COMPLETED`라면 채점 방법·점수·채점 시각이 있어야 한다. 객관식은 저장된 정답과 비교하고, 다른 유형은 합의한 AI 응답을 반영한다. 현재 SQL에는 Docker 실행 결과나 테스트 케이스 테이블이 없다.

## 9. `user_progress` — 회원별 챕터 진행 상태

`user_id`와 `template_id`가 **함께 PK**이므로 회원 한 명·챕터 한 개당 행 하나다. 예를 들어 같은 사용자의 `Java 조건문`과 `Python 반복문`은 각각 별도 행이다.

- `user_id` (PK/FK): 학습 중인 회원 ID.
- `template_id` (PK/FK): 학습 중인 챕터 ID.
- `last_section_id` (FK, NULL 가능): 마지막으로 본 목차 항목. 재진입 시 이어서 보기 위치로 사용한다.
- `started_at`: 이 챕터를 처음 시작한 시각.
- `last_studied_at`: 마지막으로 학습한 시각. 학습 API가 갱신해야 한다.
- `completed_at` (NULL 가능): 챕터를 완료한 시각. 미완료면 비운다.

`last_section_id`는 반드시 같은 `template_id`의 목차여야 한다. 이 테이블에는 진행률 숫자나 문제별 정답 여부가 없다. 진행률 표시 규칙은 API에서 정하고, 문제 풀이 내역은 `submissions`에서 조회한다.

## 10. `template_bookmarks` — 챕터 즐겨찾기

한 회원이 챕터를 즐겨찾기했는지 표현하는 연결 테이블이다. 즐겨찾기한 행이 있으면 저장 상태이고, 없으면 저장하지 않은 상태다.

- `user_id` (PK/FK): 즐겨찾기한 회원 ID.
- `template_id` (PK/FK): 즐겨찾기한 챕터 ID.
- `created_at`: 즐겨찾기를 누른 시각.

## 구현할 때 헷갈리기 쉬운 점

- **테이블 생성과 API 구현은 다르다.** Flyway가 테이블을 만들고 현재 인증·콘텐츠 조회·학습 활동·관리자 콘텐츠 저장 API가 일부 구현됐다. 실제 제공 범위는 [학습 API 명세](../learning-api.md)와 Swagger를 확인한다.
- **`is_active`와 `is_published`는 다르다.** 전자는 언어·분류 선택지 사용 여부이고, 후자는 챕터·문제 공개 여부다. 앱 하단 메뉴를 끄는 값이 아니다.
- **작성자 FK가 관리자 권한을 강제하지 않는다.** `created_by`·`uploaded_by`에 아무 회원 ID나 연결할 수 있으므로 등록 API가 권한을 검사해야 한다.
- **정답과 내부 기준은 일반 조회 응답에서 숨긴다.** `correct_choice_key`, `reference_answer`, 비공개 `grading_criteria`가 제출 전에 노출되지 않도록 DTO를 분리한다.
- **재제출은 덮어쓰지 않는다.** `submissions`에 새 행을 만들고 필요하면 최신 제출을 조회한다.
- **이미지·AI·이메일/토큰 기능은 스키마만으로 동작하지 않는다.** SMTP·Redis 기반 인증은 구현됐지만 S3 이미지 업로드와 FastAPI 생성·채점 연동은 별도 작업이다.

관계선을 보며 읽고 싶다면 [`cobip-erd.svg`](cobip-erd.svg)와 [`erd-relationships.md`](erd-relationships.md)를 함께 참고한다.
