<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { NAV } from '@/router'
import { useNotices } from '@/composables/useNotices'
import NoticeDrawer from '@/components/NoticeDrawer.vue'

const route = useRoute()
const notices = useNotices()
const open = ref(false)
onMounted(notices.load)
/* 화면이 바뀔 때마다 다시 센다 — 지정·발송 같은 일이 알림 수를 바꾼다 */
watch(() => route.fullPath, () => notices.load())
function toggle() { open.value = !open.value; if (open.value) notices.markAll() }
</script>

<template>
  <header class="bar">
    <RouterLink class="logo" to="/landing"><span class="mark"><i></i></span>CBAM&nbsp;CT</RouterLink>
    <!-- custom + <button> 으로 감싸면 href 가 사라져 새 탭으로 열 수 없고 보조기기가 링크로 읽지 못한다.
         RouterLink 를 그대로 쓰고 CSS 로 버튼처럼 보이게 한다. 켜진 메뉴는 aria-current 로도 말한다. -->
    <nav class="navlinks" id="nav" aria-label="주요 메뉴">
      <RouterLink v-for="n in NAV" :key="n.key" :to="n.to"
                  :class="{ on: route.meta.nav === n.key }"
                  :aria-current="route.meta.nav === n.key ? 'page' : undefined">{{ n.label }}</RouterLink>
    </nav>
    <!-- 19·22·51번 「담당자에게 알린다」 — 알림 API 가 없어 접수함·발송 이력에서 골라낸다 (useNotices) -->
    <button class="bell" :class="{ has: notices.unread.value }" :aria-label="`알림 ${notices.items.value.length}건`" @click="toggle">
      <svg class="i" viewBox="0 0 24 24"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 0 0 4 0" /></svg>
      <b v-if="notices.unread.value">{{ notices.unread.value }}</b>
    </button>
    <NoticeDrawer :open="open" :items="notices.items.value" :loaded-at="notices.loadedAt.value" @close="open = false" />
  </header>
</template>
