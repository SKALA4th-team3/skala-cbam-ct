<script setup>
import { useRoute, RouterLink } from 'vue-router'
/** 한 메뉴 밑의 화면들 — 관제 「요약 · 마감」, 피드백 「초안 · 발송 관리」.
 *  메뉴를 늘리는 대신 있는 화면을 나눈다. 링크를 타야만 가던 화면이 여기서 보인다. */
defineProps({ tabs: { type: Array, required: true } })   // [{ label, to, count? }]
const route = useRoute()
</script>

<template>
  <nav class="subtabs stage" style="--d:0ms" aria-label="하위 화면">
    <RouterLink v-for="t in tabs" :key="t.to" :to="t.to" :class="{ on: route.path === t.to }" :aria-current="route.path === t.to ? 'page' : undefined">
      {{ t.label }}<b v-if="t.count != null">{{ t.count }}</b>
    </RouterLink>
  </nav>
</template>
