/* AI 응답 ↔ 화면 · ↔ 명세 №21 경계.

   ADR-0010 으로 「응답 규격을 화면이 이미 읽는 모양에서 뽑는다」를 정했는데,
   원본 명세를 받아 보니 **№21 제출 데이터 상세가 이미 저장 규격을 갖고 있었다**
   (activityData · unregisteredParts). 그래서 셋을 잇는다.

     OpenAI 구조화 출력 (items[] 배열)
       ├─ rowsFromExtraction()      → 화면 행        (RawStdPair.vue · 명세 30번)
       └─ activityDataFrom()        → №21 activityData (서버가 저장하는 모양)

   **배열로 받고 객체로 옮긴다.** 구조화 출력 strict 는 모든 속성을 required 로 요구해
   동적 키(fuel_lng · fuel_anthracite …)를 표현할 수 없는데, 연료 종류는 자료마다 다르다.
   변환이 기계적이라(key 가 곧 객체의 키다) 사람이 판단할 자리가 없다.

   이 파일은 아무것도 import 하지 않는다 — `node scripts/ai-verify.mjs` 가 vite 없이 바로 읽는다. */

/** 33번 필수 항목 — 「부품·생산량·직접 배출량」. 직접 배출량은 키가 자료마다 달라(fuel_*)
 *  이름이 아니라 emissionScope 로 찾는다. */
export const REQUIRED_KEYS = ['partName', 'production']
export const LOW_CONFIDENCE = 0.9

/** 표시 상태는 **모델이 정하지 않는다.** 값에서 끌어낸다 — 같은 값에 같은 색이어야 한다 (ADR-0010 ③).
 *
 *  순서가 뜻이다. `conversionFailReason` 이 먼저인 이유 —
 *  №21 의 fuel_lng 는 `value: null` 이면서 `rawValue: "45,000"` 이다.
 *  원문에는 값이 있는데 단위를 몰라 못 옮긴 것(R5)이지, 원문에 없는 것(R2)이 아니다.
 *  둘을 섞으면 협력사에 「기재해 주세요」라고 잘못 요청하게 된다. */
export function toneOf(item) {
  if (item.conversionFailReason) return 'anomaly'                 // R5 — 원문은 있는데 못 옮겼다
  if (item.value === null) return 'missing'                       // R2 — 원문에 없다
  if (item.confidence < LOW_CONFIDENCE) return 'expiring'         // 추론 — 사람 확인 대기
  return 'complete'
}

/** 추출 결과 → 검토 화면의 행 (RawStdPair.vue 가 field · raw · value · unit · note · tone 을 읽는다).
 *  `field` 는 화면이 mono 로 보여주는 이름이라 연료처럼 키가 낯선 것은 원문이 부른 이름(label)을 쓴다. */
export function rowsFromExtraction(extraction) {
  return (extraction.items ?? []).map(it => ({
    field: it.key.startsWith('fuel_') ? it.label : it.key,
    key: it.key,
    label: it.label,
    raw: it.rawValue,
    value: it.value,
    unit: it.unit,
    note: it.note,
    where: it.source?.locator ?? null,
    attachmentId: it.source?.attachmentId ?? null,
    emissionScope: it.emissionScope,
    confidence: it.confidence,
    tone: toneOf(it),
  }))
}

/** 모델 출력(배열) → 명세 №21 의 `activityData` (객체). **서버가 저장할 때 쓰는 모양이다.**
 *  화면은 이 모양을 읽지 않는다 — rowsFromExtraction() 이 화면 몫이다. */
export function activityDataFrom(extraction) {
  const out = {}
  for (const it of extraction.items ?? []) {
    const cell = { value: it.value, rawValue: it.rawValue, source: it.source }
    if (it.unit !== null) cell.unit = it.unit
    if (it.emissionScope !== null) cell.emissionScope = it.emissionScope
    if (it.conversionFailReason !== null) cell.conversionFailReason = it.conversionFailReason
    out[it.key] = cell
  }
  return out
}

