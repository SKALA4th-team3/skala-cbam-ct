/* 화면이 부르는 API 표면 전부.
   경로·메서드·응답 봉투·에러 코드는 「CBAM API 명세서 v10」을 그대로 따른다.
   각 함수는 명세의 엔드포인트 하나와 1:1 이고, 그 경로를 request() 의 첫 인자로 들고 있다.
   BE 가 붙으면 여기 본문만 fetch 로 갈아끼운다. 화면 코드는 건드릴 일이 없다.

   상태값은 서버가 영문 enum(ACTIVE·QUALIFIED·NOT_SUBMITTED …), 화면이 한글(협력유지중·적격·미제출 …)이다.
   ADR-0004 로 정했다 — 매핑표는 enums.js, 응답 변환은 client.js 의 request() 한 곳,
   서버로 보내는 필터·정렬은 각 엔드포인트가 toServer() 로 바꿔 보낸다. */
import { request, page, startTask, getTask, ApiError } from './client'
import { toServer, toCode } from './enums'
import { suppliers, parts } from '@/mocks/seed'
import { PRODUCTS, EMISSIONS, INBOX, SUBMISSION, SUBMISSIONS, QUEUE, DEADLINES, REMINDERS, DISPATCH, TONES } from './fixtures'

const clone = v => JSON.parse(JSON.stringify(v))
let SUP = clone(suppliers)
let PRT = clone(parts)

/* ── 협력업체 (명세 №1~4) ───────────────────────────────── */
export const Suppliers = {
  /** №3 GET /suppliers?search&country&status&submissionStatus&months&page&size&sort
      필터·정렬은 서버로 영문 enum 과 명세의 sort 키로 나간다 (ADR-0004).
      기본 정렬은 `companyName` — 요구사항 4번의 미결 항목을 ADR-0006 으로 닫았다.
      화면이 「심각도 높은 순」을 기본으로 쓰던 것은 명세가 허용하지 않는 키였다. */
  list: (q = {}) => request('GET /suppliers', () => {
    const query = toServer(q)                       // 한글 라벨 → enum. 실서버도 이 값을 받는다
    let rows = SUP
    if (query.q) rows = rows.filter(s => s.name.toLowerCase().includes(query.q.toLowerCase()))
    for (const k of ['country', 'tie', 'judgement'])
      if (query[k]?.length) rows = rows.filter(s => query[k].includes(toCode(s[k])))
    /* 명세가 허용하는 sort 키는 companyName · lastSubmittedAt 둘뿐이다.
       lastSubmittedAt 은 목 데이터에 그 필드가 없어 정렬하지 않는다 — 없는 값을 지어내지 않는다. */
    if ((query.sort ?? 'companyName') === 'companyName')
      rows = [...rows].sort((a, b) => a.name.localeCompare(b.name))
    return page(rows, query)
  }),
  /** №4 GET /suppliers/{supplierId} */
  get: id => request('GET /suppliers/{supplierId}', () => {
    const s = SUP.find(x => x.id === +id)
    if (!s) throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다')
    return { ...s, parts: PRT.filter(p => p.supplier === s.name) }
  }),
  /** №1 POST /suppliers */
  create: body => request('POST /suppliers', () => {
    if (SUP.some(s => s.bizNo && s.bizNo === body.bizNo))
      throw new ApiError(409, 'DUPLICATE_BUSINESS_NUMBER', '이미 등록된 사업자등록번호입니다', { fields: ['bizNo'] })
    if (SUP.some(s => s.email && s.email === body.email))
      throw new ApiError(409, 'DUPLICATE_CONTACT_EMAIL', '이미 등록된 담당자 이메일입니다', { fields: ['email'] })
    const row = { id: SUP.length + 1, judgement: '미제출', tie: '협력유지중', strip: '0'.repeat(12), ...body }
    SUP = [row, ...SUP]
    return row
  }),
}

