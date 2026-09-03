# CBAM CT API 계약 (초안)

FE 가 화면을 만들면서 실제로 부른 것들을 그대로 적었다.
**확정본이 아니다.** BE 담당자와 이 문서를 놓고 맞춘 뒤에 확정한다.

공통 규칙
- 베이스: `/api/v1`
- 목록 응답 봉투: `{ items, page, size, total }`
- 에러 응답: `{ code, message, fields? }` + 알맞은 HTTP 상태
- 시각은 ISO 8601 + 오프셋 (`2026-09-02T15:10:00+09:00`)
- 판정값은 `적격 | 부적격 | 미제출` 세 가지만 (명세 2차 수정)

---

## UC-01 협력업체

| | |
|---|---|
| `GET /suppliers` | `?q&country[]&tie[]&judgement[]&sort&page&size` |
| `GET /suppliers/facets` | 필터 배지 숫자. **화면이 직접 세지 않는다** |
| `GET /suppliers/{id}` | 상세 + 공급 부품 목록 |
| `POST /suppliers` | 409 `DUPLICATE` (`fields: ['bizNo','email']`) |
| `PUT /suppliers/{id}` | 담당자명 · 이메일 · 전화만 |
| `PUT /suppliers/{id}/tie` | `협력끊김` 전환. 데이터는 지우지 않는다 |

```json
// GET /suppliers/1
{ "id": 1, "name": "성진스틸", "bizNo": "123-45-67890", "country": "대한민국",
  "city": "포항", "item": "열연강판", "contact": "김철수",
  "email": "cs.kim@sungjin.co.kr", "phone": "054-123-4567",
  "tie": "협력유지", "judgement": "미제출", "why": "5개월 연속 미제출",
  "strip": "000100122222",
  "parts": [{ "name": "슬래브", "cn": "7207 11", "factor": "1.92", "unit": "tCO2e/t" }] }
```

`strip` 은 최근 12개월 판정. `0` 문제없음 `1` 부적격 `2` 미제출.

## UC-02 부품

| | |
|---|---|
| `GET /parts` | `?q&supplier[]&cn[]` |
| `GET /parts/facets` | |
| `POST /parts` | 400 `INVALID_CN` (8자리 숫자) · 409 `DUPLICATE` (부품명이 키) |
| `PUT /parts/{id}` | 벤치마크 팩터(평균값) 수정 |
| `POST /parts/bulk` | 엑셀 일괄. 행 단위 검증, 하나라도 틀리면 전체 미등록 |

## UC-03 완제품

| | |
|---|---|
| `GET /products` | `?q&cn[]` |
| `POST /products` | 부품 세부 포함 (부품명 · 협력사 · 투입량 · 상태) |
| `GET /products/{id}/emissions` | 확정분만 합산. 미확정은 `null` 로 두고 0 으로 채우지 않는다 |

## UC-04 이메일 접수

| | |
|---|---|
| `GET /submissions/inbox` | 접수 이력 |
| `PUT /submissions/{id}/supplier` | 미확인 건에 협력업체 직접 지정 |

메일함 폴링(1분)은 서버가 한다. 같은 메일은 `Message-ID` 로 한 번만 접수한다.

## UC-05 AI 분석 — **202 + 폴링**

```
POST /submissions/{id}/parse
→ 202 { "taskId": "tsk-8f2a", "pollAfterMs": 1200 }

GET /tasks/tsk-8f2a
→ 200 { "taskId": "tsk-8f2a",
        "status": "PENDING | PROCESSING | COMPLETED | FAILED",
        "resultId": "sub-1" | null,
        "error": { "code": "PARSE_FAILED", "message": "..." } | null }
```

FE 는 `pollAfterMs` 간격으로 `GET /tasks/{id}` 를 친다. 이 계약만 지키면 화면은 그대로다.

