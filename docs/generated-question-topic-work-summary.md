# fix: AI 생성 문제 주제 저장과 조회 응답 보완

## 목적

AWS·Docker 등의 생성 주제가 언어 매핑 실패 시 유실되어 재로그인 후 구분되지 않는 문제를 해결한다. 브랜치는 `develop`에서 만든 `fix/generated-question-topic`이며, 작업 정리는 `CONVENTIONS.md`의 PR 규칙(목적·주요 변경 사항·실행 또는 확인 방법·API 예시·FE 영향)을 따른다. README에는 별도 작업 보고 템플릿이 없다.

## 주요 변경 사항

- 기존 마이그레이션을 수정하지 않고 V6로 `questions.generation_topic text NULL`을 추가한다. 기존 데이터는 추측해 채우지 않는다.
- 요청의 `category`를 trim하여 저장하고, 기존 언어 코드·이름을 대소문자 구분 없이 조회한다. JS → JAVASCRIPT 별칭은 유지한다. 대응하는 언어가 없으면 언어 정보는 null이다.
- 실제 INSERT부터 공통 SELECT·행 매핑·QuestionResponse까지 연결하여 저장 신규/재시도, 본인 목록, 공개 목록, 상세에서 `generationTopic`을 반환한다. Swagger에 입력 의미와 nullable 응답 필드를 설명한다.
- CODING_PROBLEM·JWT 소유·비공개·챕터/목차 미연결, 배열/단일 객체 응답 형식, 해설 공개 정책, 소프트 삭제·재시도 처리와 기존 제출 로직을 유지한다.
- README, 학습 API 명세, DB 컬럼 설명을 보완하고 PostgreSQL 회귀 테스트를 추가한다.

## 요청·응답 예시

```json
{
  "clientRequestId": "8d72de82-ae71-46d0-9d1d-b4d7b9757751",
  "category": "  AWS  ",
  "difficulty": "중급",
  "question": {
    "id": 1,
    "type": "ox",
    "question": "Amazon EC2는 가상 서버를 제공하는 서비스다.",
    "options": ["O", "X"],
    "answer": "O",
    "explanation": "EC2에서는 가상 서버 인스턴스를 실행할 수 있다.",
    "references": [],
    "gradingKeywords": []
  }
}
```

관련 응답 필드(기존 나머지 필드는 유지):

```json
{
  "categoryCode": "CODING_PROBLEM",
  "languageId": null,
  "languageCode": null,
  "languageName": null,
  "generationTopic": "AWS",
  "relatedTemplateId": null,
  "relatedSectionId": null,
  "published": false
}
```

## 실행 또는 확인 방법

Java 21에서 CI와 동일한 빌드를 실행한다:

```powershell
.\gradlew.bat assemble --no-daemon
```

회귀 테스트에는 별도로 준비한 **폐기 가능한 PostgreSQL DB**를 명시한다. 테스트는 임의 스키마에 V1~V5 적용 → 기존 문제 생성 → V6 적용을 검증하고, 끝나면 해당 임시 스키마만 제거한다. DB URL이 없으면 DB 통합 테스트는 건너뛴다.

```powershell
$env:QUESTION_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/postgres'
$env:QUESTION_TEST_DB_USER = 'topic_test'
$env:QUESTION_TEST_DB_PASSWORD = '<임시 DB 비밀번호>'
.\gradlew.bat test assemble --rerun-tasks --no-daemon
```

테스트는 컨트롤러와 실제 PostgreSQL 저장소를 함께 실행한다. AWS·Docker·Java·aws, JS 별칭, 동적 언어 코드·이름, 빈 입력 검증, 새 저장 201·재시도 200·삭제 후 동일 ID 409, DB 원문 저장, 새 저장소/JWT에서 재조회, 네 응답 경로의 주제, 기존 데이터 null, 비공개 접근 제한, 공개 목록과 챕터 문제 유지, 해설 숨김, 소프트 삭제와 제출 행 보존을 확인한다. 실제 Flutter 재실행·로그인, 화면 필터와 AI 채점 서비스는 이 테스트의 범위에 포함되지 않는다.

앱 확인은 README의 Docker 재빌드 명령으로 서버를 갱신한 뒤 AWS·Docker·Java를 저장하고 앱 재실행·새 로그인 후 본인 목록과 상세의 주제를 확인한다.

### 수행 결과 (2026-10-13)

- Java 21 `assemble --no-daemon`: 성공.
- 별도 임시 PostgreSQL 18.3에서 `test assemble --rerun-tasks --no-daemon`: 성공. DB 회귀 테스트 1개가 실제 실행되어 통과했다.
- `git diff --check`: 통과. 검증 후 임시 PostgreSQL 서버를 종료했다.
- Docker 엔진이 실행되지 않아 Docker 재빌드와 Flutter 앱 재실행·로그인은 수행하지 않았다. 재조회는 새 저장소·컨트롤러·JWT 객체로 검증했다.

## FE 영향

요청 경로·필드는 그대로이며 문제 응답에 `generationTopic: string | null`만 추가된다. FE는 최초 입력 주제를 `category`로 전달하고, 언어 미연결 문제를 generationTopic으로 표시·필터링한다. AWS/aws 통합과 일반 문제 대상 제한은 FE에서 수행한다. 공통 공개 문제 API에서 챕터 문제를 제외하지 않으며 비공개 저장 문제는 공개 목록에 노출하지 않는다.
