# ADR-0011: 비동기 작업이 만든 것을 `task_resource` 로 가리킨다

- 상태: 채택
- 날짜: 2026-09-04
- 관련 기능: 22 · 42 · 43 · 45 · 46번 (전제) · API 명세 v10 №19
- 근거: [`AI_WORKFLOW.md`](../AI_WORKFLOW.md) 4항 — 「**스키마 모양**이면 `decisions/`에 먼저 쓴다」

## 배경

명세 №19 `GET /tasks/{taskId}` 는 **설계 파생 1건**이고, 202 를 반환하는 API 전부의 결과를 확인하는 유일한 창구다. 응답이 이렇다.

```jsonc
{ "taskId": "tsk-456", "taskType": "ANALYZE_MAIL_RECEIPT", "status": "COMPLETED",
  "resourceType": "submission", "resourceIds": [7001, 7002],   // ← 만들어진 것을 가리킨다
  "progress": { "total": 2, "done": 2, "failed": 0 },
  "fallbackApplied": false, "unregisteredPartCount": 1, ... }
```

CBAM-88(PR #31)이 `task` 테이블을 먼저 만들었고 그 주석이 남겨 뒀다 — 「이 프로젝트에 아직 task 도메인 담당이 없다. **다른 타입을 쓸 도메인이 생기면 이 엔티티를 그대로 확장해서 써야 한다(중복 매핑 금지)**」.

**지금 `Task` 는 만든 것을 가리키지 못한다.** `feedback` · `feedback_draft` 를 `@ManyToOne` **단수**로만 갖는데,

- **43번 일괄 생성**은 초안 N 개를 만들고 Task 는 **하나**다 — 단수 FK 로는 N 개를 가리킬 수 없다
- **`ANALYZE_MAIL_RECEIPT`** 는 feedback 과 무관하다 — 그 FK 가 아예 빈다. 가리켜야 할 것은 submission 이다

그래서 지금은 초안을 만들어도 **그 id 를 받을 길이 없다.** 화면이 `/feedback-histories` 를 훑어 찾아야 한다 (PR #31 리뷰에서 확인했다).

## 검토한 선택지

### A. `task_resource` 별도 테이블을 더한다 (고른 것)

```
task_resource
  task_id       (FK → task.id)
  resource_type (submission | feedback | feedback_draft | mail_receipt)
  resource_id   (BIGINT)
  sequence_no
```

- 장점 — **작업 하나가 여러 결과를 가리킨다.** 43번 일괄이 그대로 담긴다
- 장점 — **도메인마다 FK 컬럼을 늘리지 않는다.** submission·mail_receipt 가 붙을 때 `task` 에 컬럼이 또 생기지 않는다
- 장점 — **기존 파일을 안 건드린다.** 새 테이블·새 엔티티라 다른 사람 작업과 부딪히지 않는다
- 단점 — FK 무결성을 DB 가 못 지킨다 (`resource_id` 가 어느 테이블을 가리키는지 `resource_type` 이 정한다)
- 단점 — 테이블이 하나 는다

### B. `task` 에 컬럼을 더한다 (`submission_id` · `mail_receipt_id` …)

- 장점 — FK 무결성이 지켜진다
- **고르지 않은 이유** — **43번 일괄을 못 담는다.** 초안 N 개를 단수 컬럼에 넣을 수 없다
- **고르지 않은 이유** — 도메인이 붙을 때마다 `task` 가 넓어진다. 대부분 null 인 컬럼이 쌓인다

### C. `resource_ids` 를 JSON 컬럼 하나로

- 장점 — 테이블이 안 는다
- **고르지 않은 이유** — H2(dev)와 PostgreSQL(prod) 의 JSON 취급이 달라 프로필마다 다르게 동작할 위험이 있다. [ADR-0004](0004-database-profiles.md) 가 프로필별 차이를 줄이자고 한 것과 어긋난다

## 결정

**`task_resource` 테이블을 더한다.** 함께 정한 것 셋.

**① `Task` 엔티티를 옮기지 않는다.**
`feedback.domain.Task` 에 그대로 두고 `task` 패키지가 읽는다. **옮겨도 의존 방향이 안 바뀌기 때문이다** — `Task` 가 `Feedback` · `FeedbackDraft` 를 참조하고 있어서, `task.domain` 으로 옮기면 `task → feedback` 의존이 그대로 따라온다. 그 참조를 `task_resource` 로 걷어낸 뒤에 옮기는 것이 순서다. 지금 옮기면 방금 머지된 파일 넷을 건드리면서 얻는 것이 없다.

**② 기존 FK 는 지우지 않는다.**
CBAM-88 이 `feedback_id` · `attempt_number` 로 재발송 회차를 세고 있다(`countByFeedbackIdAndType`). 지금 지우면 그 코드가 깨진다. **새 타입만 `task_resource` 를 쓰고**, feedback 계열은 FK 에서 유도한다. 둘을 합치는 것은 그 코드를 옮길 사람의 몫이다.

**③ `unregisteredPartCount` 는 저장하지 않고 조회 시 센다.**
`ANALYZE_MAIL_RECEIPT` 전용 필드 하나를 모든 작업이 지고 가지 않는다. `task_resource` 가 가리키는 submission 의 미등록 부품을 세면 된다 — submission 도메인이 붙기 전에는 0 이고, 그때는 `resourceIds` 도 비어 있어 값이 어긋나지 않는다.

## 결과

- 쉬워진다 — 42·43번이 만든 초안을 화면이 №19 한 번으로 받는다. `/feedback-histories` 를 훑지 않는다
- 쉬워진다 — AI 분석(22번)·재판정·리마인드가 붙을 때 `task` 스키마를 안 고친다
- **감수한다** — `resource_id` 의 FK 무결성을 DB 가 못 지킨다. 기록하는 쪽이 맞는 id 를 넣어야 한다
- **감수한다** — feedback 계열은 FK, 나머지는 `task_resource` 로 **두 길이 잠시 공존한다.** №19 조회가 그 둘을 한 모양으로 합쳐 내보낸다

## 다시 볼 조건

- **`Task` 의 `feedback` · `feedback_draft` 참조를 `task_resource` 로 옮길 때** — 그때 `Task` 를 `task.domain` 으로 옮기고 두 길을 하나로 합친다
- `resource_type` 이 다섯을 넘어가면 — 타입별 조회가 늘어나 별도 인덱스 전략이 필요해진다