/* ── 부품 (명세 №5~8) ───────────────────────────────────── */
export const Parts = {
  /** №7 GET /parts?search&supplierId&cnCode&page&size&sort */
  list: (q = {}) => request('GET /parts', () => {
    let rows = PRT
    if (q.q) rows = rows.filter(p => p.name.includes(q.q))
    if (q.supplier?.length) rows = rows.filter(p => q.supplier.includes(p.supplier))
    if (q.cn?.length) rows = rows.filter(p => q.cn.includes(p.cnGroup))
    return page(rows, { ...q, size: q.size ?? 50 })
  }),
  /** №5 POST /parts — unregisteredPartId 를 함께 보내면 미등록 부품을 해소한다 (요구사항 28) */
  create: body => request('POST /parts', () => {
    if (!/^\d{4} ?\d{2}$/.test(body.cn || ''))
      throw new ApiError(400, 'INVALID_CN_CODE', 'CN코드는 8자리 숫자여야 합니다', { fields: ['cn'] })
    if (PRT.some(p => p.name === body.name))
      throw new ApiError(409, 'DUPLICATE_PART_NAME', '같은 부품명이 이미 있습니다', { fields: ['name'] })
    const row = { factor: null, unit: 'tCO2e/t', ...body }
    PRT = [row, ...PRT]
    return row
  }),
}

/* ── 완제품 (명세 №9~12) ────────────────────────────────── */
export const Products = {
  /** №11 GET /products?search&cnCode&reportingMonth&calculationStatus */
  list: (q = {}) => request('GET /products', () => {
    let rows = PRODUCTS
    if (q.q) rows = rows.filter(p => p.name.includes(q.q))
    if (q.cn?.length) rows = rows.filter(p => q.cn.includes(p.cnGroup))
    return page(rows, { ...q, size: q.size ?? 20 })
  }),
  /** №12 GET /products/{productId} — 상세와 내재배출량을 한 번에 준다 (v10에서 통합) */
  get: id => request('GET /products/{productId}', () => EMISSIONS[id] || EMISSIONS['hr-2400']),
}

/* ── 이메일 접수 (명세 №15~18) ──────────────────────────── */
export const Inbox = {
  /** №15 GET /mail-receipts?supplierId&status&receivedFrom&receivedTo */
  list: () => request('GET /mail-receipts', () => page(INBOX, { size: 100 })),
  /** №18 PATCH /mail-receipts/{receiptId}/supplier — 미확인 건을 담당자가 직접 연결한다 */
  assign: (id, supplierName) => request('PATCH /mail-receipts/{receiptId}/supplier', () => {
    const m = INBOX.find(x => x.id === id); if (m) { m.supplier = supplierName; m.state = '검토 대기' }
    return m
  }),
}

/* ── 제출 데이터 · 검토 (명세 №19~23) ───────────────────── */
export const Analysis = {
  /** №21 GET /submissions/{submissionId}
      AI 분석은 접수·수동 매칭 직후 자동 실행된다(요구사항 20). 화면이 실행을 요청하는 API 는 없다.
      진행 상태는 응답의 latestAnalysisTaskId 로 №19를 폴링해 확인한다. */
  get: id => request('GET /submissions/{submissionId}', () => {
    const s = SUBMISSIONS[id] ?? SUBMISSION
    const task = startTask('내부 자동 실행 (요구사항 20)', 'analyze', { result: id })
    return { ...s, latestAnalysisTaskId: task.taskId, pollAfterMs: task.pollAfterMs }
  }),
  /** №19 GET /tasks/{taskId} */
  task: getTask,
}

