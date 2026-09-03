<script setup>
import { useRoute, RouterLink } from 'vue-router'
import { NAV } from '@/router'
const route = useRoute()
</script>

<template>
  <header class="bar">
    <RouterLink class="logo" to="/landing"><span class="mark"><i></i></span>CBAM&nbsp;CT</RouterLink>
    <nav class="navlinks" id="nav">
      <RouterLink v-for="n in NAV" :key="n.key" :to="n.to"
                  custom v-slot="{ navigate }">
        <button :class="{ on: route.meta.nav === n.key }" @click="navigate">{{ n.label }}</button>
      </RouterLink>
    </nav>
  </header>
</template>
