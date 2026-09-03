<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Inbox, Suppliers, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useUi } from '@/stores/ui'

const router = useRouter(); const ui = useUi()
const items = ref([])
const suppliers = ref([])

onMounted(async () => {
  items.value = allRows(await Inbox.list(), 'GET /mail-receipts')
  suppliers.value = allRows(await Suppliers.list({ size: 1000 }), 'GET /suppliers')
})

const unknown = computed(() => items.value.filter(m => !m.supplier))

/* 요구사항 **21번** — 「미확인 건은 담당자가 협력업체를 직접 지정할 수 있다」.
   19번은 그 앞 단계다 — 발신 주소를 담당자 이메일과 대조해 식별하고,
   「일치하는 업체가 없으면 미확인으로 두고 담당자에게 알린다」. 지정 권한은 21번이 준다.
   전에는 어느 건을 눌러도 '대한화학' 으로 고정 지정됐다 — 골라야 기능이다. */
const picking = ref(null)     // 지금 협력사를 고르는 중인 접수 건 id
const q = ref('')
const matches = computed(() => {
  const n = q.value.trim().toLowerCase()
  const rows = n
    ? suppliers.value.filter(s => s.name.toLowerCase().includes(n) || (s.email ?? '').toLowerCase().includes(n))
    : suppliers.value
  return rows.slice(0, 8)
})

/** 발신 주소로 먼저 찾아 본다 — 담당자 이메일이 매칭 키다 (요구사항 1번·19번).
    고르는 것은 사람이다. 화면이 대신 지정하지 않는다 (21번) */
const guess = m => suppliers.value.find(s => s.email && m.from?.toLowerCase().includes(s.email.toLowerCase()))

function openPicker(m) {
  picking.value = m.id
  const g = guess(m)
  q.value = g ? g.name : ''
  if (g) ui.say(`발신 주소가 ${g.name} 의 담당자 이메일과 일치합니다 — 확인 후 지정하세요`)
}

async function assign(m, supplier) {
  try {
    await Inbox.assign(m.id, supplier.name)
    ui.say(`${m.subject} 을 ${supplier.name} 으로 지정했습니다 · 검토 대기로 넘어갑니다`)
    picking.value = null; q.value = ''
    items.value = allRows(await Inbox.list(), 'GET /mail-receipts')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
</script>

<template>
  <ViewHead api="UC-04 · 이메일 접수 · GET /mail-receipts (수신은 스케줄러)">
    <template #title>오늘 {{ items.length }}건이 들어왔습니다.</template>
    <template #lede>협력사가 보낸 메일을 그대로 받습니다. 사람이 다시 옮겨 적지 않습니다.
      발신 주소가 담당자 이메일과 맞지 않으면 「미확인」으로 두고 담당자가 직접 연결합니다.</template>
  </ViewHead>

  <div class="alerts stage" style="--d:160ms">
    <template v-for="m in items" :key="m.id">
      <div class="at" :class="{ link: m.supplier, open: picking === m.id }" v-clickable
           :aria-label="m.supplier ? `${m.supplier} 제출자료 열기` : `${m.subject} 발신자 지정`"
           @click="m.supplier ? router.push(`/submissions/${m.id}`) : openPicker(m)">
        <span class="rule">{{ m.at }}</span>
        <div>
          <b>{{ m.supplier ?? '미확인 발신자' }}</b>
          <span class="sub">{{ m.from }} · {{ m.subject }}</span>
        </div>
        <span class="why">{{ m.files }}</span>
        <StatusChip :label="m.state" :tone="m.tone" />
        <span class="ago">{{ m.supplier ? '' : '지정' }}</span>
      </div>

      <!-- 협력사 고르기. 이름·담당자 이메일 어느 쪽으로도 찾는다 -->
      <div v-if="picking === m.id" class="picker">
        <div class="pk-head">
          <input v-model="q" placeholder="협력업체명 또는 담당자 이메일로 찾기" @click.stop />
          <button class="quiet sm" @click.stop="picking = null">닫기</button>
        </div>
        <div v-if="!matches.length" class="pk-none">
          찾는 협력사가 없습니다 — 먼저
          <a href="#" @click.prevent.stop="router.push('/suppliers/new')">협력사를 등록</a>하세요.
        </div>
        <button v-for="s in matches" :key="s.id" class="pk-row" @click.stop="assign(m, s)">
          <b>{{ s.name }}</b>
          <span class="pk-mail">{{ s.email }}</span>
          <span class="pk-hit" v-if="guess(m)?.id === s.id">발신 주소 일치</span>
        </button>
      </div>
    </template>
  </div>

  <ActionBar :title="unknown.length ? `미확인 ${unknown.length}건은 담당자가 협력사를 지정해야 넘어갑니다.` : '미확인 건이 없습니다.'"
             note="첨부만 떼어내고 원문은 그대로 보관합니다. 읽을 수 없는 값은 추정하지 않습니다 (NFR-04)">
    <button class="btn" :disabled="!items.some(m => m.supplier)"
            @click="router.push(`/submissions/${items.find(m => m.supplier).id}`)">검토 대기로</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
