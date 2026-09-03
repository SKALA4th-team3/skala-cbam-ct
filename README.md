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

## 기술 스택

| | |
| --- | --- |
| Backend | Java · Spring Boot |
| Frontend | Vue.js |

## 규칙 요약

- **F 번호 없이 코드를 쓰지 않는다** — 명세에 없으면 팀에 먼저 묻는다
- **모르면 채우지 말고 비우고 사유를 남긴다** — 제품의 규칙이자 우리의 작업 규칙이다
- **되는 것만 확인하지 않는다** — 명세의 조건은 대부분 막는 쪽에 있다
- **비밀값을 커밋하지 않는다** — 메일 계정·AI API 키·DB 접속정보
