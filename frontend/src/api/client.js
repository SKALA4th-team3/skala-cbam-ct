/* 가짜 전송 계층.
   실제 API 가 나오면 이 파일의 request() 만 fetch 로 바꾸면 된다.
   응답 모양(성공/에러/202)은 실제 서버가 지켜야 할 계약 그대로 흉내낸다. */

const LATENCY = [120, 320]          // 목이라는 걸 잊지 않게 일부러 조금 늦춘다
const rand = (a, b) => a + Math.random() * (b - a)
export const sleep = ms => new Promise(r => setTimeout(r, ms))

/** 실제 서버의 에러 바디와 같은 모양 */
export class ApiError extends Error {
  constructor(status, code, message, fields) {
    super(message)
    this.status = status; this.code = code; this.fields = fields || null
  }
}

/** 모든 목 엔드포인트가 통과하는 지점 */
export async function request(handler) {
  await sleep(rand(...LATENCY))
  return handler()
}

/** 목록 응답 공통 봉투 — 실제 API 도 이 모양을 지킨다 */
export function page(items, { page = 1, size = 20 } = {}) {
  const start = (page - 1) * size
  return { items: items.slice(start, start + size), page, size, total: items.length }
}

/* ── 202 Accepted + 폴링 ────────────────────────────────────────────
   UC-05 자료 분석은 오래 걸리므로 즉시 taskId 를 주고 뒤에서 돈다.
   실제 서버도 같은 계약을 지켜야 화면 코드를 안 고친다. */
const tasks = new Map()

export function startTask(kind, { ms = 2600, result = null, fail = false } = {}) {
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

export async function getTask(id) {
  return request(() => {
    const t = tasks.get(id)
    if (!t) throw new ApiError(404, 'TASK_NOT_FOUND', '해당 작업이 없습니다')
    return { taskId: t.id, status: t.status, resultId: t.status === 'COMPLETED' ? t.result : null, error: t.error || null }
  })
}
