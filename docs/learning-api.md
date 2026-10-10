# COBIA 학습·관리자 API 명세

실행 중인 서버의 전체 요청·응답 스키마는 [Swagger UI](http://localhost:8080/swagger-ui.html)에서 확인한다. 이 문서는 Flutter 팀과 관리자 웹 팀이 호출 순서와 현재 구현 범위를 이해하기 위한 안내다. 인증번호·회원가입·로그인·비밀번호 재설정은 [인증 API 명세](auth-api.md)를 따른다.

## 공통 규칙

- PC에서 실행한 서버 주소: `http://localhost:8080`. 같은 PC의 Android 에뮬레이터 주소: `http://10.0.2.2:8080`.
- 이 문서의 모든 API에는 `Authorization: Bearer <accessToken>`이 필요하다. `/api/admin/**`는 로그인한 계정의 DB 권한이 `ADMIN`이어야 한다. 권한을 DB에서 바꿨다면 다시 로그인해 새 토큰을 받는다.
- JSON 필드명은 `camelCase`, 시간은 ISO 8601 오프셋 문자열이다. 생성은 `201`, 조회는 `200`, 본문 없는 수정·삭제는 `204`다.
- 기존 활동 API는 아직 경로·본문·쿼리에 `userId`를 받지만 JWT의 사용자 ID와 다르면 `403`이다. 클라이언트가 다른 사용자의 ID를 보내도 접근할 수 없다.
- 입력 형식 오류는 `400`, 없는 단건은 `404`, 중복값·존재하지 않는 참조 ID 등 DB 제약 충돌은 `409`가 될 수 있다. 활동·콘텐츠 오류는 `{timestamp,status,error,message}` 형식이고 인증 오류는 `{code,message,fieldErrors?}` 형식이다. 인증 토큰이 없거나 만료되면 `401`이다.

## 사용자 화면: 분류와 콘텐츠

| 메서드 | 경로 | 응답과 용도 |
| --- | --- | --- |
| GET | `/api/languages` | 언어 목록. 기본값은 활성 언어만 |
| GET | `/api/categories` | 카테고리 트리. 기본값은 활성 항목만 |
| GET | `/api/templates?categoryId=&languageId=` | 공개된 학습 챕터 카드 목록. 필터는 선택 |
| GET | `/api/templates/{templateId}` | 공개된 챕터 상세와 목차·미디어 |
| GET | `/api/templates/{templateId}/sections` | 공개된 챕터 목차 트리 |
| GET | `/api/questions?categoryId=&languageId=&templateId=&sectionId=` | 공개된 문제 목록. 필터는 선택 |
| GET | `/api/questions/{questionId}` | 공개된 문제 상세. 정답·모범 답안은 응답하지 않음 |

템플릿·문제 조회의 `publishedOnly=false`는 관리자만 사용한다. 관리자는 이를 이용해 비공개 초안도 조회한다. 일반 사용자가 이 값을 보내면 `403`이다. 일반 사용자는 비공개 템플릿의 목차를 직접 조회할 수도 없다. DB 초기값에는 언어·카테고리만 있고 **템플릿·목차·문제는 자동 생성되지 않는다.**

## 사용자 화면: 계정과 활동

| 메서드 | 경로 | 요청 | 성공 |
| --- | --- | --- | --- |
| GET | `/api/users/me` | 없음 | `200` 내 `userId`, `email`, `nickname`, `role`, `createdAt` |
| GET | `/api/users/me/progress` | 없음 | `200` 내 학습 배열. 기록 없으면 `[]` |
| POST | `/api/questions/{questionId}/submissions` | 답안 객체 | `201` 제출 객체 |
| GET | `/api/questions/{questionId}/submissions/latest?userId={userId}` | 없음 | `200` 최신 제출, 없으면 `404` |
| PUT | `/api/users/{userId}/progress/templates/{templateId}` | 진도 객체 | `200` 저장된 진도 |
| GET | `/api/users/{userId}/progress/templates/{templateId}` | 없음 | `200` 진도, 없으면 `404` |
| GET | `/api/users/{userId}/bookmarks` | 없음 | `200` 공개 템플릿의 북마크 배열. 기록 없으면 `[]` |
| PUT | `/api/users/{userId}/bookmarks/templates/{templateId}` | 없음 | `200` 공개 템플릿의 북마크 한 건. 중복 추가는 기존 기록 반환 |
| DELETE | `/api/users/{userId}/bookmarks/templates/{templateId}` | 없음 | `204`. 이미 없어도 `204` |

### 내 학습과 이어하기

`GET /api/users/me/progress`는 **JWT의 사용자**에게 속한 공개 템플릿의 기록을 `lastStudiedAt` 최신순으로 반환한다. `templateId`, `title`, `categoryCode`, `categoryName`, `languageCode`, `languageName`, `lastSectionId`, `lastSectionTitle`, `startedAt`, `lastStudiedAt`, `completedAt`을 포함한다. `lastSectionId`는 마지막으로 저장한 목차 ID이며 아직 기록하지 않았다면 `null`이다.

```json
[
  {
    "templateId": 5,
    "title": "Java 조건문",
    "categoryCode": "GRAMMAR",
    "categoryName": "문법",
    "languageCode": "JAVA",
    "languageName": "Java",
    "lastSectionId": 20,
    "lastSectionTitle": "1-1. 비교 연산",
    "startedAt": "2026-10-10T02:00:00Z",
    "lastStudiedAt": "2026-10-10T03:00:00Z",
    "completedAt": null
  }
]
```

홈의 ‘이어하기’는 배열에서 첫 번째 **미완료** 항목(`completedAt == null`)을 사용한다. 항목이 없으면 ‘학습 시작하기’를 표시한다. `lastSectionId == null`이면 해당 템플릿의 첫 목차에서 시작한다. 마지막 목차 ID는 방문·저장 위치이므로 **완료한 단계 수나 퍼센트로 해석하지 않는다.** 화면 상태는 기록 없음 / 학습 중 / 완료만 사용한다.

### 진도 저장

`PUT /api/users/{userId}/progress/templates/{templateId}` 요청 예시:

```json
{"lastSectionId":20,"completed":false}
```

사용자·템플릿 조합당 한 행을 생성·갱신한다. 공개 템플릿이어야 하고 `lastSectionId`는 같은 템플릿의 목차여야 한다. `completed:true`는 최초 완료 시각을 기록하고, `false`는 완료를 해제하며, `null` 또는 누락은 기존 완료 상태를 유지한다. 기존 기록에서 `lastSectionId`를 누락하거나 `null`로 보내면 **기존 위치를 유지**한다. 새 기록에서는 위치가 `null`로 시작한다. 응답은 `userId`, `templateId`, `lastSectionId`, `startedAt`, `lastStudiedAt`, `completedAt`이다. 문제 제출만으로 진도가 자동 갱신되지는 않는다.

### 문제 제출과 채점

`POST /api/questions/{questionId}/submissions`에는 현재 아래 세 답안 중 **정확히 하나**와 `userId`를 보낸다. 재제출할 때마다 새 행을 만든다.

```json
{"userId":1,"selectedChoiceKey":"A"}
```

| 필드 | 사용 유형 |
| --- | --- |
| `selectedChoiceKey` | 객관식 선택지 키 |
| `answerText` | 코드 해석·설명형 답안 |
| `sourceCode` | 코드 작성형 답안 |

제출 응답은 `id`, `userId`, `questionId`, 세 답안 필드, `gradingStatus`, `gradingMethod`, `score`, `feedback`, `criteriaResults`, `aiModelVersion`, `submittedAt`, `gradedAt`을 담는다. 문제 유형과 답안 필드가 맞지 않으면 `400`이다. 객관식은 DB 정답 키와 비교해 `COMPLETED`·`DB`·100점 또는 0점으로 즉시 저장한다. 그 외 답안은 현재 `PENDING`으로 저장된다. **FastAPI/AI 채점과 채점 결과 갱신은 아직 연결되지 않았다.**

최신 제출 조회는 `submittedAt` 내림차순, 같은 시각이면 제출 ID 내림차순으로 한 건을 반환한다. 본인에게 기록이 없으면 `404`다. 제출할 때 문제 공개 여부를 확인하지만, 이미 제출한 기록을 다시 조회할 때는 현재 공개 여부를 확인하지 않는다.

### 북마크

북마크 목록은 추가 시각 내림차순, 같은 시각이면 템플릿 ID 내림차순이다. 공개된 템플릿만 목록에 표시한다. 추가 응답은 배열이 아닌 `userId`, `templateId`, `title`, `summary`, `difficulty`, `createdAt` 객체 한 건이다. 이미 북마크가 있으면 새 행을 만들지 않고 최초 `createdAt`을 유지한다. 삭제 응답은 본문 없는 `204`이며, 대상이 없어도 `204`다. 현재 목록은 페이지네이션하지 않는다.

## 관리자 웹: 콘텐츠 저장

관리자 API는 먼저 **비공개 초안**을 저장한다. 관리자가 확인한 뒤 공개 전환 API를 호출해야 앱 목록에 나타난다. AI가 만든 문제도 관리자가 검토·수정한 내용을 일반 문제 저장 API에 보내면 된다. **AI 서버에 생성 요청을 전달하는 API는 아직 없다.**

| 메서드 | 경로 | 성공 |
| --- | --- | --- |
| POST | `/api/admin/languages` | `201 {"id":…}` |
| PUT | `/api/admin/languages/{languageId}` | `204` |
| POST | `/api/admin/categories` | `201 {"id":…}` |
| PUT | `/api/admin/categories/{categoryId}` | `204` |
| POST | `/api/admin/templates` | `201 {"id":…}` 비공개 초안 |
| PUT | `/api/admin/templates/{templateId}` | `204` |
| PATCH | `/api/admin/templates/{templateId}/publication` | `204` |
| POST | `/api/admin/templates/{templateId}/sections` | `201 {"id":…}` |
| PUT | `/api/admin/templates/{templateId}/sections/{sectionId}` | `204` |
| POST | `/api/admin/questions` | `201 {"id":…}` 비공개 초안 |
| GET | `/api/admin/questions/{questionId}` | `200` 정답·모범 답안·평가 기준을 포함한 관리자 상세 |
| PUT | `/api/admin/questions/{questionId}` | `204` |
| PATCH | `/api/admin/questions/{questionId}/publication` | `204` |

언어 저장 본문은 `code`, `name`, `displayOrder`, `active`; 카테고리 생성 본문은 여기에 `parentId`를 더한다. 카테고리 수정에서 부모는 변경할 수 없다. 기존 언어·카테고리 전체 목록은 `/api/languages?activeOnly=false`, `/api/categories?activeOnly=false`로 볼 수 있다.

템플릿 저장 본문은 `categoryId`, `languageId`(선택), `title`, `summary`, `description`, `difficulty`(`EASY|NORMAL|HARD`), `displayOrder`다. 목차 생성 본문은 `parentSectionId`(선택), `title`, `body`(선택), `exampleCode`(선택), `displayOrder`다. 목차 수정에서 부모는 변경할 수 없다. 템플릿 상세는 `GET /api/templates/{templateId}?publishedOnly=false`로 확인한다.

문제 저장 본문은 `categoryId`, `relatedTemplateId`(선택), `relatedSectionId`(선택), `languageId`(선택), `questionType`(`MULTIPLE_CHOICE|CODE_EXPLANATION|CODE_WRITING`), `prompt`, `codeSnippet`(선택), `choiceOptions`(JSON 배열 또는 null), `correctChoiceKey`(선택), `referenceAnswer`(선택), `gradingCriteria`(JSON 배열 또는 null), `explanation`(선택), `difficulty`, `passScore`(0~100), `displayOrder`다. 객관식은 비어 있지 않은 `choiceOptions`와 `correctChoiceKey`가 필요하다. 관련 목차를 지정하면 관련 템플릿도 지정해야 하고, 그 목차가 해당 템플릿에 속해야 한다. 문제 목록은 `GET /api/questions?publishedOnly=false`, 정답을 포함한 상세는 관리자 전용 `GET /api/admin/questions/{questionId}`로 본다.

공개 전환 본문은 템플릿·문제 모두 `{"published":true}` 또는 `{"published":false}`다. 삭제 API와 이미지 업로드 API는 아직 없다. DB에서 관리자 권한을 부여한 계정은 **다시 로그인**해 새 `ADMIN` JWT를 받아야 한다.

## 현재 미구현·추가 결정 사항

- AI 서버를 통한 문제 초안 생성과 제출 답안 채점 연동: FastAPI 계약을 합의한 뒤 추가한다. 현재 코드 작성·설명 답안은 `PENDING`에서 자동으로 바뀌지 않는다.
- 이미지 업로드·S3 저장 및 관리자 웹 화면 자체: 이 백엔드 API 묶음에 포함되지 않았다.
- 단계별 완료 퍼센트·총 학습 시간·개인화 추천: 현재 DB가 측정하지 않는다. 홈 추천은 우선 기존 공개 템플릿 목록에서 선택한다.
- 실제 서비스 약관 원문: 인증 API는 동의 여부와 시각을 저장하지만, Flutter의 약관 화면에는 아직 예시 문구가 있다.
