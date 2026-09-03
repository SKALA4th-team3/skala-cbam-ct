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

/** 명세 31 — 적격이고 미등록 부품이 없을 때만 확정된다 */
const blocking = computed(() => (sub.value?.missingFields ?? []).filter(f => !resolved.value.includes(f)))

function resolve(field) { resolved.value = [...resolved.value, field]; ui.say(field + ' 값을 채웠습니다') }

async function confirm() {
  try {
    if (blocking.value.length) throw new ApiError(400, 'MISSING_FIELDS', '누락 항목이 있어 확정할 수 없습니다')
    await Review.confirm(route.params.id)
    board.applyConfirm()
    ui.say('확정 완료 · 재산정이 돌고 경보 2건이 해소됐습니다')
    router.push('/')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
</script>

<template>
  <ViewHead api="UC-07 · 데이터 검토 · PUT /submissions/{id} → CONFIRMED"
            back="자료 변환" :backTo="`/submissions/${route.params.id}`">
    <template #title>{{ blocking.length ? `${blocking.length}개 항목만 확인하면 확정할 수 있습니다.` : '확정할 준비가 됐습니다.' }}</template>
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
          <button v-if="blocking.includes(r.field)" class="quiet" style="margin-left:8px"
                  @click="resolve(r.field)">값 입력</button>
        </div>
      </div>
    </div>
  </div>

  <ActionBar :title="blocking.length ? '누락이 남아 있으면 확정 버튼이 잠깁니다.' : '확정하면 재산정이 돌고 걸려 있던 판정이 스스로 해소됩니다.'"
             note="강행하면 400 Bad Request 와 missingFields 를 돌려줍니다">
    <button class="quiet" @click="Review.reject(route.params.id, 'R2 필수 항목 누락').then(() => { ui.say('반려했습니다 · 사유를 저장했습니다'); router.push('/feedback') })">반려</button>
    <button class="btn" :disabled="blocking.length > 0" @click="confirm">확정하기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
