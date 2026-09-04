<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
/** 41번 — 확정 데이터 기준 완제품별 배출량 합계. «미확정 부품이 포함된 완제품은 따로 표시».
 *  합계를 모르는 제품은 빗금으로 끝을 열어 둔다 — 값이 없다는 뜻이지 0 이 아니다 (24번). */
const props = defineProps({ rows: { type: Array, default: () => [] } })
const router = useRouter()
const max = computed(() => Math.max(1, ...props.rows.map(r => r.confirmed)))
const w = r => Math.max(2, Math.round(r.confirmed / max.value * 100))
const fmt = n => n == null ? '—' : n.toLocaleString('ko-KR', { maximumFractionDigits: 0 })
</script>

<template>
  <div class="ebars">
    <button v-for="r in rows" :key="r.id" class="eb-row" :class="{ open: !r.reportable }"
            :title="r.reportable ? `${r.name} · 확정 ${fmt(r.total)} tCO₂e` : `${r.name} · 미확정 부품 ${r.pendingCount}개 — 합계를 낼 수 없습니다`"
            @click="router.push(`/products/${r.id}/report`)">
      <span class="eb-name">{{ r.name }}</span>
      <span class="eb-bar">
        <i class="eb-seg" :style="{ width: w(r) + '%' }"></i>
        <i v-if="!r.reportable" class="eb-unk" :style="{ left: w(r) + '%' }"></i>
      </span>
      <span class="eb-val"><b>{{ fmt(r.confirmed) }}</b><small>{{ r.reportable ? ' tCO₂e' : ` + 미확정 ${r.pendingCount}` }}</small></span>
    </button>
  </div>
</template>
