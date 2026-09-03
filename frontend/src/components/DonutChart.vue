<script setup>
import { computed } from 'vue'
/** 도넛. 링 두께는 DW 하나로 잡고, 분절 사이는 4 만큼 띄워 색이 붙지 않게 한다. */
const props = defineProps({ parts: Array, total: Number, width: { type: Number, default: 15 } })
const R = 48, C = 2 * Math.PI * R
const arcs = computed(() => {
  let off = 0
  return props.parts.map(([n, tone]) => {
    const len = C * n / props.total - 4
    const a = { len: len.toFixed(1), gap: (C - len).toFixed(1), off: (-off).toFixed(1), tone }
    off += C * n / props.total
    return a
  })
})
</script>
<template>
  <svg class="donut" viewBox="0 0 120 120" role="img"
       :aria-label="parts.map(p => p[2] + ' ' + p[0]).join(', ')">
    <circle cx="60" cy="60" :r="R" fill="none" stroke="var(--hair)" :stroke-width="width" />
    <circle v-for="(a, i) in arcs" :key="i" cx="60" cy="60" :r="R" fill="none"
            :stroke="`var(--${a.tone})`" :stroke-width="width"
            :stroke-dasharray="`${a.len} ${a.gap}`" :stroke-dashoffset="a.off"
            transform="rotate(-90 60 60)" />
  </svg>
</template>
