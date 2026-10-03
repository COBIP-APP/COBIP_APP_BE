# COBIA App Backend

Spring Boot 백엔드입니다. 기존 GitHub 저장소명과 Java 패키지명은 유지하고, 사용자에게 보이는 앱 이름은 **COBIA**를 사용합니다. 개발용 PostgreSQL·Redis, 초기 DB 스키마와 인증 API를 포함합니다.

## 개발 환경

- Java 21
- Spring Boot 3.5.16
- Gradle Wrapper 8.14.4
- Docker Desktop (PostgreSQL 16, Redis 7)

## 실행

Docker Desktop을 실행한 뒤, PowerShell에서 이 폴더로 이동합니다. `.env.example`을 `.env`로 복사하고 `DB_PASSWORD`에 로컬 개발용 비밀번호를 입력합니다. `.env`는 Git에서 제외됩니다.

```powershell
Copy-Item .env.example .env
# .env 파일의 DB_PASSWORD 값을 입력한 다음 실행
docker compose up -d --wait
```

그다음 같은 비밀번호와 **32바이트 이상인 JWT 서명 키**를 현재 PowerShell 창의 환경 변수로 지정하고 Java 21로 Spring Boot를 실행합니다. `java -version`이 21이 아니라면, `JAVA_HOME`을 설치된 Java 21 JDK 폴더로 지정하고 그 `bin` 경로를 현재 창의 `Path` 앞에 추가하세요.

```powershell
# Java 21이 기본이 아닐 때만 실행: 실제 설치 폴더로 바꿔 입력
$env:JAVA_HOME = '<Java 21 JDK 설치 폴더>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

```powershell
$env:DB_PASSWORD = Read-Host 'DB_PASSWORD (.env 파일과 같은 값)'
$env:JWT_SECRET_KEY = Read-Host 'JWT_SECRET_KEY (32바이트 이상)'
.\gradlew.bat bootRun
```

처음 시작할 때 Flyway가 빈 PostgreSQL DB에 [`V1__initial_schema.sql`](src/main/resources/db/migration/V1__initial_schema.sql)을 적용합니다. 브라우저에서 `http://localhost:8080/`을 열면 시작 화면을 볼 수 있습니다. Redis는 이메일 인증번호와 재발급 토큰, 로그아웃된 JWT 식별자를 만료 시간과 함께 저장합니다.

이메일 인증번호를 실제로 보내려면 서버 실행 전 `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` 환경 변수를 SMTP 제공자의 값으로 설정하세요. 설정하지 않으면 메일 발송 API는 `503 MAIL_UNAVAILABLE`을 반환하며, 서버 자체는 실행됩니다. SMTP 자격증명이나 JWT 키를 `.env.example`·Git에 넣지 마세요. 프론트 팀원에게 공유할 요청·응답은 [인증 API 계약](docs/auth-api.md)에 있습니다.

DB 상태는 `docker compose ps`로 확인할 수 있습니다. 작업을 마치면 `docker compose down`으로 종료합니다. `down -v`는 PostgreSQL 데이터를 지우므로 평소에는 사용하지 마세요.

PostgreSQL과 Redis 포트는 개발 PC의 `127.0.0.1:5432`, `127.0.0.1:6379`에만 열립니다. 다른 DB에 연결할 때는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` 환경 변수를 설정하세요. 기존 데이터를 담은 DB에는 초기 마이그레이션을 그대로 적용하지 마세요.

스키마를 바꾸려면 적용된 `V1` 파일을 수정하지 말고 `V2__...sql` 같은 새 마이그레이션 파일을 추가합니다. [`docs/database/schema.sql`](docs/database/schema.sql)은 초기 설계를 살펴보기 위한 사본입니다.

테이블별 역할과 모든 컬럼의 의미는 [`DB 테이블·컬럼 설명`](docs/database/TABLE_COLUMN_GUIDE.md)을 참고하세요.
