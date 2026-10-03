# COBIA 앱 백엔드

Flutter 앱 **COBIA**가 사용할 Spring Boot 서버입니다. GitHub 저장소명과 Java 패키지명에는 기존 `COBIP` 표기가 남아 있지만, 사용자에게 보이는 앱 이름은 COBIA입니다. PostgreSQL·Redis와 인증 API를 포함합니다. **현재 Flutter 인증 화면은 서버에 연결되지 않은 UI 미리보기**입니다.

처음 보는 팀원을 위한 용어 정리: **Spring Boot**는 앱의 요청을 받는 서버, **PostgreSQL**은 회원 정보를 오래 보관하는 DB, **Redis**는 몇 분 동안만 필요한 인증번호와 로그인 재발급 토큰을 보관하는 저장소, **SMTP**는 인증 메일을 보내는 통신 설정입니다. **JWT**는 로그인 후 보호된 API를 호출할 때 사용하는 서명된 Access Token입니다.

## 처음 실행할 때: 전체 순서

1. Java 21과 Docker Desktop을 확인합니다.
2. `.env`에 자기 PC에서만 쓸 DB 비밀번호를 적습니다.
3. Docker Compose로 PostgreSQL과 Redis를 켭니다.
4. 같은 DB 비밀번호와 JWT 서명 키를 PowerShell 환경 변수에 넣습니다.
5. 인증 메일을 실제로 보낼 경우 SMTP 환경 변수도 넣습니다.
6. Spring Boot를 실행합니다.

아래 명령은 **Windows PowerShell에서 이 저장소 폴더**(`C:\COBIP_APP\COBIP_APP_BE`)를 연 상태를 기준으로 합니다. 첫 실행 시 Gradle·Docker 이미지 다운로드가 필요할 수 있습니다.

## 1. 필요한 프로그램 확인

PowerShell에서 다음 명령을 각각 실행합니다.

```powershell
java -version
docker --version
docker compose version
```

Java 버전은 **21**이어야 합니다. 다른 버전이 표시되면 이 PowerShell 창에서만 Java 21을 선택합니다. 경로는 설치 위치에 맞게 바꾸세요.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Docker Desktop도 실행합니다. `docker ps`가 오류 없이 빈 목록이라도 보여주면 Docker 엔진은 켜진 것입니다. 프로젝트 DB와 Redis는 다음 단계에서 별도 실행합니다.

## 2. 로컬 DB 비밀번호 준비

저장소 폴더로 이동해 예시 파일을 복사합니다. 이미 `.env`가 있다면 덮어쓰지 마세요.

```powershell
cd C:\COBIP_APP\COBIP_APP_BE
Copy-Item .env.example .env
notepad .env
```

메모장에서 첫 줄을 다음처럼 수정하고 저장합니다. `my-local-password`는 자신이 정한 값으로 바꿉니다. 팀원마다 다른 값을 써도 됩니다.

```dotenv
DB_PASSWORD=my-local-password
```

`.env`는 **Docker Compose가 PostgreSQL을 만들 때** 읽습니다. Git에서는 제외됩니다. 비밀번호를 채팅·커밋·스크린샷에 공개하지 마세요. 이미 생성한 DB의 비밀번호는 `.env`만 수정해도 자동으로 바뀌지 않습니다. 기존 데이터를 유지하면서 바꾸려면 PostgreSQL 계정 비밀번호도 변경해야 합니다.

## 3. PostgreSQL·Redis 실행

Docker Desktop이 켜진 상태에서 저장소 폴더에서 실행합니다.

```powershell
docker compose up -d --wait
docker compose ps
```

`postgres`와 `redis`가 실행 중이고 정상 상태면 다음 단계로 갑니다. PostgreSQL은 `127.0.0.1:5432`, Redis는 `127.0.0.1:6379`에 연결됩니다. 다른 프로그램이 같은 포트를 쓰면 충돌하므로 해당 프로그램을 종료하거나 `compose.yaml`의 호스트 쪽 포트와 연결 설정을 함께 바꿔야 합니다.

## 4. Spring Boot용 비밀값 설정

**같은 PowerShell 창**에서 실행합니다. 2단계 `.env`에 적은 것과 **똑같은 DB 비밀번호**를 입력합니다. `$env:...` 값은 현재 창에만 적용되므로 새 창을 열었다면 다시 설정해야 합니다.

```powershell
$env:DB_PASSWORD = Read-Host '2단계 .env에 적은 DB_PASSWORD'
```

