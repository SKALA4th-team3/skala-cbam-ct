/* 화면이 부르는 API 표면 전부.
   경로·메서드·응답 봉투·에러 코드는 「CBAM API 명세서 v10」을 그대로 따른다.
   각 함수는 명세의 엔드포인트 하나와 1:1 이고, 그 경로를 request() 의 첫 인자로 들고 있다.
   BE 가 붙으면 여기 본문만 fetch 로 갈아끼운다. 화면 코드는 건드릴 일이 없다.

   상태값은 서버가 영문 enum(ACTIVE·QUALIFIED·NOT_SUBMITTED …), 화면이 한글(협력유지중·적격·미제출 …)이다.
   ADR-0005 로 정했다 — 매핑표는 enums.js, 응답 변환은 client.js 의 request() 한 곳,
   서버로 보내는 필터·정렬은 각 엔드포인트가 toServer() 로 바꿔 보낸다. */
import { request, page, startTask, getTask, ApiError } from './client'
import { toServer, toCode } from './enums'
import { suppliers, parts } from '@/mocks/seed'
import { PRODUCTS, EMISSIONS, INBOX, SUBMISSION, SUBMISSIONS, QUEUE, DEADLINES, REMINDERS, DISPATCH, TONES } from './fixtures'

const clone = v => JSON.parse(JSON.stringify(v))
/* 목이 상태를 바꾸는 대상은 예외 없이 복제해서 들고 있는다.
   원본 fixture 를 직접 고치면 HMR 뒤나 다른 화면에서 변경이 남는 곳과 안 남는 곳이 갈린다. */
let SUP = clone(suppliers)
let PRT = clone(parts)
const MAIL = clone(INBOX)
const DISP = clone(DISPATCH)

/** 목록 화면은 전량을 받아 브라우저에서 필터한다 — 패싯 배지 숫자가 전체 기준이어야 해서다(useTable).
 *  ADR-0009 로 「이 목록은 페이징하지 않는다」를 정했다. 서버는 전량을 돌려준다.
 *  그래도 잘렸으면 개발 콘솔에 남긴다 — 조용히 틀리는 것만은 막는다.
 *  이 경고가 뜨면 그때가 필터·정렬을 서버로 옮길 때다 (ADR-0009 「다시 볼 조건」). */
export function allRows(res, where) {
  if (import.meta.env?.DEV && res.totalElements > res.content.length)
    console.warn(`[API] ${where}: ${res.totalElements}건 중 ${res.content.length}건만 받았다.`
      + ' 화면이 브라우저에서 필터하므로 배지 숫자와 목록이 전체를 반영하지 않는다 (ADR-0009)')
  return res.content
}

/* 요구사항 1번이 입력받는 여섯 항목 — 이 목록이 곧 필수값이다 */
const REQUIRED = [
  ['name', '협력업체명'], ['bizNo', '사업자 등록번호'], ['country', '국가'],
  ['contact', '담당자명'], ['email', '담당자 이메일'], ['phone', '전화번호'],
]

