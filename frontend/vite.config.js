import { fileURLToPath, URL } from 'node:url'
import { cwd } from 'node:process'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, cwd(), '')
  /* 개발 중 백엔드 주소. 다르게 띄웠으면 .env 의 VITE_BACKEND_ORIGIN 으로 바꾼다.
     프록시를 쓰는 이유 — 화면이 같은 오리진(/api/v1)으로 부르게 되어 CORS 에 기대지 않는다.
     BE 가 안 떠 있어도 목으로 도는 화면은 그대로 뜬다 (VITE_REAL_API 에 없는 것은 목이다). */
  const backend = env.VITE_BACKEND_ORIGIN || 'http://localhost:8081'

  return {
    plugins: [
      vue(),
      vueDevTools(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: backend,
          changeOrigin: true,
        },
      },
    },
  }
})
