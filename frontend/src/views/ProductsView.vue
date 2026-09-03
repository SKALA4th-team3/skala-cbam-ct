<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Products, allRows } from '@/api'
import { useTable } from '@/composables/useTable'
import { useFlip } from '@/composables/useFlip'
import DataToolbar from '@/components/DataToolbar.vue'
import ViewHead from '@/components/ViewHead.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import MeanBullet from '@/components/MeanBullet.vue'
import EmptyState from '@/components/EmptyState.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import ProductForm from '@/components/ProductForm.vue'

const router = useRouter()
const rows = ref([]); const loading = ref(true)
async function load() { rows.value = allRows(await Products.list({ size: 1000 }), 'GET /products'); loading.value = false }
onMounted(load)
/* 14번 — 제품명 검색 · CN코드 필터 */
const facets = [{ key: 'cn', label: 'CN 코드', field: 'cnGroup' }]
const t = useTable(rows, { search: 'name', facets, sync: true })
const list = ref(null)
useFlip(list, () => t.filtered)
const adding = ref(false)
</script>

<template>
  <ViewHead kicker="EU 수출 · 로테르담 · 함부르크">
    <template #title>완제품 {{ rows.length }}종</template>
    <template #lede>배출 원단위를 동일 품목 평균값과 견줍니다. 허용 범위 ± 30%를 벗어나면 부적격입니다. 평균값은 명세대로 하드코딩입니다 (14번).</template>
    <template #acts>
      <button class="quiet" @click="adding = true">
        <svg class="i" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></svg>완제품 등록
      </button>
    </template>
  </ViewHead>

  <DataToolbar :table="t" :facets="facets" placeholder="제품명 검색" unit="종" />

  <div class="list" ref="list" v-reveal>
    <SkeletonRows v-if="loading" :rows="3" :cols="['1fr', '200px', '110px', '92px']" />
    <div v-for="p in t.filtered" :key="p.id" :data-flip="p.id" v-clickable class="row pr link"
         :aria-label="`${p.name} 신고 가능 여부`" @click="router.push(`/products/${p.id}/report`)">
      <div class="n"><b>{{ p.name }}<em v-if="!p.reportable" class="cut">신고 불가</em></b>
        <span>CN {{ p.cn }} · {{ p.euCountry }} · {{ p.tons.toLocaleString() }} t · 부품 {{ p.partCount }}</span></div>
      <MeanBullet :ratio="p.ratio" />
      <span class="num">{{ p.actual == null ? '—' : p.actual.toLocaleString() }}<small class="mean"> / 평균 {{ p.mean == null ? '—' : p.mean.toLocaleString() }}</small></span>
      <StatusBadge :value="p.judgement" />
    </div>
    <EmptyState v-if="!loading && !t.filtered.length" title="조건에 맞는 항목이 없습니다."
      note="데이터가 없는 게 아니라 필터에 걸린 것이 없다는 뜻입니다." action="필터 해제" @action="t.clearAll()" />
  </div>
  <div class="after">
    <a href="#" @click.prevent="router.push('/parts')">부품 벤치마크 확인하기</a>
    <span class="legend mb">평균값 대비 실측값 · 빗금 구간이 허용 범위 ± 30%, 흰 선이 평균값 · 행을 누르면 BOM 편집과 신고 가능 여부</span>
  </div>
  <ProductForm :open="adding" @close="adding = false" @created="load" />
  <div class="spacer"></div>
</template>
