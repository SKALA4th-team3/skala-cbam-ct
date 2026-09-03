<script setup>
import { onMounted, ref, computed } from 'vue'
import { Parts } from '@/api'
import { useTable } from '@/composables/useTable'
import DataToolbar from '@/components/DataToolbar.vue'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUi } from '@/stores/ui'
import { useRouter } from 'vue-router'

const ui = useUi(); const router = useRouter()
const rows = ref([])
onMounted(async () => { rows.value = (await Parts.list({ size: 1000 })).content })
const facets = [
  { key: 'supplier', label: '공급 협력업체', field: 'supplier' },
  { key: 'cn', label: 'CN 코드', field: 'cnGroup' },
]
const t = useTable(rows, { search: 'name', facets })
const gaps = computed(() => rows.value.filter(p => !p.factor).length)
</script>

<template>
  <ViewHead api="UC-02 · 부품 · GET /parts">
    <template #title>부품 {{ rows.length }}개</template>
    <template #lede>부품명이 키입니다. CN 코드는 8자리 숫자로 검증하고, 단위는 kg · ton · EA 중에서 고릅니다.</template>
    <template #acts>
      <!-- 11번 엑셀 일괄 등록은 우선순위가 낮아 보류다 (이슈 #15). 버튼만 두고 하는 일을 숨기지 않는다 -->
      <button class="quiet" @click="ui.say('엑셀 일괄 등록(11번)은 아직 준비 중입니다 — 부품 등록으로 한 건씩 넣습니다')">엑셀 일괄 등록</button>
      <button class="quiet" @click="ui.say('부품 등록은 부품명 · CN 코드 · 공급 협력업체 · 단위를 받습니다')">
        <svg class="i" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></svg>부품 등록
      </button>
    </template>
  </ViewHead>

  <DataToolbar :table="t" :facets="facets" placeholder="부품명 검색" unit="개" />

  <div class="parts stage" style="--d:180ms">
    <div class="h"><span>부품명</span><span>CN 코드</span><span>공급 협력업체</span><span>벤치마크 팩터 · 단위</span></div>
    <div v-for="p in t.filtered.value" :key="p.name" class="pt link" :class="{ gap: !p.factor }"
         @click="router.push('/suppliers/1')">
      <b>{{ p.name }}</b>
      <span class="sup">{{ p.cn }}</span>
      <span class="val">{{ p.supplier }}</span>
      <StatusChip v-if="p.factor" :label="p.factor" tone="complete" />
      <StatusChip v-else label="벤치마크 미등록" tone="missing" />
    </div>
    <EmptyState v-if="!t.filtered.value.length" title="조건에 맞는 항목이 없습니다."
      note="데이터가 없는 게 아니라 필터에 걸린 것이 없다는 뜻입니다."
      action="필터 해제" @action="t.clearAll()" />
  </div>

  <ActionBar :title="`부품 ${rows.length}개 중 벤치마크 미등록 ${gaps}개`"
             note="벤치마크 팩터는 평균값 비교(± 30%)의 기준이 됩니다">
    <button class="btn" @click="router.push('/products/hr-2400/bom')">완제품 BOM 에서 쓰기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
