/* 전 화면 · 전 버튼 실동작 점검.  node scripts/e2e.mjs   (dev 서버가 5173 에 떠 있어야 한다)
   준비:  npm i -D playwright-core && npx playwright-core install chromium   — devDependency 로 넣지 않았다.
          브라우저 바이너리를 CI 마다 받는 비용이 커서 손으로 돌리는 검사로 둔다.

   무엇을 보나
     · 핵심 줄 — 접수 → 변환 → 검토 → 확정 (sub-7) 이 끝까지 이어지는지
     · 막는 쪽 — sub-1 은 확정이 잠기고, 반려 → 초안으로 흘러가는지 (31·32번)
     · 28번   — sub-9 의 미등록 부품을 등록하면 확정이 열리는지
     · 42~52번 — 초안 → 문체·지시 재생성 → 수정본 → 폐기(사유 필수) → 확정(잠김) → 발송 → 재발송
     · 화면마다 보이는 <button> 을 전부 눌러, 눌렀는데 아무 일도 없는 버튼과 콘솔·페이지 오류를 잡는다

   ⚠️ 목 상태는 «페이지 로드»마다 초기화된다 — page.goto 는 전체 새로고침이라 상태가 날아간다.
      「확정 뒤 잠김」처럼 상태가 이어져야 하는 검사는 앱 안의 링크로 이동해서 본다.
   ⚠️ 「아무 일도 없음」 판정은 주소·토스트·main 의 HTML 길이 변화를 본다. 바뀐 글자 수가
      우연히 같으면 거짓 양성이 난다. 지금 나오는 다섯은 전부 그 경우이고 눈으로 확인했다:
        · 관제 월 열 「6」「8」「11」「12」 — 다른 달을 골라도 열 모양과 글자 수가 같다
        · 관제 「손봐야 할 곳 N」 — 이미 열려 있는 탭을 다시 누른 것이라 바뀔 것이 없다
      이 다섯 말고 다른 것이 뜨면 진짜다. */
import { chromium } from 'playwright-core'
const BASE = 'http://localhost:5173'
const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 1440, height: 950 } })
const page = await ctx.newPage()
page.setDefaultTimeout(4000)
const problems = []
const errs = []
page.on('pageerror', e => errs.push(`pageerror: ${e.message}`))
page.on('console', m => { if (m.type() === 'error' && !/favicon/.test(m.text())) errs.push(`console: ${m.text().slice(0, 200)}`) })
const note = (where, what) => problems.push(`${where} — ${what}`)
const wait = ms => page.waitForTimeout(ms)
const toast = async () => page.evaluate(() => (document.querySelector('#toast.on')?.textContent ?? '').trim())
const url = () => page.url().replace(BASE, '')

async function go(path) { await page.goto(BASE + path, { waitUntil: 'load' }); await wait(650) }
/** 화면의 모든 버튼을 하나씩 눌러 본다. 누르기 전후로 주소·토스트·DOM 길이 중 하나는 달라져야 한다 */
async function pressAll(where, { skip = [], max = 18 } = {}) {
  const seen = new Set()
  for (let i = 0; i < max; i++) {
    const btns = page.locator('main button:visible')
    const n = await btns.count()
    let target = null, label = ''
    for (let k = 0; k < n; k++) {
      const el = btns.nth(k)
      const t = ((await el.textContent()) ?? '').trim().replace(/\s+/g, ' ').slice(0, 40) || (await el.getAttribute('aria-label')) || `#${k}`
      if (seen.has(t) || skip.some(s => t.includes(s))) continue
      if (await el.isDisabled()) { seen.add(t); continue }
      target = el; label = t; break
    }
    if (!target) break
    seen.add(label)
    const before = { url: url(), html: (await page.locator('main').innerHTML()).length, toast: await toast() }
    try { await target.click({ timeout: 2500 }) } catch { note(where, `「${label}」 누를 수 없음`); continue }
    await wait(320)
    const after = { url: url(), html: (await page.locator('main').innerHTML()).length, toast: await toast() }
    const ovl = await page.locator('.ovl, .drawer').count()
    const changed = before.url !== after.url || before.html !== after.html || (after.toast && after.toast !== before.toast) || ovl
    if (!changed) note(where, `「${label}」 눌렀는데 아무 일도 없음`)
    if (ovl) { await page.keyboard.press('Escape'); await wait(250); if (await page.locator('.ovl, .drawer').count()) note(where, `「${label}」 덮개가 Esc 로 안 닫힘`) }
    if (after.url !== before.url) { await page.goBack({ waitUntil: 'load' }); await wait(350); if (url() !== before.url) await go(before.url) }
  }
}

