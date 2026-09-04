# API 명세서 v10 대조표 — 원본을 받고 맞춰 본 결과

**원본(`CBAM_API명세서_v10`)을 처음으로 직접 읽고 FE 코드와 한 줄씩 대조했다.** 그전까지는 원본이 저장소에 없어 [정정표](API_SPEC_ERRATA.md)와 화면이 읽는 모양을 근거로 추정한 자리가 여럿 있었다. 그 추정들의 결과를 여기 적는다.

> ⚠️ **원본은 여전히 저장소에 없다.** 이 문서는 원본을 읽고 옮긴 것이고, **원본과 다르면 원본이 맞다.** 원본 파일을 가진 사람이 확인해 주면 좋겠다.

**엔드포인트 수는 31건 대 31건으로 같은데, 그 안이 갈린다.**

---

## 🔴 1. 맞춰야 하는 것 — 코드가 명세와 다르다

### ① `POST /suppliers/{supplierId}/deactivate` 를 지운다 — №2 에 흡수돼 있었다

협력 끊김(6번)의 경로를 명세에서 못 찾아 **설계 파생**으로 만들었는데, **№2 가 수정과 끊김을 한 PATCH 에 담고 있었다.**

```
PATCH /api/v1/suppliers/{supplierId}
{ "contactName": "…", "contactEmail": "…", "phone": "…",
  "status": "INACTIVE", "statusReason": "거래 종료" }
→ { …, "excludedSubmissionCount": 3, "preservedSubmissionCount": 27 }
```

명세가 변경 사유에 그 이유를 적어 뒀다 — 「**같은 리소스에 PATCH 엔드포인트가 둘 있을 이유가 없다.** status 를 보내면 상태 전이가, 보내지 않으면 정보 수정만 일어난다」. 응답의 `excludedSubmissionCount`·`preservedSubmissionCount` 가 6번의 「제외된다 / 보존한다」를 숫자로 확인해 준다 — 화면이 「제출 데이터는 보존됩니다」라고 말만 하던 자리다.

### ② 부품의 공급 협력업체는 **배열**이다

| | 명세 (№5·№6·№7·№8) | 지금 FE |
| --- | --- | --- |
| 등록·수정 | `supplierIds: [1, 2, 3]` | `supplier: "성진스틸"` 하나 |
| 목록 응답 | `suppliers: [{id, name}, …]` | 이름 문자열 하나 |
| 키 | **`partCode` (PK)** + `partName` | `id` + `name` — `partCode` 가 없다 |
| 단위 | **`KG` · `TON` · `EA`** (대문자) | `kg` · `ton` · `EA` |
| 팩터 | `benchmarkFactor` 숫자 `decimal(10,4) ≥ 0` | `factor: "1.92 tCO₂e/t"` 문자열 |

요구사항 7번의 「**부품코드(PK)와 부품명은 중복 불가**」에서 부품코드를 빠뜨렸다. 명세에 `409 DUPLICATE_PART_CODE` 가 따로 있다.

### ③ 미등록 부품 해소는 `unregisteredPartId` 로 한다

내 목은 `resolves: { submissionId, name }` 을 만들어 썼는데, 명세 №5 는 **미등록 부품 자체에 id 가 있다** (№21 응답의 `unregisteredParts: [{ "id": 3001, "rawPartName": "hot rolled coil" }]`). 그 id 를 부품 등록에 실어 보내면 서버가 제출 데이터에 연결하고 **판정을 재실행**한다 (`revalidationTaskId`).

### ④ 완제품의 수출국은 **배열이고 ISO 코드**다

| | 명세 (№9) | 지금 FE |
| --- | --- | --- |
| 수출국 | `exportCountries: ["DE", "FR"]` — **EU 27개국 코드만** | `euCountry: "네덜란드"` 하나 |
| 부품 세부 | `{ partId, supplierId, inputQtyPerTon }` — **부품마다 협력사를 고른다** | 부품명 + 투입량 |
| 오류 | `400 INVALID_EU_COUNTRY` | 없음 |

요구사항 12번의 부품 세부 「② 협력사(누락 여부 상관없이 전부 표시)」가 **부품마다 협력사를 고르는 것**이라는 뜻이었다. 같은 부품을 여러 협력사가 공급하기 때문이다(②의 배열).

### ⑤ 접수 상태값의 영문 enum 이 있다 — 「확인하지 못했다」를 닫는다

