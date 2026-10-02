# COBIP PostgreSQL 스키마 초안

[`schema.sql`](schema.sql)은 모바일 앱의 **10개 테이블**을 정의하는 초기 설계 사본이다. Spring Boot는 동일한 초기 스키마를 [`V1__initial_schema.sql`](../../src/main/resources/db/migration/V1__initial_schema.sql)로 Flyway를 통해 빈 개발 DB에 적용한다. 이후 변경은 적용된 V1을 수정하지 않고 새 버전의 마이그레이션에 기록한다. 2026-09-30에 임시 PostgreSQL 16의 빈 DB에 SQL을 적용해 `COMMIT`까지 확인했다. 이어서 관리자 언어·실무 분류 추가, 1/1-1/1-2 목차, 선택 입력 예제 코드, 목차별 문제·학습 재개 위치를 임시 트랜잭션에서 검증했다. 다른 템플릿의 목차에 문제를 연결하려는 시도는 외래 키로 거부되었다. 기존 데이터가 있는 DB에 V1을 적용하지 않는다.

## ERD 이미지

- [`cobip-erd.png`](cobip-erd.png): ERDCloud 스타일의 고해상도 이미지. 10개 테이블, 98개 컬럼, 23개 외래 키 연결을 표시한다.
- [`cobip-erd.svg`](cobip-erd.svg): 확대해서 보기 좋은 벡터 이미지.
- [`erd-relationships.md`](erd-relationships.md): SQL의 `REFERENCES`와 `FOREIGN KEY`에서 먼저 추출한 관계 23개의 방향·복합 컬럼·카디널리티 목록.
- [`erd-schema.json`](erd-schema.json): SQL에서 추출한 구조화 데이터. 컬럼 타입, NULL, DEFAULT, IDENTITY, PK, UNIQUE, FK를 포함한다.
- [`erd-routes.json`](erd-routes.json): 관계별 실제 선 좌표와 카드 접속 방향. 생성기는 각 선이 카드 내부를 관통하지 않는지 검사한다.
- [`erd_model.py`](erd_model.py), [`generate_erd.py`](generate_erd.py): `python generate_erd.py`로 SQL 파싱 → 관계 검증 → 장애물 회피 직각선 계산 → PNG/SVG 생성을 다시 실행한다. 카드의 한글 설명과 배치 정보만 렌더러에 둔다.

2026-10-01에 현재 SQL을 PostgreSQL 16의 빈 DB에 적용해 파서와 실제 카탈로그를 대조했다. 10개 테이블·98개 컬럼의 타입/NULL/IDENTITY/DEFAULT, FK 23개의 방향·컬럼 순서, PK/UNIQUE 인덱스 26개가 모두 일치했다. 복합 FK 3개는 각각 하나의 관계선과 `FK(2)` 표시로 표현하며 전체 컬럼 쌍은 관계 목록에 기록한다. `users.profile_media_id`는 NULL 허용 + UNIQUE FK라서 `media_files`와 선택적 1:1로 표시한다.

초기 SQL 사본을 변경한다면 ERD를 다시 생성하고, 새 컬럼의 한글 설명을 렌더러에 추가한다. ERD 이미지는 시각 자료이며 실행되는 DB 정의는 Flyway 마이그레이션이 기준이다.

## 테이블과 화면의 관계

| 테이블 | 역할 |
|---|---|
| `users` | 회원, 이메일 인증 여부, 필수 약관 두 항목의 동의 시각, 관리자 권한 |
| `languages` | 관리자가 추가할 수 있는 프로그래밍 언어. Java·Python·JavaScript 초기 데이터 포함 |
| `categories` | 문법·실무·코딩문제 영역과 그 하위 주제. 실무의 캐시·DB·네트워크·로그 초기 데이터 포함 |
| `templates` | 학습 목록의 카드/챕터. 예: 캐시 관리와 Redis 활용 |
| `template_sections` | 챕터 안의 목차와 학습 내용. 예: 1. 캐시가 필요한 상황 → 1-1. 적용 기준 |
| `questions` | 객관식·코드 해석·코드 작성 문제. 목차 항목별 문제도 연결 가능 |
| `media_files` | 프로필·챕터·목차 항목·문제 이미지의 S3 객체 정보 |
| `submissions` | 제출할 때마다 새 행을 남기는 답안 및 채점 결과 |
| `user_progress` | 사용자·챕터별 시작·마지막 학습·완료 시각과 마지막으로 본 목차 항목 |
| `template_bookmarks` | 사용자별 챕터 즐겨찾기 |

`languages`와 `categories`는 모두 화면에서 필터처럼 보이지만 뜻이 다르다. Java는 언어이고 캐시는 실무 주제다. 관리자에게 언어 추가 기능을 제공할 수 있도록 `languages`를 별도 테이블로 뒀다. `categories.parent_category_id`는 실무 아래 캐시·DB·네트워크·로그처럼 하위 주제를 연결한다. 실무에 배포 등의 새 주제를 추가할 때 SQL의 고정 목록을 수정할 필요가 없다. `전체` 칩은 카테고리 행이 아닌 필터를 적용하지 않은 상태다. 현재 설계에서는 한 템플릿이 하나의 카테고리에 속한다.