JWT 서명 키는 Access Token 위조를 막는 비밀값입니다. 최소 32바이트가 필요합니다. 로컬 실습에서는 아래처럼 무작위 키를 만들어 현재 창에만 넣을 수 있습니다. 서버를 다시 켤 때 새 키를 만들면 **이전 로그인 토큰은 무효**가 됩니다.

```powershell
$jwtBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
$env:JWT_SECRET_KEY = [Convert]::ToBase64String($jwtBytes)
```

팀 공용/배포 서버에는 매번 새 키를 만들지 말고 운영 환경의 비밀값 저장 수단에 고정된 키를 넣으세요. 키를 README나 `.env.example`에 적어 커밋하지 않습니다.

## 5. 이메일 인증을 사용할 경우: SMTP 설정

회원가입의 6자리 인증번호를 실제 메일로 받으려면 이메일 서비스 제공자가 안내한 SMTP 정보와 발신 계정이 필요합니다. 아래 값은 **형식 예시**이며 그대로 사용하면 전송되지 않습니다.

```powershell
$env:MAIL_HOST = 'smtp.example.com'
$env:MAIL_PORT = '587'
$env:MAIL_USERNAME = 'sender@example.com'
$env:MAIL_PASSWORD = Read-Host 'SMTP 비밀번호 또는 앱 비밀번호'
$env:MAIL_FROM = 'sender@example.com'
```

SMTP 제공자가 인증 또는 STARTTLS 설정을 달리 지정한다면 `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS_ENABLE`도 그 안내에 맞춰 설정하세요. 기본값은 둘 다 `true`입니다. SMTP 설정이 없어도 서버는 켜지지만 **인증번호 발송 API는 `503 MAIL_UNAVAILABLE`**을 반환하므로 새 회원가입은 완료할 수 없습니다. 인증번호를 서버 로그에 출력하는 우회 기능은 없습니다.

## 6. 서버 켜기

DB와 Redis가 실행 중이고 환경 변수를 설정한 **같은 PowerShell 창**에서 실행합니다.

```powershell
.\gradlew.bat bootRun
```

