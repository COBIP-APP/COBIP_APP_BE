# COBIA 백엔드

COBIA Flutter 앱의 Spring Boot API입니다. 로컬 개발에서는 Docker Compose로 Spring Boot·PostgreSQL·Redis를 각자 PC에서 실행합니다. API 목록은 서버를 켠 뒤 [Swagger UI](http://localhost:8080/swagger-ui.html)에서 확인합니다.

## Flutter 팀원: 처음 실행

**필요한 것:** Git, Docker Desktop. Java는 Docker 이미지 안에서 실행되므로 별도 설치하지 않아도 됩니다. Windows PowerShell 기준입니다. 저장소는 현재 공개되어 있어 내려받기 권한을 따로 받을 필요가 없습니다.

```powershell
git clone --branch develop https://github.com/COBIP-APP/COBIP_APP_BE.git
cd COBIP_APP_BE
Copy-Item .env.example .env
Copy-Item config/application-local.yml.example config/application-local.yml
```

이미 저장소나 설정 파일이 있다면 다시 복사해 덮어쓰지 마세요. 다음 두 파일은 **처음 한 번만** 설정합니다.

1. `.env`의 `DB_PASSWORD`에 자신의 로컬 PostgreSQL 비밀번호를 적습니다.
2. `config/application-local.yml`의 `app.jwt.secret`에 32바이트 이상의 키를 적습니다. 키를 매번 바꾸는 것이 아니라 **한 번 만든 값을 계속 사용**합니다. 각자 DB와 서버를 따로 쓰므로 팀원 간 같은 키일 필요는 없습니다. 기존에 만든 로컬 설정 파일이 있으면 그 키를 그대로 사용하세요.

키가 없다면 PowerShell에서 한 번 생성해 결과를 `app.jwt.secret`에 붙여 넣습니다.

```powershell
$bytes = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
```

Docker 실행에서는 DB 비밀번호를 `config/application-local.yml`에 또 적지 않아도 됩니다. Compose가 `.env`의 값을 Spring에 전달합니다. 회원가입 이메일 인증이나 비밀번호 재설정을 시험하려면 같은 설정 파일의 `spring.mail`과 `app.mail.from`에 자신의 SMTP 발신 정보를 입력해야 합니다. 비워두면 서버는 켜지지만 메일 발송 API는 `503 MAIL_UNAVAILABLE`을 반환합니다. **`.env`와 `config/application-local.yml`은 Git에 올라가지 않으며, 팀원에게 비밀값을 보내지 않습니다.**

Docker Desktop을 켠 뒤 저장소 폴더에서 실행합니다.

```powershell
docker compose --profile full up -d --build --wait
docker compose ps
```

`app`, `postgres`, `redis`가 실행 중이면 [Swagger UI](http://localhost:8080/swagger-ui.html)를 엽니다. 앱을 새로 받은 뒤에는 `git pull origin develop`을 하고 위의 `--build` 명령을 다시 실행하세요. 서버 오류는 `docker compose logs app`에서 확인합니다.

## Flutter에서 접속

| 실행 위치 | API 기본 주소 |
| --- | --- |
| PC 브라우저·PowerShell | `http://localhost:8080` |
| **같은 PC의 Android 에뮬레이터** | `http://10.0.2.2:8080` |

Flutter 팀은 Dio의 `baseUrl`을 Android 에뮬레이터 기준으로 설정합니다. 예를 들어 로그인 경로는 `POST /api/auth/login`입니다. 각 팀원이 띄운 PostgreSQL은 **서로 다른 로컬 DB**이므로 다른 PC에서 가입한 계정은 내 PC에 자동으로 생기지 않습니다. 실제 휴대폰이나 다른 PC에서 접속할 때만 `.env`에 `APP_BIND_ADDRESS=0.0.0.0`을 추가하고 백엔드 PC의 LAN IP와 방화벽 설정을 확인하세요.

요청·응답 필드는 Swagger에서, 인증번호 → 회원가입 → 로그인 → 토큰 재발급의 호출 순서는 [인증 API 명세](docs/auth-api.md)와 [Flutter 연결 안내](docs/auth-fe-alignment.md)에서 확인합니다. 비밀번호 재설정 API는 해당 PR이 `develop`에 병합된 뒤 Swagger에도 나타납니다.

## 백엔드 팀원: Spring을 직접 실행할 때

Java 21로 코드를 수정하며 실행하려면 Docker에는 DB·Redis만 켜고 Spring은 로컬에서 실행할 수 있습니다. 위의 전체 Docker 실행과 동시에 실행하면 8080 포트가 충돌합니다.

```powershell
docker compose up -d --wait
.\gradlew.bat bootRun
```

이 방식에서는 `config/application-local.yml`의 `spring.datasource.password`도 `.env`의 `DB_PASSWORD`와 같게 적어야 합니다. `bootRun`을 실행하는 PowerShell에서 `java -version`이 21이 아니면 `JAVA_HOME`을 자신의 Java 21 설치 경로로 맞춥니다. 설정 파일은 저장소 루트에서 실행할 때 읽힙니다.

## 종료와 자주 생기는 오류

```powershell
docker compose --profile full down
```

이 명령은 PostgreSQL 데이터를 유지합니다. **`down -v`는 로컬 DB 데이터까지 지우므로 사용하지 마세요.** Spring을 직접 실행했다면 먼저 그 창에서 `Ctrl+C`를 누른 뒤 `docker compose down`을 실행합니다.

- `DB_PASSWORD` 오류: `.env`에 값을 입력했는지 확인합니다. 이미 만든 DB의 비밀번호는 `.env`만 바꿔도 자동으로 바뀌지 않습니다.
- JWT 키 오류: `config/application-local.yml`의 `app.jwt.secret`이 32바이트 이상인지 확인합니다.
- 8080 포트 오류: 다른 Spring 서버나 Docker `app`이 이미 실행 중인지 확인합니다.
- 메일 발송 `503`: `spring.mail`과 `app.mail.from`의 SMTP 설정을 확인합니다.

DB 구조는 [테이블·컬럼 설명](docs/database/TABLE_COLUMN_GUIDE.md)을 참고합니다.
