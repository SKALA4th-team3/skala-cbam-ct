/* 「담당자에게 알린다」 — 명세가 세 곳에서 같은 말을 한다.
     19번  일치하는 업체가 없으면 «미확인»으로 두고 담당자에게 알린다
     22번  암호가 걸렸거나 파싱 실패면 «분석 실패»로 기록하고 담당자에게 알린다
     51번  발송 실패 시 사유를 저장하고 담당자에게 알린다

   알림 API 는 명세에 없다. 그래서 새 엔드포인트를 지어내지 않고,
   이미 있는 목록 셋(접수함·발송 이력·미제출 경보)에서 «알려야 할 것»을 골라 보여준다.
   서버가 알림 API 를 주면 이 파일만 그것으로 바꾼다. */
import { ref, computed } from 'vue'
import { Inbox, Feedback, Dashboard } from '@/api'

const items = ref([])
const loadedAt = ref(null)
const seen = ref(new Set())     // 이번 세션에서 읽은 것. 새로고침하면 다시 «새로» 뜬다 — 서버 상태가 아니라서다

export function useNotices() {
  async function load() {
    const [mail, hist, board] = await Promise.all([Inbox.list(), Feedback.list(), Dashboard.summary()])
    const out = []
    for (const m of mail.content) {
      if (m.state === '미확인') out.push({ id: 'mail-' + m.id, kind: '미확인', tone: 'missing', title: `미확인 발신자 · ${m.from}`, sub: `${m.subject} — 협력업체를 지정해야 분석이 돕니다 (19번)`, at: m.at, to: '/inbox' })
      if (m.state === '분석 실패') out.push({ id: 'mail-' + m.id, kind: '분석 실패', tone: 'missing', title: `${m.supplier} · 분석 실패`, sub: `${m.reason} (22번)`, at: m.at, to: `/submissions/${m.id}` })
      if (m.state === '접수 불가') out.push({ id: 'mail-' + m.id, kind: '접수 불가', tone: 'anomaly', title: `${m.supplier} · 접수 불가`, sub: `${m.reason}`, at: m.at, to: '/inbox' })
    }
    for (const h of hist.content)
      if (h.state === '발송 실패') out.push({ id: 'fb-' + h.id, kind: '발송 실패', tone: 'missing', title: `${h.supplier} · 발송 실패`, sub: `${h.failReason ?? h.note} (51번)`, at: h.sentAt?.slice(5, 16).replace('T', ' ') ?? '', to: '/feedback/dispatch' })
    if (board.dDay <= 7)
      out.push({ id: 'dday', kind: 'D-7', tone: 'expiring', title: `마감 D-${board.dDay} · 미제출 ${board.judgement.미제출}곳`, sub: 'D-7 부터 미제출 업체에 경보가 뜹니다 (39번)', at: '', to: '/deadlines' })
    items.value = out
    loadedAt.value = new Date()
  }
  const unread = computed(() => items.value.filter(n => !seen.value.has(n.id)).length)
  function markAll() { seen.value = new Set(items.value.map(n => n.id)) }
  return { items, unread, load, markAll, loadedAt }
}