export const Review = {
  /** №20 GET /submissions?supplierId&partId&reportingMonth&status&judgement&severity */
  queue: () => request('GET /submissions', () => page(QUEUE, { size: 100 })),
  /* №22 POST /submissions/{submissionId}/confirm
     요구사항 31 — 「판정이 적격이고 미등록 부품이 없는 경우에만 확정할 수 있다」
     화면이 버튼을 잠그더라도 서버가 다시 막는다. 실제 BE 도 이 셋을 지켜야 한다. */
  confirm: id => request('POST /submissions/{submissionId}/confirm', () => {
    const s = SUBMISSIONS[id] ?? SUBMISSION
    if (s.missingFields.length)
      throw new ApiError(400, 'NOT_QUALIFIED', '필수 항목이 누락돼 확정할 수 없습니다', { missingFields: s.missingFields })
    if (s.judgement !== '적격')
      throw new ApiError(400, 'NOT_QUALIFIED', `판정이 ${s.judgement} 이라 확정할 수 없습니다`)
    if (s.unmappedParts.length)
      throw new ApiError(400, 'UNREGISTERED_PART_EXISTS', '미등록 부품이 있어 확정할 수 없습니다', { unregisteredPartIds: s.unmappedParts })
    return { submissionId: id, status: 'CONFIRMED', confirmedBy: '이과장', confirmedAt: '2026-09-02T15:10:00+09:00' }
  }),
  /** №23 POST /submissions/{submissionId}/reject — resultStatus 는 REJECTED | NOT_SUBMITTED */
  reject: (id, reason, resultStatus = 'REJECTED') =>
    request('POST /submissions/{submissionId}/reject', () => ({
      submissionId: id, status: resultStatus, judgement: 'UNQUALIFIED',
      reasonCode: 'MISSING_REQUIRED_FIELD', reason, rejectedBy: '이과장',
    })),
}

/* ── 제출 마감 (명세 №13~14) ────────────────────────────── */
export const Deadlines = {
  /** №13 GET /submission-deadlines?from&to — 월 목록은 범위 조회, 페이징하지 않는다 */
  list: () => request('GET /submission-deadlines', () => ({ from: '2025-10', to: '2026-09', months: DEADLINES })),
  /** №20 GET /submissions?status=NOT_SUBMITTED — 미제출은 제출 데이터 행이 없어 target 으로 식별한다 */
  unsubmitted: () => request('GET /submissions', () => page(REMINDERS, { size: 100 })),
  /** №14 POST /reminders { reportingMonth, targets:[{supplierId, partId}] } */
  remind: targets => request('POST /reminders', () => ({
    taskId: 'tsk-101', status: 'PENDING', targetCount: targets.length,
  })),
}

/* ── 피드백 (명세 №26~31) ───────────────────────────────── */
export const Feedback = {
  /** №26 POST /feedback-drafts { reportingMonth, submissionIds, targets, style } */
  draft: (submissionId, tone = '격식') => request('POST /feedback-drafts', () => ({
    submissionId, style: tone, body: TONES[tone], version: 1, source: 'AI', status: 'DRAFT',
  })),
  /** №31 GET /suppliers/{supplierId}/feedback-histories?type&status&from&to
      ⚠️ 명세는 협력업체별 조회만 정의한다. 지금 화면은 전체 발송 목록을 보여준다.
         전체 조회 경로를 추가할지 화면을 협력업체 상세로 옮길지 아직 답이 없다 — 이슈 #13. */
  list: () => request('GET /suppliers/{supplierId}/feedback-histories', () => page(DISPATCH, { size: 100 })),
  /** №29 PATCH /feedback-drafts/{draftId} { status: 'READY_TO_SEND' } — 확정 */
  confirm: id => request('PATCH /feedback-drafts/{draftId}', () => ({
    draftId: id, status: 'READY_TO_SEND', recipient: 'kim@daehan.co.kr', confirmedBy: '이과장',
  })),
  /** №30 POST /feedback-drafts/{draftId}/send — 최초 발송 */
  send: ids => request('POST /feedback-drafts/{draftId}/send', () => ({
    taskId: 'tsk-792', status: 'PENDING', sent: ids.length, attempt: 1,
  })),
  /** №30 POST /feedback-drafts/{draftId}/send — 재발송. reason 필수 (SEND_FAILED | NO_REPLY) */
  resend: (id, reason = 'SEND_FAILED') => request('POST /feedback-drafts/{draftId}/send', () => {
    if (!reason) throw new ApiError(400, 'RESEND_REASON_REQUIRED', '재발송에는 사유가 필요합니다')
    return { taskId: 'tsk-793', status: 'PENDING', draftId: id, attempt: 2 }
  }),
}

/* ── 대시보드 (명세 №24) ────────────────────────────────── */
export const Dashboard = {
  /** №24 GET /dashboard?month — 집계 응답이라 페이징하지 않는다 */
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
