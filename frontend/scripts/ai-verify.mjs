/* AI 응답 규격이 정한 대로인지 센다.  npm run ai:verify

   여기 있는 것은 「되는 것」 확인이 아니라 **막는 쪽**이다. ADR-0010 이 정한 넷을 센다.
     ① 스키마가 OpenAI 구조화 출력의 strict 규칙을 지키는가
        (모든 객체 additionalProperties:false · 모든 속성이 required · 루트가 object)
        — 안 지키면 요청이 400 으로 거절된다. 배포 전에 알아야 한다
     ② 예시 응답이 스키마를 통과하는가
     ③ **예시를 화면 변환에 통과시키면 화면이 읽는 키가 전부 나오는가**
        — 「기존 웹 구조와 호환되는가」를 문서의 주장이 아니라 검사로 만드는 자리다
     ④ 초안이 근거 밖 항목을 요구하지 않는가 (46번 세 번째 실패)

   되돌려서 확인했다 — 아래 「막는 쪽」 검사는 일부러 어긴 값을 넣어 실제로 걸리는지 본다.

   검증기를 직접 썼다. ajv 를 넣지 않은 이유는 ADR-0010 ④ 에 있다 —
   쓰는 스키마가 타입·enum·required·nullable 유니온뿐이고, 이 저장소는 이미
   「테스트 러너 없이 node 로 돌린다」를 택했다. 스키마가 그 범위를 넘으면 그때 ajv 를 넣는다. */
import { readFileSync } from 'node:fs'
import {
  rowsFromExtraction, unmappedPartsFrom, missingFieldsFromExtraction,
  toneOf, unsupportedRequests, submissionFromExtraction, LOW_CONFIDENCE,
} from '../src/api/ai.js'

let fail = 0, n = 0
const ok = (cond, t, extra = '') => { n++; console.log((cond ? 'ok   ' : 'FAIL ') + t + (extra ? '  ' + extra : '')); if (!cond) fail++ }
const H = s => console.log(`\n── ${s}`)
const load = f => JSON.parse(readFileSync(new URL(`../../docs/product/prompts/schema/${f}`, import.meta.url), 'utf8'))

const extractionSchema = load('extraction.schema.json')
const draftSchema = load('draft.schema.json')
const extraction = load('extraction.example.json')
const draft = load('draft.example.json')

/* ── 최소 JSON Schema 검증기 ────────────────────────────────
   지원: type(문자열 또는 유니온) · enum · properties · required · additionalProperties · items
   그 밖(pattern · $ref · anyOf · minimum …)은 쓰지 않는다 — 쓰게 되면 ajv 로 간다. */
function validate(schema, value, path = '$', errs = []) {
  const types = [].concat(schema.type ?? [])
  const actual = value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value
  const hit = types.length === 0 || types.some(t =>
    t === actual || (t === 'integer' && Number.isInteger(value)) || (t === 'number' && actual === 'number'))
  if (!hit) { errs.push(`${path}: ${types.join('|')} 가 와야 하는데 ${actual}`); return errs }
  if (value === null) return errs

  if (schema.enum && !schema.enum.includes(value)) errs.push(`${path}: ${JSON.stringify(value)} 는 enum 에 없다`)

  if (actual === 'object' && schema.properties) {
    for (const key of schema.required ?? [])
      if (!(key in value)) errs.push(`${path}.${key}: required 인데 없다`)
    if (schema.additionalProperties === false)
      for (const key of Object.keys(value))
        if (!(key in schema.properties)) errs.push(`${path}.${key}: 스키마에 없는 속성`)
    for (const [key, sub] of Object.entries(schema.properties))
      if (key in value) validate(sub, value[key], `${path}.${key}`, errs)
  }
  if (actual === 'array' && schema.items)
    value.forEach((v, i) => validate(schema.items, v, `${path}[${i}]`, errs))
  return errs
}

/** OpenAI strict 규칙 — 모든 객체가 additionalProperties:false 이고 모든 속성이 required 여야 한다 */
function strictProblems(schema, path = '$', out = []) {
  const types = [].concat(schema.type ?? [])
  if (types.includes('object') && schema.properties) {
    if (schema.additionalProperties !== false) out.push(`${path}: additionalProperties:false 가 없다`)
    const keys = Object.keys(schema.properties)
    const req = schema.required ?? []
    const miss = keys.filter(k => !req.includes(k))
    if (miss.length) out.push(`${path}: required 에 빠진 속성 ${miss.join(', ')} — strict 는 전부 required 여야 한다 (선택은 null 유니온으로)`)
    for (const [k, sub] of Object.entries(schema.properties)) strictProblems(sub, `${path}.${k}`, out)
  }
  if (types.includes('array') && schema.items) strictProblems(schema.items, `${path}[]`, out)
  return out
}