const R = []
const step = (s) => { R.push(s); console.log('·', s) }

// ── 관제
await go('/'); step('관제 열림 · ' + (await page.locator('.brief h1').textContent()).trim().slice(0, 40))
await pressAll('관제', { skip: ['재판정'] })
await page.locator('.mh-col').nth(5).click(); await wait(300)
step('월별 판정 열 선택 → ' + (await page.locator('.jg-r .cap').textContent()).trim())
await page.locator('.eb-row').first().click(); await wait(600); step('배출량 막대 → ' + url()); await go('/')

// ── 접수함
await go('/inbox'); step(`접수함 ${await page.locator('.alerts .at').count()}건 · 상태 ${[...new Set(await page.locator('.alerts .at .chip').allTextContents())].join('/')}`)
await page.locator('.alerts .at').filter({ hasText: '미확인' }).first().click(); await wait(400)
step('미확인 클릭 → 협력사 선택창 ' + (await page.locator('.picker').count() ? '열림' : '안 열림'))
await page.locator('.picker .pk-row').first().click(); await wait(600); step('지정 → ' + await toast())
await page.locator('.ago.lnk').first().click(); await wait(300); step('원문 보기 → ' + (await page.locator('.mailview').count() ? '펼쳐짐' : '안 펼쳐짐'))
await pressAll('접수함', { skip: ['지금 확인', '원문', '닫기'] })

// ── 자료 변환 → 검토 → 확정 흐름 (핵심 줄)
await go('/submissions/sub-7'); await page.waitForSelector('.st.done', { timeout: 6000 }).catch(() => {}); await wait(3500)
step('sub-7 변환 → ' + (await page.locator('.vhead h2').textContent()).trim())
const pass = page.locator('button', { hasText: '검토로 넘기기' })
if (await pass.isDisabled()) note('변환', '완료됐는데 「검토로 넘기기」가 잠김')
else { await pass.click(); await wait(600); step('검토로 → ' + url()) }
await go('/review/sub-7'); step('sub-7 검토 → ' + (await page.locator('.vhead h2').textContent()).trim())
const cbtn = page.locator('button', { hasText: '확정하기' })
if (await cbtn.isDisabled()) note('검토', 'sub-7 은 세 조건을 통과하는데 확정이 잠김')
else { await cbtn.click(); await wait(800); step('확정 → ' + await toast() + ' → ' + url()) }

// 막는 쪽: sub-1 확정 잠김 · 반려 → 피드백
await go('/review/sub-1'); step('sub-1 검토 → ' + (await page.locator('.vhead h2').textContent()).trim())
if (!(await page.locator('button', { hasText: '확정하기' }).isDisabled())) note('검토', 'sub-1 은 누락·부적격인데 확정이 안 잠김')
await page.locator('button', { hasText: '반려' }).first().click(); await wait(300)
const rjOk = page.locator('button', { hasText: '반려 확정' })
step('반려 패널 → 사유 채워짐 ' + (await rjOk.isDisabled() ? '아님(잠김)' : '됨'))
await rjOk.click(); await wait(900); step('반려 → ' + url() + ' · ' + await toast())

// 28번: sub-9 미등록 부품 → 등록 → 확정
await go('/review/sub-9'); step('sub-9 → 미등록 ' + await page.locator('.um-row').count() + '건 · 확정 ' + (await page.locator('button', { hasText: '확정하기' }).isDisabled() ? '잠김' : '열림'))
await page.locator('.um-row button').first().click(); await wait(400)
step('부품 등록 모달 ' + (await page.locator('.ovl').count() ? '열림' : '안 열림') + ' · 이름 미리 채움 ' + await page.locator('.ovl input').first().inputValue())
await page.locator('.ovl input').first().fill('아연도금 증기 (정식)')
await page.locator('.ovl input').nth(1).fill('27112100')
await page.locator('.ovl select').selectOption({ label: '한빛철강' })
await page.locator('.ovl button', { hasText: '등록' }).last().click(); await wait(900)
step('등록 → ' + await toast() + ' · 미등록 남음 ' + await page.locator('.um-row').count() + ' · 확정 ' + (await page.locator('button', { hasText: '확정하기' }).isDisabled() ? '잠김' : '열림'))

