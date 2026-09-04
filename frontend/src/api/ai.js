/* AI 응답 ↔ 화면 경계.

   ADR-0010 으로 정했다 — **AI 응답 JSON 규격은 화면이 이미 읽는 모양에서 뽑는다.**
   그래서 변환 계층이 따로 없고, 모델 출력에서 화면 행까지 이 파일의 함수 하나를 지난다.

     OpenAI 구조화 출력
       → docs/product/prompts/schema/extraction.schema.json 대로의 JSON
       → rowsFromExtraction()
       → RawStdPair.vue 가 그린다 (명세 30번)

   이 파일은 아무것도 import 하지 않는다 — `node scripts/ai-verify.mjs` 가 vite 없이 바로 읽는다. */

/** 스키마의 `field` enum 중 화면이 이름을 따로 보여주지 않는 것 */
const NAMED = new Set(['part_name', 'production', 'emission_direct', 'emission_indirect', 'production_country', 'reporting_month'])

/** 33번 필수 항목 — 「부품·생산량·직접 배출량」 */
export const REQUIRED_KINDS = ['part_name', 'production', 'emission_direct']

/** confidence 가 이 아래면 화면이 「사람 확인 대기」로 표시한다 */
export const LOW_CONFIDENCE = 0.9

/** 표시 상태는 **모델이 정하지 않는다.** 값에서 끌어낸다 — 같은 값에 같은 색이어야 한다 (ADR-0010 ③).
 *  순서가 뜻이다: 값이 없는 것이 먼저고, 그다음이 단위, 그다음이 확신도다. */
export function toneOf(item) {
  if (item.value === null) return 'missing'                       // 값이 없다 (R2 로 이어진다)
  if (item.unit === null) return 'anomaly'                        // R5 단위 불명확
  if (item.confidence < LOW_CONFIDENCE) return 'expiring'         // 추론 — 사람 확인 대기
  return 'complete'
}

/** 추출 결과 → 검토 화면의 행.
 *  `field` 는 화면이 mono 로 보여주는 이름이고, `kind` 는 스키마의 enum 이다 —
 *  `other` 가 다섯 개면 화면에 「other」가 다섯 번 나오므로 그때는 원문이 부른 이름(label)을 쓴다. */
export function rowsFromExtraction(extraction) {
  return (extraction.items ?? []).map(it => ({
    field: NAMED.has(it.field) ? it.field : it.label,
    kind: it.field,
    label: it.label,
    raw: it.raw,
    value: it.value,
    unit: it.unit,
    note: it.note,
    where: it.where,
    confidence: it.confidence,
    tone: toneOf(it),
  }))
}

/** 25번 — 등록 부품과 매칭되지 않은 품명. **원문 표기 그대로** 넘긴다 (27번이 그대로 보여준다) */
export function unmappedPartsFrom(extraction) {
  return (extraction.parts ?? []).filter(p => p.matchedPartId === null).map(p => p.rawName)
}

/** 33번 필수 항목 검증.
 *  ⚠️ **이건 규칙이지 AI 가 아니다.** 서버가 판정하고 응답에 담아 준다 (ADR-0010 ①).
 *     여기 있는 것은 목이 22번 → 33번 흐름을 끝까지 보여주기 위한 것이고,
 *     실서버가 붙으면 서버가 준 missingFields 를 그대로 쓴다. */
export function missingFieldsFromExtraction(extraction) {
  const seen = new Map((extraction.items ?? []).map(it => [it.field, it]))
  return REQUIRED_KINDS
    .filter(k => seen.get(k) == null || seen.get(k).value === null)
    .map(k => (seen.get(k)?.label ?? k))
}

/** 22번 — 모델이 「읽지 못했다」고 정상 응답한 경우. API 오류와 구별된다 */
export const analysisFailedFrom = extraction => extraction.status === 'ANALYSIS_FAILED'

/** 추출 결과를 화면이 읽는 제출 데이터 모양으로. 판정(judgement·rule·severity)은 여기서 만들지 않는다 */
export function submissionFromExtraction(extraction, base = {}) {
  return {
    ...base,
    sourceLanguage: extraction.sourceLanguage,
    reportingMonth: extraction.reportingMonth,
    productionCountry: extraction.productionCountry,
    rows: rowsFromExtraction(extraction),
    unmappedParts: unmappedPartsFrom(extraction),
    missingFields: missingFieldsFromExtraction(extraction),
    analysisFailed: analysisFailedFrom(extraction),
    failure: extraction.failure,
  }
}

/* ── 피드백 초안 (42~45번) ─────────────────────────────────── */

/** 초안 응답 → 화면이 읽는 모양. body 는 문단 배열 그대로다 */
export function draftFromResponse(res, base = {}) {
  return { ...base, subject: res.subject, body: res.body, requestedItems: res.requestedItems, citedBasis: res.citedBasis, dueDate: res.dueDate }
}

/** 근거에서 «요구해도 되는 항목» 이름을 모은다 — missingFields · 행 이름 · 미등록 부품 */
export function allowedRequestFields(basis = {}) {
  const out = new Set()
  for (const f of basis.missingFields ?? []) out.add(String(f).trim())
  for (const r of basis.rows ?? []) { if (r.field) out.add(String(r.field).trim()); if (r.label) out.add(String(r.label).trim()) }
  for (const p of basis.unmappedParts ?? []) out.add(String(p).trim())
  return out
}

/** 46번 세 번째 실패 — **스키마는 지키면서 근거 밖 항목을 요구하는 경우.**
 *  스키마로 막히지 않으므로 서버가 대조해야 한다. 하나라도 나오면 초안을 버리고 기본 템플릿으로 간다.
 *  「없는 값을 채우자고 요구하지 않는다」가 이 함수로 지켜진다. */
export function unsupportedRequests(draft, basis) {
  const allowed = allowedRequestFields(basis)
  return (draft.requestedItems ?? []).map(r => String(r.field).trim()).filter(f => !allowed.has(f))
}
