<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Inbox, Suppliers, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import { useUi } from '@/stores/ui'

const router = useRouter(); const ui = useUi()
const items = ref([]); const suppliers = ref([]); const loading = ref(true)

/* 18번 — 지정 메일함을 주기적으로(예: 1분) 조회한다. 수신은 스케줄러의 일이고 화면은 그 결과를 다시 읽을 뿐이다.
   「지금 보는 목록이 언제 것인지」를 말해 주지 않으면 사람이 새로고침을 반복한다. */
const checkedAt = ref(null)
let timer = null
async function load() {
  items.value = allRows(await Inbox.list(), 'GET /mail-receipts')
  checkedAt.value = new Date()
  loading.value = false
}
onMounted(async () => {
  await load()
  suppliers.value = allRows(await Suppliers.list({ size: 1000 }), 'GET /suppliers')
  timer = setInterval(load, 60_000)
})
onBeforeUnmount(() => clearInterval(timer))
const hhmm = d => d ? d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : ''

const unknown = computed(() => items.value.filter(m => m.state === '미확인'))
const TONE = { '접수 대기': 'processing', '검토 대기': 'expiring', 미확인: 'missing', '접수 불가': 'missing', '분석 실패': 'missing', 적격: 'complete', 부적격: 'reject' }
/** 열 수 있는 건 — 분석이 돌았거나 도는 중인 것. 접수 불가·미확인은 열 데이터가 없다 */
const openable = m => m.supplier && !['접수 불가', '미확인'].includes(m.state)

/* 21번 — 원문 메일과 첨부를 같은 화면에서 확인한다. 행을 펼치면 아래에 나온다 */
const expanded = ref(null)
function toggleView(m) { expanded.value = expanded.value === m.id ? null : m.id }

/* 21번 — 「미확인 건은 담당자가 협력업체를 직접 지정할 수 있다」. 19번은 그 앞 단계다 —
   발신 주소를 담당자 이메일과 대조해 식별하고, 일치하는 업체가 없으면 미확인으로 두고 담당자에게 알린다. */
