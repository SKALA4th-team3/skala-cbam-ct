<script setup>
import { useUi } from '@/stores/ui'
const ui = useUi()
</script>

<template>
  <div id="toast" :class="{ on: !!ui.toast, run: ui.toast?.sticky }">
    <i v-if="ui.toast?.sticky"></i>{{ ui.toast?.text }}
  </div>
</template>
