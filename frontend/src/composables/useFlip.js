/* 목록이 걸러질 때 남는 행이 «제자리를 찾아간다».

   필터를 켜면 지금은 목록이 툭 바뀐다 — 무엇이 걸러졌는지 알 수 없다.
   행이 움직이는 걸 보여 주면 필터가 무슨 일을 했는지가 보인다.

   GSAP Flip 이 이걸 위한 플러그인이지만, 원리는 getBoundingClientRect 를
   DOM 이 바뀌기 전후로 한 번씩 재는 것이라 아래로 충분하다.
   (First · Last · Invert · Play — 이름이 그래서 FLIP 이다) */
import { nextTick, watch } from 'vue'
import { REDUCE, EASE } from './motion'

/**
 * @param elRef  행들을 직접 담고 있는 컨테이너 ref
 * @param source 이게 바뀌면 자리가 바뀐다고 본다 (보통 t.filtered)
 *
 * 각 행에 `:data-flip="고유키"` 가 있어야 한다 — 없으면 그 행은 그냥 나타난다.
 */
export function useFlip(elRef, source) {
  if (REDUCE) return
  let before = new Map()

  const measure = () => {
    const el = elRef.value
    if (!el) return
    before = new Map(
      [...el.querySelectorAll('[data-flip]')].map(c => [c.dataset.flip, c.getBoundingClientRect()]),
    )
  }

  // flush:'pre' — DOM 이 바뀌기 전. 여기서 「어디 있었는지」를 잡는다
  watch(source, measure, { flush: 'pre' })

  watch(source, async () => {
    await nextTick()
    const el = elRef.value
    if (!el || !before.size) return
    for (const c of el.querySelectorAll('[data-flip]')) {
      const b = before.get(c.dataset.flip)
      if (!b) continue                                  // 새로 들어온 행은 건드리지 않는다
      const a = c.getBoundingClientRect()
      const dx = b.left - a.left, dy = b.top - a.top
      if (!dx && !dy) continue
      c.animate(
        [{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'none' }],
        { duration: 420, easing: EASE },
      )
    }
    before.clear()
  }, { flush: 'post' })
}
