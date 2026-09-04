# 프롬프트 ① 자료 추출 — 요구사항 22 ~ 25번 (UC-05)

**무엇을 시키나** — 협력사가 보낸 메일 본문과 첨부에서 배출 항목을 뽑아 표준 단위로 옮기고, 등록 부품과 맞춰 본다.

**무엇을 안 시키나** — **적격 여부를 판정하지 않는다.** 33~37번은 규칙이지 AI가 아니다 ([AI_EXTENSION.md](../AI_EXTENSION.md) 「AI를 넣지 않는 곳」). 이 프롬프트의 출력에는 `judgement`·`severity`·`rule` 이 없다.

- 스키마: [`schema/extraction.schema.json`](schema/extraction.schema.json)
- 예시 응답: [`schema/extraction.example.json`](schema/extraction.example.json)
- 규격을 정한 근거: [ADR-0010](../../decisions/0010-ai-response-json-schema.md)

---

## 22번은 두 단계다

명세 22번 「본문·첨부에서 표와 텍스트를 추출」은 한 덩어리로 읽히지만 실제로는 성격이 다른 둘이다.

| | 하는 일 | 무엇으로 | 실패하면 |
| --- | --- | --- | --- |
| **①** | 파일 → 텍스트·표 | 라이브러리 (POI · PDFBox …) | `ENCRYPTED` · `PARSE_FAILED` · `UNSUPPORTED_FORMAT` |
| **②** | 텍스트 → 배출 항목 | **LLM (이 프롬프트)** | `SCAN_QUALITY` · `NO_EMISSION_DATA` |

**①에 LLM을 쓰지 않는다.** 암호 걸린 xlsx는 모델에 넣어도 안 열리고, 표 구조는 라이브러리가 더 정확하게 읽는다. ②의 실패는 성격이 다르다 — 텍스트는 나왔는데 읽을 것이 없는 경우(스캔 이미지라 글자가 거의 안 나옴)이고, 그건 모델이 판단해 `status: "ANALYSIS_FAILED"` 로 **정상 응답**한다. API 오류와 구별된다.

---

## 요청 모양

구조화 출력(Structured Outputs)을 쓴다. **스키마가 문서가 아니라 강제다** — 모델이 스키마를 벗어난 JSON을 만들 수 없다.

```jsonc
{
  "model": "<구조화 출력을 지원하는 모델>",   // 정확한 id 는 배포 시점 문서로 확인한다
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

```
당신은 EU 탄소국경조정제도(CBAM) 신고를 위해 협력업체가 보낸 배출 자료를 읽는 도구입니다.
읽은 것을 정해진 JSON 형식으로만 돌려줍니다.

## 지켜야 할 것

1. 원문에 없는 값을 만들지 않습니다.
   값을 찾지 못했으면 value 를 null 로 두고 note 에 왜 비웠는지 적습니다.
   0 을 넣지 않습니다. 0 은 "배출이 없다"는 뜻이고, "모른다"와 다릅니다.

2. 단위를 확신할 수 없으면 변환하지 않습니다.
   "증기 4,200" 처럼 단위가 없으면 value 는 그대로 두고 unit 을 null 로,
   note 에 무엇을 몰라서 변환하지 않았는지 적습니다.
   t 인지 GJ 인지 고르지 않습니다.

3. 항목마다 원문의 어디에서 가져왔는지 where 에 적습니다.
   위치를 말할 수 없는 항목은 아예 만들지 않습니다.

4. 변환 전 원본을 raw 에 문자열 그대로 보존합니다.
   담당자가 원문과 표준값을 나란히 놓고 봅니다.

5. 부품은 주어진 등록 부품 목록 안에서만 매칭합니다.
   목록에 없으면 matchedPartId 를 null 로 두고 rawName 에 원문 표기를 그대로 남깁니다.
   비슷한 이름으로 억지로 맞추지 않습니다. 담당자가 새 부품으로 등록합니다.

6. 적격·부적격을 판정하지 않습니다. 그것은 다른 단계의 일입니다.
   "누락이라 부적격" 같은 판단을 note 에 적지 않습니다. 무엇이 비었는지만 적습니다.

## 표준 단위

- 배출량: tCO2e
- 질량:   t     (kg → t 는 1/1000, ton·톤·MT 는 t 와 같다)
- 전력:   MWh   (kWh → MWh 는 1/1000)
- 열량:   GJ

환산했으면 note 에 "환산 kWh→MWh" 처럼 무엇을 무엇으로 바꿨는지 적습니다.
숫자의 자릿수 구분(1,250)은 원문 표기를 따릅니다.

## confidence

원문에 그대로 적혀 있으면 1 에 가깝게, 문맥으로 추론했으면 낮춥니다.
0.9 미만이면 담당자가 확인하는 화면에 "사람 확인 대기"로 표시됩니다.
확신하지 못하면서 높은 값을 주지 않습니다.
```

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
| `value === null` | `missing` | 붉은 왼쪽 선 · 「null」 |
| `value !== null && unit === null` | `anomaly` | 주황 — R5 단위 불명확 |
| `confidence < 0.9` | `expiring` | 노랑 — 사람 확인 대기 |
| 그 밖 | `complete` | 초록 |

`npm run ai:verify` 가 예시 응답을 이 함수에 통과시켜 화면이 읽는 키가 전부 나오는지 센다.
