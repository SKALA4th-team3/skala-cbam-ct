<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Products } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'

const route = useRoute(); const router = useRouter()
const e = ref(null)
/* 라우트가 /products/:id/report 인데 'hr-2400' 이 박혀 있어 어느 제품을 눌러도 같은 화면이었다 */
onMounted(async () => { e.value = await Products.get(route.params.id) })
const pct = computed(() => e.value ? Math.round(e.value.confirmed / e.value.total * 100) : 0)
</script>

<template>
  <ViewHead v-if="e" api="UC-03 · 완제품 · GET /products/{productId}" back="제품" backTo="/products">
    <template #title>{{ e.product }} 은 아직 신고할 수 없습니다.</template>
    <template #lede>부품 하나라도 확정되지 않으면 내재배출량이 확정되지 않습니다. 빗금은 아직 값이 없다는 뜻입니다.</template>
  </ViewHead>

  <div v-if="e" class="minis stage" style="--d:120ms">
    <div><div class="cap">총 내재 배출량</div><b>{{ e.total.toLocaleString() }}</b><span>tCO₂e · 잠정</span></div>
    <div><div class="cap">확정분</div><b>{{ e.confirmed.toLocaleString() }}</b><span>{{ pct }}%</span></div>
    <div><div class="cap">미확정분</div><b>{{ e.pending.toLocaleString() }}</b><span>부품 1개 대기</span></div>
  </div>

  <div v-if="e" class="stage" style="--d:170ms;padding:22px 0 6px">
    <div class="cbar">
      <i class="done" :style="{ width: pct + '%' }"></i>
      <i class="hatch" :style="{ width: (100 - pct) + '%' }"></i>
    </div>
    <div class="note" style="margin-top:10px">확정 {{ pct }}% · 미확정 {{ 100 - pct }}% — 미확정 구간은 값이 없다는 뜻이지 0 이 아닙니다</div>
  </div>

  <div v-if="e" class="bom stage" style="--d:200ms">
    <div class="h"><span>부품명</span><span>공급 협력사</span><span>투입량 t/t</span><span>상태</span><span>벤치마크 팩터</span></div>
    <div v-for="row in e.parts" :key="row.name" class="bt">
      <b>{{ row.name }}</b>
      <div class="cell">{{ row.supplier }}</div>
      <div class="cell mono">{{ row.input.toFixed(2) }}</div>
      <StatusChip :label="row.state" :tone="row.state === '확정' ? 'complete' : 'missing'" />
      <div class="cell mono">{{ row.factor ?? '미등록' }}</div>
    </div>
  </div>

  <ActionBar v-if="e" :title="`신고를 막는 항목 ${e.blocking.length}개`" :note="e.blocking.join(' · ')">
    <button class="btn" @click="router.push('/feedback')">해당 협력사에 안내문 보내기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
