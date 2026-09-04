/* 움직임 공통.
   여기 있는 것은 전부 「무엇이 방금 바뀌었는지」를 말하기 위한 것이지 장식이 아니다.
   그래서 규칙이 둘이다.
     1. prefers-reduced-motion 이면 최종 상태로 바로 간다 — 빼는 게 아니라 즉시 도착시킨다
     2. 한 화면에서 스스로 반복해 움직이는 것은 최대 두 개 (경보 맥동) */

export const REDUCE =
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

/** base.css 의 --ease 와 같은 곡선 */
export const EASE = 'cubic-bezier(.2,.7,.2,1)'
const easeOut = t => 1 - Math.pow(1 - t, 3)

/**
 * 숫자를 굴린다. 값이 바뀐 것을 눈이 따라가게 하는 게 목적이라
 * 시작값이 없거나(첫 진입) 차이가 크면 길게, 1~2 차이면 짧게 간다.
 * @returns {() => void} 취소 함수
 */
export function tweenNumber(from, to, onTick, dur) {
  if (REDUCE || from === to) { onTick(to); return () => {} }
  const ms = dur ?? Math.min(1100, 380 + Math.abs(to - from) * 26)
  const t0 = performance.now()
  let raf = 0
  const step = now => {
    const p = Math.min(1, (now - t0) / ms)
    onTick(Math.round(from + (to - from) * easeOut(p)))
    if (p < 1) raf = requestAnimationFrame(step)
  }
  raf = requestAnimationFrame(step)
  return () => cancelAnimationFrame(raf)
}

/**
 * 최근 12개월 스트립에서 «끝에 붙어 있는» 미제출 연속 개월 수.
 * 관제 화면이 「5개월째 답이 없습니다」라고 말할 때 그 5다.
 * @param pattern '000100122222' — 0 문제없음 / 1 부적격 / 2 미제출
 */
export function trailingMissing(pattern = '') {
  let n = 0
  for (let i = pattern.length - 1; i >= 0 && pattern[i] === '2'; i--) n++
  return n
}

/** 경보 맥동을 붙일 조건. 여기서만 정한다 — 화면마다 따로 정하면 결국 전부 깜빡인다 */
export const ALARM_MONTHS = 3
export const isAlarming = row =>
  trailingMissing(row?.strip) >= ALARM_MONTHS
