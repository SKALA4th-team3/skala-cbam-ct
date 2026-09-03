/* 화면이 부르는 API 표면 전부.
   각 함수는 실제 엔드포인트 하나와 1:1 이고, 주석에 그 경로를 적어 둔다.
   BE 가 나오면 여기 본문만 fetch 로 갈아끼운다. 화면 코드는 건드릴 일이 없다. */
import { request, page, startTask, getTask, ApiError } from './client'
import { suppliers, parts } from '@/mocks/seed'
import { PRODUCTS, EMISSIONS, INBOX, SUBMISSION, SUBMISSIONS, QUEUE, CHECKS, SEVERITIES, DEADLINES, REMINDERS, DISPATCH, TONES } from './fixtures'

const clone = v => JSON.parse(JSON.stringify(v))
let SUP = clone(suppliers)
let PRT = clone(parts)

/* ── UC-01 협력업체 ─────────────────────────────────────── */
export const Suppliers = {
  /** GET /suppliers?q&country&tie&judgement&page */
  list: (q = {}) => request('GET /suppliers', () => {
    let rows = SUP
    if (q.q) rows = rows.filter(s => s.name.toLowerCase().includes(q.q.toLowerCase()))
    for (const [k, key] of [['country', 'country'], ['tie', 'tie'], ['judgement', 'judgement']])
      if (q[k]?.length) rows = rows.filter(s => q[k].includes(s[key]))
    if (q.sort === '업체명순') rows = [...rows].sort((a, b) => a.name.localeCompare(b.name))
    else rows = [...rows].sort((a, b) => SEV[a.judgement] - SEV[b.judgement])
    return page(rows, q)
  }),
  /** GET /suppliers/{id} */
  get: id => request('GET /suppliers/{id}', () => {
    const s = SUP.find(x => x.id === +id)
    if (!s) throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다')
    return { ...s, parts: PRT.filter(p => p.supplier === s.name) }
  }),
  /** POST /suppliers */
  create: body => request('POST /suppliers', () => {
    const dup = SUP.find(s => s.bizNo && s.bizNo === body.bizNo) || SUP.find(s => s.email && s.email === body.email)
    if (dup) throw new ApiError(409, 'DUPLICATE', '사업자 등록번호나 담당자 이메일이 이미 있습니다', ['bizNo', 'email'])
    const row = { id: SUP.length + 1, judgement: '미제출', tie: '협력유지중', strip: '0'.repeat(12), ...body }
    SUP = [row, ...SUP]
    return row
  }),
  /** GET /suppliers/facets — 필터 배지 숫자. 화면이 직접 세지 않는다 */
  facets: () => request('GET /suppliers/facets', () => ({
    country: count(SUP, 'country'), tie: count(SUP, 'tie'), judgement: count(SUP, 'judgement'), total: SUP.length,
  })),
}
const SEV = { 미제출: 0, 부적격: 1, 적격: 2 }
const count = (rows, k) => rows.reduce((m, r) => (m[r[k]] = (m[r[k]] || 0) + 1, m), {})

/* ── UC-02 부품 ─────────────────────────────────────────── */
export const Parts = {
  /** GET /parts?q&supplier&cn */
  list: (q = {}) => request('GET /parts', () => {
    let rows = PRT
    if (q.q) rows = rows.filter(p => p.name.includes(q.q))
    if (q.supplier?.length) rows = rows.filter(p => q.supplier.includes(p.supplier))
    if (q.cn?.length) rows = rows.filter(p => q.cn.includes(p.cnGroup))
    return page(rows, { ...q, size: q.size ?? 50 })
  }),
  facets: () => request('GET /parts/facets', () => ({ supplier: count(PRT, 'supplier'), cn: count(PRT, 'cnGroup'), total: PRT.length })),
  /** POST /parts — 미등록 부품을 담당자가 직접 등록한다 (명세 28) */
  create: body => request('POST /parts', () => {
    if (!/^\d{4} ?\d{2}$/.test(body.cn || '')) throw new ApiError(400, 'INVALID_CN', 'CN 코드는 8자리 숫자입니다', ['cn'])
    if (PRT.some(p => p.name === body.name)) throw new ApiError(409, 'DUPLICATE', '같은 부품명이 이미 있습니다', ['name'])
    const row = { factor: null, unit: 'tCO2e/t', ...body }
    PRT = [row, ...PRT]
    return row
  }),
}

/* ── UC-03 완제품 ───────────────────────────────────────── */
export const Products = {
  /** GET /products */
  list: (q = {}) => request('GET /products', () => {
    let rows = PRODUCTS
    if (q.q) rows = rows.filter(p => p.name.includes(q.q))
    if (q.cn?.length) rows = rows.filter(p => q.cn.includes(p.cnGroup))
    return page(rows, { ...q, size: 20 })
  }),
  facets: () => request('GET /products/facets', () => ({ cn: count(PRODUCTS, 'cnGroup'), total: PRODUCTS.length })),
  /** GET /products/{id}/emissions */
  emissions: id => request('GET /products/{id}/emissions', () => EMISSIONS[id] || EMISSIONS['hr-2400']),
}

