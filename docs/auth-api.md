# COBIA 인증 API 계약

기준: `feature/auth`의 현재 구현. 요청 본문과 본문이 있는 응답은 JSON이고 필드명은 `camelCase`다. 요청에는 `Content-Type: application/json; charset=utf-8`을 사용한다. 보호된 요청은 `Authorization: Bearer <accessToken>`을 보낸다. 비밀번호·인증번호·토큰 원문을 로그에 남기지 않는다.

## 공통 오류

```json
{"code":"INVALID_REQUEST","message":"입력값을 확인해주세요.","fieldErrors":{"email":"입력값을 확인해주세요."}}
```

`fieldErrors`는 입력 검증 오류에만 포함되며, 해당 필드명과 일반 문구를 담는다. 값이 없으면 JSON에서 생략된다. 클라이언트는 HTTP 상태와 `code`로 분기하고, `fieldErrors`가 있으면 해당 입력칸에 표시한다. 필드별 자세한 문구는 서버가 아직 제공하지 않는다.

| 상태 | 코드 | 의미 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 형식·필수 동의·비밀번호 길이 오류 |
| 400 | `INVALID_CODE` | 인증번호 오류·만료·시도 횟수 초과 |
| 400 | `EMAIL_NOT_VERIFIED` | 이메일 인증을 완료하지 않고 가입 요청 |
| 401 | `INVALID_CREDENTIALS` | 로그인 정보 불일치 또는 사용 불가능한 계정. 계정 존재 여부를 구분해 알리지 않음 |
| 401 | `INVALID_REFRESH_TOKEN` | 재발급 토큰 오류·만료·이미 사용됨 |
| 401 | `UNAUTHORIZED` | 보호된 API에 Access Token이 없거나 유효하지 않음 |
| 403 | `FORBIDDEN` | 로그인은 했지만 접근 권한이 없음 |
| 409 | `EMAIL_ALREADY_USED`, `NICKNAME_ALREADY_USED`, `ACCOUNT_ALREADY_USED` | 가입 시 중복. 동시 가입 충돌은 마지막 코드로 반환 |
| 429 | `CODE_RATE_LIMITED` | 인증번호 재전송 대기 중 |
| 503 | `MAIL_UNAVAILABLE` | 메일 설정/발송 실패 |

## 1. 인증번호 발송

`POST /api/auth/email-verifications/send` — 인증 불필요

```json
{"email":"student@example.com"}
```

`202 Accepted`:

```json
{"message":"인증번호를 발송했습니다. 메일함을 확인해주세요.","expiresInSeconds":300,"resendAfterSeconds":60}
```

이미 가입된 메일에도 같은 응답을 반환해 가입 여부를 노출하지 않는다. 이 경우에는 **메일을 실제로 보내지 않는다.** 새 가입 주소로 발송하려면 SMTP 설정이 필요하다. 6자리 코드는 5분 유효하고 같은 이메일로 60초 안에 재요청하면 429다. 재전송에 성공하면 이전 번호와 이메일 인증 완료 상태는 무효화된다.

## 2. 인증번호 확인

`POST /api/auth/email-verifications/confirm` — 인증 불필요

```json
{"email":"student@example.com","code":"012345"}
```

`200 OK`: `{"verified":true}`. 틀린 번호는 최대 5번까지 입력할 수 있으며 그 뒤에는 새 번호를 요청해야 한다. 확인된 이메일은 30분 안에 가입해야 한다. 이메일을 바꾸면 새 이메일을 다시 인증한다.

## 3. 회원가입

`POST /api/auth/register` — 인증 불필요

```json
{
  "email":"student@example.com",
  "nickname":"두리",
  "password":"example-password-123",
  "serviceTermsAgreed":true,
  "privacyTermsAgreed":true
}
```

이메일은 최대 255자, 닉네임 2~50자, 비밀번호 8~64자다. 비밀번호 확인값은 클라이언트에서 비교하며 API로 보내지 않는다. 서버에서도 **두 필수 동의와 이메일 인증 완료**를 확인한다. 동의 시각은 DB에 서버 시각으로 기록하고 비밀번호는 해시로 저장한다. 이메일 인증 성공과 가입 요청에는 같은 이메일을 사용해야 한다.

`201 Created`:

```json
{"userId":1,"email":"student@example.com","nickname":"두리","role":"USER"}
```

가입 후 로그인 화면으로 이동한다. 회원가입만으로 토큰은 발급하지 않는다.

## 4. 로그인

`POST /api/auth/login` — 인증 불필요

```json
{"email":"student@example.com","password":"example-password-123"}
```

`200 OK`:

```json
{
  "accessToken":"<JWT>",
  "refreshToken":"<opaque-token>",
  "tokenType":"Bearer",
  "accessExpiresInSeconds":900,
  "user":{"userId":1,"email":"student@example.com","nickname":"두리","role":"USER"}
}
```

Access Token은 15분 JWT다. Refresh Token은 14일 유효한 난수 토큰이며 Redis에는 원문이 아닌 해시를 저장한다. Flutter는 토큰을 일반 텍스트 파일·로그에 쓰지 않는다.

## 5. 토큰 재발급

`POST /api/auth/refresh` — Access Token 불필요

```json
{"refreshToken":"<opaque-token>"}
```

`200 OK`: 로그인과 같은 토큰 응답. 기존 Refresh Token은 즉시 폐기되고 새 토큰으로 교체된다. 동시에 두 번 요청하면 한 번만 성공한다.

## 6. 로그아웃

`POST /api/auth/logout` — Access Token 필요

```json
{"refreshToken":"<opaque-token>"}
```

`204 No Content`로 응답 본문은 없다. 해당 Refresh Token과 현재 Access Token을 폐기한다. 다른 기기에서 발급받은 토큰은 유지한다. Flutter는 응답 후 로컬 토큰을 삭제하고 로그인 화면으로 이동한다.

## 프론트 연결 메모

- 메일 발송 응답의 `expiresInSeconds`와 `resendAfterSeconds`로 타이머를 시작한다. 타이머 숫자를 화면에 고정값으로 넣지 않는다.
- 이메일 인증 후 이메일 필드를 수정하면 `verified` 상태를 해제한다.
- 401 응답은 자동으로 무한 재시도하지 않는다. 재발급이 실패하면 저장한 토큰을 지우고 로그인 화면으로 보낸다.
- 앱 표시 이름은 **COBIA**다. GitHub 저장소명과 Java 패키지명은 기존 값을 유지해도 API 동작에는 영향이 없다.
