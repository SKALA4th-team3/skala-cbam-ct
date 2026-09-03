<script setup>
/** 판정 배지. 색만으로 말하지 않도록 점 + 글자를 항상 같이 낸다.
 *  심각도(HIGH·MEDIUM·LOW)도 여기서 맡는다 — 관제·검토·설정 세 화면이
 *  같은 삼항식을 따로 들고 있었고, 한 곳만 고치면 색이 어긋났다. */
const TONE = {
  적격: 'complete', 부적격: 'reject', 미제출: 'missing', 이상치: 'anomaly',
  '만료 임박': 'expiring', '변환 중': 'processing', 완료: 'complete',
  HIGH: 'missing', MEDIUM: 'anomaly', LOW: 'expiring',
}
defineProps({ value: String, tone: String })
</script>
<template><span class="badge" :class="tone || TONE[value] || 'complete'">{{ value }}</span></template>