[ADR-0005](../decisions/0005-status-enum-boundary.md) 와 `enums.js` 에 「접수 4값의 enum 이름을 확인하지 못해 비워 뒀다」고 적어 두었다. **№15 에 있다.**

```
WAITING | MATCHED | UNMATCHED | REJECTED | ANALYZED | ANALYSIS_FAILED
```

⚠️ **여섯이다.** 요구사항 「상태값」 절은 네 개(`접수 대기`·`미확인`·`접수 불가`·`분석 실패`)만 말한다. `MATCHED`(발신자 매칭됨)와 `ANALYZED`(분석 완료)가 명세 표에 없다 — **한글 이름을 팀이 정해야 한다.**

### ⑥ 분석 실패 사유도 enum 이다 (№16)

```
ENCRYPTED_FILE | PARSE_FAILED | UNSUPPORTED_FORMAT | NO_ATTACHMENT
```

내가 AI 스키마에 만든 `SCAN_QUALITY`·`NO_EMISSION_DATA` 는 **명세에 없다.** R3 의 「스캔 품질 미달」을 담을 코드가 명세에 없는 셈이라 팀에 물어야 한다.

---

## 🟡 2. 없는 화면 둘

| 명세 | 무엇 | 지금 |
| --- | --- | --- |
| **№17** `GET /attachments/{attachmentId}?disposition=inline\|attachment` | 원본 첨부 열람·다운로드 | 파일 이름만 보여주고 **열 수 없다.** 30번 「원본 첨부를 같은 화면에서 연다」가 반쪽이다 |
| **№25** `GET /dashboard/alerts` | 39번 마감 임박·미제출 경보. `ruleId`·`severity` 필터, `dDay` 포함 | 관제의 「손봐야 할 곳」이 이 API 를 안 쓰고 협력사 목록에서 만든다 |

№16 응답이 첨부마다 `viewUrl`·`downloadUrl` 을 이미 준다 — 화면은 그 URL 을 열기만 하면 된다.

---

## 🟢 3. 맞춘 것 — 추정이 들어맞았다

- **№16 접수 메일 상세** `GET /mail-receipts/{receiptId}` — 경로도 `latestAnalysisTaskId` 도 맞다
- **№28 재생성** `POST /feedback-drafts/{draftId}/regenerate` — 경로가 그대로다
- **№29 수정·확정·폐기를 PATCH 하나에** — 명세도 셋을 한 엔드포인트에 실었다. 「status 와 문안을 함께 보낼 수 없다(400 MIXED_UPDATE)」까지 같은 판단이다
- **№22 확정** — `details: { missingFields, unregisteredPartIds }` 까지 같다
- **№23 반려** — `resultStatus`(REJECTED\|NOT_SUBMITTED)와 `judgement` 를 두 축으로 나눈 것이 같다
- **№12 완제품 상세** — 미확정 부품이 있으면 `embeddedEmission: null` · `missingPartIds[]`. **합계를 지어내지 않는다**는 판단이 명세와 같다
- **№20 제출 목록** — 미제출은 `id: null` + `target`, `status` 와 `judgement` 분리
- **№13 마감** — 페이징하지 않고 `from`·`to` 범위 조회
- **№14 리마인드** — `targets: [{supplierId, partId}]`, partId 생략 가능
- **№19 「담당자에게 알린다」에 별도 알림 API 가 없다** — 명세가 못박았다. 「**요구사항 №19·№22 의 '담당자에게 알린다'는 이 목록의 status 필터로 충족한다(별도 알림 API 없음)**」. `useNotices.js` 가 접수함·발송 이력에서 골라내는 방식이 명세의 의도와 같다
- **33~37 판정이 규칙이다** — №21 응답의 `validation.rules[].checks[]` 가 규칙별 결과를 담는다. AI 응답에 판정을 넣지 않기로 한 [ADR-0010](../decisions/0010-ai-response-json-schema.md) ① 과 같은 방향이다

---

## 4. 설계 파생은 №19 였다 — ADR-0008 을 다시 봐야 한다

명세 머리글이 말한다: 「**총 31건 (요구사항 직접 30 · 설계 파생 1)**」. 그 1건은 **№19 `GET /tasks/{taskId}`** 다 (「v7 에 남은 유일한 [설계 파생] API」).

