<script setup>
import { useRouter } from 'vue-router'
defineProps({ kicker: String, api: String, back: String, backTo: [String, Object] })
const router = useRouter()
</script>
<template>
  <div class="vhead stage" style="--d:0ms">
    <button v-if="back" class="back" @click="router.push(backTo)">
      <svg class="i" viewBox="0 0 24 24"><path d="M15 6l-6 6 6 6" /></svg>{{ back }}
    </button>
    <div class="kicker"><i></i><span v-if="api" class="api">{{ api }}</span><template v-else>{{ kicker }}</template></div>
    <h2><slot name="title" /></h2>
    <p><slot name="lede" /></p>
    <div v-if="$slots.acts" class="acts" style="margin-top:16px"><slot name="acts" /></div>
  </div>
</template>
