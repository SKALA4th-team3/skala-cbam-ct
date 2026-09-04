<script setup>
import { useRoute, useRouter } from 'vue-router'
import ViewHead from '@/components/ViewHead.vue'
const route = useRoute(); const router = useRouter()
</script>

<template>
  <ViewHead kicker="없는 주소">
    <template #title>이 주소에는 화면이 없습니다.</template>
    <template #lede><code>{{ route.fullPath }}</code> — 링크가 낡았거나 주소를 잘못 적었습니다. 관제로 돌려보내지 않고 여기서 말합니다.</template>
    <template #acts>
      <button class="quiet" @click="router.back()">뒤로</button>
      <button class="btn" @click="router.push('/')">관제로</button>
    </template>
  </ViewHead>
  <div class="spacer"></div>
</template>
