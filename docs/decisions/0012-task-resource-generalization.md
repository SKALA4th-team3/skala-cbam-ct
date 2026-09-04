# ADR-0012: 비동기 작업이 만든 것을 `task.resource_ids` 로 가리킨다

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

CBAM-88(PR #31)이 만든 **`Task` 엔티티**는 그것을 가리키지 못했다. `feedback` · `feedback_draft` 를 `@ManyToOne` **단수**로만 갖는데,

- **43번 일괄 생성**은 초안 N 개를 만들고 Task 는 **하나**다 — 단수 FK 로는 N 개를 가리킬 수 없다
- **`ANALYZE_MAIL_RECEIPT`** 는 feedback 과 무관하다 — 가리켜야 할 것은 submission 이다

그래서 초안을 만들어도 **그 id 를 받을 길이 없었다.** 화면이 `/feedback-histories` 를 훑어 찾아야 했다 (PR #31 리뷰에서 확인했다).

## ⚠️ 처음 쓴 결론을 뒤집었다

이 ADR 의 첫 판은 **`task_resource` 라는 별도 테이블을 더하자**고 했다. 그리고 「`task` 에 컬럼을 더한다」와 「JSON 컬럼 하나로 둔다」를 검토해서 안 고른 것으로 적었다.

**그 판단은 ERD 를 못 보고 내린 것이었다.** CBAM-90(PR #22)이 머지되면서 들어온 [`db/init_db.sql`](../../backend/src/main/resources/db/init_db.sql) 을 보니 `task` 테이블이 **이미 그 컬럼들을 갖고 있었다.**

```sql
CREATE TABLE task (
    ...
    mail_receipt_id bigint REFERENCES mail_receipt(id),
    submission_id   bigint REFERENCES submission(id),
    resource_type   varchar(50),
    resource_ids    jsonb,            -- ← 여러 개를 담는다
    unregistered_part_count integer NOT NULL DEFAULT 0,
    ...
);
```

**엔티티가 ERD 를 다 옮기지 않았을 뿐이고, 스키마는 이미 №19 를 풀어 두었다.** 별도 테이블을 더했다면 `Task` 주석이 경고한 바로 그것이 됐다 — 「**다른 타입을 쓸 도메인이 생기면 이 엔티티를 그대로 확장해서 써야 한다(중복 매핑 금지)**」.

되돌린 접근을 지우지 않고 남긴다. 다음 사람이 같은 길로 가지 않도록.

## 결정

**ERD 의 `task.resource_type` · `resource_ids` · `unregistered_part_count` 를 엔티티에 매핑해 쓴다.** 새 테이블을 만들지 않는다.

**① `resource_ids` 는 `jsonb` 다.**
Hibernate 6 의 `@JdbcTypeCode(SqlTypes.JSON)` 로 `List<Long>` 을 매핑한다. PostgreSQL 은 `jsonb`, 테스트용 H2 는 Hibernate 가 만드는 JSON 타입이라 프로필별로 코드가 갈리지 않는다.

**② `Task` 엔티티를 옮기지 않는다.**
`feedback.domain` 에 그대로 두고 `task` 패키지가 읽는다. **옮겨도 의존 방향이 안 바뀌기 때문이다** — `Task` 가 `Feedback` · `FeedbackDraft` 를 참조하고 있어서 `task.domain` 으로 옮기면 `task → feedback` 의존이 따라온다. 그 참조를 `resource_ids` 로 걷어낸 뒤에 옮기는 것이 순서다.

**③ 기존 FK 는 지우지 않는다.**
CBAM-88 이 `feedback_id` · `attempt_number` 로 재발송 회차를 센다(`countByFeedbackIdAndType`). 지금 지우면 그 코드가 깨진다. **새 작업은 `resource_ids` 를 채우고**, 그것이 빈 예전 작업은 FK 에서 유도한다 — №19 조회가 둘을 한 모양으로 합쳐 내보낸다.

**④ `unregistered_part_count` 는 분석이 저장한다.**
조회 때마다 세지 않는다. ERD 에 컬럼이 있고, 세는 시점(분석 직후)과 보는 시점 사이에 담당자가 부품을 등록하면(28번) 값이 달라지는데, **№19 는 「그 작업이 무엇을 남겼는가」를 말하는 자리**다. 지금 남은 미등록 부품 수는 №21 제출 상세가 말한다.

## 결과

- 쉬워진다 — 42·43번이 만든 초안을 화면이 №19 한 번으로 받는다. `/feedback-histories` 를 훑지 않는다
- 쉬워진다 — 새 테이블이 없어 `init_db.sql` 을 고치지 않는다. `ddl-auto: validate` 가 그대로 통과한다
- **감수한다** — `resource_ids` 의 무결성을 DB 가 못 지킨다. 기록하는 쪽이 맞는 id 를 넣어야 한다
- **감수한다** — 새 작업은 `resource_ids`, 예전 작업은 FK 로 **두 길이 잠시 공존한다**

## 다시 볼 조건

- **`Task` 의 `feedback` · `feedback_draft` 참조를 `resource_ids` 로 옮길 때** — 그때 `Task` 를 `task.domain` 으로 옮기고 두 길을 하나로 합친다
- `resource_ids` 로 거꾸로 찾아야 할 때 (「이 제출 건을 만든 작업이 무엇인가」) — `jsonb` 인덱스가 필요해진다. 지금은 그 조회가 없다
