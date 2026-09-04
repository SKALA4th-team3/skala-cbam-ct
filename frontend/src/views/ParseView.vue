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
const state = ref('run')        // run · done · err · missing
const step = ref(1)
const error = ref(null)
let timer = null
const stop = () => { clearInterval(timer); timer = null }

onMounted(async () => {
  try { sub.value = await Analysis.get(route.params.id) }
  catch (e) { state.value = 'missing'; error.value = e; return }
  /* 분석은 접수·수동 매칭 직후 자동 실행된다(20번). 화면이 실행을 요청하는 API 는 없다.
     상세가 준 latestAnalysisTaskId 로 №19를 폴링만 한다. */
  const { latestAnalysisTaskId: taskId, pollAfterMs } = sub.value
  timer = setInterval(async () => {
    try {
      const t = await Analysis.task(taskId)
      if (t.status === 'PROCESSING') step.value = Math.min(4, step.value + 1)
      if (t.status === 'COMPLETED') { state.value = 'done'; step.value = 5; stop()
        ui.say(`변환 완료 · 항목 ${rows.value.length}개 중 ${flagged.value}개는 사람 확인 대기`) }
      if (t.status === 'FAILED') {
        /* №19 는 작업이 실패해도 200 이다 — 실패는 status·errorCode 로 말한다 */
        state.value = 'err'
        error.value = { code: t.errorCode, message: t.errorMessage ?? '분석에 실패했습니다' }
        stop(); ui.say('분석 실패 · ' + error.value.message)
      }
    } catch (e) {
      /* 폴링이 던지면(작업이 사라진 404 등) 조용히 실패하면서 interval 은 계속 돈다.
         읽지 못한 것을 「읽는 중」으로 두지 않는다 — 멈추고 그렇다고 말한다 (22번 「분석 실패」). */
      state.value = 'err'; error.value = { message: e.message }; stop()
      ui.say(`분석 상태를 확인할 수 없습니다 · ${e.status ?? ''} ${e.code ?? e.message}`)
    }
  }, pollAfterMs)
})
onBeforeUnmount(stop)

const rows = computed(() => sub.value?.rows ?? [])
const flagged = computed(() => rows.value.filter(r => r.tone !== 'complete').length)
const rawOnly = ref(false)
const canPass = computed(() => state.value === 'done')
const files = computed(() => sub.value?.attachments ?? [])
const received = computed(() => sub.value?.receivedAt?.replace('T', ' ').slice(0, 16) ?? '')
</script>

<template>
  <ViewHead api="UC-05 · AI 분석 · GET /submissions/{submissionId} → GET /tasks/{taskId}"
            back="접수함" backTo="/inbox">
    <template #title>
      <template v-if="state === 'missing'">없는 제출 건입니다.</template>
      <template v-else-if="state === 'done'">{{ sub?.supplier }} {{ sub?.period }} 자료를 읽었습니다.</template>
      <template v-else-if="state === 'err'">{{ sub?.supplier }} 자료를 읽지 못했습니다.</template>
      <template v-else>{{ sub?.supplier ?? '' }} {{ sub?.period ?? '' }} 제출자료를 읽는 중입니다.</template>
    </template>
    <template #lede>
      <template v-if="state === 'missing'">{{ error?.message }} — <code>{{ route.params.id }}</code></template>
      <template v-else>원문 {{ files.length }}개 파일 · 접수 {{ received }} · revision {{ sub?.revision ?? 1 }}</template>
    </template>
  </ViewHead>

  <div v-if="state !== 'missing'" class="steps stage" style="--d:140ms">
    <div v-for="(s, i) in sub?.steps ?? []" :key="s" class="st"
         :class="{ done: i < step || state === 'done', now: i === step && state === 'run', fail: state === 'err' && i === step }">
      <i></i><div class="l"><b>{{ s }}</b><em v-if="i === step && state === 'run'" class="shim">읽는 중</em><em v-else-if="state === 'err' && i === step" class="bad">실패</em></div>
    </div>
  </div>

  <!-- 22번 — 암호·파싱 실패는 「분석 실패」로 기록하고 담당자에게 알린다. 채우지 않는다 -->
  <div v-if="state === 'err'" class="alert stage" style="--d:160ms">
    <span class="ic">!</span>
    <div><b>분석 실패 · {{ error?.code ?? '' }}</b><p>{{ error?.message }} — 읽을 수 없는 값은 추정하지 않습니다. 협력사에 자료를 다시 요청합니다 (R3 · AI 재요청문).</p></div>
    <button class="btn go" @click="router.push(`/feedback/${route.params.id}`)">재요청문 만들기</button>
  </div>

  <!-- 30번 — 원본 첨부를 같은 화면에서 연다 -->
  <div v-if="files.length" class="mv-files inpage stage" style="--d:180ms">
    <span v-for="f in files" :key="f.name" class="mv-file"><b>{{ f.name }}</b><em>{{ f.size }}</em></span>
    <span class="mv-note">항목마다 추출 근거(원문 위치)를 함께 저장합니다 (23번) — 표 오른쪽 위치가 그것입니다</span>
  </div>

  <RawStdPair v-if="rows.length" :rows="rows" :class="{ 'raw-only': rawOnly }" />

  <ActionBar v-if="state !== 'missing'"
             :title="state === 'done' ? `항목 ${rows.length}개 · 사람 확인 대기 ${flagged}개 — revision ${sub?.revision ?? 1}, 이전 변환본은 is_current=false 로 남습니다.` : state === 'err' ? '표준 데이터가 없습니다 — 검토로 넘길 것이 없습니다.' : '변환이 끝나면 검토로 넘길 수 있습니다.'"
             note="읽을 수 없는 값은 추정하지 않습니다 (24번) · 변환 전 원본 값을 함께 보존합니다">
    <button class="quiet" :disabled="!rows.length" @click="rawOnly = !rawOnly">{{ rawOnly ? '표준 데이터 보기' : '원문 보기' }}</button>
    <button class="btn" :disabled="!canPass" @click="router.push(`/review/${route.params.id}`)">검토로 넘기기</button>
  </ActionBar>
  <ActionBar v-else title="접수함에서 다시 고르세요." note="주소의 제출 건 id 가 없는 값입니다">
    <button class="btn" @click="router.push('/inbox')">접수함으로</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
