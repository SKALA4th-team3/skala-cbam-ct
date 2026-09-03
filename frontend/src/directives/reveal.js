/* `v-reveal` — 화면에 들어올 때 떠오른다.

   base.css 의 `.stage` 는 animation-delay 로 순서를 주는데, 그 기준이 «페이지가 뜬 시점»이다.
   협력사 화면은 문서 높이가 4545px 라 8행 아래는 사람이 스크롤해 도착했을 때 이미 끝나 있다.
   기준을 «화면에 들어올 때»로 바꾸면 같은 코드가 실제로 보인다.

   GSAP ScrollTrigger 가 이 일을 하지만, 여기 필요한 건 진입 한 번뿐이라
   IntersectionObserver 로 충분하다. 핀·스크럽이 필요해지면 그때 판단한다.

   쓰기: <div v-reveal>            — 바로
        <div v-reveal="160">       — 160ms 늦게
*/
import { REDUCE } from '@/composables/motion'

const io = typeof IntersectionObserver === 'function'
  ? new IntersectionObserver(entries => {
      for (const e of entries) {
        if (!e.isIntersecting) continue
        e.target.classList.add('revealed')
        io.unobserve(e.target)
      }
    }, { threshold: 0.12, rootMargin: '0px 0px -8% 0px' })
  : null

export const reveal = {
  mounted(el, binding) {
    if (REDUCE || !io) { el.classList.add('revealed'); return }
    el.classList.add('reveal')
    if (binding.value) el.style.setProperty('--rd', binding.value + 'ms')
    io.observe(el)
  },
  unmounted(el) { io?.unobserve(el) },
}
