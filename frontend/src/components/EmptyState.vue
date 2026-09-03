<script setup>
defineProps({ title: String, note: String, action: String })
defineEmits(['action'])
</script>
<template>
  <div class="noresult">
    <b>{{ title }}</b><p>{{ note }}</p>
    <button v-if="action" @click="$emit('action')">{{ action }}</button>
  </div>
</template>
