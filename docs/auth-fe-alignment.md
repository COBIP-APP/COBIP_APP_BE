# COBIA 인증 API 연결 안내 (Flutter 팀 전달용)

기준: BE `feature/auth`의 [인증 API 명세](auth-api.md)와 FE `develop`의 `lib/features/auth/presentation/` 화면을 2026-10-08에 비교했다. **Flutter 파일은 수정하지 않았다.** 현재 FE의 가입·로그인·비밀번호 재설정은 화면 미리보기이며 서버 요청을 보내지 않는다. 백엔드 인증번호 발송·확인·가입은 로컬 PostgreSQL·Redis·SMTP로 실제 동작한다.

## 1. 어디에 연결하나

| 실행 환경 | API 기본 주소 | 설명 |
| --- | --- | --- |
| PC의 PowerShell·브라우저 | `http://localhost:8080` | 서버가 실행 중인 PC 자신 |
| 같은 PC의 Android 에뮬레이터 | `http://10.0.2.2:8080` | 에뮬레이터의 `localhost`는 PC가 아님 |
| 실제 Android 휴대폰 | `http://<백엔드 PC의 LAN IP>:8080` | 같은 네트워크, PC 방화벽 및 휴대폰의 접근 확인 필요 |
| 배포 앱 | 추후 확정할 `https://` 주소 | 주소가 바뀌어도 API 경로·JSON 형식은 동일하게 유지 |

Flutter 팀은 **기본 주소 한 곳만 환경별로 변경**하고, API 호출은 `Dio` 인스턴스 하나로 관리한다. 예를 들어 로컬 Android 에뮬레이터에서는 `baseUrl`을 `http://10.0.2.2:8080`으로 설정한 뒤 `POST /api/auth/login`을 호출한다. 현재 FE `pubspec.yaml`에는 `dio`가 없으므로 FE 담당자가 의존성을 추가해야 한다. 로컬 `http://` 통신이 Android에서 막히면 FE 담당자가 디버그 빌드의 네트워크 설정을 확인한다. 배포 주소와 SMTP·DB·JWT 비밀값은 Flutter 앱에 넣지 않는다.

