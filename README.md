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
cp .env.example .env          # DB_USERNAME·DB_PASSWORD 값을 채운다. .env 는 커밋되지 않는다
docker compose up -d
set -a && source .env && set +a
export SPRING_PROFILES_ACTIVE=dev

# 새 터미널에서 실행한다.
cd backend && ./gradlew bootRun              # http://localhost:8080

# 다른 터미널에서 실행한다.
cd frontend && npm install && npm run dev   # http://localhost:5173
```

개발·발표·운영 환경은 PostgreSQL을 사용한다. PostgreSQL 컨테이너가 빈 볼륨을 처음 만들 때 초기화 SQL을 실행하며, 테스트만 격리된 H2를 사용한다 — [ADR-0011](docs/decisions/0011-postgresql-development-and-init-sql.md).

### 막히면 여기부터 본다

| 증상 | 원인 | |
| --- | --- | --- |
| `bind: address already in use` | 5432 를 이미 쓰고 있다 | `.env` 의 `DB_PORT` 와 `DB_URL` 을 같은 다른 포트로 바꾼다 |
| 부팅이 `Schema validation` 으로 죽는다 | 엔티티와 `init_db.sql` 이 어긋났다 | 컬럼 이름·타입을 맞춘다. **`ddl-auto` 를 바꿔 덮지 않는다** |
| 화면은 뜨는데 데이터가 목이다 | 그 엔드포인트가 `VITE_REAL_API` 에 없다 | 아래 「FE 와 BE 를 붙인다」 |
| `docker compose up` 뒤에도 데이터가 없다 | 볼륨이 이미 있어 초기화 SQL 이 안 돈다 | `docker compose down -v` 로 볼륨째 지우고 다시 올린다 |

## FE 와 BE 를 붙인다

**FE 는 기본적으로 목으로 돈다.** BE 없이도 화면 전체가 뜬다. 실서버로 보낼 엔드포인트만
`frontend/.env` 의 `VITE_REAL_API` 에 하나씩 넣는다 — 거기 없는 것은 목이다.

```bash
cd frontend
cp .env.example .env       # VITE_REAL_API 에 실서버로 보낼 것을 적는다
npm run dev                # /api 요청은 vite 가 VITE_BACKEND_ORIGIN 으로 넘긴다 (CORS 없음)

npm run api:status         # 지금 무엇이 실서버이고 무엇이 목인지
npm run api:real           # 실서버 계약 검사 — BE 가 8080 에 떠 있어야 한다
```

지금 실서버로 붙어 있는 것과, 아직 못 붙이는 것과 그 이유다.

| 엔드포인트 | | |
| --- | --- | --- |
| `GET /suppliers` · `GET /suppliers/{id}` · `POST /suppliers` | ✅ 붙었다 | `npm run api:real` 16건 통과 |
| `GET /tasks/{taskId}` | ✅ 붙었다 | 목과 실서버가 같은 모양이라 변환이 없다 |
| `GET /dashboard` | ⛔ 못 붙인다 | 화면이 추세·할 일·접수 현황·완제품 합계를 읽는데 **BE 응답에 없다.** 붙이면 관제 화면이 빈다 |
| `GET /mail-receipts` | ⛔ 못 붙인다 | 목록 응답에 `subject`·`body` 가 없다. 접수함이 제목을 못 그린다 |
| `GET /parts` · `/products` · `/submissions` … | ⛔ 아직 | 필드 이름이 달라 `shapes.js` 에 변환이 필요하다 |

**⛔ 는 「BE 가 틀렸다」가 아니라 「아직 안 맞춰 봤다」는 뜻이다.** 붙이는 순서는
`shapes.js` 에 변환을 쓰고 → `api/index.js` 의 그 호출에 세 번째 인자(real)를 채우고 →
`.env` 의 `VITE_REAL_API` 에 넣는다. 화면 코드는 건드리지 않는다.

## AI 를 실제로 돌려 본다 (22~25번)

`.env` 에 `AI_API_KEY` 를 채우고 BE 를 띄운 뒤, **미확인 접수 건을 협력업체에 연결**하면
그 즉시 분석이 자동으로 돈다(요구사항 20번).

```bash
# 데모 데이터의 5006 번은 이걸 보려고 둔 건이다 — 본문에 등록 부품·미등록 부품·
# 단위 없는 값이 일부러 섞여 있다
curl -X PATCH localhost:8080/api/v1/mail-receipts/5006/supplier \
     -H 'Content-Type: application/json' -H 'X-Operator-Id: demo' \
     -d '{"supplierId":1001}'
# → {"analyzeTaskId":"tsk-…"}

curl localhost:8080/api/v1/tasks/tsk-…      # PENDING → COMPLETED (10초 안팎)
# → resourceIds 에 만들어진 제출 데이터 id, unregisteredPartCount 에 미등록 부품 수
```

**키가 없어도 앱은 뜬다.** 그때 분석은 실패로, 피드백 초안은 46번의 기본 템플릿으로 간다 —
[ADR-0013](docs/decisions/0013-ai-call-restclient-and-polling.md).

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
