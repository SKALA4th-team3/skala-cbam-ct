# 프롬프트 ① 자료 추출 — 요구사항 22 ~ 25번 (UC-05)

**무엇을 시키나** — 협력사가 보낸 메일 본문과 첨부에서 배출 항목을 뽑아 표준 단위로 옮기고, 등록 부품과 맞춰 본다.

**무엇을 안 시키나** — **적격 여부를 판정하지 않는다.** 33~37번은 규칙이지 AI가 아니다 ([AI_EXTENSION.md](../AI_EXTENSION.md) 「AI를 넣지 않는 곳」). 이 프롬프트의 출력에는 `judgement`·`severity`·`rule` 이 없다.

- 스키마: [`schema/extraction.schema.json`](schema/extraction.schema.json) — 명세 №21 의 `activityData` 로 옮겨진다
- 명세 대조: [`API_SPEC_V10_RECONCILE.md`](../API_SPEC_V10_RECONCILE.md) 5항
- 예시 응답: [`schema/extraction.example.json`](schema/extraction.example.json)
- 규격을 정한 근거: [ADR-0010](../../decisions/0010-ai-response-json-schema.md)

---

## 22번은 두 단계다

명세 22번 「본문·첨부에서 표와 텍스트를 추출」은 한 덩어리로 읽히지만 실제로는 성격이 다른 둘이다.

| | 하는 일 | 무엇으로 | 실패하면 |
| --- | --- | --- | --- |
| **①** | 파일 → 텍스트·표 | 라이브러리 (POI · PDFBox …) | `ENCRYPTED_FILE` · `PARSE_FAILED` · `UNSUPPORTED_FORMAT` · `NO_ATTACHMENT` (№16) |
| **②** | 텍스트 → 배출 항목 | **LLM (이 프롬프트)** | ⚠️ 담을 코드가 명세에 없다 — 아래 참고 |

**①에 LLM을 쓰지 않는다.** 암호 걸린 xlsx는 모델에 넣어도 안 열리고, 표 구조는 라이브러리가 더 정확하게 읽는다. ②의 실패는 성격이 다르다 — 텍스트는 나왔는데 읽을 것이 없는 경우(스캔 이미지라 글자가 거의 안 나옴)이고, 그건 모델이 판단해 `status: "ANALYSIS_FAILED"` 로 **정상 응답**한다. API 오류(№19 의 `errorCode: AI_ERROR|AI_TIMEOUT`)와 구별된다.

> ⚠️ **R3 「스캔 품질 미달」을 담을 실패 코드가 명세에 없다.** №16 의 `failureReason` 은 `ENCRYPTED_FILE` · `PARSE_FAILED` · `UNSUPPORTED_FORMAT` · `NO_ATTACHMENT` 넷뿐이다. 지어내지 않고 그때까지 `PARSE_FAILED` 로 둔다 — 팀 확인 대기 ([대조표](../API_SPEC_V10_RECONCILE.md) 6항).

## 왜 배열로 받고 객체로 옮기나

명세 **№21 의 저장 규격은 `activityData` 객체**다 — 키가 항목 이름(`electricity` · `fuel_lng` …)이다. 그런데 **구조화 출력 `strict` 는 모든 속성을 `required` 로 요구해 동적 키를 표현할 수 없고**, 연료 종류는 자료마다 다르다.

그래서 **모델에게는 배열(`items[]`)로 받고 서버가 객체로 옮긴다.** 각 항목이 `key` 를 들고 있어 변환이 기계적이다 — 사람이 판단할 자리가 없다. `frontend/src/api/ai.js` 의 `activityDataFrom()` 이 그 변환이고 `npm run ai:verify` 가 명세 예시와 같은 모양이 나오는지 센다.

---

## 요청 모양

구조화 출력(Structured Outputs)을 쓴다. **스키마가 문서가 아니라 강제다** — 모델이 스키마를 벗어난 JSON을 만들 수 없다.

```jsonc
{
  "model": "gpt-4.1-mini",                   // 실제로 불러 보고 고른 값 — 아래 「모델을 고른 근거」
  "temperature": 0,                          // 규제 신고 데이터다. 같은 입력에 같은 출력이어야 한다
  "messages": [
    { "role": "system",   "content": "<아래 시스템 프롬프트>" },
    { "role": "user",     "content": "<아래 사용자 메시지>" }
  ],
  "response_format": {
    "type": "json_schema",
    "json_schema": {
      "name": "cbam_extraction",
      "strict": true,
      "schema": { /* extraction.schema.json 을 그대로 */ }
    }
  }
}
```

> `strict: true` 는 스키마에 제약을 건다 — 모든 객체가 `additionalProperties: false` 이고 모든 속성이 `required` 에 있어야 한다. 선택 항목은 `"type": ["string", "null"]` 로 표현한다. 우리 스키마는 그 규칙을 지키고 있고, `npm run ai:verify` 가 그것을 센다.

---

## 시스템 프롬프트

**원본은 [`system/extraction.system.txt`](system/extraction.system.txt) 하나다.** 여기 옮겨 적지 않는다 —
두 벌을 두면 갈라지고, 갈라지면 문서가 말하는 것과 서버가 보내는 것이 달라진다 ([ADR-0012](../../decisions/0012-ai-call-restclient-and-polling.md) ⑤).
Gradle 의 `processResources` 가 그 파일을 백엔드 classpath 의 `ai/` 로 복사하고, `AiAssets` 가 그것을 읽는다.

