<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { NAV } from '@/router'
import { useNotices } from '@/composables/useNotices'
import { onEscape } from '@/composables/useEscape'
import NoticeDrawer from '@/components/NoticeDrawer.vue'

/* 메뉴를 셋으로 묶는다 — 관제 │ 접수함 → 검토 → 피드백 │ 기준정보 ▾ · · · 🔔 ⚙
   여덟 개를 한 줄에 같은 무게로 두면 순서에 뜻이 없다. 흐름 셋은 «일이 흐르는 순서»라 화살표로 잇고
   건수를 붙여 메뉴 자체가 「지금 어디에 일이 쌓였나」를 말하게 한다. */
const route = useRoute()
const notices = useNotices()
const home = NAV.find(n => n.group === 'home')
const flow = NAV.filter(n => n.group === 'flow')
const master = NAV.filter(n => n.group === 'master')
const settings = NAV.find(n => n.key === 'settings')
const on = key => route.meta.nav === key
const masterOn = computed(() => master.some(n => on(n.key)))
const countOf = key => notices.counts.value[key] ?? 0

const open = ref(false)          // 알림 서랍
const menu = ref(false)          // 기준정보 드롭다운
onMounted(notices.load)
/* 화면이 바뀔 때마다 다시 센다 — 지정·발송 같은 일이 건수를 바꾼다 */
watch(() => route.fullPath, () => { notices.load(); menu.value = false })
function toggle() { open.value = !open.value; if (open.value) notices.markAll() }
const closeMenu = () => (menu.value = false)
onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
onEscape(() => (menu.value = false))
</script>

<template>
  <header class="bar">
    <RouterLink class="logo" to="/landing"><span class="mark"><i></i></span>CBAM&nbsp;CT</RouterLink>

    <nav class="navlinks" id="nav" aria-label="주요 메뉴">
      <RouterLink :to="home.to" :class="{ on: on(home.key) }" :aria-current="on(home.key) ? 'page' : undefined">{{ home.label }}</RouterLink>

      <span class="navsep" role="separator"></span>

      <!-- 일이 흐르는 순서. 건수는 접수함(처리 전) · 검토(대기) · 피드백(초안 없음 + 발송 대기) -->
      <div class="navgroup" aria-label="처리 흐름">
        <template v-for="(n, i) in flow" :key="n.key">
          <span v-if="i" class="navarrow" aria-hidden="true">→</span>
          <RouterLink :to="n.to" :class="{ on: on(n.key) }" :aria-current="on(n.key) ? 'page' : undefined">
            {{ n.label }}<b v-if="countOf(n.key)" class="navbadge">{{ countOf(n.key) }}</b>
          </RouterLink>
        </template>
      </div>

      <span class="navsep" role="separator"></span>

      <!-- 기준정보 — 매일 여는 화면이 아니라 한 단계 밑에 접는다 -->
      <div class="navdrop" :class="{ open: menu, on: masterOn }" @click.stop>
        <button :aria-expanded="menu" aria-haspopup="menu" @click="menu = !menu">
          기준정보<span class="cv"></span>
        </button>
        <div v-if="menu" class="menu" role="menu">
          <RouterLink v-for="n in master" :key="n.key" :to="n.to" role="menuitem" :class="{ on: on(n.key) }" @click="menu = false">{{ n.label }}</RouterLink>
        </div>
      </div>
    </nav>

    <div class="tools">
      <!-- 19·22·51번 「담당자에게 알린다」 — 알림 API 가 없어 접수함·발송 이력에서 골라낸다 (useNotices) -->
      <button class="bell" :class="{ has: notices.unread.value }" :aria-label="`알림 ${notices.items.value.length}건`" @click="toggle">
        <svg class="i" viewBox="0 0 24 24"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 0 0 4 0" /></svg>
        <b v-if="notices.unread.value">{{ notices.unread.value }}</b>
      </button>
      <RouterLink class="gear" :to="settings.to" :class="{ on: on('settings') }" :aria-label="settings.label" :title="settings.label"
                  :aria-current="on('settings') ? 'page' : undefined">
        <svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></svg>
      </RouterLink>
    </div>
    <NoticeDrawer :open="open" :items="notices.items.value" :loaded-at="notices.loadedAt.value" @close="open = false" />
  </header>
</template>