FE 팀이 구현할 최소 호출 형태는 아래와 같다. 이는 안내 예시이며 BE 저장소에 Flutter 코드를 추가하지 않는다. `data`의 필드명은 [계약 문서](auth-api.md)와 같아야 한다. Dio의 `BaseOptions`·`post` 사용법은 [공식 패키지 문서](https://pub.dev/packages/dio)를 참고한다.

```dart
final dio = Dio(BaseOptions(baseUrl: 'http://10.0.2.2:8080'));
final response = await dio.post(
  '/api/auth/login',
  data: {'email': email, 'password': password},
);
final user = response.data['user'];
```

## 2. 화면별 요청과 성공 조건

**모든 요청은 JSON**이다. `Content-Type: application/json; charset=utf-8`을 사용하고 필드명은 아래의 `camelCase`를 그대로 보낸다. 각 요청·응답 JSON과 오류 코드의 원본은 [인증 API 명세](auth-api.md)를 따른다.

| 화면 동작 | 요청 | 보내는 값 | 성공할 때만 수행할 동작 |
| --- | --- | --- | --- |
| 회원가입: 인증 요청·재전송 | `POST /api/auth/email-verifications/send` | `email` | `202`면 번호 입력을 열고 응답의 `expiresInSeconds`(300), `resendAfterSeconds`(60)로 타이머 시작 |
| 회원가입: 번호 확인 | `POST /api/auth/email-verifications/confirm` | 같은 `email`, 6자리 문자열 `code` | `200`과 `verified: true`면 인증 완료 표시 |
| 회원가입: 가입 완료 | `POST /api/auth/register` | 같은 `email`, `nickname`, `password`, `serviceTermsAgreed`, `privacyTermsAgreed` | `201`이면 가입 완료 화면으로 이동. 가입 응답에는 토큰이 없으므로 이후 로그인 필요 |
| 로그인 | `POST /api/auth/login` | `email`, `password` | `200`이면 토큰과 `user`를 보관한 뒤 홈으로 이동 |
| 앱 재실행·Access Token 만료 | `POST /api/auth/refresh` | 가장 최근의 `refreshToken` | `200`이면 **두 토큰을 모두** 새 값으로 교체 |
| 로그아웃 | `POST /api/auth/logout` | 본문에 `refreshToken`, 헤더에 `Authorization: Bearer <accessToken>` | `204`이면 로컬 토큰을 삭제하고 로그인 화면으로 이동 |
| 비밀번호 찾기: 인증번호 발송 | `POST /api/auth/password-resets/send` | `email` | `202`면 인증번호 입력 단계로 이동. 미가입 이메일에도 같은 응답 |
| 비밀번호 찾기: 번호 확인 | `POST /api/auth/password-resets/confirm` | 같은 `email`, 6자리 문자열 `code` | `200`이면 `resetToken`을 메모리에 보관하고 새 비밀번호 화면으로 이동 |
| 비밀번호 찾기: 비밀번호 변경 | `POST /api/auth/password-resets/complete` | `resetToken`, `newPassword` | `204`이면 완료 화면으로 이동하고 `resetToken`을 지움. 다시 로그인 필요 |

회원가입 때 이메일을 수정하면 이전 인증 완료 표시를 지우고 새 이메일을 다시 인증한다. 번호 확인에 성공한 뒤에도 가입 전에 30분이 지나면 `EMAIL_NOT_VERIFIED`가 반환되므로 인증을 다시 시작한다. 서버는 닉네임 2~50자와 비밀번호 8~64자를 요구한다. 비밀번호 확인 입력은 FE에서 비교하고 요청에는 넣지 않는다. 두 약관 동의값은 실제로 동의한 상태에서만 `true`로 보낸다.

## 3. Dio 호출·오류 처리 순서

1. FE 담당자가 Dio 의존성과 인증 API 호출 코드를 추가한다. 화면에서 `Future.delayed`로 성공을 흉내 내는 부분을 실제 요청의 응답 처리로 교체한다.
2. 요청 중에는 버튼을 비활성화하고 중복 요청을 막는다. HTTP 성공 상태를 받기 전에는 다음 화면으로 이동하지 않는다.
3. 실패하면 `DioException.response?.statusCode`와 응답 JSON의 `code`, `message`, `fieldErrors`를 읽는다. `fieldErrors`의 키는 입력 필드명이다. 서버의 필드별 문구는 현재 모두 일반적인 “입력값을 확인해주세요.”이므로 FE의 길이 안내도 함께 보여준다.
4. `429 CODE_RATE_LIMITED`는 재전송까지 기다리게 하고, `400 INVALID_CODE`는 번호 재확인을 안내한다. `503 MAIL_UNAVAILABLE`은 발송 실패로 표시한다. `409` 중복 이메일·닉네임은 해당 입력칸에 표시한다. 인터넷 연결 실패는 HTTP 상태가 없으므로 네트워크 오류로 처리한다.
5. Access Token이 만료된 보호 API 요청만 Refresh Token으로 **한 번** 재발급 후 원래 요청을 다시 시도한다. 로그인 실패나 재발급 실패를 무한 재시도하지 않는다. 재발급 실패 시 로컬 토큰을 지우고 로그인 화면으로 보낸다.

로그인 응답의 `accessToken`은 15분, `refreshToken`은 14일 유효하다. Refresh Token은 재발급할 때마다 바뀌고 이전 값은 즉시 폐기된다. 토큰은 앱의 안전한 저장소에 보관하고 로그·Git·일반 설정 파일에 남기지 않는다. 화면 표시에는 서버가 보낸 `user.role`을 쓰고, 실제 접근 권한은 백엔드가 검사한다. 닉네임으로 권한을 판단하지 않는다.

## 4. 현재 FE 화면과 실제 API의 차이

| 현재 FE `develop` | 연결하면서 FE 담당자가 해야 할 일 |
| --- | --- |
| 가입 화면에서 350ms 대기 후 발송·가입 성공으로 표시 | `/send`, `/register`의 실제 성공 응답과 오류로 바꾸기 |
| 번호가 6자리이기만 하면 인증 완료로 표시 | `/confirm`의 `verified: true`를 받은 뒤에만 완료 표시 |
| 가입 화면에 `nickname` 입력칸이 없음 | 필수 닉네임 입력칸과 2~50자 검증 추가 |
| 비밀번호는 비어 있지 않은지만 확인 | 서버 요구사항인 8~64자 검증과 안내 추가 |
| 로그인 화면에서 350ms 후 홈으로 이동 | `/login` 성공 후 토큰 저장과 홈 이동 연결 |
| `go_router`의 보호 화면을 직접 열 수 있음 | 로그인 상태에 따른 보호 경로 이동 제어 연결 |
| 비밀번호 찾기 화면도 성공을 흉내 냄 | 세 비밀번호 재설정 API를 순서대로 연결. 서버가 반환한 `resetToken`을 새 비밀번호 요청에 사용하고 `204` 이후에만 완료 화면 표시 |

## 5. 백엔드에서 확인된 것과 남은 경계

- 로컬 Docker PostgreSQL과 Redis를 연결한 상태에서 SMTP 인증번호 발송, 번호 확인, 신규 회원가입으로 사용자 ID 생성까지 확인했다.
- 2026-10-08에 로컬 임시 계정으로 로그인 `200`과 Access·Refresh Token 발급, 토큰 재발급 `200`과 Refresh Token 교체, 로그아웃 `204`, 로그아웃한 Refresh Token 재사용 시 `401`을 확인했다. 임시 계정은 확인 직후 삭제했다. Flutter 화면에서의 연결은 아직 확인하지 않았다.
- 비밀번호 재설정 API는 `feature/password-reset`에서 구현했다. 2026-10-08에 실제 SMTP 발송 `202`와 임시 계정의 인증번호 확인 `200` → 비밀번호 변경 `204` → 새 비밀번호 로그인 `200`을 확인했다. 재사용한 재설정 토큰 `400`, 이전 비밀번호·Access Token·Refresh Token 거부도 확인했고 임시 계정은 삭제했다. 실제 사용자 계정의 비밀번호는 변경하지 않았다. 이 기능은 인증 PR 병합 후 별도 PR로 `develop`에 반영해야 한다.
- Flutter 화면 연결과 Dio 추가는 FE 팀 작업이다. 백엔드 팀은 이 문서와 [인증 API 명세](auth-api.md)를 계약으로 공유하고, API 변경 시 두 문서를 함께 수정한다.