### 실제로 불러 보고 고친 것

프롬프트를 **`gpt-4.1-mini` 로 실제 호출해** 사례 셋(정상 · 미등록 부품 · 읽을 것 없음)을 돌렸다.
첫 판은 아래 여섯 군데가 틀렸고, 전부 프롬프트 문제였다.

| 무엇이 틀렸나 | 왜 | 어떻게 고쳤나 |
| --- | --- | --- |
| **`1,250 t` → `1.25`** | 표준 단위 목록에 `t` 가 없어 kg 인 줄 알고 1/1000 을 곱했다 | 질량 단위 읽는 법을 나열하고 「이미 표준 단위인 값을 다시 나누지 않는다」를 명시 |
| `partName` 항목을 안 만듦 | 「partName 에 담는다」만 있고 「항목을 만든다」가 없었다 | 「반드시 찾아보는 항목」 절을 추가 |
| `productionCountry`·`documentMonth` 누락 | 위와 같음 | 위와 같음 |
| 단위 없는 값에 `VALUE_NOT_NUMERIC` | 코드 셋 중 하나만 설명했다 | 셋을 다 설명하고 「숫자는 있는데 단위만 없으면 UNIT_NOT_RECOGNIZED」 명시 |
| **등록된 부품이 미등록으로 안 잡힘** | 「목록에 없으면 unregisteredParts」만 있고 결정 절차가 없었다 | 「찾았다 / 못 찾았다」 둘로 갈라 쓰고, `partName: null` 로 대신하지 말라고 명시 |
| **분석 실패인데 입력 목록을 결과로 베낌** | 실패했을 때 배열을 어떻게 하라는 말이 없었다 | 「읽을 것이 없을 때」 절을 추가 |
| `productionCountry` 를 협력사 소재지로 추론 | 「원문에 없으면 만들지 않는다」가 이 항목에 안 걸렸다 | 항목 설명에 「추측하지 않는다」 추가 |

**고쳐도 모델이 안 지키는 것이 하나 남았다** — `conversionFailReason` 을 채우면서 `value` 에 숫자를 남긴다.
프롬프트에 두 번 적어도 그랬다. 그래서 **서버가 강제한다** (`ExtractionResult.normalize()`).
그대로 저장되면 **단위 없는 숫자가 신고 배출량이 된다.**

### 모델을 고른 근거

같은 자료로 셋을 비교했다. **`gpt-4.1-mini` 를 기본값으로 둔다** (`cbam.ai.model`).

| 모델 | 「값이 없다」(R2) 와 「단위를 몰라 못 옮겼다」(R5) 를 가르는가 |
| --- | --- |
| `gpt-4o-mini` | ✗ 항목을 통째로 빠뜨리거나 값을 남긴다 |
| `gpt-4o` | ✗ 값 없음을 `VALUE_NOT_NUMERIC` 으로 잘못 분류 |
| **`gpt-4.1-mini`** | **✓ 둘 다 정확. 실패 사유도 `PARSE_FAILED` 로 맞게 골랐다** |

이 구분이 무너지면 안내문이 협력사에 **이미 보낸 값을 다시 보내라고** 요청한다.

---

## 사용자 메시지

```
# 접수 정보
협력업체: {supplierName}
수신 일시: {receivedAt}
제출 대상 월: {reportingMonth}   ← 알려진 값이 없으면 이 줄을 빼십시오

# 메일 본문
{mailBody}

# 첨부에서 뽑아낸 텍스트
## {fileName1}
{extractedText1}

## {fileName2}
{extractedText2}

# 등록 부품 목록 (이 안에서만 매칭)
| id | 부품명 | CN 코드 | 공급 협력업체 |
| --- | --- | --- | --- |
{registeredParts}
```

**등록 부품 목록을 매번 넣는다.** 안 넣으면 모델이 그럴듯한 부품명을 지어내고, 그러면 25번의 「미등록 부품」이 한 번도 나오지 않는다 — 담당자는 매칭이 잘 됐다고 믿게 된다.

---

## 화면으로 어떻게 이어지나

출력은 `frontend/src/api/ai.js` 의 `rowsFromExtraction()` 을 지나 검토 화면의 행이 된다. **표시 상태(tone)는 모델이 정하지 않고 값에서 끌어낸다** — 같은 값에 같은 색이어야 하기 때문이다.

| 조건 | tone | 화면 |
| --- | --- | --- |
| `conversionFailReason` 이 있다 | `anomaly` | 주황 — R5. **원문은 있는데 못 옮겼다** |
| `value === null` | `missing` | 붉은 왼쪽 선 — R2. **원문에 없다** |
| `confidence < 0.9` | `expiring` | 노랑 — 사람 확인 대기 |
| 그 밖 | `complete` | 초록 |

**순서가 뜻이다.** №21 의 `fuel_lng` 는 `value: null` 이면서 `rawValue: "45,000"` 이다 — 원문에는 값이 있는데 단위를 몰라 못 옮긴 것이고, 원문에 없는 것과 다르다. 둘을 섞으면 안내문이 협력사에 「기재해 주세요」라고 잘못 요청한다.

`npm run ai:verify` 가 예시 응답을 이 함수에 통과시켜 화면이 읽는 키가 전부 나오는지 센다.
