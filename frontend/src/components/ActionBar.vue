<script setup>
defineProps({ title: String, note: String, delay: { type: String, default: '220ms' } })
</script>
<template>
  <div class="actbar stage" :style="{ '--d': delay }">
    <div class="msg"><b>{{ title }}</b><span>{{ note }}</span></div>
    <slot />
  </div>
</template>
