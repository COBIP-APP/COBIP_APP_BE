# COBIP App BE 개발 컨벤션

Spring Boot 백엔드를 함께 개발할 때 사용하는 기본 규칙입니다. 현재 저장소는 서버 시작 화면만 있는 단계입니다. 기능·API·DB 구조가 정해지면 필요한 규칙을 팀 합의로 추가합니다.

## 1. 브랜치와 PR

- `main`: 발표·배포에 사용할 안정 브랜치입니다. 기능 작업은 직접 커밋하지 않습니다.
- `develop`: 팀원이 만든 기능을 통합하는 브랜치입니다.
- 기능 브랜치는 `develop`에서 만들고 PR로 `develop`에 병합합니다. 발표 가능한 상태가 되면 `develop`에서 `main`으로 PR을 만듭니다.
- 브랜치 이름은 소문자 영문과 kebab-case를 사용합니다: `feature/email-verification`, `fix/token-refresh`, `docs/api-conventions`.
- PR에는 목적, 주요 변경 사항, 실행 또는 확인 방법을 적습니다. API를 추가·변경하면 요청·응답 예시와 FE에 영향을 주는 변경을 함께 적습니다.
- 병합 전 다른 팀원 **1명 이상 승인**을 받습니다. 본인이 올린 PR을 본인이 승인한 것으로 계산하지 않습니다.
- CI의 `verify`가 통과하고 리뷰 대화가 해결된 뒤 병합합니다. 새 커밋을 추가했다면 다시 승인을 받습니다.

## 2. 커밋 메시지

제목은 `type: 한국어 요약` 형식으로 작성합니다. 한 커밋에는 한 가지 목적의 변경을 담고 끝에 마침표를 붙이지 않습니다.

| 유형 | 용도 | 예시 |
| --- | --- | --- |
| `feat` | 기능 추가 | `feat: 이메일 인증 요청 API 추가` |
| `fix` | 오류 수정 | `fix: 만료된 토큰 처리 수정` |
| `docs` | 문서 변경 | `docs: 로컬 실행 방법 보완` |
| `refactor` | 동작을 유지한 구조 정리 | `refactor: 인증 서비스 분리` |
| `test` | 테스트 코드 변경 | `test: 회원가입 검증 사례 추가` |
| `chore` | 설정·도구·의존성 변경 | `chore: 스프링 기본 환경 설정` |

## 3. Java 명명 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스·인터페이스·enum | `UpperCamelCase` | `EmailVerificationService` |
| 메서드·변수·필드 | `lowerCamelCase` | `verificationCode`, `sendCode()` |
| 패키지 | 모두 소문자 | `com.cobip.auth` |
| 상수 | `UPPER_SNAKE_CASE` | `MAX_ATTEMPTS` |
| boolean | 질문처럼 읽히는 이름 | `isVerified`, `hasExpired` |

- 변수는 의미를 드러내는 명사를 사용하고, 메서드는 동사로 시작합니다: `findUser`, `createSubmission`, `verifyEmail`.
- `data`, `temp`, `obj`처럼 역할이 보이지 않는 이름과 `strEmail` 같은 타입 접두어를 피합니다.
- 약어를 이름 전체에 대문자로 쓰지 않습니다: `JwtTokenService`, `userId`, `apiResponse`.
- Spring 컴포넌트 이름은 책임을 드러냅니다: `AuthController`, `AuthService`, `UserRepository`.

## 4. 패키지와 책임

- 기능이 생기면 `com.cobip` 아래에 기능별 패키지를 둡니다. 예: `auth`, `learning`, `question`, `submission`.
- 컨트롤러는 HTTP 요청·응답을 다루고, 서비스는 업무 규칙을 처리하며, 저장소는 데이터 접근을 맡습니다.
- 요청·응답 DTO는 엔티티와 구분합니다. 비밀번호 해시, 인증 코드, 내부 오류 정보가 응답으로 나가지 않도록 합니다.
- 기능이 하나뿐일 때 불필요한 인터페이스나 공통 계층을 먼저 만들지 않습니다. 실제 중복이 생기면 공통 코드로 옮깁니다.
- 패키지 간 참조가 필요하면 공개된 서비스나 명확한 모델을 통해 연결하고 다른 기능의 내부 구현에 직접 의존하지 않습니다.

## 5. API와 오류

- 경로는 복수형 명사와 소문자 kebab-case를 사용합니다: `/api/questions`, `/api/email-verifications`.
- HTTP 메서드와 상태 코드를 용도에 맞게 사용합니다. 생성은 `POST`, 조회는 `GET`, 수정은 `PATCH` 또는 `PUT`, 삭제는 `DELETE`를 기본으로 합니다.
- 요청 검증은 서버에서 수행합니다. Flutter에서 검증했더라도 서버 검증을 생략하지 않습니다.
- FE와 합의한 요청·응답 필드명을 바꿀 때는 PR에 변경 내용을 적습니다. 날짜·시간, 페이징, 오류 응답 형식은 첫 API를 만들 때 함께 확정합니다.
- 예외를 잡고 성공처럼 반환하거나 원인을 기록하지 않은 채 삼키지 않습니다. 사용자에게는 민감정보 없는 메시지를 전달합니다.

## 6. 설정과 비밀값

- 공통 설정은 `application.yml`에 둡니다. 개발 PC별 값과 비밀번호·JWT 서명 키·SMTP 비밀번호·AWS 키는 커밋하지 않습니다.
- 환경별 설정이 필요해지면 `local`·`prod` 프로필을 분리합니다. 현재 기본 프로젝트에는 실제 서비스 설정을 미리 추가하지 않습니다.
- SMTP, JWT, Redis, PostgreSQL, S3, Docker, FastAPI 의존성은 담당 기능을 구현할 때 추가하고 연결 방법을 README에 적습니다.
- 로그에 비밀번호, 인증 코드, JWT 원문, 제출 코드 전체를 남기지 않습니다.
- 이메일 인증번호와 재설정 코드는 만료 시간과 시도 횟수를 관리하며, 저장이 필요하면 원문 대신 해시를 사용합니다.

## 7. 코드 형식과 리뷰

- Java 파일은 UTF-8, 들여쓰기는 공백 4칸을 사용합니다. 파일 끝에 줄바꿈을 둡니다.
- 사용하지 않는 import, 주석 처리한 오래된 코드, 이유 없는 TODO를 남기지 않습니다.
- 긴 메서드는 책임에 따라 나누되 한 줄짜리 메서드만 만들기 위한 분리는 피합니다.
- PR에서 동작 변경과 단순 형식 정리를 가능하면 분리합니다.
- 외부 서비스 호출(SMTP·AI·S3 등)에는 실패와 지연을 고려하고, 실패를 사용자에게 어떻게 보여줄지 FE와 합의합니다.

## 8. 커밋 대상

- 커밋: `src/`, `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradlew.bat`, `gradle/wrapper/`, 공통 문서.
- 제외: `.gradle/`, `build/`, IDE 개인 설정, `.env`, 로그, 비밀키와 개인 PC 경로가 담긴 파일.
- 올리기 전 `git status`로 새 파일과 생성 파일을 확인합니다.
