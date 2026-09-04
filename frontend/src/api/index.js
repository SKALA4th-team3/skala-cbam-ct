/* 화면이 부르는 API 표면 전부.
   경로·메서드·응답 봉투·에러 코드는 「CBAM API 명세서 v10」을 그대로 따른다.
   각 함수는 명세의 엔드포인트 하나와 1:1 이고, 그 경로를 request() 의 첫 인자로 들고 있다.
   BE 가 붙으면 여기 본문만 fetch 로 갈아끼운다. 화면 코드는 건드릴 일이 없다.

   상태값은 서버가 영문 enum(ACTIVE·QUALIFIED·NOT_SUBMITTED …), 화면이 한글(협력유지중·적격·미제출 …)이다.
   ADR-0005 로 정했다 — 매핑표는 enums.js, 응답 변환은 client.js 의 request() 한 곳,
   서버로 보내는 필터·정렬은 각 엔드포인트가 toServer() 로 바꿔 보낸다.

   ⚠️ 명세 №번호를 모르는 경로는 번호 대신 「설계 파생」이라고 적었다. 지어내지 않는다.
      원본(xlsx)이 없어 №2·№5~12·№16·№27~29 의 정확한 경로를 대조하지 못했다 — API-CONTRACT.md 에 남겼다. */
import { request, page, startTask, getTask, http, ApiError } from './client'
import { toServer, toCode } from './enums'
import {
  supplierToServer, supplierRowFromServer, supplierDetailFromServer,
  partRowFromServer, partDetailFromServer,
  productRowFromServer, productDetailFromServer,
  submissionRowFromServer,
} from './shapes'
import { suppliers, parts } from '@/mocks/seed'
import {
  NOW, PRODUCTS, INBOX, SUBMISSIONS, DEADLINES, REMINDER_LOG, DISPATCH,
  draftBody, TEMPLATE_BODY,
} from './fixtures'

const clone = v => JSON.parse(JSON.stringify(v))
/* 목이 상태를 바꾸는 대상은 예외 없이 복제해서 들고 있는다.
   원본 fixture 를 직접 고치면 HMR 뒤나 다른 화면에서 변경이 남는 곳과 안 남는 곳이 갈린다. */
let SUP = clone(suppliers)
let PRT = clone(parts)
let PRD = clone(PRODUCTS)
const MAIL = clone(INBOX)
const SUBS = clone(SUBMISSIONS)
const DISP = clone(DISPATCH)

/** 목록 화면은 전량을 받아 브라우저에서 필터한다 — 패싯 배지 숫자가 전체 기준이어야 해서다(useTable).
 *  ADR-0009 로 「이 목록은 페이징하지 않는다」를 정했다. 서버는 전량을 돌려준다.
 *  그래도 잘렸으면 개발 콘솔에 남긴다 — 조용히 틀리는 것만은 막는다. */
export function allRows(res, where) {
  if (import.meta.env?.DEV && res.totalElements > res.content.length)
    console.warn(`[API] ${where}: ${res.totalElements}건 중 ${res.content.length}건만 받았다.`
      + ' 화면이 브라우저에서 필터하므로 배지 숫자와 목록이 전체를 반영하지 않는다 (ADR-0009)')
  return res.content
}

/* ── 공통 계산 ─────────────────────────────────────────── */
const DAY = 86400000
const daysLeft = (deadline, from = NOW) => Math.round((new Date(deadline) - new Date(from)) / DAY)
/** '1.92 tCO₂e/t' → 1.92. 없으면 null — 0 으로 두면 「배출이 없다」가 된다 */
const factorNum = f => (f == null ? null : (parseFloat(String(f)) || null))
const partOf = name => PRT.find(p => p.name === name) ?? null
const supplierOf = name => SUP.find(s => s.name === name) ?? null
/** 6번 — 협력끊김은 마감 대상과 미제출 경보에서 빠진다. 집계도 «협력 중인 곳»만 센다 */
const active = () => SUP.filter(s => s.tie === '협력유지중')
const trailingMissing = strip => { let n = 0; for (let i = strip.length - 1; i >= 0 && strip[i] === '2'; i--) n++; return n }

/** 판정 건수 — 38번. 협력 중인 곳만 센다 */
function judgementCounts() {
  const rows = active()
  const c = { 적격: 0, 부적격: 0, 미제출: 0, '검토 대기': 0, total: rows.length }
  for (const s of rows) c[s.judgement] = (c[s.judgement] ?? 0) + 1
  return c
}

/** 40번 — 월별 적격/부적격/미제출. 협력사 스트립(최근 12개월)에서 센다.
 *  마지막 칸(이번 달)은 스트립이 아니라 판정값에서 센다 — 도넛과 같은 숫자여야 한다. */
