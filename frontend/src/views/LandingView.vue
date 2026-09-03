<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <header class="bar">
    <a class="logo" href="#"><span class="mark"><i></i></span>CBAM&nbsp;CT</a>
  </header>
  <div class="hero">
    <div class="kicker"><i></i>EU 탄소국경조정제도 · 2026 본격 시행</div>
    <h1>협력사 제출자료를<br />표준 데이터로 바꿔드립니다.</h1>
    <p>메일로 들어온 엑셀과 PDF에서 배출 데이터를 뽑아 판정하고,
       빠진 것은 빠진 대로 남깁니다. 없는 값을 지어내지 않습니다.</p>
    <button class="tactile" @click="router.push('/')">
      <span class="plate"></span>
      <span class="cap">
        <svg class="i" viewBox="0 0 24 24"><path d="M3 12h13M11 6l6 6-6 6" /></svg>관제탑 열기
      </span>
    </button>
    <div class="facts">
      <div><b>48</b><span>협력업체</span></div>
      <div><b>24</b><span>부품</span></div>
      <div><b>3</b><span>완제품</span></div>
      <div><b>2</b><span>AI 확장 지점</span></div>
    </div>
  </div>
</template>
