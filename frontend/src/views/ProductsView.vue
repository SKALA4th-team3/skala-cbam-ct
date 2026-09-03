<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Products, allRows } from '@/api'
import { useTable } from '@/composables/useTable'
import DataToolbar from '@/components/DataToolbar.vue'
import ViewHead from '@/components/ViewHead.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import MeanBullet from '@/components/MeanBullet.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const rows = ref([])
onMounted(async () => { rows.value = allRows(await Products.list({ size: 1000 }), 'GET /products') })
const facets = [{ key: 'cn', label: 'CN 코드', field: 'cnGroup' }]
const t = useTable(rows, { search: 'name', facets })
</script>

<template>
  <ViewHead kicker="EU 수출 · 로테르담 · 함부르크">
    <template #title>완제품 {{ rows.length }}종</template>
    <template #lede>배출 원단위를 동일 품목 평균값과 견줍니다. 허용 범위 ± 30%를 벗어나면 부적격입니다.</template>
    <template #acts>
      <button class="quiet" @click="router.push('/products/hr-2400/bom')">
        <svg class="i" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></svg>제품 · BOM 편집
      </button>
    </template>
  </ViewHead>

  <DataToolbar :table="t" :facets="facets" placeholder="제품명 검색" unit="종" />

  <div class="list stage" style="--d:180ms">
    <div v-for="p in t.filtered" :key="p.id" v-clickable class="row pr link"
         :aria-label="`${p.name} 신고 가능 여부`" @click="router.push(`/products/${p.id}/report`)">
      <div class="n"><b>{{ p.name }}</b><span>CN {{ p.cn }} · {{ p.tons.toLocaleString() }} t · 부품 {{ p.partCount }}</span></div>
      <MeanBullet :ratio="p.ratio" />
      <span class="num">{{ p.actual.toLocaleString() }}</span>
      <StatusBadge :value="p.judgement" />
    </div>
    <EmptyState v-if="!t.filtered.length" title="조건에 맞는 항목이 없습니다."
      note="데이터가 없는 게 아니라 필터에 걸린 것이 없다는 뜻입니다."
      action="필터 해제" @action="t.clearAll()" />
  </div>
  <div class="after">
    <a href="#" @click.prevent="router.push('/products/hr-2400/report')">신고 리포트 미리보기</a>
    <!-- 이 화면의 지표는 MeanBullet 이다. 앞 화면의 12개월 스트립 legend 를 복사해 색이 뜻과 어긋나 있었다 -->
    <span class="legend mb">평균값 대비 실측값 · 회색 띠가 허용 범위 ± 30%</span>
  </div>
  <div class="spacer"></div>
</template>
