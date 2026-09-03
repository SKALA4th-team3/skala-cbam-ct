<script setup>
import { onMounted, ref } from 'vue'
const props = defineProps({ label: String, percent: { type: Number, default: 62 } })
const off = ref(100)
onMounted(() => requestAnimationFrame(() => requestAnimationFrame(() => { off.value = props.percent })))
</script>
<template>
  <div class="ringw">
    <svg viewBox="0 0 36 36">
      <circle class="trk" cx="18" cy="18" r="15.9" />
      <circle class="vv" cx="18" cy="18" r="15.9" path-length="100" :style="{ strokeDashoffset: off }" />
    </svg><b>{{ label }}</b>
  </div>
</template>
