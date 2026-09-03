<script setup>
import { useRoute, RouterLink } from 'vue-router'
import { NAV } from '@/router'
const route = useRoute()
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
  </header>
</template>
