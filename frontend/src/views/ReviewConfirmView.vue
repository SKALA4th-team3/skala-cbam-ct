<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Analysis, Review, ApiError, SEVERITIES } from '@/api'
import { useBoard } from '@/stores/board'
import { useUi } from '@/stores/ui'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import RawStdPair from '@/components/RawStdPair.vue'
import PartForm from '@/components/PartForm.vue'
import StatusChip from '@/components/StatusChip.vue'

const route = useRoute(); const router = useRouter()
const board = useBoard(); const ui = useUi()
const sub = ref(null); const missing = ref(null)
async function load() {
  try { sub.value = await Analysis.get(route.params.id) }
  catch (e) { missing.value = e }
}
onMounted(load)

/* 명세 31 — 「판정이 적격이고 미등록 부품이 없는 경우에만 확정할 수 있다」
   막는 조건은 셋이다. 누락만 보면 부적격 건이 그대로 확정된다.
   담당자가 누락 값을 직접 채우는 기능은 없다 — 29~32번에 없고 API 도 없다. 누락 건은 반려(32번) → 피드백(50번)으로 흘려보낸다. */
const gaps = computed(() => sub.value?.missingFields ?? [])
const unmapped = computed(() => sub.value?.unmappedParts ?? [])
const blockers = computed(() => {
  const s = sub.value
  if (!s) return []
  return [
    ...gaps.value.map(f => `${f} 값 누락`),
    ...(s.judgement && s.judgement !== '적격' ? [`판정이 ${s.judgement}`] : []),
    ...unmapped.value.map(p => `미등록 부품 · ${p}`),
  ]
})
const done = computed(() => sub.value?.status && sub.value.status !== '검토 대기')