```json
// GET /submissions/sub-1 — 표준값과 원문을 나란히 준다
{ "id": "sub-1", "supplier": "성진스틸", "period": "2026 3분기",
  "rows": [
    { "field": "electricity", "raw": "전력사용량: 982,000 kWh",
      "value": "982", "unit": "MWh", "note": "환산 kWh→MWh" },
    { "field": "fuel_natural_gas", "raw": "연료 사용량: (기재 없음)",
      "value": null, "unit": "", "note": "R2 missingFields 등재" }
  ],
  "missingFields": ["fuel_natural_gas"],
  "unitUncertain": true, "confidence": 0.86 }
```

**읽지 못한 값은 `null` 이고 추정하지 않는다 (NFR-04).**

## UC-07 검토 · 확정

| | |
|---|---|
| `GET /submissions?status=review` | 기본 정렬 심각도 높은 순 |
| `PUT /submissions/{id}` | `{ "status": "CONFIRMED" }`. 적격 + 미등록 부품 없음일 때만 |
| `PUT /submissions/{id}/reject` | `{ "reason": "R2 필수 항목 누락" }` |

```json
// 확정 조건 위반
400 { "code": "MISSING_FIELDS", "message": "누락 항목이 있어 확정할 수 없습니다",
      "fields": ["fuel_natural_gas"] }
```

## UC-08 적격 판정

| | |
|---|---|
| `GET /rules` | 검증 3종 + 규칙별 심각도 |
| `PUT /rules` | 임계값 변경. **다음 판정부터** 적용 |

검증 3종은 필수 항목 / 평균값 ±30% / 이전 기간 대비 50%.
심각도는 R1~R2 High, R3~R4·R7 Medium, R5~R6 Low.

## UC-09 마감

| | |
|---|---|
| `GET /deadlines` | 월별 말일 고정. 일정별 적격 · 부적격 · 미제출 · 남은 일수 |
| `GET /deadlines/current/unsubmitted` | 리마인드 대상 |
| `POST /reminders` | `{ "supplierIds": [1,2,5] }` |

## UC-10·11 피드백

| | |
|---|---|
| `POST /feedback/draft` | `{ submissionId, tone: "격식\|간결\|친근" }` |
| `POST /feedback/draft/bulk` | 부적격·미제출 전체 일괄 |
| `GET /feedback` | 발송 이력 (발송일 · 제목 · 상태 · 회신 여부) |
| `PUT /feedback/{id}` | 초안 수정. AI 초안과 별도 버전 |
| `PUT /feedback/{id}/confirm` | 확정 → 수신자 · 제목 · 본문 잠김 |
| `POST /feedback/{id}/send` | |
| `POST /feedback/{id}/resend` | 실패 건 · 회신 없는 건 |

AI 실패 시 서버가 기본 템플릿 초안을 대신 준다 (명세 46).

## UC-12 대시보드

```
GET /dashboard?month=2026-09
→ { "month": "2026-09", "deadline": "2026-09-30", "dDay": 27,
    "judgement": { "적격": 31, "부적격": 12, "미제출": 5, "total": 48 },
    "severity": { "HIGH": 2, "MEDIUM": 6, "LOW": 2 },
    "trend": [21,23,22,26,29,31] }
```

---

## 먼저 합의해야 할 것

1. **페이지네이션** — offset 인지 cursor 인지. 지금 FE 는 `page/size` 를 가정한다.
2. **필터 배지 숫자** — 서버가 `facets` 로 주는 게 맞다. 화면이 세면 목록이 잘린 순간 어긋난다.
3. **`strip` (최근 12개월)** — 서버가 만들어 줄지, FE 가 제출 이력에서 만들지.
4. **파일 업로드** — 엑셀 일괄 등록(명세 11)은 `multipart/form-data` 인지 presigned URL 인지.
5. **인증** — 로그인은 범위 밖으로 정했다. 헤더에 무엇을 실을지만 정하면 된다.
