/* 전송 계층.
   지금은 BE 가 없어 전부 목이다. 목이라는 사실을 숨기지 않기 위해
   모든 호출이 자기 엔드포인트를 문자열로 들고 이 파일을 지나간다.

   모든 응답이 request() 한 곳을 지나가므로 상태값 변환(영문 enum → 한글)도 여기서 한다 — ADR-0005.
   매핑표는 api/enums.js 에 있다. 목(한글)은 그대로 통과한다.

   BE 가 붙으면 두 가지만 한다.
   1) api/index.js 의 해당 호출에 세 번째 인자(real)를 채운다
   2) .env 의 VITE_REAL_API 에 그 엔드포인트를 넣는다
   화면 코드는 건드리지 않는다. */

import { fromServer } from './enums'

const LATENCY = [120, 320]          // 목이라는 걸 잊지 않게 일부러 조금 늦춘다
const rand = (a, b) => a + Math.random() * (b - a)
export const sleep = ms => new Promise(r => setTimeout(r, ms))

/** 실서버 주소. 목만 쓰는 동안에는 아무도 안 본다 */
export const BASE = import.meta.env.VITE_API_BASE ?? '/api/v1'

/** 담당자 식별자. 명세 v10 규약 2항 — 인증 수단이 아니라 감사 기록용이다.
    상태를 바꾸는 요청은 이 헤더로 행위자를 남긴다(confirmedBy·rejectedBy·editedBy …). */
export const operatorId = import.meta.env.VITE_OPERATOR_ID ?? 'demo'
export const headers = () => ({ 'Content-Type': 'application/json', 'X-Operator-Id': operatorId })

/** 실서버로 보낼 엔드포인트 목록 — .env 의 VITE_REAL_API 로 하나씩 늘린다 */
const REAL = new Set(
  (import.meta.env.VITE_REAL_API ?? '').split(',').map(s => s.trim()).filter(Boolean),
)

/** 이번 세션에서 실제로 불린 엔드포인트와 그 출처 — 화면이 부를 때마다 쌓인다 */
export const wired = new Map()      // 'GET /suppliers' -> 'mock' | 'real'
const mark = (endpoint, kind) => wired.set(endpoint, kind)

/** 실제 서버의 에러 바디와 같은 모양 — API 명세서 v10 규약 3항 */
export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message)
    this.status = status; this.code = code
    this.details = details || {}
    this.timestamp = new Date().toISOString()
  }
  /** 서버가 내려줄 바디 그대로 */
  get body() {
    return { timestamp: this.timestamp, status: this.status, code: this.code, message: this.message, details: this.details }
  }
}

/**
 * 모든 엔드포인트가 통과하는 지점.
 * @param endpoint 'GET /suppliers' — 실제 경로. 주석이 아니라 값이라 세고 검사할 수 있다
 * @param handler  목 구현
 * @param real     실서버 구현. BE 가 나오면 채운다. 없으면 목으로 간다
 *
 * 응답은 목이든 실서버든 fromServer() 를 통과한다. 화면은 언제나 한글 상태값을 본다.
 */
export async function request(endpoint, handler, real) {
  if (REAL.has(endpoint)) {
    if (!real) throw new Error(`VITE_REAL_API 에 ${endpoint} 가 있는데 실서버 구현이 없다`)
    mark(endpoint, 'real')
    return fromServer(await real(BASE))
  }
  mark(endpoint, 'mock')
  await sleep(rand(...LATENCY))
  return fromServer(handler())
}

/* ── 실서버 전송 ──────────────────────────────────────────────────
   BE 가 붙은 엔드포인트만 이 길로 간다 (.env 의 VITE_REAL_API).
   에러 바디는 규약 3항 모양 그대로 오는데 **필드 목록의 이름이 다르다** —
   BE 는 `details.fieldErrors` 를 객체로 주고, 화면은 `details.fields` 를 배열로 읽는다.
   실서버에 확인했다: `{"code":"INVALID_REQUEST","details":{"fieldErrors":{"country":"…"}}}`.
   그래서 여기서 fields 를 함께 채워 준다 — 원본 fieldErrors 도 남긴다(메시지가 그 안에 있다). */
const qs = q => {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(q ?? {})) {
    if (v == null || v === '' || (Array.isArray(v) && !v.length)) continue
    /* 배열 필터는 첫 값만 보낸다 — 명세 №3 의 country·status 는 단일값이다.
       화면의 다중 선택은 브라우저가 거른다 (ADR-0009). */
    p.set(k, Array.isArray(v) ? v[0] : v)
  }
  const s = p.toString()
  return s ? `?${s}` : ''
}

/* 409 중복 오류는 `details` 가 비어 온다 (실서버 확인: DUPLICATE_BUSINESS_NUMBER → `"details":{}`).
   어느 칸이 문제인지 화면이 붉게 표시하려면 필드 이름이 필요하다 —
   **에러 코드가 그것을 결정적으로 말해 주므로** 코드에서 끌어온다. 지어내는 것이 아니다. */
const FIELDS_OF_CODE = {
  DUPLICATE_BUSINESS_NUMBER: ['bizNo'],
  DUPLICATE_CONTACT_EMAIL: ['email'],
}

/** 서버 필드 이름 → 화면 폼 필드 이름. shapes.js 의 반대 방향이다 */
const FORM_FIELD = {
  companyName: 'name', businessRegistrationNumber: 'bizNo',
  contactName: 'contact', contactEmail: 'email', phone: 'phone', country: 'country',
}

