/* 실서버에 붙여 센다.  npm run api:real   (백엔드가 8080 에 떠 있어야 한다)
   목이 아니라 **dev 의 BE** 에 요청을 넣는다 — 필드 이름·국가 코드·상태값 경계가
   실제로 맞는지 보는 것이 목적이다. 목으로는 증명할 수 없는 부분이다.

   확인하는 것
     · ADR-0005 — 화면은 한글로 고르고, 서버에는 enum·국가코드가 나간다
     · shapes.js — companyName↔name · businessRegistrationNumber↔bizNo …
     · 막는 쪽    — 중복 사업자번호 409 가 **어느 칸이 문제인지** 화면에 전달되는지 */
import { Suppliers, ApiError } from '@/api'
import { wired } from '@/api/client'

let fail = 0, n = 0
const ok = (c, t, e = '') => { n++; console.log((c ? 'ok   ' : 'FAIL ') + t + (e ? '  ' + e : '')); if (!c) fail++ }
const H = s => console.log(`\n── ${s}`)

const stamp = Date.now().toString().slice(-6)
const form = {
  name: `검증상사-${stamp}`, bizNo: `${stamp.slice(0, 3)}-99-${stamp}`, country: '대한민국',
  contact: '검증담당', email: `verify-${stamp}@verify.example`, phone: '02-000-0000',
}

H('실서버로 나가고 있는지')
/* 첫 등록이 실패하면 뒤 검사가 전부 의미를 잃는다. 던지게 두면 스택만 찍히고
   **무엇이 틀렸는지 한 줄로 안 보인다** — 잡아서 사유를 적고 멈춘다. */
let made
try {
  made = await Suppliers.create(form)
} catch (e) {
  ok(false, '실서버 등록이 되지 않았다 — 아래 검사를 돌릴 수 없다',
    `${e.status ?? ''} ${e.code ?? e.message} ${JSON.stringify(e.details ?? {})}`)
  console.log('\n백엔드가 8080 에 떠 있는지, 요청 필드 이름·국가 코드가 맞는지 본다 (shapes.js)')
  console.log(`\n검사 ${n}건 · 실패 ${fail}건`)
  process.exit(1)
}
ok(wired.get('POST /suppliers') === 'real', 'POST /suppliers 가 실서버로 갔다', wired.get('POST /suppliers'))

H('필드 이름 경계 (shapes.js)')
ok(made.name === form.name, '응답의 companyName 이 화면의 name 으로 온다', made.name)
ok(made.bizNo === form.bizNo, 'businessRegistrationNumber → bizNo', made.bizNo)
ok(made.contact === form.contact && made.email === form.email, 'contactName·contactEmail → contact·email')

H('값 경계 (ADR-0005)')
ok(made.country === '대한민국', '화면이 보낸 「대한민국」이 KR 로 나가고 「대한민국」으로 돌아온다', made.country)
ok(made.tie === '협력유지중', '서버의 status=ACTIVE 가 화면용 「협력유지중」으로 온다', made.tie)

H('목록 조회 (명세 №3)')
const list = await Suppliers.list({ size: 100 })
ok(wired.get('GET /suppliers') === 'real', 'GET /suppliers 가 실서버로 갔다', wired.get('GET /suppliers'))
ok(list.content.every(r => r.name && ['협력유지중', '협력끊김'].includes(r.tie)),
  '목록의 모든 행이 화면 모양(name·tie 한글)으로 온다', `${list.content.length}곳`)
const filtered = await Suppliers.list({ size: 100, tie: ['협력유지중'] })
ok(filtered.content.length > 0,
  '화면이 「협력유지중」으로 걸러도 서버는 ACTIVE 로 받아 결과를 준다 — 실서버에서 필터가 빈 목록이 되지 않는다',
  `${filtered.content.length}곳`)
const byCountry = await Suppliers.list({ size: 100, country: ['대한민국'] })
ok(byCountry.content.length > 0, '「대한민국」 필터가 KR 로 나가 결과를 준다 (한글을 그대로 보내면 400 이다)',
  `${byCountry.content.length}곳`)

H('상세 조회 (명세 №4)')
const one = await Suppliers.get(made.id)
ok(one.name === form.name && one.bizNo === form.bizNo, '상세도 화면 모양으로 온다', `${one.name} · ${one.bizNo}`)
ok(one.strip?.length === 12, '월별 제출 상태가 12칸 스트립으로 온다', one.strip)

H('막는 쪽')
try {
  await Suppliers.create({ ...form, email: `other-${stamp}@verify.example` })
  ok(false, '같은 사업자등록번호는 실서버가 막는다')
} catch (e) {
  ok(e instanceof ApiError && e.status === 409 && e.code === 'DUPLICATE_BUSINESS_NUMBER',
    '같은 사업자등록번호는 실서버가 막는다', `${e.status} ${e.code}`)
  ok(e.details.fields.includes('bizNo'),
    '중복 오류가 **어느 칸**인지 화면 폼 이름으로 전달된다 (서버 details 는 비어 온다)',
    JSON.stringify(e.details.fields))
}
try {
  await Suppliers.create({ ...form, bizNo: `${stamp.slice(0, 3)}-88-${stamp}`, email: 'not-an-email' })
  ok(false, '이메일 형식은 실서버가 막는다')
} catch (e) {
  ok(e.status === 400, '이메일 형식은 실서버가 막는다', `${e.status} ${e.code}`)
  ok(e.details.fields.includes('email'), '형식 오류도 화면 폼 이름으로 전달된다 (BE 는 details.fieldErrors 로 준다)',
    JSON.stringify(e.details.fields))
}

console.log(fail ? `\n검사 ${n}건 · 실패 ${fail}건` : `\n검사 ${n}건 · 전부 통과`)
if (fail) process.exit(1)
