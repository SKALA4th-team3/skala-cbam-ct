<script setup>
import { computed } from 'vue'
/** 40번 — 월별 적격/부적격/미제출 비율. 열 하나가 한 달, 세 색이 쌓인다 (bklit Heatmap 의 축소판).
 *  현재 달이 기본 선택이고, 누르면 그 달의 비율과 심각도 건수가 옆에 뜬다. */
const props = defineProps({ months: { type: Array, default: () => [] }, picked: String })
const emit = defineEmits(['pick'])
const max = computed(() => Math.max(1, ...props.months.map(m => m.적격 + m.부적격 + m.미제출)))
const h = (m, k) => Math.round((m[k] / max.value) * 100)
const mm = s => String(Number(s.slice(5)))
</script>

<template>
  <div class="mheat" role="group" aria-label="월별 판정 현황">
    <button v-for="m in months" :key="m.month" class="mh-col" :class="{ on: picked === m.month }"
            :title="`${m.month} · 적격 ${m.적격} · 부적격 ${m.부적격} · 미제출 ${m.미제출}`" @click="emit('pick', m.month)">
      <span class="mh-bar">
        <i class="mh-seg s2" :style="{ height: h(m, '미제출') + '%' }"></i>
        <i class="mh-seg s1" :style="{ height: h(m, '부적격') + '%' }"></i>
        <i class="mh-seg s0" :style="{ height: h(m, '적격') + '%' }"></i>
      </span>
      <span class="mh-lbl">{{ mm(m.month) }}</span>
    </button>
  </div>
</template>