/* ── ① strict 규칙 ─────────────────────────────────────── */
H('구조화 출력 strict 규칙 (ADR-0010)')
for (const [name, s] of [['extraction', extractionSchema], ['draft', draftSchema]]) {
  const p = strictProblems(s)
  ok(p.length === 0, `${name}.schema.json 이 strict 규칙을 지킨다`, p.join(' · '))
  ok([].concat(s.type).includes('object'), `${name} 루트가 object 다 — 배열을 루트로 두면 거절된다`)
}
/* 되돌려 확인: 일부러 어긴 스키마는 실제로 걸린다 */
ok(strictProblems({ type: 'object', properties: { a: { type: 'string' } }, required: [], additionalProperties: false }).length > 0,
  '막는 쪽 — required 가 비면 strict 검사가 잡는다')
ok(strictProblems({ type: 'object', properties: { a: { type: 'string' } }, required: ['a'] }).length > 0,
  '막는 쪽 — additionalProperties 를 빠뜨리면 잡는다')

/* ── ② 예시가 스키마를 통과한다 ──────────────────────────── */
H('예시 응답이 스키마를 통과한다')
for (const [name, s, ex] of [['extraction', extractionSchema, extraction], ['draft', draftSchema, draft]]) {
  const e = validate(s, ex)
  ok(e.length === 0, `${name}.example.json 이 스키마를 통과한다`, e.slice(0, 3).join(' · '))
}
/* 되돌려 확인: 검증기가 실제로 잡는다 */
ok(validate(extractionSchema, { ...extraction, status: 'DONE' }).length > 0, '막는 쪽 — enum 밖 status 를 잡는다')
ok(validate(extractionSchema, { ...extraction, items: [{ ...extraction.items[0], where: undefined }] }).length > 0,
  '막는 쪽 — 추출 근거(where)가 빠지면 잡는다 (23번)')
ok(validate(extractionSchema, { ...extraction, extra: 1 }).length > 0, '막는 쪽 — 스키마에 없는 속성을 잡는다')
ok(validate(extractionSchema, { ...extraction, items: [{ ...extraction.items[0], value: 0 }] }).length > 0,
  '막는 쪽 — value 는 문자열이거나 null 이다. 숫자 0 을 넣으면 잡는다')

/* ── 규격이 명세의 규칙을 담고 있는가 ──────────────────────── */
H('규격이 명세를 담고 있는가')
const P = extractionSchema.properties
ok([].concat(P.items.items.properties.value.type).includes('null'),
  '24번 — value 가 null 이 될 수 있다 (변환 못 하면 비운다)')
ok(P.items.items.required.includes('note'),
  '24번 — note 가 required 다 (비웠으면 사유를 남긴다)')
ok(P.items.items.required.includes('where'),
  '23번 — where 가 required 다 (항목별 추출 근거)')
ok(P.items.items.required.includes('raw'),
  '24번 — raw 가 required 다 (변환 전 원본을 보존한다)')
ok([].concat(P.parts.items.properties.matchedPartId.type).includes('null'),
  '25번 — 매칭 실패를 null 로 말할 수 있다 (미등록 부품)')
const forbidden = ['judgement', 'severity', 'rule', 'isQualified']
ok(forbidden.every(k => !(k in P)),
  'ADR-0010 ① — 스키마에 판정 필드가 없다 (33~37번은 규칙이지 AI 가 아니다)', forbidden.join('/') + ' 없음')
ok(P.failure.properties.code.enum.includes('SCAN_QUALITY'),
  'R3 — 스캔 품질 미달을 «읽기의 실패»로 말한다')
ok(draftSchema.properties.requestedItems && draftSchema.required.includes('citedBasis'),
  '44번 — 초안이 무엇을 요구하고 어떤 근거를 썼는지 함께 낸다')

/* ── ③ 화면 변환 — 「기존 웹 구조와 호환되는가」 ────────────── */
H('화면 변환 (기존 웹 구조와 호환되는가)')
const rows = rowsFromExtraction(extraction)
/* RawStdPair.vue 가 실제로 읽는 키 — 이 목록이 곧 호환의 정의다 */
const NEEDED = ['field', 'raw', 'value', 'unit', 'note', 'tone']
ok(rows.length === extraction.items.length, '항목 수가 그대로다', `${rows.length}행`)
ok(rows.every(r => NEEDED.every(k => k in r)),
  'RawStdPair.vue 가 읽는 키가 전부 있다', NEEDED.join(' · '))
