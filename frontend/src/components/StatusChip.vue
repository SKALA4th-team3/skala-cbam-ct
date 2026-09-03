<script setup>
const MAP = { complete: 'c', missing: 'm', anomaly: 'a', expiring: 'e', processing: 'p', reject: 'p' }
defineProps({ label: String, tone: { type: String, default: 'complete' }, flat: Boolean })
</script>
<template><span class="chip" :class="[MAP[tone] || 'c', { flat }]">{{ label }}</span></template>
