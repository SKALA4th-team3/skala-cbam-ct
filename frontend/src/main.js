import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { logWiring } from '@/api/client'
import { clickable } from '@/directives/clickable'
import { reveal } from '@/directives/reveal'
import { installFocusTrap } from '@/composables/focusTrap'
import './styles/tokens.css'
import './styles/base.css'

createApp(App)
  .use(createPinia())
  .use(router)
  .directive('clickable', clickable)   // 클릭으로 이동하는 목록 행을 키보드로도 닿게 한다
  .directive('reveal', reveal)         // 로드 시점이 아니라 화면에 들어올 때 떠오르게 한다
  .mount('#root')

/* 모달·서랍이 열리면 포커스를 안에 가둔다 — 뷰마다 심지 않고 한 곳에서 관찰한다 */
installFocusTrap()

/* 지금 화면이 목을 보고 있는지 개발 콘솔에 알린다 */
logWiring()