/* ── 협력업체 (명세 №1~4) ───────────────────────────────── */
export const Suppliers = {
  /** №3 GET /suppliers?search&country&status&submissionStatus&months&page&size&sort
      필터·정렬은 서버로 영문 enum 과 명세의 sort 키로 나간다 (ADR-0005).
      기본 정렬은 `companyName` — 요구사항 4번의 미결 항목을 ADR-0007 으로 닫았다.
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
    /* 요구사항 1번이 받는다고 적은 여섯 항목. 하나라도 비면 등록하지 않는다.
       빈 폼이 그대로 들어가면 담당자 이메일이 없는 협력사가 생기고, 19번 매칭 키가 사라진다. */
    const blank = REQUIRED.filter(([k]) => !String(body[k] ?? '').trim())
    if (blank.length)
      throw new ApiError(400, 'MISSING_REQUIRED_FIELD',
        `${blank.map(([, label]) => label).join(' · ')} 을(를) 입력해야 합니다`,
        { fields: blank.map(([k]) => k) })
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email))
      throw new ApiError(400, 'INVALID_EMAIL_FORMAT', '담당자 이메일 형식이 올바르지 않습니다', { fields: ['email'] })
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
  /** №7 GET /parts?search&supplierId&cnCode&page&size&sort
      공급 협력업체는 이름만이 아니라 id 도 함께 준다 — 화면이 협력사 상세로 이어야 해서다.
      전에는 부품 목록의 모든 행이 `/suppliers/1` 로 갔다. 이름만 갖고 있어서였다. */
  list: (q = {}) => request('GET /parts', () => {
    let rows = PRT.map(p => ({ ...p, supplierId: SUP.find(s => s.name === p.supplier)?.id ?? null }))
    if (q.q) rows = rows.filter(p => p.name.includes(q.q))
    if (q.supplier?.length) rows = rows.filter(p => q.supplier.includes(p.supplier))
    if (q.cn?.length) rows = rows.filter(p => q.cn.includes(p.cnGroup))
    return page(rows, { ...q, size: q.size ?? 50 })
  }),
  /** №5 POST /parts — unregisteredPartId 를 함께 보내면 미등록 부품을 해소한다 (요구사항 28) */
  create: body => request('POST /parts', () => {
    /* 요구사항 7번 — 「CN코드는 8자리 숫자 형식 검증」.
       공백은 표기 습관이라 허용하고(`7207 1100`), 숫자만 세어 8자리인지 본다. */
    if (!/^\d{8}$/.test((body.cn || '').replace(/\s/g, '')))
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
  list: () => request('GET /mail-receipts', () => page(MAIL, { size: 100 })),
  /** №18 PATCH /mail-receipts/{receiptId}/supplier — 미확인 건을 담당자가 직접 연결한다 */
  assign: (id, supplierName) => request('PATCH /mail-receipts/{receiptId}/supplier', () => {
    const m = MAIL.find(x => x.id === id)
    if (!m) throw new ApiError(404, 'MAIL_RECEIPT_NOT_FOUND', '없는 접수 건입니다')
    if (!SUP.some(s => s.name === supplierName))
      throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다', { fields: ['supplier'] })
    m.supplier = supplierName; m.state = '검토 대기'; m.tone = 'processing'
    return m
  }),
}

/** 제출 건 → 그 건의 분석 작업. 같은 건을 다시 열면 같은 작업을 본다 */
const TASK_OF = new Map()

/* ── 제출 데이터 · 검토 (명세 №19~23) ───────────────────── */
export const Analysis = {
  /** №21 GET /submissions/{submissionId}
      AI 분석은 접수·수동 매칭 직후 자동 실행된다(요구사항 20). 화면이 실행을 요청하는 API 는 없다.
      진행 상태는 응답의 latestAnalysisTaskId 로 №19를 폴링해 확인한다. */
  get: id => request('GET /submissions/{submissionId}', () => {
    const s = SUBMISSIONS[id] ?? SUBMISSION
    /* 분석 작업은 제출 건마다 하나다. 상세를 다시 열었다고 새로 돌지 않는다 —
       화면이 이 API 를 두 곳(자료 변환·검토 확정)에서 부르므로, 호출마다 만들면 작업이 쌓인다.
       첫 인자가 null 인 것은 이 작업이 내부 자동 실행이라 엔드포인트가 없다는 뜻이다 (요구사항 20). */
    if (!TASK_OF.has(id)) TASK_OF.set(id, startTask(null, 'analyze', { result: id }))
    const task = TASK_OF.get(id)
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
  /** №23 POST /submissions/{submissionId}/reject — resultStatus 는 REJECTED | NOT_SUBMITTED
      요구사항 32 — 「상태를 부적격/미제출로 설정하고 **사유 저장**」.
      사유 없는 반려는 피드백 초안(UC-10)이 근거로 쓸 것이 없어 막는다. */
  reject: (id, reason, reasonCode = 'MISSING_REQUIRED_FIELD', resultStatus = 'REJECTED') =>
    request('POST /submissions/{submissionId}/reject', () => {
      if (!String(reason ?? '').trim())
        throw new ApiError(400, 'REJECT_REASON_REQUIRED', '반려 사유를 입력해야 합니다', { fields: ['reason'] })
      return {
        submissionId: id, status: resultStatus, judgement: 'UNQUALIFIED',
        reasonCode, reason, rejectedBy: '이과장', rejectedAt: new Date().toISOString(),
      }
    }),
}

/* ── 제출 마감 (명세 №13~14) ────────────────────────────── */
export const Deadlines = {
  /** №13 GET /submission-deadlines?from&to — 월 목록은 범위 조회, 페이징하지 않는다 */
  list: () => request('GET /submission-deadlines', () => ({ from: '2025-10', to: '2026-09', months: DEADLINES })),
  /** №20 GET /submissions?status=NOT_SUBMITTED — 미제출은 제출 데이터 행이 없어 target 으로 식별한다 */
  unsubmitted: () => request('GET /submissions', () => page(REMINDERS, { size: 100 })),
  /** №14 POST /reminders { reportingMonth, targets:[{supplierId, partId}] }
      화면이 id 배열을 그대로 넘기고 있었다 — 명세는 객체 배열을 받는다. */
  remind: (targets, reportingMonth = '2026-09') => request('POST /reminders', () => {
    if (!Array.isArray(targets) || !targets.length)
      throw new ApiError(400, 'NO_REMINDER_TARGET', '보낼 대상을 하나 이상 골라야 합니다')
    const bad = targets.filter(t => t?.supplierId == null)
    if (bad.length) throw new ApiError(400, 'INVALID_REMINDER_TARGET', 'targets 에는 supplierId 가 있어야 합니다')
    return { taskId: 'tsk-101', status: 'PENDING', reportingMonth, targetCount: targets.length }
  }),
}

/* ── 피드백 (명세 №26~31) ───────────────────────────────── */
export const Feedback = {
  /** №26 POST /feedback-drafts { reportingMonth, submissionIds, targets, style } */
  draft: (submissionId, tone = '격식') => request('POST /feedback-drafts', () => ({
    submissionId, style: tone, body: TONES[tone], version: 1, source: 'AI', status: 'DRAFT',
  })),
  /** GET /feedback-histories?supplierId&type&status&from&to&page&size&sort
      ⚠️ **설계 파생 1건** — 명세 v10 №31 은 `GET /suppliers/{supplierId}/feedback-histories`,
         즉 협력업체별 조회만 정의한다. 51번(발송 실패 건 확인)·53번(발송 이력 조회)은 전사 목록을 요구하는데
         그 경로로는 협력업체 48곳을 하나씩 불러야 한다. 전체 조회를 더하기로 정했다 — ADR-0008.
         `supplierId` 를 주면 №31 과 같은 것을 돌려준다. 협력업체 상세는 그렇게 부른다.
         **BE 가 이 경로를 만들어야 완성된다.** 그때까지 목으로 돈다 — `npm run api:status` 가 센다. */
  list: (q = {}) => request('GET /feedback-histories', () => {
    let rows = DISP
    if (q.supplierId) {
      const name = SUP.find(s => s.id === +q.supplierId)?.name
      rows = rows.filter(r => r.supplier === name)
    }
    /* 발송 상태는 한글로 비교한다 — 이 값의 영문 enum 이름을 확인하지 못했다.
       ADR-0005 가 「피드백 4값」을 비워 둔 것과 같은 이유다. 지어내지 않는다. */
    if (q.status) rows = rows.filter(r => [].concat(q.status).includes(r.state))
    return page(rows, { ...q, size: q.size ?? 100 })
  }),
  /** №29 PATCH /feedback-drafts/{draftId} { status: 'READY_TO_SEND' } — 확정 */
  confirm: id => request('PATCH /feedback-drafts/{draftId}', () => ({
    draftId: id, status: 'READY_TO_SEND', recipient: SUP.find(s => s.name === '성진스틸')?.email, confirmedBy: '이과장',
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