const picking = ref(null)
const q = ref('')
const matches = computed(() => {
  const n = q.value.trim().toLowerCase()
  const rows = n ? suppliers.value.filter(s => s.name.toLowerCase().includes(n) || (s.email ?? '').toLowerCase().includes(n)) : suppliers.value
  return rows.filter(s => s.tie === '협력유지중').slice(0, 8)
})
const guess = m => suppliers.value.find(s => s.email && m.from?.toLowerCase().includes(s.email.toLowerCase()))
function openPicker(m) {
  picking.value = m.id; expanded.value = null
  const g = guess(m); q.value = g ? g.name : ''
  if (g) ui.say(`발신 주소가 ${g.name} 의 담당자 이메일과 일치합니다 — 확인 후 지정하세요`)
}
async function assign(m, supplier) {
  try {
    const r = await Inbox.assign(m.id, supplier.name)
    ui.say(r.state === '접수 불가' ? `${supplier.name} 으로 지정했지만 첨부가 없어 접수 불가입니다` : `${supplier.name} 으로 지정했습니다 · AI 분석이 자동으로 돕니다`)
    picking.value = null; q.value = ''
    await load()
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
function rowClick(m) {
  if (m.state === '미확인') return openPicker(m)
  if (openable(m)) return router.push(`/submissions/${m.id}`)
  toggleView(m)
}
</script>

<template>
  <ViewHead api="UC-04 · 이메일 접수 · GET /mail-receipts (수신은 스케줄러)">
    <template #title>오늘 {{ items.length }}건이 들어왔습니다.</template>
    <template #lede>협력사가 보낸 메일을 그대로 받습니다. 사람이 다시 옮겨 적지 않습니다.
      발신 주소가 담당자 이메일과 맞지 않으면 「미확인」으로 두고 담당자가 직접 연결합니다.</template>
    <template #acts>
      <span class="live"><i></i>1분마다 메일함 확인 · 마지막 {{ hhmm(checkedAt) }}</span>
      <button class="quiet sm" @click="load">지금 확인</button>
    </template>
  </ViewHead>

  <div class="alerts stage" style="--d:160ms">
    <SkeletonRows v-if="loading" :rows="5" :cols="['78px', '1fr', '210px', '92px']" />
    <template v-for="m in items" :key="m.id">
      <div class="at" :class="{ link: openable(m) || m.state === '미확인', open: picking === m.id || expanded === m.id, dead: !openable(m) && m.state !== '미확인' }" v-clickable
           :aria-label="m.state === '미확인' ? `${m.subject} 발신자 지정` : openable(m) ? `${m.supplier} 제출자료 열기` : `${m.subject} 원문 보기`"
           @click="rowClick(m)">
        <span class="rule">{{ m.at }}</span>
        <div>
          <b>{{ m.supplier ?? '미확인 발신자' }}</b>
          <span class="sub">{{ m.from }} · {{ m.subject }}</span>
        </div>
        <span class="why">{{ m.files.length ? m.files.map(f => f.name.split('.').pop()).join(' · ') + ` ${m.files.length}개` : '첨부 없음' }}</span>
        <StatusChip :label="m.state" :tone="TONE[m.state] ?? 'processing'" />
        <button class="ago lnk" @click.stop="toggleView(m)">{{ expanded === m.id ? '닫기' : '원문' }}</button>
      </div>

      <!-- 21번 · 30번 — 원문 메일과 첨부. 읽을 수 없는 값은 추정하지 않는다 -->
      <div v-if="expanded === m.id" class="mailview">
        <div class="mv-meta"><span>Message-ID <code>{{ m.messageId }}</code></span><span>수신 {{ m.receivedAt?.replace('T', ' ') }}</span></div>
        <p v-if="m.reason" class="mv-reason">{{ m.reason }}</p>
        <pre class="mv-body">{{ m.body }}</pre>
        <div class="mv-files">
          <span v-for="f in m.files" :key="f.name" class="mv-file"><b>{{ f.name }}</b><em>{{ f.size }}</em></span>
          <span v-if="!m.files.length" class="mv-none">첨부가 없습니다 — xlsx · csv · pdf 중 하나가 있어야 접수됩니다 (20번)</span>
        </div>
        <div v-if="m.state === '분석 실패'" class="mv-acts">
          <button class="quiet sm" @click="router.push(`/feedback/${m.id}`)">재요청문 만들기 (R3)</button>
          <button class="quiet sm" @click="router.push(`/submissions/${m.id}`)">실패 내역 보기</button>
        </div>
      </div>

      <!-- 협력사 고르기. 이름·담당자 이메일 어느 쪽으로도 찾는다 -->
      <div v-if="picking === m.id" class="picker">
        <div class="pk-head">
          <input v-model="q" placeholder="협력업체명 또는 담당자 이메일로 찾기" @click.stop />
          <button class="quiet sm" @click.stop="picking = null">닫기</button>
        </div>
        <div v-if="!matches.length" class="pk-none">
          찾는 협력사가 없습니다 — 먼저 <a href="#" @click.prevent.stop="router.push('/suppliers/new')">협력사를 등록</a>하세요.
        </div>
        <button v-for="s in matches" :key="s.id" class="pk-row" @click.stop="assign(m, s)">
          <b>{{ s.name }}</b><span class="pk-mail">{{ s.email }}</span>
          <span class="pk-hit" v-if="guess(m)?.id === s.id">발신 주소 일치</span>
        </button>
      </div>
    </template>
  </div>

  <ActionBar :title="unknown.length ? `미확인 ${unknown.length}건은 담당자가 협력사를 지정해야 넘어갑니다.` : '미확인 건이 없습니다.'"
             note="같은 메일은 Message-ID 기준으로 한 번만 접수합니다 (18번). 첨부만 떼어내고 원문은 그대로 보관합니다">
    <button class="btn" :disabled="!items.some(openable)"
            @click="router.push(`/submissions/${items.find(openable).id}`)">첫 제출자료 열기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
