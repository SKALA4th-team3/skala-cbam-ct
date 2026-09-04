<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Products } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import MeanBullet from '@/components/MeanBullet.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'

const route = useRoute(); const router = useRouter()
const e = ref(null); const missing = ref(null)
onMounted(async () => { try { e.value = await Products.get(route.params.id) } catch (x) { missing.value = x } })
/* 15번 — 확정분만 합계다. 미확정 구간은 «값이 없다» 이지 0 이 아니다 */
const known = computed(() => e.value ? e.value.parts.filter(r => r.contribution != null) : [])
const maxC = computed(() => Math.max(1, ...known.value.map(r => r.contribution)))
const pctOf = r => Math.round(r.contribution / (e.value.confirmed || 1) * 100)
const fmt = n => n == null ? '—' : n.toLocaleString('ko-KR', { maximumFractionDigits: 1 })
const blockedSupplier = computed(() => e.value?.parts.find(r => r.contribution == null))
</script>

<template>
  <ViewHead v-if="missing" kicker="없는 완제품" back="제품" backTo="/products">
    <template #title>없는 완제품입니다.</template>
    <template #lede>{{ missing.message }} — <code>{{ route.params.id }}</code></template>
  </ViewHead>
  <SkeletonRows v-else-if="!e" :rows="4" />
  <template v-else>
    <ViewHead api="UC-03 · 완제품 · GET /products/{productId}" back="제품" backTo="/products">
      <template #title>{{ e.name }}{{ e.reportable ? ' 은 신고할 수 있습니다.' : ' 은 아직 신고할 수 없습니다.' }}</template>
      <template #lede>CN {{ e.cn }} · {{ e.euCountry }} · 연간 {{ e.tons.toLocaleString() }} t · 부품 {{ e.partCount }}개
        — {{ e.reportable ? '구성 부품이 전부 확정입니다.' : '부품 하나라도 확정되지 않으면 내재배출량이 확정되지 않습니다. 빗금은 아직 값이 없다는 뜻입니다.' }}</template>
      <template #acts><button class="quiet sm" @click="router.push(`/products/${e.id}/bom`)">BOM 편집</button></template>
    </ViewHead>

    <div class="minis stage" style="--d:120ms">
      <div><div class="cap">내재 배출량 합계</div><b :class="{ nul: e.total == null }">{{ e.total == null ? '값 없음' : fmt(e.total) }}</b><span>tCO₂e{{ e.total == null ? ' · 미확정 부품이 있어 합계를 내지 않습니다' : ' · 확정' }}</span></div>
      <div><div class="cap">확정분</div><b>{{ fmt(e.confirmed) }}</b><span>tCO₂e · 부품 {{ known.length }}개</span></div>
      <div><div class="cap">미확정</div><b :class="{ nul: e.pendingCount }">{{ e.pendingCount }}</b><span>{{ e.pendingCount ? '부품 대기 · 값을 지어내지 않습니다' : '없음' }}</span></div>
    </div>

    <!-- 평균값 대비 실측값 (14번 ⑤) — 목록과 같은 지표를 상세에서도 보여준다 -->
    <div class="stage compare" style="--d:150ms">
      <div class="cap">평균값 대비 실측값</div>
      <div class="cmp-row"><MeanBullet :ratio="e.ratio" /><span class="num">실측 {{ fmt(e.actual) }} / 평균 {{ fmt(e.mean) }}</span>
        <StatusChip :label="e.judgement" :tone="e.judgement === '적격' ? 'complete' : 'reject'" /></div>
    </div>

    <!-- 구성 막대 — 확정분은 채우고, 미확정은 빗금으로 끝을 연다 -->
    <div class="stage" style="--d:170ms;padding:22px 0 6px">
      <div class="cbar comp">
        <i v-for="r in known" :key="r.part" class="k" :style="{ width: (r.contribution / (e.confirmed || 1) * (e.reportable ? 100 : 78)) + '%' }" :title="`${r.part} ${fmt(r.contribution)}`"></i>
        <i v-if="!e.reportable" class="hatch u" style="flex:1"></i>
      </div>
      <div class="note" style="margin-top:10px">
        {{ e.reportable ? '구성 부품 전부 확정 — 막대가 곧 합계입니다' : `확정분 ${fmt(e.confirmed)} tCO₂e + 미확정 ${e.pendingCount}개 — 빗금 구간은 값이 없다는 뜻이지 0 이 아닙니다` }}
      </div>
    </div>

    <div class="bom stage" style="--d:200ms">
      <div class="h"><span>부품명</span><span>공급 협력사</span><span>투입량 t/t</span><span>상태</span><span>기여량 tCO₂e</span></div>
      <div v-for="r in e.parts" :key="r.part" v-clickable class="bt link" :aria-label="`${r.part} 상세`" @click="r.partId ? router.push(`/parts/${r.partId}`) : router.push('/parts')">
        <b>{{ r.part }}</b>
        <div class="cell">{{ r.supplier ?? '—' }}</div>
        <div class="cell mono">{{ r.input.toFixed(2) }}</div>
        <StatusChip :label="r.state" :tone="r.state === '확정' ? 'complete' : 'missing'" />
        <div class="cell mono contrib">
          <template v-if="r.contribution != null"><i class="inbar"><i :style="{ width: (r.contribution / maxC * 100) + '%' }"></i></i>{{ fmt(r.contribution) }}<small>{{ pctOf(r) }}%</small></template>
          <template v-else><span class="nul">값 없음</span><small>{{ r.factor == null ? '벤치마크 미등록' : '부품 미등록' }}</small></template>
        </div>
      </div>
    </div>

    <ActionBar :title="e.reportable ? '신고를 막는 항목이 없습니다.' : `신고를 막는 항목 ${e.blocking.length}개`" :note="e.reportable ? '확정 데이터만 집계에 반영됩니다 (31번)' : e.blocking.join(' · ')">
      <button v-if="!e.reportable && blockedSupplier?.partId" class="quiet" @click="router.push(`/parts/${blockedSupplier.partId}`)">벤치마크 등록하기</button>
      <button v-if="!e.reportable && blockedSupplier?.supplierId" class="btn" @click="router.push(`/suppliers/${blockedSupplier.supplierId}`)">{{ blockedSupplier.supplier }} 상세</button>
      <button v-else class="btn" @click="router.push('/')">관제로</button>
    </ActionBar>
  </template>
  <div class="spacer"></div>
</template>
