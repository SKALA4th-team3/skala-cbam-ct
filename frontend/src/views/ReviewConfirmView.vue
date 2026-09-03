<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Analysis, Review, ApiError, SEVERITIES } from '@/api'
import { useBoard } from '@/stores/board'
import { useUi } from '@/stores/ui'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import RawStdPair from '@/components/RawStdPair.vue'

const route = useRoute(); const router = useRouter()
const board = useBoard(); const ui = useUi()
const sub = ref(null)
onMounted(async () => { sub.value = await Analysis.get(route.params.id) })

/* 담당자가 누락 값을 직접 채우는 기능은 없다 — 요구사항 29~32번에 없고 API 도 없다.
   화면에만 채워지고 서버의 missingFields 는 그대로여서 확정이 400 으로 막혔다.
   누락 건은 반려(32번) → 피드백 발송(50번)으로 흘려보낸다. 이슈 #14 에서 그렇게 정했다.

   명세 31 — 「판정이 적격이고 미등록 부품이 없는 경우에만 확정할 수 있다」
   막는 조건은 셋이다. 누락만 보면 부적격 건이 그대로 확정된다. */
const missing = computed(() => sub.value?.missingFields ?? [])
const blockers = computed(() => {
  const s = sub.value
  if (!s) return []
  return [
    ...missing.value.map(f => `${f} 값 누락`),
    ...(s.judgement && s.judgement !== '적격' ? [`판정이 ${s.judgement}`] : []),
    ...(s.unmappedParts ?? []).map(p => `미등록 부품 · ${p}`),
  ]
})

async function confirm() {
  try {
    if (blockers.value.length) throw new ApiError(400, 'NOT_CONFIRMABLE', blockers.value.join(' · '))
    await Review.confirm(route.params.id)
    board.applyConfirm()
    ui.say('확정 완료 · 재산정이 돌고 경보 2건이 해소됐습니다')
    router.push('/')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}

/* ── 반려 (명세 32번) ────────────────────────────────────────
   「상태를 부적격/미제출로 설정하고 **사유 저장**」.
   전에는 사유가 'R2 필수 항목 누락' 으로 코드에 박혀 있어, 어느 건을 반려해도 같은 사유가 저장됐다.
   그 사유는 피드백 초안(UC-10)이 근거로 읽는 값이라 아무 건이나 같으면 안내문도 같아진다. */
const rejecting = ref(false)
const RESULTS = [
  ['REJECTED', '부적격', '자료는 왔지만 검증을 통과하지 못했다'],
  ['NOT_SUBMITTED', '미제출', '자료 자체를 받지 못한 것으로 되돌린다'],
]
const reasonCode = ref('')
const reasonText = ref('')
const resultStatus = ref('REJECTED')
/** 규칙 코드는 설정 화면(UC-08)이 쓰는 것과 같은 표를 본다 */
const rules = SEVERITIES
/** 이 건이 걸린 규칙을 기본값으로 집어 준다 — 사람이 다시 고를 수 있다 */
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
    ui.say(`반려했습니다 · ${reasonCode.value} 사유를 저장했습니다`)
    router.push('/feedback')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
</script>

<template>
  <ViewHead api="UC-07 · 데이터 검토 · POST /submissions/{submissionId}/confirm"
            back="자료 변환" :backTo="`/submissions/${route.params.id}`">
    <template #title>{{ blockers.length ? `${blockers.length}가지가 남아 확정할 수 없습니다.` : '확정할 준비가 됐습니다.' }}</template>
    <template #lede>확정된 데이터만 대시보드 집계와 완제품 내재배출량 계산에 반영됩니다.</template>
  </ViewHead>

  <RawStdPair :rows="sub?.rows ?? []" delay="160ms" />

  <!-- 반려 사유 — 명세 32번이 「사유 저장」을 요구한다. 비어 있으면 서버가 400 으로 막는다 -->
  <div v-if="rejecting" class="reject stage" style="--d:60ms">
    <div class="subhead"><h3>반려 사유</h3><p>여기 적은 것이 그대로 피드백 초안(UC-10)의 근거가 됩니다.</p></div>

    <div class="rj-row">
      <span class="rj-cap">되돌릴 상태</span>
      <div class="rj-opts">
        <button v-for="[code, label, hint] in RESULTS" :key="code" :title="hint"
                :class="{ on: resultStatus === code }" @click="resultStatus = code">{{ label }}</button>
      </div>
    </div>

    <div class="rj-row">
      <span class="rj-cap">규칙 코드</span>
      <div class="rj-opts">
        <button v-for="r in rules" :key="r.rule" :title="r.desc"
                :class="{ on: reasonCode === r.rule }"
                @click="reasonCode = r.rule; reasonText = `${r.name} — ${blockers.join(' · ') || r.desc}`">
          {{ r.rule }} {{ r.name }}
        </button>
      </div>
    </div>

    <label class="rj-row">
      <span class="rj-cap">사유</span>
      <textarea v-model="reasonText" rows="3"
                placeholder="무엇이 왜 비었는지 적습니다. 없는 값을 채우자고 요구하지 않습니다." />
    </label>
    <p v-if="!canReject" class="rj-warn">규칙 코드와 사유를 모두 채워야 반려할 수 있습니다.</p>
  </div>

  <ActionBar :title="blockers.length ? blockers.join(' · ') + ' — 확정 버튼이 잠깁니다.' : '확정하면 재산정이 돌고 걸려 있던 판정이 스스로 해소됩니다.'"
             note="누락된 값을 담당자가 채우는 기능은 없습니다 — 반려해 피드백으로 보냅니다. 화면이 잠가도 서버가 다시 막습니다 (400 NOT_QUALIFIED · 400 UNREGISTERED_PART_EXISTS)">
    <button v-if="!rejecting" class="quiet" @click="openReject">반려</button>
    <template v-else>
      <button class="quiet" @click="rejecting = false">반려 취소</button>
      <button class="btn warn" :disabled="!canReject" @click="reject">반려 확정</button>
    </template>
    <button v-if="!rejecting" class="btn" :disabled="blockers.length > 0" @click="confirm">확정하기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
