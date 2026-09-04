import { defineStore } from 'pinia'
import { Dashboard } from '@/api'

/** 관제 숫자는 확정(UC-07) 한 번에 같이 움직인다.
 *  화면마다 따로 세면 어긋나므로 여기 한 군데서만 갖고 있는다. (AC-06) */
export const useBoard = defineStore('board', {
  state: () => ({ summary: null, loading: false }),
  getters: {
    judged: s => s.summary?.judgement ?? { 적격: 0, 부적격: 0, 미제출: 0, total: 0 },
    okRate() { const j = this.judged; return j.total ? Math.round(j.적격 / j.total * 100) : 0 },
  },
  actions: {
    /** 화면 진입 시 한 번. 이미 받아 뒀으면 다시 부르지 않는다 */
    async load() {
      if (this.summary) return
      await this.refresh()
    },
    /**
     * 집계를 **실제 제출 데이터에서 다시 센다** (요구사항 41번).
     *
     * 확정(31번)·반려(32번)가 제출 상태를 바꾸므로 그 직후에 부른다.
     * 전에는 확정 때 적격 32 · 부적격 12 · 미제출 4 를 화면에서 하드코딩해 넣었고,
     * 반려는 아예 반영되지 않아 관제 숫자가 실제와 갈라졌다.
     * 담당자가 누르는 「재판정」 버튼이 그 어긋남을 손으로 맞추는 유일한 수단이었는데,
     * 버튼은 판정을 다시 돌리는 게 아니라 이 조회를 한 번 더 할 뿐이었다 —
     * 그래서 버튼을 없애고 **바꾼 쪽이 직접 부르게** 했다.
     */
    async refresh() {
      if (this.loading) return
      this.loading = true
      try { this.summary = await Dashboard.summary() } finally { this.loading = false }
    },
  },
})
