# skala-cbam-ct

**협력업체 탄소데이터 관제탑.** 협력업체가 메일로 보낸 배출 데이터를 받아 AI로 읽고, 표준화하고, 적격 여부를 판정한다. 원청 담당자가 검토·확정하고 부적격 건에 피드백을 보낸다.

## 처음 온 사람이 읽는 순서

| | |
| --- | --- |
| 1 | [`AGENTS.md`](AGENTS.md) — **작업 규칙. 코드를 쓰기 전에 읽는다** |
| 2 | [`docs/product/REQUIREMENTS.md`](docs/product/REQUIREMENTS.md) — 기능 53개 |
| 3 | [`docs/AI_WORKFLOW.md`](docs/AI_WORKFLOW.md) — AI로 작업하는 순서 |
| 4 | [`docs/GIT_CONVENTION.md`](docs/GIT_CONVENTION.md) · [`docs/JIRA_CONVENTION.md`](docs/JIRA_CONVENTION.md) — 브랜치·커밋·PR·이슈 |

[`CLAUDE.md`](CLAUDE.md)는 `AGENTS.md`와 **같은 파일이다.** 도구마다 읽는 이름이 달라 둘 다 둔다 — 한쪽을 고치면 다른 쪽도 같이 고친다.

## 디렉터리

| | | 브랜치 도메인 |
| --- | --- | --- |
| [`backend/`](backend/) | Spring Boot 4.1 · Java 21 · Gradle | `be` |
| [`frontend/`](frontend/) | Vue 3 · Vite · JavaScript | `fe` |
| [`docs/`](docs/) | 명세 · 컨벤션 · 기술 결정 | — |

## 실행

```bash
cp .env.example .env          # 값을 채운다. .env 는 커밋되지 않는다

cd backend  && ./gradlew bootRun            # http://localhost:8080
cd frontend && npm install && npm run dev   # http://localhost:5173
```

개발·발표·운영 환경은 PostgreSQL을 사용한다. PostgreSQL 컨테이너가 빈 볼륨을 처음 만들 때 초기화 SQL을 실행하며, 테스트만 격리된 H2를 사용한다 — [ADR-0010](docs/decisions/0010-postgresql-development-and-init-sql.md).

### PostgreSQL

루트의 `.env.example`을 `.env`로 복사하고 DB 값을 채운 뒤, Docker Desktop을 실행한다.

```bash
docker compose up -d
docker compose ps
docker compose logs -f postgres
```

기본 `docker compose up`은 `docker-compose.override.yml`을 자동으로 합친다. 빈 DB에 `init_db.sql`로 스키마와 국가 기준정보를 만들고, 이어서 `init_demo_data.sql`로 개발·발표용 샘플 데이터를 넣는다.

운영 환경은 override를 제외해 샘플 데이터가 들어가지 않도록 기본 Compose 파일만 명시한다.

```bash
docker compose -f docker-compose.yml up -d
```

Docker Compose는 루트의 `.env`를 읽지만 Spring Boot는 이 파일을 자동으로 읽지 않는다. PostgreSQL에 애플리케이션을 연결하려면 `.env` 값을 실행 셸의 환경변수로 불러온다. 일반 개발·발표는 `dev`, 운영은 `prod` 프로필을 사용한다.

macOS/Linux:

```bash
set -a
source .env
set +a
export SPRING_PROFILES_ACTIVE=dev
cd backend && ./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/cbam"
$env:DB_USERNAME = "<.env의 DB_USERNAME>"
$env:DB_PASSWORD = "<.env의 DB_PASSWORD>"
cd backend; .\gradlew.bat bootRun
```

`DB_PORT` 또는 `POSTGRES_DB`를 기본값에서 변경했다면 `DB_URL`도 같은 포트와 DB 이름을 가리켜야 한다.

컨테이너를 중지하고 제거하되 DB 데이터를 유지하려면 다음 명령을 사용한다.

```bash
docker compose down
```

DB 데이터까지 초기화할 때만 `--volumes`를 붙인다. **이 명령은 PostgreSQL 데이터를 복구할 수 없게 삭제한다.**

```bash
docker compose down --volumes
```

초기화 SQL은 PostgreSQL 데이터 볼륨을 처음 생성할 때만 실행된다. SQL 변경을 기존 볼륨에 자동 적용하지 않으므로 초기 상태로 다시 만들 때만 `docker compose down --volumes` 후 재기동한다. 샘플 데이터 이메일은 모두 `example.test` 예약 도메인이다.

## 개발 시작할 때

**`dev` 에서 브랜치를 딴다. PR 도 `dev` 로 낸다.** `main` 은 검증 끝난 것만 올라간다.

**브랜치는 터미널이 아니라 Jira 이슈 화면의 「브랜치 만들기」로 만든다.** 원본 브랜치는 `dev`, 이름은 **이슈 키 하나**(`CBAM-43`)로 둔다.

```bash
git fetch origin
git switch CBAM-43        # Jira 가 만들어 둔 브랜치를 받아 온다
```

형식은 [`docs/GIT_CONVENTION.md`](docs/GIT_CONVENTION.md) 를 본다.

## 규칙 요약

- **요구사항 번호 없이 코드를 쓰지 않는다** — 명세에 없으면 팀에 먼저 묻는다
- **모르면 채우지 말고 비우고 사유를 남긴다** — 제품의 규칙이자 우리의 작업 규칙이다
- **되는 것만 확인하지 않는다** — 명세의 조건은 대부분 막는 쪽에 있다
- **비밀값을 커밋하지 않는다** — 메일 계정·AI API 키·DB 접속정보