처음에는 Gradle 파일을 다운로드하느라 시간이 걸릴 수 있습니다. `Started CobipApplication`이 보이면 서버가 켜진 것입니다. 브라우저에서 [http://localhost:8080/](http://localhost:8080/)을 열어 시작 화면을 확인하세요. 이 창을 닫거나 `Ctrl+C`를 누르면 서버도 종료됩니다.

빈 PostgreSQL DB를 처음 실행할 때 Flyway가 [`V1__initial_schema.sql`](src/main/resources/db/migration/V1__initial_schema.sql)을 적용해 테이블을 만듭니다. 다음 실행부터는 같은 마이그레이션을 중복 실행하지 않습니다. 기존 데이터가 담긴 다른 DB에 V1을 그대로 적용하지 마세요.

## 7. 인증 API 호출 예시

서버를 실행하는 창은 그대로 두고 **새 PowerShell 창**을 엽니다. 아래 이메일을 자신이 메일을 받을 수 있는 주소로 바꿉니다.

```powershell
$api = 'http://localhost:8080/api/auth'
$email = 'student@example.com'
$body = @{ email = $email } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$api/email-verifications/send" -ContentType 'application/json; charset=utf-8' -Body $body
```

성공하면 `202` 응답에 `expiresInSeconds: 300`, `resendAfterSeconds: 60`이 포함됩니다. 메일로 받은 실제 6자리 번호를 넣어 확인합니다.

```powershell
$code = Read-Host '메일로 받은 6자리 인증번호'
$body = @{ email = $email; code = $code } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$api/email-verifications/confirm" -ContentType 'application/json; charset=utf-8' -Body $body
```

가입에는 닉네임, 비밀번호(8~64자), 필수 약관 동의 두 항목이 필요합니다. 아래 예시의 비밀번호와 닉네임은 직접 정합니다. **실제 약관 내용을 확인하고 동의했을 때만** 두 값을 `true`로 보내세요.

```powershell
$nickname = Read-Host '닉네임(2~50자)'
$password = Read-Host '가입할 비밀번호(8~64자)'
$body = @{
  email = $email
  nickname = $nickname
  password = $password
  serviceTermsAgreed = $true
  privacyTermsAgreed = $true
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$api/register" -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

가입에 성공했으면 같은 이메일·비밀번호로 로그인합니다. 로그인 응답에는 **Access Token과 Refresh Token**이 함께 들어옵니다.

```powershell
$body = @{ email = $email; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$api/login" -ContentType 'application/json; charset=utf-8' -Body $body
$login.user
```

보호된 API를 호출할 때는 `Authorization: Bearer <accessToken>` 헤더를 붙입니다. Access Token은 15분 유효합니다. 만료 전에 또는 만료 후 로그인 상태를 이어가려면 Refresh Token으로 두 토큰을 새로 받습니다. 기존 Refresh Token은 이 요청 직후 폐기됩니다.

```powershell
$body = @{ refreshToken = $login.refreshToken } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$api/refresh" -ContentType 'application/json; charset=utf-8' -Body $body
```

로그아웃은 현재 Access Token과 **가장 최근에 받은 Refresh Token**을 함께 보냅니다. 성공 응답은 내용 없는 `204`입니다.

```powershell
$body = @{ refreshToken = $login.refreshToken } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$api/logout" -Headers @{ Authorization = "Bearer $($login.accessToken)" } -ContentType 'application/json; charset=utf-8' -Body $body
```

위 PowerShell 변수에는 실행 중 토큰·비밀번호가 들어 있으므로 공용 PC라면 창을 닫고, 값을 복사해 채팅이나 커밋에 남기지 마세요. 전체 요청·응답과 오류 코드는 [인증 API 계약](docs/auth-api.md)을 보세요. **현재 Swagger UI는 설치하지 않았으므로** `/swagger-ui.html`이 열리지 않는 것은 정상입니다.

## Flutter 앱에서 접속할 때

- **PC의 PowerShell/브라우저:** `http://localhost:8080`.
- **같은 PC의 Android 에뮬레이터:** 일반적으로 `http://10.0.2.2:8080`. 에뮬레이터 안의 `localhost`는 PC가 아니라 에뮬레이터 자신입니다.
- **실제 휴대폰:** 휴대폰과 PC가 같은 네트워크일 때 PC의 LAN IP를 사용합니다. 방화벽과 Android의 HTTP 통신 설정도 확인해야 합니다. 외부 배포에는 HTTPS 주소를 사용합니다.

현재 FE 인증 화면은 실제 HTTP 요청을 보내지 않는 **UI 미리보기**입니다. 화면에서 가입/로그인 성공처럼 보여도 백엔드와 연결된 것은 아닙니다. 연결 전에 [FE 인증 화면과 API 차이](docs/auth-fe-alignment.md)를 확인하세요.

## 자주 막히는 부분

| 증상 | 확인할 곳 |
| --- | --- |
| `25.0.3` 등 Java 버전 관련 Gradle 오류 | `java -version`이 21인지 보고 1단계의 `JAVA_HOME`을 다시 설정 |
| `DB_PASSWORD`가 없다는 Docker 오류 | `.env` 파일에 `DB_PASSWORD=...`를 저장했는지 확인 |
| DB 로그인 실패 | PowerShell의 `$env:DB_PASSWORD`와 `.env` 값이 같은지 확인 |
| `JWT_SECRET_KEY must be at least 32 UTF-8 bytes` | 4단계 JWT 키를 설정한 **같은 창**에서 서버를 실행했는지 확인 |
| `503 MAIL_UNAVAILABLE` | SMTP 서버·포트·발신 주소·계정 비밀번호 확인 |
| `429 CODE_RATE_LIMITED` | 같은 이메일로 60초 안에 재요청함. 잠시 후 재시도 |
| `400 INVALID_CODE` | 번호 오입력, 5분 만료 또는 5회 실패. 새 번호 요청 |
| `401 UNAUTHORIZED` | Access Token이 없거나 만료/로그아웃됨. 로그인 또는 토큰 재발급 필요 |
| `Connection refused` 또는 포트 충돌 | `docker compose ps`, Docker Desktop, 5432/6379/8080 포트 사용 여부 확인 |

## 종료와 데이터 보관

Spring Boot 창에서 `Ctrl+C`로 서버를 종료합니다. DB와 Redis도 끄려면 저장소 폴더에서 다음을 실행합니다.

```powershell
docker compose down
```

이 명령은 PostgreSQL 데이터 볼륨을 유지하므로 다음 `up`에서도 회원 정보가 남습니다. **`docker compose down -v`는 DB 볼륨까지 삭제하므로 평소에는 사용하지 마세요.** Redis 인증번호·재발급 토큰은 캐시 데이터이므로 컨테이너 재생성 시 사라질 수 있습니다. 그 경우 다시 인증/로그인하면 됩니다.

스키마 변경 시 이미 적용된 V1을 수정하지 말고 `V2__...sql` 같은 새 Flyway 파일을 만듭니다. [`docs/database/schema.sql`](docs/database/schema.sql)은 초기 설계 참고 사본입니다. 컬럼별 의미는 [DB 테이블·컬럼 설명](docs/database/TABLE_COLUMN_GUIDE.md)을 참고하세요.
