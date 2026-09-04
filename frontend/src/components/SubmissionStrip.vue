<script setup>
import { computed } from 'vue'
import { trailingMissing } from '@/composables/motion'

/** 최근 12개월 판정 스트립. 0 문제없음 / 1 부적격 / 2 미제출
 *
 *  값은 다 있는데 「연속이라는 사실」만 안 그려져 있었다 —
 *  관제 화면이 「5개월째 답이 없습니다」라고 말하는 그 5를 세어야 알 수 있었다.
 *  끝에 붙은 미제출 구간에 브래킷을 얹으면 세지 않아도 된다. */
const props = defineProps({
  pattern: { type: String, default: '000000000000' },
  endMonth: { type: String, default: '2026-09' },   // 마지막 칸이 어느 달인지
  axis: Boolean,                                    // 월 눈금을 함께 낼지
})

const cells = computed(() => props.pattern.split(''))

/** 마지막 칸부터 거꾸로 월을 매긴다 */
const months = computed(() => {
  const [y, m] = props.endMonth.split('-').map(Number)
  return cells.value.map((_, i) => {
    const back = cells.value.length - 1 - i
    const d = new Date(y, m - 1 - back, 1)
    return { y: d.getFullYear(), m: d.getMonth() + 1 }
  })
})

const STATE = { 0: '문제없음', 1: '부적격', 2: '미제출' }
const run = computed(() => trailingMissing(props.pattern))
</script>

<template>
  <div class="strip12" :class="{ axed: axis }">
    <div class="cells">
      <i v-for="(c, i) in cells" :key="i" :class="'s' + c"
         :title="`${months[i].y}-${String(months[i].m).padStart(2, '0')} ${STATE[c] ?? ''}`"></i>
    </div>
    <!-- 끝에 붙은 연속 미제출. 2개월까지는 굳이 표시하지 않는다 — 세면 보인다 -->
    <span v-if="run >= 3" class="run" :style="{ width: `calc(${run} * 13px - 3px)` }"
          :aria-label="`${run}개월 연속 미제출`"><span>{{ run }}개월</span></span>
    <div v-if="axis" class="axis">
      <span v-for="(mo, i) in months" :key="i">{{ i % 2 === 0 ? mo.m : '' }}</span>
    </div>
  </div>
</template>
