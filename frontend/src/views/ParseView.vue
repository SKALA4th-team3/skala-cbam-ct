<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Analysis } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import RawStdPair from '@/components/RawStdPair.vue'
import { useUi } from '@/stores/ui'

const router = useRouter(); const route = useRoute(); const ui = useUi()
const sub = ref(null)
const state = ref('run')        // run · done · err
const step = ref(3)
let timer = null
const stop = () => { clearInterval(timer); timer = null }

onMounted(async () => {
  sub.value = await Analysis.get(route.params.id)
  /* 분석은 접수·수동 매칭 직후 자동 실행된다(요구사항 20). 화면이 실행을 요청하는 API 는 없다.
     상세가 준 latestAnalysisTaskId 로 №19를 폴링만 한다. */
  const { latestAnalysisTaskId: taskId, pollAfterMs } = sub.value
  timer = setInterval(async () => {
    /* 폴링이 던지면(작업이 사라진 404 등) 조용히 실패하면서 interval 은 계속 돈다.
       읽지 못한 것을 「읽는 중」으로 두지 않는다 — 멈추고 그렇다고 말한다 (22번 「분석 실패」). */
    try {
      const t = await Analysis.task(taskId)
      if (t.status === 'PROCESSING') step.value = 4
      if (t.status === 'COMPLETED') { state.value = 'done'; step.value = 5; stop()
        ui.say('변환 완료 · 항목 5개 중 2개는 사람 확인 대기') }
      if (t.status === 'FAILED') { state.value = 'err'; stop(); ui.say('분석 실패 · ' + t.error.message) }
    } catch (e) {
      state.value = 'err'; stop()
      ui.say(`분석 상태를 확인할 수 없습니다 · ${e.status ?? ''} ${e.code ?? e.message}`)
    }
  }, pollAfterMs)
})
onBeforeUnmount(stop)

const rawOnly = ref(false)
const canPass = computed(() => state.value === 'done')
</script>

<template>
  <ViewHead api="UC-05 · AI 분석 · GET /submissions/{submissionId} → GET /tasks/{taskId}"
            back="접수함" backTo="/inbox">
    <template #title>
      {{ state === 'done' ? `${sub?.supplier} ${sub?.period} 자료를 읽었습니다.`
        : state === 'err' ? '자료를 읽지 못했습니다.'
        : `${sub?.supplier ?? ''} ${sub?.period ?? ''} 제출자료를 읽는 중입니다.` }}
    </template>
    <template #lede>원문 3개 파일 · sourceLanguage: ko · 접수 2026-09-02 14:21</template>
  </ViewHead>

  <div class="steps stage" style="--d:140ms">
    <div v-for="(s, i) in sub?.steps ?? []" :key="s" class="st"
         :class="{ done: i < step, now: i === step && state === 'run' }">
      <i></i><div class="l"><b>{{ s }}</b><em v-if="i === step && state === 'run'">진행 중</em></div>
    </div>
  </div>

  <RawStdPair :rows="sub?.rows ?? []" :class="{ 'raw-only': rawOnly }" />

  <ActionBar title="revision 2 · 이전 변환본은 is_current=false 로 남습니다."
             note="읽을 수 없는 값은 추정하지 않습니다 (NFR-04)">
    <button class="quiet" @click="rawOnly = !rawOnly">{{ rawOnly ? '표준 데이터 보기' : '원문 보기' }}</button>
    <button class="btn" :disabled="!canPass" @click="router.push(`/review/${route.params.id}`)">검토로 넘기기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
