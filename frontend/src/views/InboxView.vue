<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Inbox } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useUi } from '@/stores/ui'

const router = useRouter(); const ui = useUi()
const items = ref([])
onMounted(async () => { items.value = (await Inbox.list()).items })

async function assign(m) {
  await Inbox.assign(m.id, '대한화학')
  ui.say('미확인 건을 대한화학으로 지정했습니다')
  items.value = (await Inbox.list()).items
}
</script>

<template>
  <ViewHead api="UC-04 · 이메일 접수 · 지정 메일함 1분 폴링">
    <template #title>오늘 {{ items.length }}건이 들어왔습니다.</template>
    <template #lede>협력사가 보낸 메일을 그대로 받습니다. 사람이 다시 옮겨 적지 않습니다.</template>
  </ViewHead>

  <div class="alerts stage" style="--d:160ms">
    <div v-for="m in items" :key="m.id" class="at" :class="{ link: m.supplier }"
         @click="m.supplier ? router.push(`/submissions/${m.id}`) : assign(m)">
      <span class="rule">{{ m.at }}</span>
      <div>
        <b>{{ m.supplier ?? '미확인 발신자' }}</b>
        <span class="sub">{{ m.from }} · {{ m.subject }}</span>
      </div>
      <span class="why">{{ m.files }}</span>
      <StatusChip :label="m.state" :tone="m.tone" />
      <span class="ago">{{ m.supplier ? '' : '지정' }}</span>
    </div>
  </div>

  <ActionBar title="첨부만 떼어내고 원문은 그대로 보관합니다"
             note="읽을 수 없는 값은 추정하지 않습니다 (NFR-04)">
    <button class="btn" @click="router.push('/submissions/sub-1')">검토 대기로</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