문법 템플릿은 `language_id`로 Java·Python 등과 연결한다. 언어와 관계없는 실무 템플릿은 `language_id`를 비울 수 있다. 코딩 문제는 필요할 때 언어를 지정한다. 관리자 등록 API는 문법 템플릿에는 언어가 있고, 실무 템플릿은 실무 하위 카테고리에 속하며, 목차에 연결된 문제는 해당 템플릿과 언어가 모순되지 않는지 확인해야 한다. 이런 영역별 의미 검사는 다른 테이블의 값을 조회해야 하므로 이 SQL의 단순 `CHECK`만으로는 보장하지 않는다. `is_active`는 언어/주제를 선택 목록에서 숨길 때 쓰며, 앱 하단 탐색 메뉴 표시 여부와는 관계없다.

## 목차, 예제 코드, 문제 풀이

`templates`는 카드 제목·요약·개요를 갖는다. `template_sections`는 그 안의 목차를 갖는다. `parent_section_id`와 `display_order`로 1, 1-1, 1-2처럼 표시하며 번호 문자열 자체는 저장하지 않는다. 같은 챕터 안의 목차만 부모·자식으로 연결되게 외래 키를 뒀다. `body`는 해당 항목의 설명이고 `example_code`는 **선택 입력**이다. 예제가 없는 항목은 `NULL`로 둔다. 템플릿 전체의 `example_code`는 제거했다.

학습 흐름이 `1-1 내용 학습 → 해당 문제 풀이 → 1-2 내용 학습`이면 `questions.related_section_id`로 문제를 1-1 항목에 연결한다. 독립된 문제은행 문제는 이 값을 비워둘 수 있다. `questions.related_template_id`와 `related_section_id`를 모두 사용할 때 두 ID가 같은 템플릿을 가리키도록 외래 키로 확인한다. 학습 재개 위치는 `user_progress.last_section_id`에 두고, 문제의 통과 여부는 해당 항목에 연결된 문제의 `submissions`에서 확인한다. 한 목차 항목에 문제가 여러 개이고 모든 문제를 통과해야 다음 항목을 열지는 학습 API에서 결정한다.

객관식 보기는 초기 구현을 단순하게 하기 위해 `questions.choice_options` JSONB에 보관한다. AI 팀의 평가 파이프라인은 문제 지문·채점 가이드라인에서 불리언 평가 기준을 추출하며, 그 목록을 `questions.grading_criteria` JSONB에 저장한다. 관리자가 공개 전에 추출 결과를 확인·수정하도록 권장한다. 문제 등록 API는 보기 키와 정답 키가 일치하는지, 공개할 AI 문제에 평가 기준이 있는지 확인해야 한다. `correct_choice_key`와 `reference_answer`는 제출 전 사용자에게 보내지 않는다.

## 인증, 이미지, 채점

가입 API는 서비스 이용약관·개인정보 관련 필수 동의를 각각 확인한 뒤 두 동의 시각을 `users`에 기록한다. `updated_at`은 수정 API가 갱신한다. 프로필 이미지 설정 API는 `users.profile_media_id`가 본인에게 업로드된 `PROFILE` 이미지인지 확인해야 한다. 챕터·목차·문제 이미지는 관리자만 등록한다. 실제 삭제 시 S3 객체와 DB 행을 함께 처리해야 하므로 이미지 메타데이터는 자동 삭제하지 않는다.

채점 API는 객관식을 DB 정답과 비교하고, 코드 해석·작성형은 AI의 기준별 Pass/Fail·점수·피드백을 `submissions`에 저장한다. `criteria_results`는 당시 판단 근거를 남기는 필드다. `grading_method`가 `RULE_FALLBACK`이면 실제 AI 채점처럼 표시하지 않는다. 현재는 사용자 코드를 실행하지 않으므로 AI가 추측한 출력을 **실제 실행 결과**로 표시하지 않는다. AI 서버 응답의 JSONB 세부 구조와 Fallback 여부는 연동 전에 합의해야 한다. 현재 공유된 AI API 예시에는 항목별 Pass/Fail 목록과 Fallback 사용 여부가 없으므로, API가 제공하지 않으면 해당 컬럼을 비워두고 전체 피드백만 표시한다.

이메일 인증번호와 JWT 리프레시 토큰 정보는 Redis에서 만료 시간을 두고 관리한다. SMTP 계정·비밀번호와 JWT 서명 키는 서버 환경 설정으로 관리한다. 챗봇 대화는 화면을 떠나면 초기화하고 DB에 저장하지 않는다. 챗봇이 추천하는 챕터 ID는 공개된 `templates.template_id`인지 서버에서 확인한다.

## 나중에 필요하면 추가할 구조

- 한 템플릿을 여러 실무 주제에 동시에 넣어야 한다면 템플릿·카테고리 연결 테이블.
- 문제별 보기를 독립적으로 관리하거나 보기 이미지가 필요하면 `question_options`.
- 챗봇의 과거 대화를 다시 보여주면 대화·메시지 테이블.
- 매일 반복 학습을 집계하면 일별 학습 활동 테이블. 현재 `user_progress`는 챕터별 진행 상태만 저장한다.
- Docker 실행을 도입하면 테스트 케이스·실행 작업·결과 테이블.

실제 운영 DB에 적용할 때는 별도 데이터베이스를 준비하고 버전별 마이그레이션 파일로 관리한다.
