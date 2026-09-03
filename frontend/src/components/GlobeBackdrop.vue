<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { createGlobe } from '@/composables/globe'

const canvas = ref(null)
const route = useRoute()
let globe = null

onMounted(() => {
  globe = createGlobe(canvas.value)
  apply()
  addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => removeEventListener('scroll', onScroll))
const onScroll = () => globe?.setScroll?.(scrollY)
function apply() { if (globe && route.meta.globe) globe.flyTo(route.meta.globe) }
watch(() => route.name, apply)
</script>

<template>
  <div id="bg" :class="{ deep: route.meta.globe?.deep }">
    <canvas ref="canvas" id="globe"></canvas>
    <!-- 글자 뒤를 덮는 층. 없으면 구의 선이 본문을 관통해 읽기 어렵다 -->
    <div class="vig"></div>
  </div>
</template>
