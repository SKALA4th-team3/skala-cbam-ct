<script setup>
/** 판정 배지. 색만으로 말하지 않도록 점 + 글자를 항상 같이 낸다. */
const TONE = { 적격: 'complete', 부적격: 'reject', 미제출: 'missing', 이상치: 'anomaly',
               '만료 임박': 'expiring', '변환 중': 'processing', 완료: 'complete' }
defineProps({ value: String, tone: String })
</script>
<template><span class="badge" :class="tone || TONE[value] || 'complete'">{{ value }}</span></template>
