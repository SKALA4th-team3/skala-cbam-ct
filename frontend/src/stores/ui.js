import { defineStore } from 'pinia'

/** 토스트 하나만 띄운다. 두 개가 겹치면 무엇이 방금 일어났는지 알 수 없다. */
export const useUi = defineStore('ui', {
  state: () => ({ toast: null, entered: false }),
  actions: {
    say(text, sticky = false) {
      this.toast = { text, sticky, id: Date.now() }
      if (!sticky) setTimeout(() => { if (this.toast?.text === text) this.toast = null }, 2600)
    },
    clear() { this.toast = null },
    enter() { this.entered = true },
  },
})