/* ── UC-04 이메일 접수 ──────────────────────────────────── */
export const Inbox = {
  /** GET /submissions/inbox */
  list: () => request('GET /submissions/inbox', () => ({ items: INBOX })),
  /** PUT /submissions/{id}/supplier — 미확인 건에 협력업체를 직접 지정 */
  assign: (id, supplierName) => request('PUT /submissions/{id}/supplier', () => {
    const m = INBOX.find(x => x.id === id); if (m) { m.supplier = supplierName; m.state = '검토 대기' }
    return m
  }),
}

/* ── UC-05 AI 분석 ──────────────────────────────────────── */
export const Analysis = {
  /** POST /submissions/{id}/parse → 202 { taskId } */
  parse: (id, opts) => startTask('POST /submissions/{id}/parse', 'parse', { result: id, ...opts }),
  /** GET /tasks/{id} */
  task: getTask,
  /** GET /submissions/{id} */
  get: id => request('GET /submissions/{id}', () => SUBMISSIONS[id] ?? SUBMISSION),
}

/* ── UC-07 검토 ─────────────────────────────────────────── */
export const Review = {
  /** GET /submissions?status=review */
  queue: () => request('GET /submissions', () => ({ items: QUEUE })),
  /** PUT /submissions/{id} → CONFIRMED */
  /* 명세 31 — 「판정이 적격이고 미등록 부품이 없는 경우에만 확정할 수 있다」
     화면이 버튼을 잠그더라도 서버가 다시 막는다. 실제 BE 도 이 셋을 지켜야 한다. */
  confirm: id => request('PUT /submissions/{id}', () => {
    const s = SUBMISSIONS[id] ?? SUBMISSION
    if (s.missingFields.length)
      throw new ApiError(400, 'MISSING_FIELDS', '누락 항목이 있어 확정할 수 없습니다', s.missingFields)
    if (s.judgement !== '적격')
      throw new ApiError(409, 'NOT_ELIGIBLE', `판정이 ${s.judgement} 이라 확정할 수 없습니다`)
    if (s.unmappedParts.length)
      throw new ApiError(409, 'UNMAPPED_PARTS', '미등록 부품이 있어 확정할 수 없습니다', s.unmappedParts)
    return { id, status: 'CONFIRMED', confirmedAt: '2026-09-02T15:10:00+09:00' }
  }),
  /** PUT /submissions/{id}/reject */
  reject: (id, reason) => request('PUT /submissions/{id}/reject', () => ({ id, status: 'REJECTED', reason })),
}

/* ── UC-08 적격 판정 ────────────────────────────────────── */
export const Rules = {
  /** GET /rules */
  get: () => request('GET /rules', () => ({ checks: CHECKS, severities: SEVERITIES })),
  /** PUT /rules */
  update: body => request('PUT /rules', () => ({ ...body, appliesFrom: 'next' })),
}

/* ── UC-09 마감 ─────────────────────────────────────────── */
export const Deadlines = {
  /** GET /deadlines */
  list: () => request('GET /deadlines', () => ({ items: DEADLINES })),
  /** GET /deadlines/current/unsubmitted */
  unsubmitted: () => request('GET /deadlines/current/unsubmitted', () => ({ items: REMINDERS })),
  /** POST /reminders { supplierIds } */
  remind: ids => request('POST /reminders', () => ({ sent: ids.length, sentAt: '2026-09-03' })),
}

/* ── UC-10·11 피드백 ────────────────────────────────────── */
export const Feedback = {
  /** POST /feedback/draft { submissionId, tone } */
  draft: (submissionId, tone = '격식') => request('POST /feedback/draft', () => ({ submissionId, tone, body: TONES[tone] })),
  /** GET /feedback */
  list: () => request('GET /feedback', () => ({ items: DISPATCH })),
  /** PUT /feedback/{id}/confirm */
  confirm: id => request('PUT /feedback/{id}/confirm', () => ({ id, status: '발송 대기', locked: true })),
  /** POST /feedback/{id}/send */
  send: ids => request('POST /feedback/{id}/send', () => ({ sent: ids.length })),
  /** POST /feedback/{id}/resend */
  resend: id => request('POST /feedback/{id}/resend', () => ({ id, resendCount: 2 })),
}

/* ── UC-12 대시보드 ─────────────────────────────────────── */
export const Dashboard = {
  /** GET /dashboard?month=2026-09 */
  summary: () => request('GET /dashboard', () => ({
    month: '2026-09', deadline: '2026-09-30', dDay: 27,
    judgement: { 적격: 31, 부적격: 12, 미제출: 5, total: 48 },
    severity: { HIGH: 2, MEDIUM: 6, LOW: 2 },
    trend: [21, 23, 22, 26, 29, 31],
    todo: SUP.filter(s => s.judgement !== '적격').slice(0, 5),
  })),
}

export { ApiError }
export * from './fixtures'
