<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Feedback, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import ModalBox from '@/components/ModalBox.vue'
import { useUi } from '@/stores/ui'

const route = useRoute(); const router = useRouter(); const ui = useUi()
const rows = ref([]); const loading = ref(true)
async function load() { rows.value = allRows(await Feedback.overview(), 'GET /feedback-drafts'); loading.value = false }
onMounted(async () => { await load(); if (route.query.bulk) bulk.value = true })

const TONE = { 초안: 'processing', 수정본: 'expiring', '발송 대기': 'complete', 폐기: 'missing' }
const none = computed(() => rows.value.filter(r => !r.draft || r.draft.status === '폐기'))
const waiting = computed(() => rows.value.filter(r => r.draft?.status === '발송 대기').length)

/* 43번 — 모든 부적격/미제출 협력업체에 보낼 초안을 한 번에. ⚠️ 프롬프트 미작성(명세 미결) — 목은 개별 초안과 같은 규칙으로 만든다 */
const bulk = ref(false); const busy = ref(false)
async function draftAll() {
  busy.value = true
  try {
    const r = await Feedback.draftAll('격식')
    ui.say(`초안 ${r.created}건을 만들었습니다${r.skipped ? ` · 이미 있는 ${r.skipped}건은 건너뛰었습니다` : ''}`)
    bulk.value = false; await load()
    if (route.query.bulk) router.replace({ query: {} })
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
</script>

<template>
  <ViewHead api="UC-10 · 피드백 · GET /feedback-drafts">
    <template #title>안내문이 필요한 곳 {{ rows.length }}건</template>
    <template #lede>부적격·미제출 건마다 판정 근거로 초안을 만들고(42번), 문체를 고르고(44번), 고치고(47번), 확정해 발송 대기로 보냅니다(48번).</template>
    <template #acts>
      <button class="quiet" :disabled="!none.length" @click="bulk = true">초안 {{ none.length }}건 일괄 생성</button>
      <button class="quiet" @click="router.push('/feedback/dispatch')">발송 관리{{ waiting ? ` · 대기 ${waiting}` : '' }}</button>
    </template>
  </ViewHead>

  <div class="alerts stage" style="--d:140ms">
    <SkeletonRows v-if="loading" :rows="5" :cols="['78px', '1fr', '210px', '92px']" />
    <div v-for="r in rows" :key="r.submissionId" v-clickable class="at link" :aria-label="`${r.supplier} 안내문`" @click="router.push(`/feedback/${r.submissionId}`)">
      <span class="rule">{{ r.rule ?? '—' }}</span>
      <div><b>{{ r.supplier }}</b><span class="sub">{{ r.why }} · 판정 {{ r.judgement }}</span></div>
      <span class="why">{{ r.draft ? `v${r.draft.version} · ${r.draft.source}${r.draft.failed ? ' · AI 실패 → 템플릿' : ''}` : '초안 없음' }}</span>
      <StatusBadge v-if="r.severity" :value="r.severity" />
      <span v-else></span>
      <StatusChip :label="r.draft?.status ?? '초안 없음'" :tone="r.draft ? TONE[r.draft.status] : 'missing'" />
    </div>
    <div v-if="!loading && !rows.length" class="noresult"><b>안내문이 필요한 건이 없습니다.</b><p>검토에서 반려하거나 판정이 부적격이면 여기 나타납니다.</p></div>
  </div>

  <ActionBar :title="none.length ? `${none.length}건은 아직 초안이 없습니다.` : '모든 건에 초안이 있습니다.'"
             note="초안은 판정 사유(37번)와 반려 사유(32번)에서 만듭니다. 없는 값을 채우자고 요구하지 않습니다">
    <button class="btn" @click="router.push('/feedback/dispatch')">발송 관리로</button>
  </ActionBar>

  <ModalBox :open="bulk" title="초안 일괄 생성" :sub="`부적격·미제출 ${none.length}건에 격식 문체로 초안을 만듭니다 (43번)`" @close="bulk = false">
    <div class="alert" style="margin-top:0">
      <span class="ic">!</span>
      <div><b>일괄 생성 프롬프트는 아직 정해지지 않았습니다</b><p>REQUIREMENTS 43번 「AI 프롬프트로 생성 예정」 — 미결입니다. 지금은 개별 초안(42번)과 같은 규칙으로 만들고, 프롬프트가 정해지면 서버가 그 결과를 줍니다. 만든 초안은 하나씩 열어 확인한 뒤 확정합니다.</p></div>
    </div>
    <template #acts>
      <button class="quiet" @click="bulk = false">취소</button>
      <button class="btn" :disabled="busy || !none.length" @click="draftAll">{{ busy ? '만드는 중…' : `${none.length}건 만들기` }}</button>
    </template>
  </ModalBox>
  <div class="spacer"></div>
</template>
