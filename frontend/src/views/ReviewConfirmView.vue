<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Analysis, Review, ApiError } from '@/api'
import { useBoard } from '@/stores/board'
import { useUi } from '@/stores/ui'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'

const route = useRoute(); const router = useRouter()
const board = useBoard(); const ui = useUi()
const sub = ref(null)
const resolved = ref([])
onMounted(async () => { sub.value = await Analysis.get(route.params.id) })

/* 명세 31 — 「판정이 적격이고 미등록 부품이 없는 경우에만 확정할 수 있다」
   막는 조건은 셋이다. 누락만 보면 부적격 건이 그대로 확정된다. */
const missing = computed(() => (sub.value?.missingFields ?? []).filter(f => !resolved.value.includes(f)))
const blockers = computed(() => {
  const s = sub.value
  if (!s) return []
  return [
    ...missing.value.map(f => `${f} 값 누락`),
    ...(s.judgement && s.judgement !== '적격' ? [`판정이 ${s.judgement}`] : []),
    ...(s.unmappedParts ?? []).map(p => `미등록 부품 · ${p}`),
  ]
})

function resolve(field) { resolved.value = [...resolved.value, field]; ui.say(field + ' 값을 채웠습니다') }

async function confirm() {
  try {
    if (blockers.value.length) throw new ApiError(400, 'NOT_CONFIRMABLE', blockers.value.join(' · '))
    await Review.confirm(route.params.id)
    board.applyConfirm()
    ui.say('확정 완료 · 재산정이 돌고 경보 2건이 해소됐습니다')
    router.push('/')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
</script>

<template>
  <ViewHead api="UC-07 · 데이터 검토 · POST /submissions/{submissionId}/confirm"
            back="자료 변환" :backTo="`/submissions/${route.params.id}`">
    <template #title>{{ blockers.length ? `${blockers.length}가지가 남아 확정할 수 없습니다.` : '확정할 준비가 됐습니다.' }}</template>
    <template #lede>확정된 데이터만 대시보드 집계와 완제품 내재배출량 계산에 반영됩니다.</template>
  </ViewHead>

  <div class="conv stage" style="--d:160ms">
    <div class="ch"><span>협력사 원문 · rawText</span><span>표준 데이터</span></div>
    <div v-for="r in sub?.rows ?? []" :key="r.field" class="cv"
         :class="resolved.includes(r.field) ? 'complete' : r.tone">
      <div class="raw">{{ r.raw }}</div>
      <div class="std">
        <span class="k">{{ r.field }}</span>
        <div class="v">
          <b :class="{ nul: r.value === null && !resolved.includes(r.field) }">
            {{ resolved.includes(r.field) ? '4,120' : (r.value ?? 'null') }}
          </b>
          <span v-if="r.unit" class="u">{{ r.unit }}</span>
          <StatusChip :label="resolved.includes(r.field) ? '담당자 입력' : r.note"
                      :tone="resolved.includes(r.field) ? 'complete' : r.tone" />
          <button v-if="missing.includes(r.field)" class="quiet" style="margin-left:8px"
                  @click="resolve(r.field)">값 입력</button>
        </div>
      </div>
    </div>
  </div>

  <ActionBar :title="blockers.length ? blockers.join(' · ') + ' — 확정 버튼이 잠깁니다.' : '확정하면 재산정이 돌고 걸려 있던 판정이 스스로 해소됩니다.'"
             note="화면이 잠가도 서버가 다시 막습니다 (400 MISSING_FIELDS · 409 NOT_ELIGIBLE · 409 UNMAPPED_PARTS)">
    <button class="quiet" @click="Review.reject(route.params.id, 'R2 필수 항목 누락').then(() => { ui.say('반려했습니다 · 사유를 저장했습니다'); router.push('/feedback') })">반려</button>
    <button class="btn" :disabled="blockers.length > 0" @click="confirm">확정하기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
