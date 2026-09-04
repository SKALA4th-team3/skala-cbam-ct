<script setup>
import { onMounted, ref } from 'vue'
import { REDUCE } from '@/composables/motion'

/** 문장이 «단어 단위»로 떠오른다. GSAP SplitText 가 하는 일이고, 필요한 건 이만큼이라 직접 나눈다.
 *
 *  글자 단위로 쪼개지 않는 이유가 둘이다.
 *    · 한글은 한 글자가 뜻의 단위라 낱자로 흩어지면 읽기 전에 눈이 먼저 피로해진다
 *    · 낱자를 inline-block 으로 감싸면 스크린리더가 글자마다 끊어 읽는다
 *  단어를 감싸면 오히려 어절 중간에서 줄이 끊기지 않아 한글 줄바꿈이 좋아진다.
 *
 *  <b> 같은 자식 요소는 그대로 두고 «텍스트 노드만» 나누므로 강조 마크업이 살아 있는다. */
const el = ref(null)

onMounted(() => {
  if (REDUCE || !el.value) return
  let i = 0
  const walk = node => {
    // replaceWith 가 childNodes 를 바꾸므로 살아 있는 목록을 그대로 돌면 안 된다
    for (const child of Array.from(node.childNodes)) {
      if (child.nodeType === Node.ELEMENT_NODE) { walk(child); continue }
      if (child.nodeType !== Node.TEXT_NODE || !child.textContent.trim()) continue
      const frag = document.createDocumentFragment()
      // 공백을 남겨 두고 나눈다 — 어절 사이 간격이 사라지면 안 된다
      for (const piece of child.textContent.split(/(\s+)/)) {
        if (!piece) continue
        if (!piece.trim()) { frag.appendChild(document.createTextNode(piece)); continue }
        const w = document.createElement('span')
        w.className = 'rw'
        w.style.setProperty('--i', i++)
        w.textContent = piece
        frag.appendChild(w)
      }
      child.replaceWith(frag)
    }
  }
  walk(el.value)
  el.value.classList.add('rt-on')
})
</script>

<template><span ref="el" class="rt"><slot /></span></template>
