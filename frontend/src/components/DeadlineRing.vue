<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { REDUCE, tweenNumber } from '@/composables/motion'

/** 마감 링.
 *  바깥에 겹치는 붉은 호가 D-7 경보 구간이다 — 「D-7 부터 경보」라는 문장을 도형으로 옮긴 것.
 *  가운데 D-일수는 0 에서 굴러 올라간다. */
const props = defineProps({
  label: String,
  percent: { type: Number, default: 62 },   // 남은 비율(0~100). 100 이면 아직 하나도 안 지났다
  days: Number,                             // D-일수. 주면 카운트업 한다
  alarmDays: { type: Number, default: 7 },  // 경보 구간 (D-7)
  totalDays: { type: Number, default: 30 },
})

const off = ref(100)
onMounted(() => requestAnimationFrame(() => requestAnimationFrame(() => (off.value = props.percent))))

/* 경보 구간은 한 바퀴의 끝쪽 alarmDays 만큼이다.
   path-length=100 이라 길이를 퍼센트로 바로 쓴다. */
const alarmLen = computed(() => Math.min(100, props.alarmDays / props.totalDays * 100))
const near = computed(() => props.days != null && props.days <= props.alarmDays)

const shown = ref(REDUCE ? (props.days ?? 0) : 0)
let cancel = () => {}
watch(() => props.days, (to, from) => {
  if (to == null) return
  cancel()
  cancel = tweenNumber(from ?? 0, to, v => (shown.value = v))
}, { immediate: true })
</script>

<template>
  <div class="ringw" :class="{ near }">
    <svg viewBox="0 0 36 36">
      <circle class="trk" cx="18" cy="18" r="15.9" />
      <!-- D-7 경보 구간. 눈금이 아니라 「여기부터는 다르다」는 표시라 트랙 바깥에 둔다 -->
      <circle class="alarm" cx="18" cy="18" r="15.9" path-length="100"
              :stroke-dasharray="`${alarmLen} ${100 - alarmLen}`" :stroke-dashoffset="alarmLen" />
      <circle class="vv" cx="18" cy="18" r="15.9" path-length="100" :style="{ strokeDashoffset: off }" />
    </svg>
    <b>{{ days != null ? `D-${shown}` : label }}</b>
  </div>
</template>