내가 [ADR-0008](../decisions/0008-feedback-history-list-endpoint.md) 로 더한 `GET /feedback-histories`(전체 조회)는 **명세에 여전히 없다.** 그런데 №31 의 Summary 가 이렇게 말한다 — 「요구사항 №51 의 **발송 실패 건은 status 필터로 확인한다**」. 명세는 협력업체별 조회 + `status=FAILED` 로 51번이 충족된다고 본다.

**ADR-0008 의 전제가 흔들린다.** 「48곳을 하나씩 불러야 한다」가 여전히 사실이지만, 명세가 그것을 감수하기로 한 것인지 아니면 그 화면을 협력업체 상세 안에 두라는 뜻인지 확인이 필요하다. → ADR-0008 「다시 볼 조건」의 첫 줄이 이 경우다.

---

## 5. AI 스키마에 미치는 것 — №21 이 이미 규격을 갖고 있다

**№21 제출 데이터 상세가 AI 추출 결과의 실제 저장 규격이다.** [ADR-0010](../decisions/0010-ai-response-json-schema.md) 을 쓸 때 이걸 못 봤다.

```jsonc
"activityData": {
  "electricity": { "value": 982, "unit": "MWh", "rawValue": "982,000 kWh",
                   "emissionScope": "INDIRECT",
                   "source": { "attachmentId": 9002, "locator": "csv:row=42,col=3" } },
  "fuel_lng":    { "value": null, "unit": "Nm3", "rawValue": "45,000",
                   "emissionScope": "DIRECT",
                   "conversionFailReason": "UNIT_NOT_RECOGNIZED",
                   "source": { "attachmentId": 9003,
                               "locator": "pdf:page=2,bbox=[72,540,180,556]" } }
}
```

| 내가 만든 것 | 명세 №21 |
| --- | --- |
| `items[]` 배열 | `activityData{}` — **키가 항목 이름** |
| `raw` | `rawValue` |
| `where: "xlsx 시트1 B4"` 문자열 | `source: { attachmentId, locator }` — **locator 문법이 규약 시트 10항에 있다** |
| `note` 자유 문장 | `conversionFailReason` **enum** (`UNIT_NOT_RECOGNIZED` …) |
| `confidence` 숫자 | 없다 |
| 없음 | `emissionScope: DIRECT \| INDIRECT` |
| `value` 문자열 | **숫자** |
| `unmappedParts: ["아연도금 증기"]` | `unregisteredParts: [{ id, rawPartName }]` — **id 가 있다** |

**`activityData` 를 모델 출력 스키마로 그대로 쓸 수는 없다.** 구조화 출력 `strict` 는 모든 속성을 `required` 로 요구해서 **동적 키(`fuel_lng`·`fuel_anthracite`…)를 표현할 수 없다.** 연료 종류는 자료마다 다르다.

**그래서 이렇게 나눈다** — AI 는 배열로 내고, **BE 가 №21 의 `activityData` 객체로 옮긴다.** 배열의 각 항목이 `key` 를 들고 있어 변환이 기계적이다. 이건 [ADR-0010](../decisions/0010-ai-response-json-schema.md) 의 「변환 계층을 만들지 않는다」와 어긋나 보이지만, **strict 모드의 제약이 강제하는 것**이라 ADR 에 그 사유를 더한다.

피드백 초안도 하나 어긋난다 — **№27 의 `body` 는 문자열**이다. 내 스키마는 문단 배열이다. 화면이 문단으로 그리므로 **BE 가 `\n\n` 으로 이어 붙이거나, 화면이 나누거나** 둘 중 하나를 정해야 한다.

`style` 도 enum 이다 — №26 이 `"FORMAL"`, №28 이 `"CONCISE"`. 세 번째(친근)의 이름은 명세 예시에 없어 확인이 필요하다.

---

## 6. 팀에 물어야 하는 것

1. **`MATCHED` · `ANALYZED` 의 한글 이름** — 요구사항 「상태값」 절에 없는 두 값
2. **R3 「스캔 품질 미달」을 담을 실패 코드** — №16 의 `failureReason` 네 값에 없다
3. **`style` 의 세 번째 값** — 친근이 `FRIENDLY` 인지
4. **№27 `body` 문자열 ↔ 화면 문단 배열** — 어디서 나눌지
5. **ADR-0008** — 발송 이력 전체 조회를 계속 밀지, №31 + `status` 필터로 갈지
6. **`locator` 문법** — 규약 시트 10항을 못 봤다. 예시 셋(`xlsx:Sheet1!B7` · `csv:row=42,col=3` · `pdf:page=2,bbox=[…]`)만 보고 썼다
