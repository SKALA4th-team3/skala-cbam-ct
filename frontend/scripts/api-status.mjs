/* 목/실서버 현황을 센다.  npm run api:status
   api/index.js 의 엔드포인트 문자열과 .env 의 VITE_REAL_API 를 대조한다. */
import { readFileSync, existsSync } from 'node:fs'

const src = readFileSync(new URL('../src/api/client.js', import.meta.url), 'utf8')
  + readFileSync(new URL('../src/api/index.js', import.meta.url), 'utf8')

const declared = [...src.matchAll(/(?:request|startTask)\(\s*'([A-Z]+ \/[^']*)'/g)].map(m => m[1])
const envFile = new URL('../.env', import.meta.url)
const env = existsSync(envFile) ? readFileSync(envFile, 'utf8') : ''
const real = new Set((env.match(/^VITE_REAL_API=(.*)$/m)?.[1] ?? '').split(',').map(s => s.trim()).filter(Boolean))

const rows = [...new Set(declared)].sort()
for (const e of rows) console.log(`${real.has(e) ? '실서버' : '  목  '}  ${e}`)
console.log(`\n실서버 ${rows.filter(e => real.has(e)).length} · 목 ${rows.filter(e => !real.has(e)).length} · 전체 ${rows.length}`)

const ghost = [...real].filter(e => !rows.includes(e))
if (ghost.length) { console.error(`\n⚠ .env 에 있는데 코드에 없는 엔드포인트: ${ghost.join(', ')}`); process.exit(1) }
