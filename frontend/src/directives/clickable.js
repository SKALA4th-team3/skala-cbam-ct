/* 목록 행처럼 `<div @click>` 으로 이동하는 자리에 붙인다 — `v-clickable`.
   마우스로만 닿는 행이 13곳 있었다. 붙이면 세 가지가 같이 붙는다.
     · tabindex="0"   — 탭으로 닿는다
     · role="button"  — 보조기기가 「누를 수 있는 것」으로 읽는다
     · Enter · Space  — 그 자리에서 el.click() 을 부른다. 기존 @click 이 그대로 실행된다

   focus 링은 base.css 의 :focus-visible 이 이미 갖고 있다. */
export const clickable = {
  mounted(el) {
    el.tabIndex = 0
    if (!el.hasAttribute('role')) el.setAttribute('role', 'button')
    el._onKey = e => {
      if (e.key !== 'Enter' && e.key !== ' ') return
      e.preventDefault()          // Space 로 화면이 스크롤되지 않게
      el.click()
    }
    el.addEventListener('keydown', el._onKey)
  },
  unmounted(el) { el.removeEventListener('keydown', el._onKey) },
}
