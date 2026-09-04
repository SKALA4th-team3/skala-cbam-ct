<script setup>
import { computed, onMounted, ref } from 'vue'
import { REDUCE } from '@/composables/motion'

/** 도넛. 링 두께는 width 하나로 잡고, 분절 사이는 4 만큼 띄워 색이 붙지 않게 한다.
 *  조각에 손을 올리면 그 조각만 남고 나머지가 흐려진다 — 범례와 양방향으로 물린다. */
const props = defineProps({
  parts: Array,                 // [[값, 톤, 라벨], …]
  total: Number,
  width: { type: Number, default: 15 },
  lit: String,                  // 밖에서(범례에서) 비추는 톤
})
const emit = defineEmits(['lit'])

const R = 48, C = 2 * Math.PI * R
const arcs = computed(() => {
  let off = 0
  return props.parts.map(([n, tone, label]) => {
    const len = C * n / props.total - 4
    const a = { len: len.toFixed(1), gap: (C - len).toFixed(1), off: (-off).toFixed(1), tone, label, n }
    off += C * n / props.total
    return a
  })
})

/* 호를 0에서 자라게 한다. GSAP DrawSVGPlugin 이 하는 일이 stroke-dashoffset 한 값이라
   이 하나 때문에 라이브러리를 넣을 이유는 없다.
   dashoffset 은 조각 위치를 잡는 데 이미 쓰이므로, 자라는 건 dasharray 로 준다. */
const grown = ref(REDUCE)
onMounted(() => requestAnimationFrame(() => requestAnimationFrame(() => (grown.value = true))))

const hover = ref(null)
const active = computed(() => props.lit ?? hover.value)
function set(tone) { hover.value = tone; emit('lit', tone) }
</script>

<template>
  <svg class="donut" :class="{ dim: !!active }" viewBox="0 0 120 120" role="img"
       :aria-label="parts.map(p => p[2] + ' ' + p[0]).join(', ')">
    <circle cx="60" cy="60" :r="R" fill="none" stroke="var(--hair)" :stroke-width="width" />

    <circle v-for="(a, i) in arcs" :key="'v' + i" class="seg" :class="{ lit: active === a.tone }"
            cx="60" cy="60" :r="R" fill="none"
            :stroke="`var(--${a.tone})`" :stroke-width="width"
            :stroke-dasharray="grown ? `${a.len} ${a.gap}` : `0 ${C}`"
            :stroke-dashoffset="a.off"
            :style="{ transitionDelay: `${i * 110}ms` }"
            transform="rotate(-90 60 60)" />

    <!-- 보이는 호는 15px 라 손으로 잡기에 좁다. 같은 자리에 투명 호를 겹쳐 잡는 면을 넓힌다 -->
    <circle v-for="(a, i) in arcs" :key="'h' + i" class="hit"
            cx="60" cy="60" :r="R" fill="none"
            :stroke-width="width + 12"
            :stroke-dasharray="`${a.len} ${a.gap}`" :stroke-dashoffset="a.off"
            transform="rotate(-90 60 60)"
            @mouseenter="set(a.tone)" @mouseleave="set(null)">
      <title>{{ a.label }} {{ a.n }}</title>
    </circle>
  </svg>
</template>
