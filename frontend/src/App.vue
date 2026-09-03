<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobeBackdrop from '@/components/GlobeBackdrop.vue'
import AppHeader from '@/components/AppHeader.vue'
import ToastHost from '@/components/ToastHost.vue'

const route = useRoute()
const isLanding = computed(() => route.name === 'landing')
</script>

<template>
  <GlobeBackdrop />
  <ToastHost />
  <main v-if="isLanding" id="landing"><RouterView /></main>
  <div v-else id="app" class="wrap on">
    <AppHeader />
    <main class="page">
      <RouterView v-slot="{ Component }">
        <section class="view on" :key="route.fullPath"><component :is="Component" /></section>
      </RouterView>
    </main>
    <footer class="foot"><span>CBAM CT</span><span>협력사 탄소데이터 관제탑</span></footer>
  </div>
</template>
