<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Products } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useUi } from '@/stores/ui'

const route = useRoute(); const router = useRouter(); const ui = useUi()
const p = ref(null)
onMounted(async () => { p.value = await Products.get(route.params.id) })
</script>

<template>
  <ViewHead api="UC-03 · 완제품 · GET /products/{productId}" back="제품" backTo="/products">
    <template #title>{{ p?.product }} 의 부품 구성</template>
    <template #lede>부품 세부는 부품명 · 협력사 · 투입량(t/t) · 상태로 이뤄집니다. 누락 여부와 상관없이 모든 협력사를 고를 수 있습니다.</template>
  </ViewHead>

  <div class="bom stage" style="--d:160ms">
    <div class="h"><span>부품명</span><span>공급 협력사</span><span>투입량 t/t</span><span>상태</span><span>벤치마크 팩터</span></div>
    <div v-for="row in p?.parts ?? []" :key="row.name" class="bt link"
         @click="row.state === '미확정' ? router.push('/feedback') : ui.say(row.name + ' 는 확정된 값입니다')">
      <b>{{ row.name }}</b>
      <div class="cell sel">{{ row.supplier }}</div>
      <div class="cell mono">{{ row.input.toFixed(2) }}</div>
      <StatusChip :label="row.state" :tone="row.state === '확정' ? 'complete' : 'missing'" />
      <div class="cell mono">{{ row.factor ?? '미등록' }}</div>
    </div>
    <div class="add" @click="router.push('/parts')">
      <span><svg class="i" viewBox="0 0 24 24" style="width:12px;height:12px"><path d="M12 5v14M5 12h14" /></svg></span>
      부품 추가 — 협력사가 아직 없으면 먼저 등록합니다
    </div>
  </div>

  <ActionBar title="부품 하나라도 미확정이면 신고할 수 없습니다."
             note="복합키는 product_id + part_id 입니다">
    <button class="quiet" @click="router.push('/products/hr-2400/report')">신고 가능 여부 확인</button>
    <button class="btn" @click="ui.say('저장했습니다'); router.push('/products')">저장</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