// ── 검토 대기 목록 필터·정렬 + URL 동기화
await go('/review'); step(`검토 대기 ${await page.locator('.alerts .at').count()}행`)
await page.locator('.fdrop button', { hasText: '심각도' }).click(); await wait(200)
await page.locator('.fdrop.open .menu label', { hasText: 'HIGH' }).click(); await wait(500)
step('심각도 HIGH 필터 → 주소 ' + url() + ' · ' + await page.locator('.alerts .at').count() + '행')
await page.reload({ waitUntil: 'networkidle' }); await wait(700)
step('새로고침 뒤 필터 유지 → ' + await page.locator('.applied .fc').count() + '개 칩')

// ── 협력사
await go('/suppliers'); await pressAll('협력사 목록', { skip: ['국가', '거래', '판정', '정렬'] })
await go('/suppliers/1'); step('협력사 1 → ' + (await page.locator('.vhead h2').textContent()).trim().slice(0, 30))
await page.locator('button', { hasText: '담당자 수정' }).click(); await wait(300)
await page.locator('.ovl input').nth(1).fill('kim.new@seongjin.example')
await page.locator('.ovl button', { hasText: '저장' }).click(); await wait(800); step('담당자 수정 → ' + await toast())
await page.locator('button', { hasText: '협력 끊김' }).click(); await wait(300)
await page.locator('.ovl button', { hasText: '협력 끊김으로 바꾸기' }).click(); await wait(800); step('협력 끊김 → ' + await toast())
await pressAll('협력사 상세', { skip: ['협력 끊김', '담당자 수정'] })
await go('/suppliers/new')
step('빈 폼 → 등록 버튼 ' + (await page.locator('button', { hasText: '등록' }).last().isDisabled() ? '잠김(정상)' : '열림(문제)') + ' · 필수 표시 ' + await page.locator('.req').count() + '칸')
await page.locator('.form input').nth(0).fill('E2E상사'); await page.locator('.form input').nth(1).fill('123-45-67890')
await page.locator('.form input').nth(3).fill('담당'); await page.locator('.form input').nth(4).fill('not-an-email'); await page.locator('.form input').nth(5).fill('02-1')
await page.locator('button', { hasText: '등록' }).last().click(); await wait(600)
step('이메일 형식 오류 → ' + await toast() + ' · 붉은 칸 ' + await page.locator('.fld.bad').count())

// ── 부품
await go('/parts'); step(`부품 ${await page.locator('.parts .pt').count()}행`)
await page.locator('button', { hasText: '부품 등록' }).click(); await wait(300)
await page.locator('.ovl input').first().fill('검증부품E2E'); await page.locator('.ovl input').nth(1).fill('7207 11')
step('CN 6자리 → 등록 버튼 ' + (await page.locator('.ovl button', { hasText: '등록' }).last().isDisabled() ? '잠김(정상)' : '열림(문제)'))
await page.locator('.ovl input').nth(1).fill('72071100'); await page.locator('.ovl select').selectOption({ label: '대한화학' })
await page.locator('.ovl button', { hasText: '등록' }).last().click(); await wait(900); step('부품 등록 → ' + await toast())
await page.locator('.parts .pt').first().click(); await wait(600); step('부품 행 → ' + url())
await page.locator('button', { hasText: '수정' }).first().click(); await wait(300)
await page.locator('.ovl input').first().fill('0'); await page.locator('.ovl button', { hasText: '저장' }).click(); await wait(600)
step('팩터 0 → ' + await toast())
await page.locator('.ovl input').first().fill('1.5'); await page.locator('.ovl button', { hasText: '저장' }).click(); await wait(700); step('팩터 1.5 → ' + await toast())
await pressAll('부품 상세')

// ── 완제품
await go('/products'); step(`완제품 ${await page.locator('.list .row').count()}행 · 신고불가 ${await page.locator('.cut').count()}`)
await page.locator('button', { hasText: '완제품 등록' }).click(); await wait(300)
await page.locator('.ovl input').nth(0).fill('E2E 제품'); await page.locator('.ovl input').nth(1).fill('84818081'); await page.locator('.ovl input').nth(2).fill('100')
await page.locator('.ovl .cellsel').first().selectOption({ index: 1 }); await page.locator('.ovl .cellin').first().fill('1.2')
await page.locator('.ovl button', { hasText: '등록' }).last().click(); await wait(900); step('완제품 등록 → ' + await toast())
for (const id of ['valve-a', 'valve-b', 'flange']) { await go(`/products/${id}/report`); step(`${id} 리포트 → ` + (await page.locator('.vhead h2').textContent()).trim()) }
await go('/products/valve-b/bom'); await page.locator('.bomedit .cellin').first().fill('1.5')
await page.locator('button', { hasText: '저장' }).last().click(); await wait(800); step('BOM 저장 → ' + await toast())
await go('/products/hr-2400/report'); step('없는 제품 → ' + (await page.locator('.vhead h2').textContent()).trim())

