<script setup>
/** 목록이 오기 전 자리. 빈 화면이 잠깐 보였다가 채워지면 「없다」로 읽힌다.
 *  실제 행과 같은 높이·간격으로 두어 도착했을 때 화면이 튀지 않게 한다. */
defineProps({ rows: { type: Number, default: 5 }, cols: { type: Array, default: () => ['1fr', '120px', '92px'] } })
</script>
<template>
  <div class="skel" aria-busy="true" aria-label="불러오는 중">
    <div v-for="i in rows" :key="i" class="skrow" :style="{ gridTemplateColumns: cols.join(' ') }">
      <span v-for="(c, j) in cols" :key="j" class="skb" :style="{ '--w': j === 0 ? (55 + (i * 17) % 35) + '%' : '100%' }"></span>
    </div>
  </div>
</template>
