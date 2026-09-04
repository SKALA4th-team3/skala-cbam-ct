<script setup>
import { computed } from 'vue'
/** 평균값 대비 실측값.
 *  허용 범위(±30%)를 회색 띠 하나로만 두면 배경과 구별되지 않아 안인지 밖인지 판정이 안 된다.
 *  사선 패턴 + 안쪽 브래킷으로 «구간»이라고 말하고, 값 자체를 숫자로 띄운다.
 *
 *  ⚠️ ratio 가 없으면 그리지 않는다 — 0 으로 두면 「평균의 0배」라는 없는 사실을 그리게 된다.
 *     명세 24번과 같은 규칙이다: 모르면 채우지 말고 비운다. */
const props = defineProps({ ratio: Number, band: { type: Number, default: 0.3 } })

const has = computed(() => typeof props.ratio === 'number' && Number.isFinite(props.ratio))
const over = computed(() => has.value && Math.abs(props.ratio - 1) > props.band)
const width = computed(() =>
  (has.value ? Math.min(100, Math.max(0, props.ratio / 2 * 100)) : 0).toFixed(1) + '%')
</script>

<template>
  <div v-if="has" class="mbul" :class="{ out: over }"
       :title="`평균값 대비 ${ratio.toFixed(2)}배 · 허용 ${(1 - band).toFixed(1)}–${(1 + band).toFixed(1)}`">
    <i class="band"></i>
    <i class="brk l"></i><i class="brk r"></i>
    <i class="val" :class="{ over }" :style="{ width }"></i>
    <i class="avg"></i>
    <b class="flag" :style="{ left: width }">{{ ratio.toFixed(2) }}</b>
  </div>
  <div v-else class="mbul none" title="평균값 대비 실측값이 아직 없습니다">
    <i class="band"></i><span>—</span>
  </div>
</template>
