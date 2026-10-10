# COBIA Flutter 인증 연결 안내

인증 요청·응답의 전체 계약은 [인증 API 명세](auth-api.md), 학습·관리자 API는 [학습 API 명세](learning-api.md), 실행 중 서버의 전체 스키마는 [Swagger UI](http://localhost:8080/swagger-ui.html)를 본다. 이 파일은 Flutter 팀이 로컬 앱을 백엔드에 연결할 때 필요한 순서만 정리한다.

## 로컬 실행

1. 백엔드 저장소의 [README](../README.md)에 따라 Docker Desktop에서 백엔드·PostgreSQL·Redis를 실행한다. 같은 PC 브라우저에서 Swagger가 열리는지 확인한다.
2. 같은 PC의 Android 에뮬레이터에서 Flutter를 실행한다.

```powershell
flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

3. Android Studio의 실행 버튼을 쓸 경우 실행 구성의 **Additional run args**에 `--dart-define=API_BASE_URL=http://10.0.2.2:8080`을 설정하고 앱을 완전히 다시 실행한다. 핫 리로드만으로는 실행 인자가 바뀌지 않는다.

PC 브라우저는 `http://localhost:8080`, 같은 PC의 에뮬레이터는 `http://10.0.2.2:8080`을 쓴다. 실기기는 백엔드 PC의 LAN 주소와 포트 공개 설정이 필요하다. Flutter 앱에 SMTP 비밀번호·DB 비밀번호·JWT 서명 키를 넣지 않는다.

## 화면별 호출 순서

| 화면 동작 | API | 다음 단계 조건 |
| --- | --- | --- |
| 가입 인증번호 요청 | `POST /api/auth/email-verifications/send` | `202` 후 번호 입력 표시 |
| 가입 인증번호 확인 | `POST /api/auth/email-verifications/confirm` | `200`과 `verified: true` |
| 회원가입 | `POST /api/auth/register` | `201` 후 로그인 화면. 가입만으로 로그인되지 않음 |
| 로그인 | `POST /api/auth/login` | `200` 후 토큰 보관·홈 이동 |
| 앱 재실행·토큰 만료 | `POST /api/auth/refresh` | `200` 후 Access·Refresh Token **둘 다 교체** |
| 로그아웃 | `POST /api/auth/logout` | `204` 후 로컬 토큰 삭제 |
| 비밀번호 재설정 | `/api/auth/password-resets/send` → `/confirm` → `/complete` | 번호 확인 뒤 받은 `resetToken`으로 비밀번호 변경 |

인증번호는 6자리 문자열로 보낸다. 인증번호를 확인한 이메일과 가입 요청의 이메일이 같아야 한다. 닉네임은 2~50자, 비밀번호는 8~64자다. 비밀번호 확인값은 앱에서만 비교한다. 서비스·개인정보 두 필수 약관에 실제로 동의했을 때만 `true`를 보낸다.

**이미 가입된 주소로 가입 인증번호를 요청하면 서버는 `202`를 반환하지만 메일을 발송하지 않는다.** 화면에 `202`가 보이더라도 메일 도착을 보장하지 않는다. 기존 사용자는 로그인 또는 비밀번호 재설정으로 안내한다. 가입 여부를 추측해 보여주는 화면은 만들지 않는다.

## 오류와 인증 헤더

- 인증 오류는 HTTP 상태와 응답 `code`로 구분한다. `429 CODE_RATE_LIMITED`는 재전송 대기, `400 INVALID_CODE`는 번호 오류, `503 MAIL_UNAVAILABLE`은 SMTP 발송 실패다.
- 보호 API에는 `Authorization: Bearer <accessToken>`을 보낸다. 만료로 `401`이 난 요청만 토큰을 한 번 재발급한 뒤 재시도한다. 재발급 실패 시 로그인 화면으로 이동하고 무한 재시도하지 않는다.
- 백엔드 활동 API의 `{userId}`와 제출 본문의 `userId`에는 로그인 응답의 `user.userId`를 사용한다. 다른 사용자 ID는 `403`이다. 새 `GET /api/users/me`와 `GET /api/users/me/progress`는 ID 전달 없이 JWT 사용자를 조회한다.
- 다른 PC에서 만든 계정은 현재 PC의 로컬 DB에 자동으로 공유되지 않는다.

## 메인 홈 화면과 학습 API

Flutter FE PR #13은 홈 화면을 기존 공개 템플릿 목록·사용자별 진도 API에 연결했다. 호출 경로, 화면 상태, 아직 연결하지 않은 카드 이동은 [학습 API 명세의 홈 화면 연동 현황](learning-api.md#flutter-홈-화면-연동-현황-fe-pr-13)을 참고한다. 백엔드 `feature/learning-api`의 새 `GET /api/users/me/progress`는 현재 Flutter 홈에서 아직 사용하지 않는다.
