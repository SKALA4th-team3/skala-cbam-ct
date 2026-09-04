<script setup>
import { computed, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { navOf } from '@/router'

/** 화면 머리. 첫 줄은 «사람 말로 된 경로»다 — `협력사 › 성진스틸`, `검토 › 검토 대기 › …`.
 *  전에는 `UC-04 · 이메일 접수 · GET /mail-receipts` 처럼 개발자용 줄이 담당자 화면에 그대로 있었다.
 *  API 경로는 개발 모드에서 `{ }` 를 켰을 때만 보인다 — 목/실서버 대조에 여전히 쓰이므로 없애진 않는다. */
const props = defineProps({ kicker: String, api: String, back: String, backTo: [String, Object] })
const route = useRoute()
const section = computed(() => navOf(route.meta.nav))
const trail = computed(() => {
  const t = []
  if (section.value) t.push({ label: section.value.label, to: section.value.to })
  if (props.back && props.back !== section.value?.label) t.push({ label: props.back, to: props.backTo ?? section.value?.to ?? '/' })
  return t
})

const DEV = import.meta.env.DEV
const read = () => { try { return localStorage.getItem('cbam.devinfo') === '1' } catch { return false } }
const devinfo = ref(read())
function toggleDev() {
  devinfo.value = !devinfo.value
  try { localStorage.setItem('cbam.devinfo', devinfo.value ? '1' : '0') } catch { /* 저장 못 해도 이번 화면은 켜진다 */ }
}
</script>

<template>
  <div class="vhead stage" style="--d:0ms">
    <div class="trail" aria-label="현재 위치">
      <template v-for="(c, i) in trail" :key="i">
        <i v-if="i" aria-hidden="true">›</i><RouterLink :to="c.to">{{ c.label }}</RouterLink>
      </template>
      <span v-if="kicker" class="tk">{{ kicker }}</span>
      <button v-if="DEV && api" class="devtg" :class="{ on: devinfo }" :aria-pressed="devinfo" title="API 경로 보기 (개발 모드)" @click="toggleDev">{ }</button>
    </div>
    <code v-if="DEV && api && devinfo" class="api">{{ api }}</code>
    <h2><slot name="title" /></h2>
    <p><slot name="lede" /></p>
    <div v-if="$slots.acts" class="acts" style="margin-top:16px"><slot name="acts" /></div>
  </div>
</template>
