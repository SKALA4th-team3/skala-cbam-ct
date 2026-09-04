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
cp .env.example .env          # 메일·AI 기능을 쓸 때 필요한 값만 채운다

# 새 터미널에서 실행한다.
cd backend && ./gradlew bootRun              # http://localhost:8080

# 다른 터미널에서 실행한다.
cd frontend && npm install && npm run dev   # http://localhost:5173
```

개발과 운영 환경 모두 H2를 사용한다 — [ADR-0004](docs/decisions/0004-database-profiles.md).

### 막히면 여기부터 본다

| 증상 | 원인 | |
| --- | --- | --- |
| H2 콘솔에 연결되지 않는다 | JDBC URL이 실행 프로필과 다르다 | 개발 프로필에서는 `jdbc:h2:mem:cbam`을 입력한다 |
| 운영 데이터를 재시작 후 찾을 수 없다 | `dev` 인메모리 프로필로 실행했다 | `SPRING_PROFILES_ACTIVE=prod`로 파일 DB를 사용한다 |
| 화면은 뜨는데 데이터가 목이다 | 그 엔드포인트가 `VITE_REAL_API` 에 없다 | 아래 「FE 와 BE 를 붙인다」 |

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

기본 실행에는 샘플 데이터를 넣지 않는다. 샘플 데이터가 필요하면 아래 `dev,mock` 프로필로 실행한다.

```bash
curl -X PATCH localhost:8080/api/v1/mail-receipts/{mailReceiptId}/supplier \
     -H 'Content-Type: application/json' -H 'X-Operator-Id: demo' \
     -d '{"supplierId":{supplierId}}'
# → {"analyzeTaskId":"tsk-…"}

curl localhost:8080/api/v1/tasks/tsk-…      # PENDING → COMPLETED (10초 안팎)
# → resourceIds 에 만들어진 제출 데이터 id, unregisteredPartCount 에 미등록 부품 수
```

**키가 없어도 앱은 뜬다.** 그때 분석은 실패로, 피드백 초안은 46번의 기본 템플릿으로 간다 —
[ADR-0013](docs/decisions/0013-ai-call-restclient-and-polling.md).

### H2 프로필

기본 `dev` 프로필은 인메모리 DB를 사용한다. 애플리케이션을 종료하면 데이터가 사라지므로 개발과 테스트에 적합하다.
[`schema-h2.sql`](backend/src/main/resources/db/schema-h2.sql)이 `ref/ERD_v10.gj.md`의 16개 테이블을 만들고,
Hibernate의 `validate`가 엔티티 매핑과 스키마가 어긋나지 않는지 시작할 때 검사한다.

```bash
cd backend
./gradlew bootRun
```

개발 중에는 `http://localhost:8080/h2-console`에서 DB를 확인할 수 있다.

| 항목 | 값 |
| --- | --- |
| JDBC URL | `jdbc:h2:mem:cbam` |
| User Name | `sa` |
| Password | 비워 둠 |

### Mock 데이터로 실행

Mock 데이터는 팀 공용 SQL 파일인
[`mock-data-h2.sql`](backend/src/main/resources/db/mock-data-h2.sql)에 있다. 기본 실행에는 들어가지 않으며,
필요한 팀원만 다음처럼 `mock` 프로필을 함께 켠다.

macOS/Linux:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev,mock ./gradlew bootRun
```

Windows PowerShell:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev,mock"
.\gradlew.bat bootRun
```

샘플 ID는 충돌 가능성을 낮추기 위해 `10001`부터 시작한다. SQL은 `MERGE ... KEY(...)`를 사용하므로
같은 인메모리 DB에 다시 실행해도 같은 ID의 행이 중복 생성되지 않는다. 모든 이메일은 실제 주소가 아닌
`example.test` 도메인을 사용한다.

`dev` DB는 **프로세스별 인메모리 DB**라서 팀원이 하나의 DB를 공유하는 방식은 아니다. 대신 모든 팀원이
같은 스키마와 mock SQL을 Git으로 공유하고 각자 동일한 DB를 재현한다. 데이터를 재시작 후에도 유지하려면
아래 `prod` 파일 프로필을 사용한다. 하나의 H2 파일을 여러 PC가 동시에 공유하는 용도로는 사용하지 않는다.

`prod` 프로필은 파일 DB를 사용해 재시작 후에도 데이터를 보존한다. 별도 DB 서버나 Docker는 필요하지 않다.

macOS/Linux:

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

Windows PowerShell:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "prod"
.\gradlew.bat bootRun
```

기본 데이터 파일은 `backend/data/cbam.mv.db`에 생성된다. 저장 위치를 바꿀 때만 `DB_URL`에 H2 파일 JDBC URL을 지정한다.

```bash
DB_URL='jdbc:h2:file:/원하는/경로/cbam;DB_CLOSE_ON_EXIT=FALSE' \
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

`prod`에서도 공용 SQL로 스키마를 만들고 Hibernate가 엔티티 매핑을 검증한다. 데이터 파일을 삭제하면 저장된 데이터를 복구할 수 없으므로 초기화 목적이 아니라면 `backend/data/`를 지우지 않는다.

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
