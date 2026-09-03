<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Feedback } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import { useUi } from '@/stores/ui'

const router = useRouter(); const ui = useUi()
const tone = ref('격식')
const body = ref([])
const loading = ref(false)
const TONES = ['격식', '간결', '친근']
async function load() {
  loading.value = true
  try { body.value = (await Feedback.draft('sub-1', tone.value)).body }
  catch (e) { ui.say(`${e.status ?? ''} ${e.code ?? e.message}`) }
  finally { loading.value = false }
}
onMounted(load)
/* 문체를 빠르게 두 번 누르면 늦게 온 응답이 먼저 온 것을 덮었다. 도는 동안 잠근다 */
async function pick(t) {
  if (loading.value || t === tone.value) return
  tone.value = t; ui.say('문체 · ' + t)
  await load()
}
</script>

<template>
  <ViewHead api="UC-10 · 피드백 초안 · POST /feedback-drafts" back="검토" backTo="/review">
    <template #title>성진스틸에 보낼 안내문을 만들었습니다.</template>
    <template #lede>판정 사유(R2 · R5)를 근거로 씁니다. 없는 값을 채우자고 요구하지 않고, 무엇이 왜 비었는지만 적습니다.</template>
  </ViewHead>

  <div class="filters stage" style="--d:100ms">
    <span style="font-size:12.5px;color:var(--muted);margin-right:4px">문체</span>
    <button v-for="t in TONES" :key="t" :class="{ on: tone === t }" :disabled="loading" @click="pick(t)">{{ t }}</button>
  </div>

  <div class="letter stage" style="--d:160ms">
    <p v-for="(para, i) in body" :key="i"
       :class="{ list: para.startsWith('-') || para.startsWith('·'), shim: loading }">{{ para }}</p>
  </div>

  <ActionBar title="확정하면 수신자 · 제목 · 본문이 잠깁니다."
             note="왼쪽은 근거(판정 사유 · missingFields · 마감), 오른쪽은 사람이 고칠 수 있는 문안">
    <button class="quiet" @click="ui.say('안내문을 클립보드에 복사했습니다')">복사</button>
    <button class="btn" @click="Feedback.confirm('fb-1').then(() => { ui.say('확정했습니다 · 발송 대기로 넘어갔습니다'); router.push('/feedback/dispatch') })">확정하고 발송 관리로</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
