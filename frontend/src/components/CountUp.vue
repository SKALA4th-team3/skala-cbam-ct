<script setup>
import { ref, watch } from 'vue'
import { REDUCE, tweenNumber } from '@/composables/motion'

/** 숫자가 바뀌면 굴러간다.
 *  지금은 확정(31번)·재판정 뒤에 관제 숫자가 «소리 없이 교체»된다 —
 *  토스트만 뜨고 어느 숫자가 왜 바뀐 건지는 안 보인다.
 *  값이 바뀔 때마다 자동으로 다시 구르므로 화면 쪽에서 할 일이 없다. */
const props = defineProps({ value: { type: Number, default: 0 }, prefix: String, suffix: String })

const shown = ref(REDUCE ? props.value : 0)
let cancel = () => {}
watch(() => props.value, (to, from) => {
  cancel()
  cancel = tweenNumber(from ?? 0, to ?? 0, v => (shown.value = v))
}, { immediate: true })
</script>

<!-- tabular-nums 가 없으면 굴러가는 동안 글자폭이 흔들려 옆 글자가 같이 움직인다 -->
<template><span class="cnum">{{ prefix }}{{ shown }}{{ suffix }}</span></template>
