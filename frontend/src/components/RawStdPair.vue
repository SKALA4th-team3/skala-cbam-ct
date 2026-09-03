<script setup>
import StatusChip from '@/components/StatusChip.vue'
/** 협력사 원문과 표준화 값을 나란히 — 명세 30번 「표준화 값과 원본 값을 나란히」.
 *  자료 변환(UC-05)과 검토·확정(UC-07) 두 화면이 같은 것을 본다. */
defineProps({ rows: { type: Array, default: () => [] }, delay: { type: String, default: '200ms' } })
</script>

<template>
  <div class="pair stage" :style="{ '--d': delay }">
    <div class="h"><span>협력사 원문 · rawText</span><span>표준 데이터</span></div>
    <div v-for="r in rows" :key="r.field" class="pr2" :class="r.tone">
      <div class="raw">{{ r.raw }}</div>
      <div class="std">
        <span class="fld">{{ r.field }}</span>
        <div class="v">
          <!-- null 을 「0」이나 빈칸으로 보여주지 않는다 — 값이 없다는 것과 0 은 다르다 (24번) -->
          <b :class="{ nul: r.value === null }">{{ r.value ?? 'null' }}</b>
          <span v-if="r.unit" class="u">{{ r.unit }}</span>
          <StatusChip :label="r.note" :tone="r.tone" />
        </div>
      </div>
    </div>
  </div>
</template>
