import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { logWiring } from '@/api/client'
import { clickable } from '@/directives/clickable'
import './styles/tokens.css'
import './styles/base.css'

createApp(App)
  .use(createPinia())
  .use(router)
  .directive('clickable', clickable)   // 클릭으로 이동하는 목록 행을 키보드로도 닿게 한다
  .mount('#root')

/* 지금 화면이 목을 보고 있는지 개발 콘솔에 알린다 */
logWiring()
