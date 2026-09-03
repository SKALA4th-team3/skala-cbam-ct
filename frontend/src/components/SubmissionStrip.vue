<script setup>
/** 최근 12개월 판정 스트립. 0 문제없음 / 1 부적격 / 2 미제출 */
defineProps({ pattern: { type: String, default: '000000000000' } })
</script>
<template>
  <div class="strip12"><i v-for="(c, i) in pattern.split('')" :key="i" :class="'s' + c"></i></div>
</template>
