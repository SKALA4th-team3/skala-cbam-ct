import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { logWiring } from '@/api/client'
import './styles/tokens.css'
import './styles/base.css'

createApp(App).use(createPinia()).use(router).mount('#root')

/* 지금 화면이 목을 보고 있는지 개발 콘솔에 알린다 */
logWiring()