async function confirm() {
  try {
    if (blockers.value.length) throw new ApiError(400, 'NOT_CONFIRMABLE', blockers.value.join(' · '))
    await Review.confirm(route.params.id)
    await board.refresh()
    ui.say('확정 완료 · 집계와 완제품 내재배출량에 반영됩니다')
    router.push('/')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}

/* ── 28번 — 미등록 부품은 담당자가 수동으로 새 부품을 추가한다. 등록되면 그 조건이 풀린다 ── */
const registering = ref(null)     // 지금 등록 중인 미등록 부품명
async function onPartCreated() { registering.value = null; await load() }

/* ── 반려 (명세 32번) — 「상태를 부적격/미제출로 설정하고 사유 저장」 ── */
const rejecting = ref(false)
const RESULTS = [
  ['REJECTED', '부적격', '자료는 왔지만 검증을 통과하지 못했다'],
  ['NOT_SUBMITTED', '미제출', '자료 자체를 받지 못한 것으로 되돌린다'],
]
const reasonCode = ref(''); const reasonText = ref(''); const resultStatus = ref('REJECTED')
const rules = SEVERITIES
const suggested = computed(() => rules.find(r => r.rule === sub.value?.rule)?.rule ?? '')
const canReject = computed(() => !!reasonCode.value && !!reasonText.value.trim())
function openReject() {
  rejecting.value = true
  reasonCode.value = reasonCode.value || suggested.value
  const r = rules.find(x => x.rule === reasonCode.value)
  if (r && !reasonText.value) reasonText.value = `${r.name} — ${blockers.value.join(' · ') || r.desc}`
}
async function reject() {
  try {
    await Review.reject(route.params.id, reasonText.value, reasonCode.value, resultStatus.value)
    /* 반려도 판정을 바꾼다 — 전에는 여기서 집계를 다시 세지 않아
       관제의 부적격 수가 실제와 갈라진 채로 남았다 (확정만 반영하고 있었다) */
    await board.refresh()
    ui.say(`반려했습니다 · ${reasonCode.value} 사유를 저장했습니다 — 안내문 초안으로 넘어갑니다`)
    router.push(`/feedback/${route.params.id}`)
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
</script>

<template>
  <ViewHead api="UC-07 · 데이터 검토 · POST /submissions/{submissionId}/confirm"
            back="검토 대기" backTo="/review">
    <template #title>
      <template v-if="missing">없는 제출 건입니다.</template>
      <template v-else-if="done">{{ sub.supplier }} · 이미 {{ sub.status }}된 건입니다.</template>
      <template v-else-if="sub?.analysisFailed">분석이 실패해 확정할 것이 없습니다.</template>
      <template v-else>{{ blockers.length ? `${blockers.length}가지가 남아 확정할 수 없습니다.` : '확정할 준비가 됐습니다.' }}</template>
    </template>
    <template #lede>
      <template v-if="sub">{{ sub.supplier }} · {{ sub.period }} · 제출 {{ sub.submittedAt?.replace('T', ' ').slice(0, 16) }} · 판정 {{ sub.judgement }}{{ sub.rule ? ` · ${sub.rule} ${sub.why}` : '' }}</template>
      <template v-else-if="missing">{{ missing.message }}</template>
    </template>
    <template #acts v-if="sub">
      <button class="quiet sm" @click="router.push(`/submissions/${sub.id}`)">자료 변환 화면</button>
      <button class="quiet sm" @click="router.push(`/suppliers/${sub.supplierId}`)">{{ sub.supplier }} 상세</button>
    </template>
  </ViewHead>

  <!-- 30번 — 원본 첨부를 같은 화면에서 연다 -->
  <div v-if="sub?.attachments?.length" class="mv-files inpage stage" style="--d:120ms">
    <span v-for="f in sub.attachments" :key="f.name" class="mv-file"><b>{{ f.name }}</b><em>{{ f.size }}</em></span>
  </div>

  <RawStdPair v-if="sub?.rows?.length" :rows="sub.rows" delay="160ms" />
  <div v-else-if="sub?.analysisFailed" class="alert stage" style="--d:160ms">
    <span class="ic">!</span>
    <div><b>분석 실패 · {{ sub.failure?.code }}</b><p>{{ sub.failure?.message }} — 표준 데이터가 없어 검토할 값이 없습니다. 반려하고 재요청문(R3)을 보냅니다.</p></div>
  </div>

  <!-- 27·28번 — 미등록 부품을 원문 표기 그대로 보여주고, 여기서 바로 등록한다 -->
  <div v-if="unmapped.length" class="unmapped stage" style="--d:180ms">
    <div class="subhead"><h3>미등록 부품 {{ unmapped.length }}</h3><p>추출 품명이 등록 부품과 맞지 않습니다. 원문 표기 그대로입니다 (27번). 정식 부품으로 등록하면 이 조건이 풀립니다 (28번).</p></div>
    <div v-for="p in unmapped" :key="p" class="um-row">
      <StatusChip label="미등록 부품" tone="missing" flat /><b>{{ p }}</b>
      <button class="quiet sm" @click="registering = p">이 이름으로 부품 등록</button>
    </div>
  </div>
  <PartForm :open="!!registering" :preset="{ name: registering ?? '', supplier: sub?.supplier ?? '', resolves: registering ? { submissionId: sub.id, name: registering } : null }"
            @close="registering = null" @created="onPartCreated" />

  <!-- 반려 사유 — 32번이 「사유 저장」을 요구한다. 비어 있으면 서버가 400 으로 막는다 -->
  <div v-if="rejecting" class="rj-box stage" style="--d:60ms">
    <div class="subhead"><h3>반려 사유</h3><p>여기 적은 것이 그대로 피드백 초안(UC-10)의 근거가 됩니다.</p></div>
    <div class="rj-row">
      <span class="rj-cap">되돌릴 상태</span>
      <div class="rj-opts">
        <button v-for="[code, label, hint] in RESULTS" :key="code" :title="hint" :class="{ on: resultStatus === code }" @click="resultStatus = code">{{ label }}</button>
      </div>
    </div>
    <div class="rj-row">
      <span class="rj-cap">규칙 코드</span>
      <div class="rj-opts">
        <button v-for="r in rules" :key="r.rule" :title="r.desc" :class="{ on: reasonCode === r.rule }"
                @click="reasonCode = r.rule; reasonText = `${r.name} — ${blockers.join(' · ') || r.desc}`">{{ r.rule }} {{ r.name }}</button>
      </div>
    </div>
    <label class="rj-row">
      <span class="rj-cap">사유</span>
      <textarea v-model="reasonText" rows="3" placeholder="무엇이 왜 비었는지 적습니다. 없는 값을 채우자고 요구하지 않습니다." />
    </label>
    <p v-if="!canReject" class="rj-warn">규칙 코드와 사유를 모두 채워야 반려할 수 있습니다.</p>
  </div>

  <ActionBar v-if="sub && !done"
             :title="blockers.length ? blockers.join(' · ') + ' — 확정 버튼이 잠깁니다.' : '확정하면 집계와 완제품 내재배출량에 반영됩니다.'"
             note="누락된 값을 담당자가 채우는 기능은 없습니다 — 반려해 피드백으로 보냅니다. 화면이 잠가도 서버가 다시 막습니다 (400 NOT_QUALIFIED · 400 UNREGISTERED_PART_EXISTS)">
    <button v-if="!rejecting" class="quiet" @click="openReject">반려</button>
    <template v-else>
      <button class="quiet" @click="rejecting = false">반려 취소</button>
      <button class="btn warn" :disabled="!canReject" @click="reject">반려 확정</button>
    </template>
    <button v-if="!rejecting" class="btn" :disabled="blockers.length > 0" @click="confirm">확정하기</button>
  </ActionBar>
  <ActionBar v-else-if="done" :title="`${sub.status} · ${(sub.confirmedAt ?? sub.rejectedAt ?? '').replace('T', ' ').slice(0, 16)} · ${sub.confirmedBy ?? sub.rejectedBy ?? ''}`"
             :note="sub.rejectReason ? `사유 ${sub.rejectCode} · ${sub.rejectReason}` : '확정된 데이터만 대시보드 집계와 완제품 내재배출량 계산에 반영됩니다'">
    <button v-if="sub.status === '반려'" class="btn" @click="router.push(`/feedback/${sub.id}`)">안내문 초안 보기</button>
    <button v-else class="btn" @click="router.push('/')">관제로</button>
  </ActionBar>
  <ActionBar v-else-if="missing" title="검토 대기 목록에서 다시 고르세요." note="주소의 제출 건 id 가 없는 값입니다">
    <button class="btn" @click="router.push('/review')">검토 대기로</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