export async function http(method, path, { query, body } = {}) {
  const res = await fetch(`${BASE}${path}${qs(query)}`, {
    method,
    headers: headers(),
    ...(body !== undefined && { body: JSON.stringify(body) }),
  })
  if (res.status === 204) return null
  const text = await res.text()
  const json = text ? JSON.parse(text) : null
  if (!res.ok) {
    const d = json?.details ?? {}
    const code = json?.code ?? 'UNKNOWN'
    const named = d.fields ?? Object.keys(d.fieldErrors ?? {}).map(f => FORM_FIELD[f] ?? f)
    const fields = named.length ? named : (FIELDS_OF_CODE[code] ?? [])
    throw new ApiError(res.status, code, json?.message ?? res.statusText, { ...d, fields })
  }
  return json
}

/** 목록 응답 공통 봉투 — API 명세서 v10 규약 4항.
    목록 API 는 예외 없이 content·page·size·totalElements·totalPages 다섯 키를 반환한다.
    page 는 0부터다. */
export function page(rows, { page = 0, size = 20 } = {}) {
  const start = page * size
  return {
    content: rows.slice(start, start + size),
    page, size,
    totalElements: rows.length,
    totalPages: Math.max(1, Math.ceil(rows.length / size)),
  }
}

/** 개발 모드에서 지금 화면이 무엇을 보고 있는지 한 줄로 알린다 */
export function logWiring() {
  if (!import.meta.env.DEV) return
  setTimeout(() => {
    const all = [...wired.entries()]
    const mocked = all.filter(([, k]) => k === 'mock')
    console.info(
      `%c[API] 실서버 ${all.length - mocked.length} · 목 ${mocked.length}`,
      'color:#f5a623',
      mocked.length ? `\n목: ${mocked.map(([e]) => e).join(', ')}` : '',
    )
  }, 2000)
}

/* ── 202 Accepted + 폴링 ────────────────────────────────────────────
   UC-05 자료 분석은 오래 걸리므로 즉시 taskId 를 주고 뒤에서 돈다.
   실제 서버도 같은 계약을 지켜야 화면 코드를 안 고친다. */
const tasks = new Map()

/** 목의 kind → 명세 №19 의 taskType. 서버가 쓰는 이름과 같아야 화면이 한 벌로 돈다. */
const TASK_TYPE = {
  analyze: 'ANALYZE_MAIL_RECEIPT',
  revalidate: 'REVALIDATE_SUBMISSION',
  draft: 'GENERATE_FEEDBACK_DRAFT',
  regenerate: 'REGENERATE_FEEDBACK_DRAFT',
  send: 'SEND_FEEDBACK',
  remind: 'SEND_REMINDER',
}

/** @param endpoint 'POST /...' — 화면이 부른 엔드포인트.
 *   내부 자동 실행(요구사항 20)처럼 부른 엔드포인트가 없으면 null 을 준다.
 *   경로 아닌 문자열을 넣으면 api:status 집계가 흐려진다. */
export function startTask(endpoint, kind, { ms = 2600, result = null, fail = false } = {}) {
  if (endpoint) mark(endpoint, 'mock')
  const id = 'tsk-' + Math.random().toString(36).slice(2, 6)
  tasks.set(id, { id, kind, status: 'PENDING', startedAt: Date.now(), ms, result, fail })
  setTimeout(() => { const t = tasks.get(id); if (t && t.status === 'PENDING') t.status = 'PROCESSING' }, 400)
  setTimeout(() => {
    const t = tasks.get(id); if (!t) return
    t.status = t.fail ? 'FAILED' : 'COMPLETED'
    t.error = t.fail ? { code: 'PARSE_FAILED', message: '첨부파일을 읽지 못했습니다' } : null
  }, ms)
  return { status: 202, taskId: id, pollAfterMs: 1200 }
}

/** №19 GET /tasks/{taskId}. 목과 실서버가 **같은 모양**이라 변환할 것이 없다 —
 *  목을 명세 모양으로 맞춰 둔 덕이다. .env 의 VITE_REAL_API 에 넣으면 그대로 실서버로 간다. */
export async function getTask(id) {
  return request('GET /tasks/{taskId}', () => {
    const t = tasks.get(id)
    if (!t) throw new ApiError(404, 'TASK_NOT_FOUND', '해당 작업이 없습니다')
    /* 명세 №19 응답 그대로. 전에는 { resultId, error } 라는 내 임의 모양이었는데
       백엔드(CBAM-86)가 실제로 내보내는 것과 달라 화면이 두 번 고쳐질 뻔했다. */
    const done = t.status === 'COMPLETED'
    return {
      taskId: t.id,
      taskType: TASK_TYPE[t.kind] ?? 'ANALYZE_MAIL_RECEIPT',
      status: t.status,
      resourceType: done && t.result != null ? (t.resourceType ?? 'submission') : null,
      resourceIds: done && t.result != null ? [t.result] : [],
      progress: { total: 1, done: done ? 1 : 0, failed: t.status === 'FAILED' ? 1 : 0 },
      fallbackApplied: Boolean(t.fallbackApplied),
      unregisteredPartCount: t.unregisteredPartCount ?? 0,
      errorCode: t.error?.code ?? null,
      errorMessage: t.error?.message ?? null,
    }
  }, () => http('GET', `/tasks/${id}`))
}