ok(rows.every(r => typeof r.field === 'string' && r.field !== 'other'),
  '화면에 「other」가 그대로 나오지 않는다 (원문이 부른 이름을 쓴다)', rows.map(r => r.field).join(', '))
ok(rows.every(r => ['complete', 'anomaly', 'expiring', 'missing'].includes(r.tone)),
  'tone 이 화면이 아는 넷 중 하나다', [...new Set(rows.map(r => r.tone))].join(' · '))

/* tone 은 모델이 정하지 않고 값에서 나온다 (ADR-0010 ③) — 네 갈래가 실제로 갈리는지 본다 */
ok(toneOf({ value: null, unit: 't', confidence: 1 }) === 'missing', 'tone — 값이 없으면 missing')
ok(toneOf({ value: '4,200', unit: null, confidence: 1 }) === 'anomaly', 'tone — 단위를 모르면 anomaly (R5)')
ok(toneOf({ value: '1', unit: 't', confidence: 0.5 }) === 'expiring', `tone — confidence < ${LOW_CONFIDENCE} 면 expiring`)
ok(toneOf({ value: '1', unit: 't', confidence: 1 }) === 'complete', 'tone — 다 갖추면 complete')
ok(new Set(rows.map(r => r.tone)).size >= 3, '예시가 tone 갈래를 실제로 여럿 보여준다 — 화면을 눈으로 볼 수 있다')

const sub = submissionFromExtraction(extraction, { id: 'sub-1', supplier: '성진스틸' })
ok(Array.isArray(sub.rows) && Array.isArray(sub.unmappedParts) && Array.isArray(sub.missingFields),
  '제출 데이터 모양(rows · unmappedParts · missingFields)이 나온다')
ok(!('judgement' in sub) && !('severity' in sub),
  '변환이 판정을 만들어 내지 않는다 — 서버(33~37번)가 채운다')
ok(missingFieldsFromExtraction(extraction).includes('제품') === false,
  '값이 있는 필수 항목은 누락으로 세지 않는다')
ok(missingFieldsFromExtraction({ items: [] }).length === 3,
  '33번 — 필수 셋(부품·생산량·직접배출량)이 통째로 없으면 셋 다 누락이다')
ok(unmappedPartsFrom({ parts: [{ rawName: '아연도금 증기', matchedPartId: null }] })[0] === '아연도금 증기',
  '25·27번 — 미등록 부품이 원문 표기 그대로 넘어온다')
ok(unmappedPartsFrom(extraction).length === 0, '예시는 매칭에 성공해 미등록 부품이 없다')

/* ── ④ 근거 밖 요구 — 46번 세 번째 실패 ────────────────────── */
H('초안이 근거 밖 항목을 요구하지 않는가 (46번)')
const basis = { missingFields: ['천연가스 사용량'], rows: rows.map(r => ({ field: r.field, label: r.label })), unmappedParts: [] }
ok(unsupportedRequests(draft, basis).length === 0,
  '예시 초안은 근거 안에서만 요구한다', draft.requestedItems.map(r => r.field).join(' · '))
const bad = { ...draft, requestedItems: [...draft.requestedItems, { field: '작년 생산 계획서', reason: '참고용' }] }
ok(unsupportedRequests(bad, basis).length === 1,
  '막는 쪽 — 근거에 없는 항목을 요구하면 잡는다 (기본 템플릿으로 떨어뜨릴 신호)', unsupportedRequests(bad, basis)[0])
ok(draft.citedBasis.length > 0, '초안이 어떤 근거를 썼는지 밝힌다')
ok(draft.body.length >= 3 && draft.body.every(p => typeof p === 'string'),
  '본문이 문단 배열이라 초안 화면이 그대로 그린다', `${draft.body.length}문단`)
ok(draft.dueDate === null || /^\d{4}-\d{2}-\d{2}$/.test(draft.dueDate),
  '회신 기한이 날짜이거나 null 이다 — 지어내지 않는다', String(draft.dueDate))

console.log(fail ? `\n검사 ${n}건 · 실패 ${fail}건` : `\n검사 ${n}건 · 전부 통과`)
if (fail) process.exit(1)
