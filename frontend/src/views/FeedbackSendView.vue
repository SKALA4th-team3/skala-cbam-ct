<script setup>
import { computed, onMounted, ref } from 'vue'
import { Feedback } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useUi } from '@/stores/ui'

const ui = useUi()
const rows = ref([])
const filter = ref('전체')
onMounted(async () => { rows.value = (await Feedback.list()).items })
const STATES = ['전체', '발송 대기', '발송 성공', '발송 실패', '회신 없음']
const shown = computed(() => filter.value === '전체' ? rows.value : rows.value.filter(r => r.state === filter.value))
const waiting = computed(() => rows.value.filter(r => r.state === '발송 대기'))
const failed = computed(() => rows.value.filter(r => r.state === '발송 실패'))

async function send() {
  const ids = waiting.value.map(r => r.id)
  await Feedback.send(ids)
  rows.value = rows.value.map(r => ids.includes(r.id)
    ? { ...r, state: '발송 성공', tone: 'complete', when: '발송 2026-09-03 10:40', note: '회신 대기' } : r)
  ui.say(ids.length + '건을 발송했습니다')
}
</script>

<template>
  <ViewHead api="UC-11 · 피드백 발송 · POST /feedback/{id}/send" back="초안" backTo="/feedback">
    <template #title>확정된 피드백 {{ waiting.length }}건이 발송을 기다립니다.</template>
    <template #lede>확정하면 수신자 · 제목 · 본문이 잠깁니다. 발송 실패는 사유와 함께 기록되고 재발송할 수 있습니다.</template>
  </ViewHead>

  <div class="filters stage" style="--d:100ms">
    <button v-for="s in STATES" :key="s" :class="{ on: filter === s }" @click="filter = s">
      {{ s }} <b>{{ s === '전체' ? rows.length : rows.filter(r => r.state === s).length }}</b>
    </button>
  </div>

  <div class="alerts stage" style="--d:160ms">
    <div v-for="r in shown" :key="r.id" class="at">
      <span class="rule">{{ r.rule }}</span>
      <div><b>{{ r.supplier }}</b><span class="sub">{{ r.line }}</span></div>
      <span class="why">{{ r.when }}</span>
      <StatusChip :label="r.state" :tone="r.tone" />
      <span class="ago">{{ r.note }}</span>
    </div>
  </div>

  <ActionBar :title="`발송 대기 ${waiting.length}건을 한 번에 보낼 수 있습니다.`"
             note="수신자는 협력업체 담당자 이메일로 자동 설정되고, 원문 제출 건이 참조로 붙습니다">
    <button v-if="failed.length" class="quiet"
            @click="Feedback.resend(failed[0].id).then(() => ui.say('실패 1건을 재발송했습니다'))">
      실패 {{ failed.length }}건 재발송
    </button>
    <button class="btn" :disabled="!waiting.length" @click="send">{{ waiting.length }}건 발송</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
