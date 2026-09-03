<script setup>
import { computed } from 'vue'
const props = defineProps({ values: Array })
const max = computed(() => Math.max(...props.values))
</script>
<template>
  <div class="spark" style="justify-content:center;margin:12px 0 6px" aria-label="최근 6개월 추이">
    <i v-for="(v, i) in values" :key="i"
       :class="{ on: i === values.length - 1 }"
       :style="{ height: Math.round(8 + v / max * 16) + 'px' }"></i>
  </div>
</template>