function monthlyCounts() {
  const rows = active()
  const [y, m] = NOW.split('-').map(Number)
  const out = []
  for (let back = 11; back >= 0; back--) {
    const d = new Date(y, m - 1 - back, 1)
    const idx = 11 - back
    const col = { month: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`, 적격: 0, 부적격: 0, 미제출: 0 }
    if (back === 0) {
      const j = judgementCounts(); col.적격 = j.적격; col.부적격 = j.부적격; col.미제출 = j.미제출
    } else for (const s of rows) {
      const ch = (s.strip ?? '')[idx]
      if (ch === '1') col.부적격++; else if (ch === '2') col.미제출++; else if (ch === '0') col.적격++
    }
    out.push(col)
  }
  return out
}

/** 완제품의 부품 구성 — 부품 세부(12번 ②③④)는 seed 의 부품에서 그때그때 끌어온다 */
function bomView(product) {
  return product.bom.map(b => {
    const p = partOf(b.part)
    const f = factorNum(p?.factor)
    return {
      part: b.part, partId: p?.id ?? null, supplier: p?.supplier ?? null,
      supplierId: supplierOf(p?.supplier)?.id ?? null,
      input: b.input, factor: f, unit: p?.unit ?? null,
      state: p ? (f != null ? '확정' : '미확정') : '미등록',
      /* 기여량 = 투입량 × 팩터 × 연간 수출량. 팩터가 없으면 «모른다» — 0 이 아니다 */
      contribution: f != null ? +(b.input * f * product.tons).toFixed(1) : null,
    }
  })
}

/** 15번 — 구성 부품의 확정 배출데이터를 합산한 내재배출량. 미확정 부품이 있으면 합계를 «잠정»으로 둔다 */
function emissionsOf(product) {
  const rows = bomView(product)
  const confirmed = rows.filter(r => r.contribution != null)
  const pending = rows.filter(r => r.contribution == null)
  const confirmedSum = +confirmed.reduce((a, r) => a + r.contribution, 0).toFixed(1)
  return {
    parts: rows,
    confirmed: confirmedSum,
    pendingCount: pending.length,
    total: pending.length ? null : confirmedSum,        // 41번 — 미확정 부품이 있으면 합계를 지어내지 않는다
    reportable: pending.length === 0,
    blocking: pending.map(r => `${r.part} — ${r.state === '미등록' ? '부품 미등록' : '벤치마크 미등록'}`),
  }
}
const productRow = p => {
  const ratio = +(p.actual / p.mean).toFixed(2)
  return {
    ...p, partCount: p.bom.length, ratio,
    judgement: Math.abs(ratio - 1) > 0.3 ? '부적격' : '적격',      // 34번 ±30% (ADR-0001)
    reportable: emissionsOf(p).reportable,
  }
}

/* 요구사항 1번이 입력받는 여섯 항목 — 이 목록이 곧 필수값이다 */
const REQUIRED = [
  ['name', '협력업체명'], ['bizNo', '사업자 등록번호'], ['country', '국가'],
  ['contact', '담당자명'], ['email', '담당자 이메일'], ['phone', '전화번호'],
]
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const need = (body, fields) => {
  const blank = fields.filter(([k]) => !String(body?.[k] ?? '').trim())
  if (blank.length)
    throw new ApiError(400, 'MISSING_REQUIRED_FIELD',
      `${blank.map(([, l]) => l).join(' · ')} 을(를) 입력해야 합니다`, { fields: blank.map(([k]) => k) })
}

/* ── 협력업체 (명세 №1~4 · 요구사항 1~6번) ───────────────── */
export const Suppliers = {
  /** №3 GET /suppliers?search&country&status&submissionStatus&months&page&size&sort */
  list: (q = {}) => request('GET /suppliers', () => {
    const query = toServer(q)
    let rows = SUP
    if (query.q) rows = rows.filter(s => s.name.toLowerCase().includes(query.q.toLowerCase()))
    for (const k of ['country', 'tie', 'judgement'])
      if (query[k]?.length) rows = rows.filter(s => query[k].includes(toCode(s[k])))
    /* 명세가 허용하는 sort 키는 companyName · lastSubmittedAt 둘뿐이다 (ADR-0007) */
    if ((query.sort ?? 'companyName') === 'companyName')
      rows = [...rows].sort((a, b) => a.name.localeCompare(b.name))
    return page(rows, query)
  }, async () => {
    const query = toServer(q)
    /* ⚠️ BE 는 size 를 100 까지만 받는다 (실서버 확인: size=1000 → 400). ADR-0009 참고 */
    const res = await http('GET', '/suppliers', {
      query: {
        search: query.q, country: query.country, status: query.tie,
        submissionStatus: query.judgement, months: 12,
        page: query.page ?? 0, size: Math.min(query.size ?? 20, 100), sort: query.sort,
      },
    })
    return { ...res, content: (res.content ?? []).map(supplierRowFromServer) }
  }),

  /** №4 GET /suppliers/{supplierId} — 5번 「상세 · 공급 부품 · 제출 이력 · 담당자 · 연락처를 한 화면에서」
      제출 이력·수신 경보·발송 이력(17번·53번)도 여기서 같이 준다 */
  get: id => request('GET /suppliers/{supplierId}', () => {
    const s = SUP.find(x => x.id === +id)
    if (!s) throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다')
    const subs = Object.values(SUBS).filter(x => x.supplierId === s.id)
      .map(x => ({ id: x.id, period: x.period, submittedAt: x.submittedAt, judgement: x.judgement, rule: x.rule, why: x.why, status: x.status ?? '검토 대기' }))
    const run = trailingMissing(s.strip)
    const alerts = []
    if (run >= 1 && s.tie === '협력유지중') alerts.push({ kind: '미제출', text: `${run}개월 연속 미제출`, severity: run >= 3 ? 'HIGH' : 'MEDIUM', at: NOW })
    for (const x of subs.filter(x => x.rule)) alerts.push({ kind: x.rule, text: x.why, severity: SUBS[x.id].severity, at: x.submittedAt?.slice(0, 10) })
    return {
      ...s,
      parts: PRT.filter(p => p.supplier === s.name),
      submissions: subs.sort((a, b) => (b.submittedAt ?? '').localeCompare(a.submittedAt ?? '')),
      alerts,
      feedbackHistories: DISP.filter(d => d.supplier === s.name),
      reminders: REMINDER_LOG[s.name] ?? null,
      latestSubmissionId: subs[0]?.id ?? null,
    }
  }, async () => supplierDetailFromServer(await http('GET', `/suppliers/${id}`, { query: { months: 12 } }))),

  /** №1 POST /suppliers */
  create: body => request('POST /suppliers', () => {
    need(body, REQUIRED)
    if (!EMAIL_RE.test(body.email))
      throw new ApiError(400, 'INVALID_EMAIL_FORMAT', '담당자 이메일 형식이 올바르지 않습니다', { fields: ['email'] })
    if (SUP.some(s => s.bizNo && s.bizNo === body.bizNo))
      throw new ApiError(409, 'DUPLICATE_BUSINESS_NUMBER', '이미 등록된 사업자등록번호입니다', { fields: ['bizNo'] })
    if (SUP.some(s => s.email && s.email === body.email))
      throw new ApiError(409, 'DUPLICATE_CONTACT_EMAIL', '이미 등록된 담당자 이메일입니다', { fields: ['email'] })
    const row = { id: Math.max(...SUP.map(s => s.id)) + 1, judgement: '미제출', tie: '협력유지중', strip: '0'.repeat(12), ...body }
    SUP = [row, ...SUP]
    return row
  }, async () => supplierDetailFromServer(await http('POST', '/suppliers', { body: supplierToServer(body) }))),

  /** №2 PATCH /suppliers/{supplierId} — 2번 「담당자명·담당자 이메일·전화번호를 수정한다」
      「수정되더라도 이전 이메일로 접수된 이력은 그대로 유지된다」 — 접수 건은 supplierId 로 이어져 있어 이메일을 바꿔도 끊기지 않는다 */
  update: (id, body) => request('PATCH /suppliers/{supplierId}', () => {
    const s = SUP.find(x => x.id === +id)
    if (!s) throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다')
    const patch = {}
    for (const k of ['contact', 'email', 'phone']) if (body[k] != null) patch[k] = String(body[k]).trim()
    need(patch, [['contact', '담당자명'], ['email', '담당자 이메일'], ['phone', '전화번호']].filter(([k]) => k in patch))
    if (patch.email && !EMAIL_RE.test(patch.email))
      throw new ApiError(400, 'INVALID_EMAIL_FORMAT', '담당자 이메일 형식이 올바르지 않습니다', { fields: ['email'] })
    if (patch.email && SUP.some(x => x.id !== s.id && x.email === patch.email))
      throw new ApiError(409, 'DUPLICATE_CONTACT_EMAIL', '이미 다른 협력업체가 쓰는 담당자 이메일입니다', { fields: ['email'] })
    Object.assign(s, patch, { editedBy: '이과장', editedAt: new Date().toISOString() })
    return { ...s }
  }),

  /** 6번 협력 끊김 — 「마감 대상과 미제출 경보에서 제외된다. 기존 제출 데이터는 삭제하지 않고 보존한다」
      ⚠️ 설계 파생 — 명세 v10 에서 이 전이의 경로를 대조하지 못했다. 규약대로 «상태 전이는 POST» 로 둔다 */
  deactivate: id => request('POST /suppliers/{supplierId}/deactivate', () => {
    const s = SUP.find(x => x.id === +id)
    if (!s) throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다')
    if (s.tie === '협력끊김') throw new ApiError(409, 'ALREADY_INACTIVE', '이미 협력끊김 상태입니다')
    s.tie = '협력끊김'; s.inactiveAt = new Date().toISOString(); s.editedBy = '이과장'
    return { ...s }
  }),
}

/* ── 부품 (명세 №5~8 · 요구사항 7~11번) ──────────────────── */
const UNITS = ['kg', 'ton', 'EA']
const withSupplierId = p => ({ ...p, supplierId: supplierOf(p.supplier)?.id ?? null })

/** 10번 — 「협력업체별 확정 배출 데이터를 리스트로」. 확정 이력이 없으면 빈 배열이다 — 지어내지 않는다.
 *  목에서는 확정된 제출 건(status=확정)에서만 만든다. */
function confirmedDataOf(part) {
  return Object.values(SUBS)
    .filter(s => s.status === '확정' && s.supplier === part.supplier)
    .map(s => ({
      submissionId: s.id, supplier: s.supplier, period: s.period, confirmedAt: s.confirmedAt,
      rows: s.rows.filter(r => r.value != null).map(r => ({ field: r.field, value: r.value, unit: r.unit })),
    }))
}

export const Parts = {
  /** №7 GET /parts?search&supplierId&cnCode&page&size&sort */
  list: (q = {}) => request('GET /parts', () => {
    let rows = PRT.map(withSupplierId)
    if (q.q) rows = rows.filter(p => p.name.includes(q.q))
    if (q.supplier?.length) rows = rows.filter(p => q.supplier.includes(p.supplier))
    if (q.cn?.length) rows = rows.filter(p => q.cn.includes(p.cnGroup))
    return page(rows, { ...q, size: q.size ?? 50 })
  }, async () => {
    const res = await http('GET', '/parts', {
      query: { search: q.q, supplierId: q.supplierId, cnCode: q.cn?.[0], page: 0, size: 100 },
    })
    return { ...res, content: (res.content ?? []).map(partRowFromServer) }
  }),
  /** GET /parts/{partId} — 10번 단일 조회 (명세 №5~8 중 하나 — 번호 확인 필요) */
  get: id => request('GET /parts/{partId}', () => {
    const p = PRT.find(x => x.id === +id)
    if (!p) throw new ApiError(404, 'PART_NOT_FOUND', '없는 부품입니다')
    return {
      ...withSupplierId(p),
      confirmedData: confirmedDataOf(p),
      usedIn: PRD.filter(pr => pr.bom.some(b => b.part === p.name)).map(pr => ({ id: pr.id, name: pr.name })),
    }
  }, async () => partDetailFromServer(await http('GET', `/parts/${id}`))),
  /** №5 POST /parts — 7번. `resolves` 를 주면 그 제출 건의 미등록 부품을 해소한다 (28번) */
  create: body => request('POST /parts', () => {
    need(body, [['name', '부품명'], ['cn', 'CN 코드'], ['supplier', '공급 협력업체'], ['unit', '단위']])
    /* 7번 — 「CN코드는 8자리 숫자 형식 검증」. 공백은 표기 습관이라 허용한다 */
    if (!/^\d{8}$/.test(String(body.cn).replace(/\s/g, '')))
      throw new ApiError(400, 'INVALID_CN_CODE', 'CN코드는 8자리 숫자여야 합니다', { fields: ['cn'] })
    if (!UNITS.includes(body.unit))
      throw new ApiError(400, 'INVALID_UNIT', `단위는 ${UNITS.join(' · ')} 중 하나여야 합니다`, { fields: ['unit'] })
    if (PRT.some(p => p.name === body.name))
      throw new ApiError(409, 'DUPLICATE_PART_NAME', '같은 부품명이 이미 있습니다', { fields: ['name'] })
    if (!supplierOf(body.supplier) && !String(body.supplier).startsWith('자사'))
      throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다', { fields: ['supplier'] })
    const cn = String(body.cn).replace(/\s/g, '')
    const row = {
      id: Math.max(...PRT.map(p => p.id)) + 1, name: body.name, cn: `${cn.slice(0, 4)} ${cn.slice(4)}`,
      cnGroup: body.cnGroup ?? `${cn.slice(0, 2)} 기타`, supplier: body.supplier,
      factor: body.factor ?? null, unit: body.unit,
    }
    PRT = [row, ...PRT]
    if (body.resolves?.submissionId) {
      const s = SUBS[body.resolves.submissionId]
      if (s) s.unmappedParts = s.unmappedParts.filter(n => n !== body.resolves.name)
    }
    return withSupplierId(row)
  }),
  /** PATCH /parts/{partId} — 8번 「부품 정보(평균값)를 수정한다」 (명세 №5~8 중 하나 — 번호 확인 필요) */
  update: (id, body) => request('PATCH /parts/{partId}', () => {
    const p = PRT.find(x => x.id === +id)
    if (!p) throw new ApiError(404, 'PART_NOT_FOUND', '없는 부품입니다')
    if (body.unit != null && !UNITS.includes(body.unit))
      throw new ApiError(400, 'INVALID_UNIT', `단위는 ${UNITS.join(' · ')} 중 하나여야 합니다`, { fields: ['unit'] })
    if (body.factor != null) {
      const n = parseFloat(body.factor)
      if (!(n > 0)) throw new ApiError(400, 'INVALID_FACTOR', '벤치마크 팩터는 0보다 큰 숫자여야 합니다', { fields: ['factor'] })
      p.factor = `${n} tCO₂e/${body.factorUnit ?? 't'}`
    }
    if (body.unit != null) p.unit = body.unit
    p.editedBy = '이과장'; p.editedAt = new Date().toISOString()
    return withSupplierId(p)
  }),
}

/* ── 완제품 (명세 №9~12 · 요구사항 12~15번) ──────────────── */
export const Products = {
  /** №11 GET /products?search&cnCode&reportingMonth&calculationStatus */
  list: (q = {}) => request('GET /products', () => {
    let rows = PRD.map(productRow)
    if (q.q) rows = rows.filter(p => p.name.includes(q.q))
    if (q.cn?.length) rows = rows.filter(p => q.cn.includes(p.cnGroup))
    return page(rows, { ...q, size: q.size ?? 20 })
  }, async () => {
    const res = await http('GET', '/products', {
      query: {
        search: q.q, cnCode: q.cn?.[0], reportingMonth: q.reportingMonth,
        calculationStatus: q.calculationStatus, page: 0, size: 100,
      },
    })
    return { ...res, content: (res.content ?? []).map(productRowFromServer) }
  }),
  /** №12 GET /products/{productId} — 상세와 내재배출량(15번)을 한 번에 준다 (v10에서 통합) */
  get: (id, q = {}) => request('GET /products/{productId}', () => {
    const p = PRD.find(x => x.id === id)
    if (!p) throw new ApiError(404, 'PRODUCT_NOT_FOUND', '없는 완제품입니다')
    return { ...productRow(p), ...emissionsOf(p) }
  }, async () => productDetailFromServer(
    await http('GET', `/products/${id}`, { query: { reportingMonth: q?.reportingMonth } }))),
  /** POST /products — 12번 등록 (명세 №9 로 추정 — 확인 필요) */
  create: body => request('POST /products', () => {
    need(body, [['name', '제품명'], ['cn', 'CN 코드'], ['euCountry', '수출 대상 EU 회원국'], ['tons', '연간 수출량']])
    if (!/^\d{8}$/.test(String(body.cn).replace(/\s/g, '')))
      throw new ApiError(400, 'INVALID_CN_CODE', 'CN코드는 8자리 숫자여야 합니다', { fields: ['cn'] })
    if (!(Number(body.tons) > 0))
      throw new ApiError(400, 'INVALID_EXPORT_VOLUME', '연간 수출량은 0보다 커야 합니다', { fields: ['tons'] })
    if (!Array.isArray(body.bom) || !body.bom.length)
      throw new ApiError(400, 'BOM_REQUIRED', '부품 세부를 하나 이상 넣어야 합니다', { fields: ['bom'] })
    const badPart = body.bom.find(b => !partOf(b.part))
    if (badPart) throw new ApiError(404, 'PART_NOT_FOUND', `「${badPart.part}」 는 등록된 부품이 아닙니다`, { fields: ['bom'] })
    if (body.bom.some(b => !(Number(b.input) > 0)))
      throw new ApiError(400, 'INVALID_INPUT', '투입량(t/t)은 0보다 커야 합니다', { fields: ['bom'] })
    if (PRD.some(p => p.name === body.name))
      throw new ApiError(409, 'DUPLICATE_PRODUCT_NAME', '같은 제품명이 이미 있습니다', { fields: ['name'] })
    const cn = String(body.cn).replace(/\s/g, '')
    const row = {
      id: 'prd-' + Math.random().toString(36).slice(2, 6), name: body.name,
      cn: `${cn.slice(0, 4)} ${cn.slice(4)}`, cnGroup: body.cnGroup ?? `${cn.slice(0, 4)} 기타`,
      euCountry: body.euCountry, tons: Number(body.tons),
      bom: body.bom.map(b => ({ part: b.part, input: Number(b.input) })),
      /* 14번 — 평균값은 하드코딩이다. 새 제품은 동일 품목 평균이 없으므로 비운다 (0 이 아니다) */
      mean: body.mean ?? null, actual: null,
    }
    PRD = [row, ...PRD]
    return { ...row, partCount: row.bom.length, ratio: null, judgement: '검토 대기', reportable: emissionsOf(row).reportable }
  }),
  /** PATCH /products/{productId} — 13번 「연간 수출량, EU 회원국, 부품 세부를 수정」 (명세 №10 으로 추정 — 확인 필요) */
  update: (id, body) => request('PATCH /products/{productId}', () => {
    const p = PRD.find(x => x.id === id)
    if (!p) throw new ApiError(404, 'PRODUCT_NOT_FOUND', '없는 완제품입니다')
    if (body.tons != null) {
      if (!(Number(body.tons) > 0)) throw new ApiError(400, 'INVALID_EXPORT_VOLUME', '연간 수출량은 0보다 커야 합니다', { fields: ['tons'] })
      p.tons = Number(body.tons)
    }
    if (body.euCountry != null) p.euCountry = String(body.euCountry).trim() || p.euCountry
    if (body.bom != null) {
      if (!body.bom.length) throw new ApiError(400, 'BOM_REQUIRED', '부품 세부를 하나 이상 넣어야 합니다', { fields: ['bom'] })
      const bad = body.bom.find(b => !partOf(b.part))
      if (bad) throw new ApiError(404, 'PART_NOT_FOUND', `「${bad.part}」 는 등록된 부품이 아닙니다`, { fields: ['bom'] })
      if (body.bom.some(b => !(Number(b.input) > 0)))
        throw new ApiError(400, 'INVALID_INPUT', '투입량(t/t)은 0보다 커야 합니다', { fields: ['bom'] })
      p.bom = body.bom.map(b => ({ part: b.part, input: Number(b.input) }))
    }
    p.editedBy = '이과장'; p.editedAt = new Date().toISOString()
    return { ...productRow(p), ...emissionsOf(p) }
  }),
}

/* ── 이메일 접수 (명세 №15~18 · 요구사항 18~21번) ────────── */
export const Inbox = {
  /** №15 GET /mail-receipts?supplierId&status&receivedFrom&receivedTo */
  list: () => request('GET /mail-receipts', () => page(MAIL.map(m => ({ ...m, supplierId: supplierOf(m.supplier)?.id ?? null })), { size: 100 })),
  /** GET /mail-receipts/{receiptId} — 21번 「원문 메일과 첨부 확인」 (명세 №16 으로 추정 — 확인 필요) */
  get: id => request('GET /mail-receipts/{receiptId}', () => {
    const m = MAIL.find(x => x.id === id)
    if (!m) throw new ApiError(404, 'MAIL_RECEIPT_NOT_FOUND', '없는 접수 건입니다')
    return { ...m, supplierId: supplierOf(m.supplier)?.id ?? null }
  }),
  /** №18 PATCH /mail-receipts/{receiptId}/supplier — 21번 「미확인 건은 담당자가 협력업체를 직접 지정」 */
  assign: (id, supplierName) => request('PATCH /mail-receipts/{receiptId}/supplier', () => {
    const m = MAIL.find(x => x.id === id)
    if (!m) throw new ApiError(404, 'MAIL_RECEIPT_NOT_FOUND', '없는 접수 건입니다')
    if (m.state !== '미확인') throw new ApiError(409, 'NOT_UNIDENTIFIED', '미확인 건만 협력업체를 지정할 수 있습니다')
    const s = supplierOf(supplierName)
    if (!s) throw new ApiError(404, 'SUPPLIER_NOT_FOUND', '없는 협력업체입니다', { fields: ['supplier'] })
    /* 20번 — 지정 뒤 AI 분석이 자동으로 돈다. 첨부가 없으면 접수 불가다 */
    m.supplier = s.name
    if (!m.files?.length) { m.state = '접수 불가'; m.tone = 'missing'; m.reason = '첨부가 없습니다 (20번)' }
    else { m.state = '접수 대기'; m.tone = 'processing'; m.reason = null }
    return { ...m, supplierId: s.id }
  }),
}

/** 제출 건 → 그 건의 분석 작업. 같은 건을 다시 열면 같은 작업을 본다 */
const TASK_OF = new Map()

/* ── 제출 데이터 · 검토 (명세 №19~23 · 요구사항 27~32번) ─── */
export const Analysis = {
  /** №21 GET /submissions/{submissionId}
      AI 분석은 접수·수동 매칭 직후 자동 실행된다(20번). 화면이 실행을 요청하는 API 는 없다.
      진행 상태는 응답의 latestAnalysisTaskId 로 №19를 폴링해 확인한다. */
  get: id => request('GET /submissions/{submissionId}', () => {
    const s = SUBS[id]
    /* 전에는 없는 id 도 sub-1 로 떨어졌다 — 어느 건을 열어도 성진스틸이 나왔다. 없으면 없다고 한다 */
    if (!s) throw new ApiError(404, 'SUBMISSION_NOT_FOUND', '없는 제출 건입니다')
    if (!TASK_OF.has(id)) TASK_OF.set(id, startTask(null, 'analyze', { result: id, fail: s.analysisFailed, ms: s.analysisFailed ? 1800 : 2600 }))
    const task = TASK_OF.get(id)
    const mail = MAIL.find(m => m.id === id)
    return { ...s, receivedAt: mail?.receivedAt ?? s.submittedAt, latestAnalysisTaskId: task.taskId, pollAfterMs: task.pollAfterMs }
  }),
  /** №19 GET /tasks/{taskId} */
  task: getTask,
}

export const Review = {
  /** №20 GET /submissions?supplierId&partId&reportingMonth&status&judgement&severity — 29번 */
  queue: (q = {}) => request('GET /submissions', () => {
    let rows = Object.values(SUBS).map(s => ({
      id: s.id, supplier: s.supplier, supplierId: s.supplierId, item: s.item, rule: s.rule, why: s.why,
      severity: s.severity, judgement: s.judgement, submittedAt: s.submittedAt, status: s.status ?? '검토 대기',
      resolvedAt: s.confirmedAt ?? s.rejectedAt ?? null,
    }))
    if (q.status) rows = rows.filter(r => r.status === q.status)
    return page(rows, { size: 100 })
  }, async () => {
    const res = await http('GET', '/submissions', {
      query: {
        supplierId: q.supplierId, partId: q.partId, reportingMonth: q.reportingMonth,
        status: q.status, judgement: q.judgement, severity: q.severity,
        page: 0, size: 100,
      },
    })
    return { ...res, content: (res.content ?? []).map(submissionRowFromServer) }
  }),
  /** №22 POST /submissions/{submissionId}/confirm — 31번 「판정이 적격이고 미등록 부품이 없는 경우에만」 */
  confirm: id => request('POST /submissions/{submissionId}/confirm', () => {
    const s = SUBS[id]
    if (!s) throw new ApiError(404, 'SUBMISSION_NOT_FOUND', '없는 제출 건입니다')
    if (s.status === '확정') throw new ApiError(409, 'ALREADY_CONFIRMED', '이미 확정된 건입니다')
    if (s.missingFields.length)
      throw new ApiError(400, 'NOT_QUALIFIED', '필수 항목이 누락돼 확정할 수 없습니다', { missingFields: s.missingFields })
    if (s.judgement !== '적격')
      throw new ApiError(400, 'NOT_QUALIFIED', `판정이 ${s.judgement} 이라 확정할 수 없습니다`)
    if (s.unmappedParts.length)
      throw new ApiError(400, 'UNREGISTERED_PART_EXISTS', '미등록 부품이 있어 확정할 수 없습니다', { unregisteredPartIds: s.unmappedParts })
    s.status = '확정'; s.confirmedBy = '이과장'; s.confirmedAt = new Date().toISOString()
    const sup = SUP.find(x => x.id === s.supplierId)
    if (sup) { sup.judgement = '적격'; sup.strip = sup.strip.slice(0, -1) + '0' }
    return { submissionId: id, status: 'CONFIRMED', confirmedBy: s.confirmedBy, confirmedAt: s.confirmedAt }
  }, async () => http('POST', `/submissions/${id}/confirm`, { body: {} })),
  /** №23 POST /submissions/{submissionId}/reject — 32번 「상태를 부적격/미제출로 설정하고 사유 저장」 */
  reject: (id, reason, reasonCode = 'MISSING_REQUIRED_FIELD', resultStatus = 'REJECTED') =>
    request('POST /submissions/{submissionId}/reject', () => {
      const s = SUBS[id]
      if (!s) throw new ApiError(404, 'SUBMISSION_NOT_FOUND', '없는 제출 건입니다')
      if (!String(reason ?? '').trim())
        throw new ApiError(400, 'REJECT_REASON_REQUIRED', '반려 사유를 입력해야 합니다', { fields: ['reason'] })
      s.status = '반려'; s.rejectedBy = '이과장'; s.rejectedAt = new Date().toISOString()
      s.rejectReason = reason; s.rejectCode = reasonCode
      s.judgement = resultStatus === 'NOT_SUBMITTED' ? '미제출' : '부적격'
      const sup = SUP.find(x => x.id === s.supplierId)
      if (sup) sup.judgement = s.judgement
      return {
        submissionId: id, status: resultStatus, judgement: 'UNQUALIFIED',
        reasonCode, reason, rejectedBy: s.rejectedBy, rejectedAt: s.rejectedAt,
      }
    }, async () => http('POST', `/submissions/${id}/reject`, {
      body: { resultStatus, reasonCode, reason, createFeedbackDraft: true },
    })),
}

/* ── 제출 마감 (명세 №13~14 · 요구사항 16·17번) ──────────── */
export const Deadlines = {
  /** №13 GET /submission-deadlines?from&to — 16번. 이번 달 건수는 판정에서 세고, 남은 일수는 NOW 에서 센다 */
  list: () => request('GET /submission-deadlines', () => {
    const months = DEADLINES.map(m => {
      const left = daysLeft(m.deadline)
      const cur = m.now ? judgementCounts() : null
      return {
        month: m.month, deadline: m.deadline, now: !!m.now, dDay: left,
        ok: cur ? cur.적격 : m.ok, reject: cur ? cur.부적격 : m.reject, missing: cur ? cur.미제출 : m.missing,
        left: left >= 0 ? `D-${left}` : '마감',
        state: m.now ? (left <= 7 ? 'D-7 경보' : '진행 중') : '종료',
        tone: m.now ? (left <= 7 ? 'missing' : 'processing') : 'complete',
        alarm: m.now && left <= 7,
      }
    })
    return { from: months.at(-1).month, to: months[0].month, months }
  }),
  /** №20 GET /submissions?status=NOT_SUBMITTED — 미제출은 제출 행이 없어 target 으로 식별한다.
      6번 — 협력끊김은 대상에서 빠진다 */
  unsubmitted: () => request('GET /submissions', () => {
    const rows = active().filter(s => s.judgement === '미제출').map(s => {
      const log = REMINDER_LOG[s.name] ?? {}
      const months = trailingMissing(s.strip)
      return {
        id: s.id, name: s.name, email: s.email, contact: s.contact,
        lastSent: log.lastSent ?? null, months,
        overdue: months >= 2 ? `${months}개월` : '이번 달', late: months >= 2,
        checked: months >= 2,
      }
    })
    return page(rows.sort((a, b) => b.months - a.months), { size: 100 })
  }),
  /** №14 POST /reminders { reportingMonth, targets:[{supplierId, partId}] } — 17번 */
  remind: (targets, reportingMonth = NOW.slice(0, 7)) => request('POST /reminders', () => {
    if (!Array.isArray(targets) || !targets.length)
      throw new ApiError(400, 'NO_REMINDER_TARGET', '보낼 대상을 하나 이상 골라야 합니다')
    if (targets.some(t => t?.supplierId == null))
      throw new ApiError(400, 'INVALID_REMINDER_TARGET', 'targets 에는 supplierId 가 있어야 합니다')
    const inactive = targets.filter(t => SUP.find(s => s.id === t.supplierId)?.tie === '협력끊김')
    if (inactive.length)
      throw new ApiError(400, 'INACTIVE_SUPPLIER', '협력끊김 업체에는 리마인드를 보내지 않습니다 (6번)', { supplierIds: inactive.map(t => t.supplierId) })
    for (const t of targets) {
      const s = SUP.find(x => x.id === t.supplierId)
      if (s) REMINDER_LOG[s.name] = { ...REMINDER_LOG[s.name], lastSent: NOW }
    }
    return { taskId: 'tsk-' + Math.random().toString(36).slice(2, 6), status: 'PENDING', reportingMonth, targetCount: targets.length }
  }),
}

/* ── 피드백 (명세 №26~31 · 요구사항 42~53번) ─────────────── */
/** 초안 저장소. draftId → 버전 하나. 같은 제출 건의 버전들은 submissionId 로 묶인다 (45번 「이전 초안은 버전으로 보관」) */
const DRAFTS = new Map()
let draftSeq = 100
const versionsOf = submissionId => [...DRAFTS.values()].filter(d => d.submissionId === submissionId).sort((a, b) => a.version - b.version)
const latestOf = submissionId => versionsOf(submissionId).at(-1) ?? null
const basisOf = s => ({
  rule: s.rule, why: s.why, severity: s.severity, judgement: s.judgement,
  missingFields: s.missingFields, unmappedParts: s.unmappedParts,
  rejectReason: s.rejectReason ?? null, deadline: '2026-10-15', period: s.period,
})
function newVersion(s, { style, instruction = '', source = 'AI', body, status = '초안', failed = false, error = null }) {
  const prev = versionsOf(s.id)
  const d = {
    id: 'fd-' + (draftSeq++), submissionId: s.id, supplier: s.supplier, supplierId: s.supplierId,
    version: prev.length + 1, style, instruction, source, status, failed, error,
    body, basis: basisOf(s), subject: `${s.period} 자료 ${s.rule === 'R3' ? '재요청' : '보완 요청'}`,
    to: SUP.find(x => x.id === s.supplierId)?.email ?? null,
    createdAt: new Date().toISOString(), locked: false,
  }
  DRAFTS.set(d.id, d)
  return d
}
/** 46번 — AI 가 실패하면 실패를 표시하고 기본 템플릿을 대신 준다.
 *  목에서는 «분석 실패한 건»에서 AI 가 실패한다 — 근거 행이 없어 초안을 만들 재료가 없다. */
function aiDraft(s, style, instruction) {
  if (s.analysisFailed)
    return { body: TEMPLATE_BODY(s), source: 'TEMPLATE', failed: true,
      error: { code: 'AI_UNAVAILABLE', message: '분석 결과가 없어 AI 초안을 만들지 못했습니다 — 기본 템플릿으로 대신합니다' } }
  return { body: draftBody(s, style, instruction), source: 'AI', failed: false, error: null }
}
const draftOr404 = id => { const d = DRAFTS.get(id); if (!d) throw new ApiError(404, 'DRAFT_NOT_FOUND', '없는 초안입니다'); return d }
const notLocked = d => { if (d.locked) throw new ApiError(409, 'DRAFT_LOCKED', '확정된 초안은 고칠 수 없습니다 — 수신자·제목·본문이 잠겨 있습니다 (48번)') }

export const Feedback = {
  /** №26 POST /feedback-drafts { submissionIds:[…], style } — 42번 개별 초안.
      이미 초안이 있으면 그것을 돌려준다 — 새로 만들려면 재생성(№28)이다 */
  draft: (submissionId, style = '격식') => request('POST /feedback-drafts', () => {
    const s = SUBS[submissionId]
    if (!s) throw new ApiError(404, 'SUBMISSION_NOT_FOUND', '없는 제출 건입니다')
    const have = latestOf(submissionId)
    if (have && have.status !== '폐기') return { ...have, versions: versionsOf(submissionId).length }
    const d = newVersion(s, { style, ...aiDraft(s, style, '') })
    return { ...d, versions: versionsOf(submissionId).length }
  }),
  /** №26 POST /feedback-drafts { submissionIds:[전부] } — 43번 일괄. ⚠️ 프롬프트 미작성(REQUIREMENTS 미결) —
      목은 개별 초안과 같은 규칙으로 만든다. 프롬프트가 정해지면 서버가 그 결과를 준다 */
  draftAll: (style = '격식') => request('POST /feedback-drafts', () => {
    const pool = Object.values(SUBS).filter(s => s.judgement !== '적격' && s.status !== '확정')
    /* 건너뛴 수는 만들기 «전에» 센다 — 만든 뒤에 세면 방금 만든 것까지 「이미 있음」이 된다 */
    const skipped = pool.filter(s => latestOf(s.id) && latestOf(s.id).status !== '폐기').length
    const targets = pool.filter(s => !latestOf(s.id) || latestOf(s.id).status === '폐기')
    for (const s of targets) newVersion(s, { style, ...aiDraft(s, style, '') })
    return { taskId: 'tsk-' + Math.random().toString(36).slice(2, 6), status: 'COMPLETED', created: targets.length, skipped }
  }),
  /** №27 GET /feedback-drafts/{draftId} — 44번 조회. 판정 근거(basis)와 나란히 준다 */
  get: draftId => request('GET /feedback-drafts/{draftId}', () => ({ ...draftOr404(draftId), versions: versionsOf(draftOr404(draftId).submissionId).length })),
  /** GET /feedback-drafts?submissionId — 45번 버전 목록 (설계 파생 — №27 이 버전을 어떻게 주는지 대조 못 함) */
  versions: submissionId => request('GET /feedback-drafts', () => page(versionsOf(submissionId), { size: 100 })),
  /** 피드백 허브 — 제출 건마다 최신 초안 상태. 목록 응답 봉투를 쓴다 */
  overview: () => request('GET /feedback-drafts', () => page(
    Object.values(SUBS).filter(s => s.judgement !== '적격' || s.status === '반려').map(s => {
      const d = latestOf(s.id)
      return { submissionId: s.id, supplier: s.supplier, supplierId: s.supplierId, rule: s.rule, why: s.why, severity: s.severity,
        judgement: s.judgement, draft: d ? { id: d.id, status: d.status, version: d.version, source: d.source, failed: d.failed } : null }
    }), { size: 100 })),
  /** №28 POST /feedback-drafts/{draftId}/regenerate { style, instruction } — 44번 문체 재생성 · 45번 추가 지시 */
  regenerate: (draftId, { style, instruction = '' } = {}) => request('POST /feedback-drafts/{draftId}/regenerate', () => {
    const d = draftOr404(draftId); notLocked(d)
    const s = SUBS[d.submissionId]
    const st = style ?? d.style
    return { ...newVersion(s, { style: st, instruction, ...aiDraft(s, st, instruction) }), versions: versionsOf(s.id).length }
  }),
  /** №29 PATCH /feedback-drafts/{draftId} { body } — 47번 「수정본은 AI 초안과 별도 버전으로 저장」 */
  edit: (draftId, body) => request('PATCH /feedback-drafts/{draftId}', () => {
    const d = draftOr404(draftId); notLocked(d)
    const paras = Array.isArray(body) ? body : String(body ?? '').split(/\n{2,}/)
    if (!paras.join('').trim()) throw new ApiError(400, 'EMPTY_BODY', '본문이 비어 있습니다')
    const s = SUBS[d.submissionId]
    return { ...newVersion(s, { style: d.style, instruction: d.instruction, source: '담당자', body: paras, status: '수정본' }), versions: versionsOf(s.id).length }
  }),
  /** №29 PATCH /feedback-drafts/{draftId} { status: 'READY_TO_SEND' } — 48번 확정. 수신자·제목·본문이 잠긴다 */
  confirm: draftId => request('PATCH /feedback-drafts/{draftId}', () => {
    const d = draftOr404(draftId)
    if (d.status === '폐기') throw new ApiError(409, 'DRAFT_DISCARDED', '폐기한 초안은 확정할 수 없습니다')
    if (d.locked) throw new ApiError(409, 'DRAFT_LOCKED', '이미 확정된 초안입니다')
    if (!d.to) throw new ApiError(400, 'NO_RECIPIENT', '협력업체 담당자 이메일이 없어 확정할 수 없습니다')
    d.status = '발송 대기'; d.locked = true; d.confirmedBy = '이과장'; d.confirmedAt = new Date().toISOString()
    const s = SUBS[d.submissionId]
    DISP.unshift({ id: 'fb-' + d.id.slice(3), draftId: d.id, submissionId: d.submissionId, rule: s.rule, supplier: d.supplier,
      to: d.to, subject: d.subject, sentAt: null, confirmedAt: d.confirmedAt, state: '발송 대기', tone: 'expiring',
      replied: null, attempts: 0, note: '잠김' })
    return { draftId: d.id, status: 'READY_TO_SEND', recipient: d.to, subject: d.subject, confirmedBy: d.confirmedBy }
  }),
  /** №29 PATCH /feedback-drafts/{draftId} { status: 'DISCARDED', reason } — 49번 「폐기 사유를 기록」 */
  discard: (draftId, reason) => request('PATCH /feedback-drafts/{draftId}', () => {
    const d = draftOr404(draftId); notLocked(d)
    if (!String(reason ?? '').trim())
      throw new ApiError(400, 'DISCARD_REASON_REQUIRED', '폐기 사유를 입력해야 합니다', { fields: ['reason'] })
    d.status = '폐기'; d.discardReason = reason; d.discardedAt = new Date().toISOString()
    return { draftId: d.id, status: 'DISCARDED', reason }
  }),
  /** GET /feedback-histories?supplierId&type&status&from&to — 53번. 설계 파생 (ADR-0008) */
  list: (q = {}) => request('GET /feedback-histories', () => {
    let rows = DISP
    if (q.supplierId) { const name = SUP.find(s => s.id === +q.supplierId)?.name; rows = rows.filter(r => r.supplier === name) }
    if (q.status) rows = rows.filter(r => [].concat(q.status).includes(r.state))
    return page(rows, { ...q, size: q.size ?? 100 })
  }),
  /** №30 POST /feedback-drafts/{draftId}/send — 50번 최초 발송. 51번 결과는 이력에 남는다 */
  send: ids => request('POST /feedback-drafts/{draftId}/send', () => {
    const targets = DISP.filter(r => ids.includes(r.id) && r.state === '발송 대기')
    if (!targets.length) throw new ApiError(400, 'NOTHING_TO_SEND', '발송 대기 건이 없습니다')
    for (const r of targets) {
      r.attempts += 1; r.sentAt = new Date().toISOString()
      /* 51번 — 주소 오류는 실패로 기록한다. 없는 사서함은 목에서도 실패한다 */
      if ((r.to ?? '').startsWith('no-such-box@')) { r.state = '발송 실패'; r.tone = 'missing'; r.failReason = '주소 오류 (550 Mailbox not found)'; r.note = r.failReason }
      else { r.state = '발송 성공'; r.tone = 'complete'; r.note = '회신 대기'; r.replied = false }
    }
    return { taskId: 'tsk-' + Math.random().toString(36).slice(2, 6), status: 'PENDING', sent: targets.length, attempt: 1 }
  }),
  /** №30 POST /feedback-drafts/{draftId}/send — 52번 재발송. reason 필수 (SEND_FAILED | NO_REPLY), 횟수·시각을 이력에 남긴다 */
  resend: (id, reason = 'SEND_FAILED') => request('POST /feedback-drafts/{draftId}/send', () => {
    if (!reason) throw new ApiError(400, 'RESEND_REASON_REQUIRED', '재발송에는 사유가 필요합니다')
    const r = DISP.find(x => x.id === id)
    if (!r) throw new ApiError(404, 'FEEDBACK_NOT_FOUND', '없는 발송 건입니다')
    if (!['발송 실패', '회신 없음'].includes(r.state))
      throw new ApiError(409, 'NOT_RESENDABLE', '실패 건이나 회신 없는 건만 다시 보낼 수 있습니다')
    r.attempts += 1; r.sentAt = new Date().toISOString(); r.resendReason = reason
    r.state = '발송 대기'; r.tone = 'processing'; r.note = `재발송 ${r.attempts}회 대기`
    return { taskId: 'tsk-' + Math.random().toString(36).slice(2, 6), status: 'PENDING', draftId: r.draftId, attempt: r.attempts }
  }),
}

/* ── 대시보드 (명세 №24 · 요구사항 38~41번) ──────────────── */
export const Dashboard = {
  /** №24 GET /dashboard?month — 집계 응답이라 페이징하지 않는다. 숫자는 전부 세어서 만든다 */
  summary: () => request('GET /dashboard', () => {
    const j = judgementCounts()
    const monthly = monthlyCounts()
    const deadline = DEADLINES.find(m => m.now)
    const sev = { HIGH: 0, MEDIUM: 0, LOW: 0 }
    for (const q of Object.values(SUBS)) if (q.severity && (q.status ?? '검토 대기') === '검토 대기') sev[q.severity]++
    const todo = active().filter(s => s.judgement !== '적격')
      .map(s => ({ ...s, run: trailingMissing(s.strip), why: s.why ?? (trailingMissing(s.strip) ? `${trailingMissing(s.strip)}개월 연속 미제출` : s.judgement) }))
      .sort((a, b) => b.run - a.run || a.name.localeCompare(b.name))
    return {
      month: deadline.month, deadline: deadline.deadline, dDay: daysLeft(deadline.deadline),
      judgement: j, severity: sev,
      trend: monthly.slice(-6).map(m => m.적격),
      monthly,
      todo: todo.slice(0, 8), todoTotal: todo.length,
      longMissing: todo.filter(s => s.run >= 3).length,
      longestRun: Math.max(0, ...todo.map(s => s.run)),
      inboxToday: MAIL.filter(m => m.receivedAt?.startsWith('2026-09-02')).length,
      unidentified: MAIL.filter(m => m.state === '미확인').length,
      draftable: Object.values(SUBS).filter(s => s.judgement !== '적격' && s.status !== '확정' && !latestOf(s.id)).length,
      /* 41번 — 확정 데이터 기준 완제품별 합계. 미확정 부품이 포함된 완제품은 total 이 null 이다 (따로 표시) */
      emissions: PRD.map(p => { const e = emissionsOf(p); return { id: p.id, name: p.name, confirmed: e.confirmed, total: e.total, pendingCount: e.pendingCount, reportable: e.reportable } }),
      lastRecalcAt: '2026-09-02T15:10:00',
    }
  }),
}

export { ApiError }
export { CHECKS, SEVERITIES, NOW } from './fixtures'
