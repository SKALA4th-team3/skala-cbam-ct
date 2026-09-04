/* 덮개(.ovl / .drawer)가 열리면 포커스를 안으로 들이고, 닫히면 원래 자리로 돌려놓는다.

   모달이 떠도 포커스가 body 에 남아 있으면 Tab 이 가려진 뒤쪽 버튼들을 훑고 다닌다 —
   화면에는 모달만 보이는데 눌리는 건 뒤라 앞뒤가 어긋난다.
   뷰마다 ref 를 심는 대신 한 곳에서 관찰한다. 모달을 더 만들어도 .ovl 만 쓰면 그대로 적용된다. */
const SEL = 'a[href],button:not(:disabled),input:not(:disabled),select:not(:disabled),' +
            'textarea:not(:disabled),[tabindex]:not([tabindex="-1"])'
const focusables = root => [...root.querySelectorAll(SEL)].filter(el => el.offsetWidth || el.offsetHeight)
const topOverlay = () => [...document.querySelectorAll('.ovl, .drawer')].pop() ?? null

export function installFocusTrap() {
  let restoreTo = null
  const enter = ovl => {
    restoreTo = document.activeElement
    const list = focusables(ovl)
    /* 바로 적어야 하는 칸이 있으면 그리로, 없으면 첫 버튼으로 */
    ;(list.find(el => el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') ?? list[0])?.focus()
  }
  const leave = () => {
    if (restoreTo && restoreTo !== document.body && restoreTo.isConnected) restoreTo.focus()
    restoreTo = null
  }
  new MutationObserver(() => {
    const ovl = topOverlay()
    if (ovl && !restoreTo) enter(ovl)
    else if (!ovl && restoreTo) leave()
  }).observe(document.body, { childList: true, subtree: true })

  /* Tab 이 덮개 밖으로 새어 나가지 않게 앞뒤를 이어 붙인다 */
  window.addEventListener('keydown', e => {
    if (e.key !== 'Tab') return
    const ovl = topOverlay(); if (!ovl) return
    const list = focusables(ovl); if (!list.length) return
    const first = list[0], last = list[list.length - 1]
    if (!ovl.contains(document.activeElement)) { e.preventDefault(); first.focus(); return }
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
  })
}
