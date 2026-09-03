<script setup>
import { computed } from 'vue'
/** 평균값 대비 실측값. 회색 띠가 허용 범위(±30%), 흰 선이 평균값이다. */
const props = defineProps({ ratio: Number })
const over = computed(() => Math.abs(props.ratio - 1) > 0.3)
const width = computed(() => Math.min(100, props.ratio / 2 * 100).toFixed(0) + '%')
</script>
<template>
  <div class="mbul" :title="`평균값 대비 ${ratio.toFixed(2)}배`">
    <i class="band"></i><i class="bar" :class="{ over }" :style="{ width }"></i><i class="avg"></i>
  </div>
</template>
