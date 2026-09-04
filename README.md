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

개발과 운영 환경 모두 H2를 사용한다 — [ADR-0004](docs/decisions/0004-database-profiles.md).

### H2 프로필

기본 `dev` 프로필은 인메모리 DB를 사용한다. 애플리케이션을 종료하면 데이터가 사라지므로 개발과 테스트에 적합하다.

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

`prod`에서도 Hibernate가 ERD에 대응하는 엔티티를 기준으로 스키마를 갱신한다. 데이터 파일을 삭제하면 저장된 데이터를 복구할 수 없으므로 초기화 목적이 아니라면 `backend/data/`를 지우지 않는다.

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
