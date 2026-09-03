import { defineStore } from 'pinia'
import { Dashboard } from '@/api'

/** 관제 숫자는 확정(UC-07) 한 번에 같이 움직인다.
 *  화면마다 따로 세면 어긋나므로 여기 한 군데서만 갖고 있는다. (AC-06) */
export const useBoard = defineStore('board', {
  state: () => ({ summary: null, loading: false, recalculated: false }),
  getters: {
    judged: s => s.summary?.judgement ?? { 적격: 0, 부적격: 0, 미제출: 0, total: 0 },
    okRate() { const j = this.judged; return j.total ? Math.round(j.적격 / j.total * 100) : 0 },
  },
  actions: {
    /** 화면 진입 시 한 번. 이미 있으면 다시 부르지 않는다 */
    async load() {
      if (this.summary || this.loading) return
      await this.reload()
    },
    /** 담당자가 「재판정」을 누른 경우. load() 를 쓰면 두 번째부터 아무 일도 일어나지 않는다 */
    async reload() {
      if (this.loading) return
      this.loading = true
      try { this.summary = await Dashboard.summary() } finally { this.loading = false }
    },
    /** 확정하면 걸려 있던 판정이 하나 풀린다.
     *  ⚠️ 아래 숫자는 목이다 — 실제로는 재산정 결과를 서버가 준다(요구사항 41번). */
    applyConfirm() {
      if (!this.summary || this.recalculated) return
      this.summary.judgement = { 적격: 32, 부적격: 12, 미제출: 4, total: 48 }
      this.summary.trend = [21, 23, 22, 26, 29, 32]
      this.recalculated = true
    },
  },
})