// ── 마감
await go('/deadlines'); step('마감 → ' + (await page.locator('.vhead h2').textContent()).trim())
await page.locator('.rmlist .h .ck').click(); await wait(200)
await page.locator('button', { hasText: '리마인드 발송' }).click(); await wait(800); step('리마인드 → ' + await toast())
await pressAll('마감', { skip: ['리마인드'] })

// ── 피드백
await go('/feedback'); step(`피드백 허브 ${await page.locator('.alerts .at').count()}건`)
await page.locator('button', { hasText: '일괄 생성' }).first().click(); await wait(300)
await page.locator('.ovl button', { hasText: '만들기' }).click(); await wait(900); step('일괄 → ' + await toast())
await go('/feedback/sub-3'); step('sub-3 초안 → ' + (await page.locator('.alert b').first().textContent().catch(() => '실패 배너 없음')))
await go('/feedback/sub-4'); step('sub-4 초안 v' + await page.locator('.vtab').count())
await page.locator('.tone button', { hasText: '간결' }).click(); await wait(800); step('문체 간결 → 버전 ' + await page.locator('.vtab').count())
await page.locator('.regen input').fill('기한을 강조'); await page.locator('.regen button').click(); await wait(800)
step('추가 지시 재생성 → 버전 ' + await page.locator('.vtab').count() + ' · 본문 반영 ' + ((await page.locator('.letter textarea').inputValue()).includes('기한을 강조')))
await page.locator('.letter textarea').fill((await page.locator('.letter textarea').inputValue()) + '\n\n(담당자 추가)')
await page.locator('button', { hasText: '수정본 저장' }).click(); await wait(800); step('수정본 저장 → ' + await toast())
await page.locator('button', { hasText: '폐기' }).first().click(); await wait(300)
step('폐기 모달 · 사유 없이 버튼 ' + (await page.locator('.ovl button', { hasText: '폐기하고' }).isDisabled() ? '잠김(정상)' : '열림(문제)'))
await page.locator('.ovl textarea').fill('근거 판정이 바뀜'); await page.locator('.ovl button', { hasText: '폐기하고' }).click(); await wait(1200)
step('폐기 → ' + await toast() + ' · 버전 ' + await page.locator('.vtab').count())
await page.locator('button', { hasText: '확정하고 발송 관리로' }).click(); await wait(900); step('확정 → ' + url())
await go('/feedback/sub-4'); step('확정 뒤 본문 readonly ' + await page.locator('.letter textarea[readonly]').count())
await go('/feedback/dispatch'); await page.locator('button', { hasText: '건 발송' }).click(); await wait(900); step('발송 → ' + await toast())
await page.locator('.ago .lnk', { hasText: '재발송' }).first().click(); await wait(300)
await page.locator('.ovl button', { hasText: '재발송' }).last().click(); await wait(800); step('재발송 → ' + await toast())

// ── 설정 · 알림 · 없는 주소
await go('/settings'); await pressAll('설정')
await page.locator('.bell').click(); await wait(300); step('알림 서랍 ' + await page.locator('.nitem').count() + '건'); await page.keyboard.press('Escape'); await wait(200)
await go('/nowhere/at/all'); step('없는 주소 → ' + (await page.locator('.vhead h2').textContent()).trim())

// ── reduced-motion
const ctx2 = await b.newContext({ viewport: { width: 1440, height: 950 }, reducedMotion: 'reduce' })
const p2 = await ctx2.newPage(); await p2.goto(BASE + '/', { waitUntil: 'networkidle' }); await p2.waitForTimeout(800)
step('reduced-motion 관제 h1 보임 ' + await p2.locator('.brief h1').isVisible())

console.log('\n════ 결과')
console.log(problems.length ? '문제 ' + problems.length + '건:\n' + problems.map(p => '  ✗ ' + p).join('\n') : '눌러서 아무 일도 없는 버튼: 없음')
console.log(errs.length ? '오류 ' + errs.length + '건:\n' + [...new Set(errs)].slice(0, 20).map(e => '  ✗ ' + e).join('\n') : '콘솔·페이지 오류: 없음')
await b.close()