/** 25번 — 등록 부품과 매칭되지 않은 품명. **원문 표기 그대로** 넘긴다 (№21 unregisteredParts · 27번) */
export const unregisteredPartNames = extraction =>
  (extraction.unregisteredParts ?? []).map(p => p.rawPartName)

/** 33번 필수 항목 검증 — 「부품·생산량·직접 배출량 중 빈 값」.
 *  ⚠️ **이건 규칙이지 AI 가 아니다.** 서버가 판정해 №21 의 validation 에 담는다 (ADR-0010 ①).
 *     여기 있는 것은 목이 22번 → 33번 흐름을 끝까지 보여주기 위한 것이다. */
export function missingFieldsFromExtraction(extraction) {
  const items = extraction.items ?? []
  const filled = k => items.some(it => it.key === k && it.value !== null)
  const out = REQUIRED_KEYS.filter(k => !filled(k))
  /* 직접 배출량은 키가 fuel_* 로 갈려 이름으로 못 찾는다 — 배출 구분으로 본다 */
  const direct = items.filter(it => it.emissionScope === 'DIRECT')
  if (!direct.some(it => it.value !== null)) out.push('directEmission')
  return out
}

/** 22번 — 모델이 「읽지 못했다」고 정상 응답한 경우. №19 의 errorCode(AI_ERROR·AI_TIMEOUT)와 구별된다 */
export const analysisFailedFrom = extraction => extraction.status === 'ANALYSIS_FAILED'

/** 추출 결과를 화면이 읽는 제출 데이터 모양으로. 판정(judgement·severity·rule)은 여기서 만들지 않는다 */
export function submissionFromExtraction(extraction, base = {}) {
  return {
    ...base,
    rows: rowsFromExtraction(extraction),
    unmappedParts: unregisteredPartNames(extraction),
    missingFields: missingFieldsFromExtraction(extraction),
    analysisFailed: analysisFailedFrom(extraction),
    failureReason: extraction.failureReason,
  }
}

/* ── 피드백 초안 (42~45번) ─────────────────────────────────── */

/** 초안 응답 → 화면이 읽는 모양.
 *  ⚠️ №27 의 `body` 는 **문자열 하나**다. 모델에게는 문단 배열로 받고 서버가 이어 붙인다 —
 *     문단 경계를 모델이 정하게 두면 화면이 다시 나눠야 하는데 그 규칙이 어디에도 없다. */
export function draftFromResponse(res, base = {}) {
  return {
    ...base,
    subject: res.subject,
    body: res.bodyParagraphs,                       // 화면이 문단마다 <p> 로 그린다
    bodyText: (res.bodyParagraphs ?? []).join('\n\n'), // №27 body 로 저장할 문자열
    requestedItems: res.requestedItems,
    citedRuleIds: res.citedRuleIds,
    dueDate: res.dueDate,
  }
}

/** 근거에서 «요구해도 되는 항목» 키를 모은다 — activityData 의 키 · 미등록 부품의 원문 표기 */
export function allowedRequestKeys(basis = {}) {
  const out = new Set()
  for (const it of basis.items ?? []) out.add(String(it.key).trim())
  for (const p of basis.unregisteredParts ?? []) out.add(String(p.rawPartName ?? p).trim())
  for (const k of basis.extraKeys ?? []) out.add(String(k).trim())
  return out
}

/** 46번 세 번째 실패 — **스키마는 지키면서 근거 밖 항목을 요구하는 경우.**
 *  스키마로 막히지 않으므로 서버가 대조해야 한다. 하나라도 나오면 초안을 버리고 기본 템플릿으로 간다
 *  (№27 의 fallbackApplied · fallbackTemplateId).
 *  「없는 값을 채우자고 요구하지 않는다」가 이 함수로 지켜진다. */
export function unsupportedRequests(draft, basis) {
  const allowed = allowedRequestKeys(basis)
  return (draft.requestedItems ?? []).map(r => String(r.key).trim()).filter(k => !allowed.has(k))
}
