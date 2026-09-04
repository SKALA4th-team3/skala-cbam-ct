<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Feedback, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import SubTabs from '@/components/SubTabs.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import ModalBox from '@/components/ModalBox.vue'
import { useUi } from '@/stores/ui'

const ui = useUi(); const router = useRouter()
const rows = ref([]); const loading = ref(true)
const filter = ref('전체')
async function load() { rows.value = allRows(await Feedback.list(), 'GET /feedback-histories'); loading.value = false }
onMounted(load)
const STATES = ['전체', '발송 대기', '발송 성공', '발송 실패', '회신 없음']
const shown = computed(() => filter.value === '전체' ? rows.value : rows.value.filter(r => r.state === filter.value))
const countOf = computed(() => Object.fromEntries(STATES.map(s => [s, s === '전체' ? rows.value.length : rows.value.filter(r => r.state === s).length])))
const waiting = computed(() => rows.value.filter(r => r.state === '발송 대기'))
const resendable = computed(() => rows.value.filter(r => ['발송 실패', '회신 없음'].includes(r.state)))
const busy = ref(false)
const day = v => v ? String(v).replace('T', ' ').slice(0, 16) : ''

/* 50번 — 확정된 피드백을 발송. 수신자는 담당자 이메일로 자동, 원문 제출 건이 참조로 붙는다 */
async function send() {
  busy.value = true
  try { const r = await Feedback.send(waiting.value.map(x => x.id)); ui.say(`${r.sent}건을 발송했습니다 · 결과는 이력에 남습니다 (51번)`); await load() }
  catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
/* 52번 — 실패 건이나 회신 없는 건을 다시 보낸다. 사유 필수, 횟수·시각을 이력에 남긴다 */
const resending = ref(null); const reason = ref('SEND_FAILED')
function openResend(r) { resending.value = r; reason.value = r.state === '회신 없음' ? 'NO_REPLY' : 'SEND_FAILED' }
async function resend() {
  busy.value = true
  try { const r = await Feedback.resend(resending.value.id, reason.value); ui.say(`재발송 ${r.attempt}회차 · 사유 ${reason.value}`); resending.value = null; await load() }
  catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
</script>

<template>
  <SubTabs :tabs="[{ label: '초안', to: '/feedback' }, { label: '발송 관리', to: '/feedback/dispatch', count: waiting.length || null }]" />
  <ViewHead api="UC-11 · 피드백 발송 · POST /feedback-drafts/{draftId}/send" back="피드백" backTo="/feedback">
    <template #title>확정된 피드백 {{ waiting.length }}건이 발송을 기다립니다.</template>
    <template #lede>확정하면 수신자 · 제목 · 본문이 잠깁니다. 발송 실패는 사유와 함께 기록되고(51번) 재발송할 수 있습니다(52번). 업체별 이력은 협력사 상세에서도 봅니다(53번).</template>
  </ViewHead>

  <div class="filters stage" style="--d:100ms">
    <button v-for="s in STATES" :key="s" :class="{ on: filter === s }" @click="filter = s">{{ s }} <b>{{ countOf[s] }}</b></button>
  </div>

  <div class="alerts stage" style="--d:160ms">
    <SkeletonRows v-if="loading" :rows="5" :cols="['78px', '1fr', '210px', '92px']" />
    <div v-for="r in shown" :key="r.id" class="at" style="cursor:default">
      <span class="rule">{{ r.rule }}</span>
      <div><b><a href="#" class="lnk" @click.prevent="router.push(`/feedback/${r.submissionId}`)">{{ r.supplier }}</a></b>
        <span class="sub">{{ r.to }} · {{ r.subject }}</span></div>
      <span class="why">{{ r.sentAt ? `발송 ${day(r.sentAt)}` : `확정 ${day(r.confirmedAt)}` }}{{ r.attempts > 1 ? ` · ${r.attempts}회` : '' }}<br /><small v-if="r.failReason" class="bad">{{ r.failReason }}</small></span>
      <StatusChip :label="r.state" :tone="r.tone" />
      <span class="ago">
        <button v-if="['발송 실패', '회신 없음'].includes(r.state)" class="lnk" @click="openResend(r)">재발송</button>
        <template v-else>{{ r.replied === true ? '회신 있음' : r.replied === false ? '회신 없음' : r.note }}</template>
      </span>
    </div>
    <div v-if="!loading && !shown.length" class="noresult"><b>해당하는 건이 없습니다.</b><p>다른 상태를 고르세요.</p></div>
  </div>

  <ActionBar :title="waiting.length ? `발송 대기 ${waiting.length}건을 한 번에 보낼 수 있습니다.` : '발송 대기 건이 없습니다.'"
             note="수신자는 협력업체 담당자 이메일로 자동 설정되고, 원문 제출 건이 참조로 붙습니다 (50번)">
    <button v-if="resendable.length" class="quiet" @click="filter = filter === '발송 실패' ? '전체' : '발송 실패'">실패 {{ countOf['발송 실패'] }} · 회신 없음 {{ countOf['회신 없음'] }}</button>
    <button class="btn" :disabled="!waiting.length || busy" @click="send">{{ waiting.length }}건 발송</button>
  </ActionBar>

  <ModalBox :open="!!resending" title="재발송" :sub="resending ? `${resending.supplier} · ${resending.subject} · 지금까지 ${resending.attempts}회` : ''" @close="resending = null">
    <div class="rj-row"><span class="rj-cap">사유</span>
      <div class="rj-opts">
        <button :class="{ on: reason === 'SEND_FAILED' }" @click="reason = 'SEND_FAILED'">SEND_FAILED · 발송 실패</button>
        <button :class="{ on: reason === 'NO_REPLY' }" @click="reason = 'NO_REPLY'">NO_REPLY · 회신 없음</button>
      </div></div>
    <p v-if="resending?.failReason" class="sub" style="margin:8px 0 0">지난 실패 사유 — {{ resending.failReason }}. 주소 오류라면 협력사 상세에서 담당자 이메일을 먼저 고칩니다 (2번).</p>
    <template #acts>
      <button class="quiet" @click="resending = null">취소</button>
      <button v-if="resending?.failReason?.includes('주소')" class="quiet" @click="router.push(`/suppliers/${rows.find(x => x.id === resending.id)?.supplierId ?? ''}`)">담당자 이메일 고치기</button>
      <button class="btn" :disabled="busy" @click="resend">재발송</button>
    </template>
  </ModalBox>
  <div class="spacer"></div>
</template>
