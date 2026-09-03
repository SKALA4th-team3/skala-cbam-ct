/* 목 API 가 정한 대로 동작하는지 센다.  npm run api:verify
   테스트 러너가 없어 vite 로 한 번 묶어 node 로 돌린다 (화면 없이 api/ 만 부른다).

   여기 있는 것은 「되는 것」 확인이 아니라 **막는 쪽**과 **정한 것**이다.
     · ADR-0005 상태값 경계 — 한글로 필터해도 영문 enum 으로 필터해도 같은 결과가 나온다
     · ADR-0007 협력업체 목록 기본 정렬 — companyName(업체명순)
     · 요구사항 31번 — 누락이 있으면 서버가 확정을 막는다 */
import { Suppliers, Review, Analysis, ApiError } from '@/api'
import { toLabel, toCode } from '@/api/enums'

let fail = 0
const ok = (cond, t, extra = '') => { console.log((cond ? 'ok   ' : 'FAIL ') + t + (extra ? '  ' + extra : '')); if (!cond) fail++ }

const all = (await Suppliers.list({ size: 1000 })).content
const names = all.map(s => s.name)
ok(names.length === 48 && JSON.stringify(names) === JSON.stringify([...names].sort((a, b) => a.localeCompare(b))),
  'ADR-0007 · 기본 정렬이 업체명순이다', names.slice(0, 2).join(' · ') + ' …')

const byLabel = (await Suppliers.list({ size: 1000, tie: ['협력유지중'] })).content
const byCode = (await Suppliers.list({ size: 1000, tie: ['ACTIVE'] })).content
ok(byLabel.length === 46, 'ADR-0005 · 한글 라벨로 필터하면 걸린다', byLabel.length + '곳')
ok(byCode.length === 46, 'ADR-0005 · 영문 enum 으로 필터해도 같은 결과다 (실서버가 보내는 값)', byCode.length + '곳')
ok(byLabel.every(s => s.tie === '협력유지중'), 'ADR-0005 · 응답의 상태값은 화면용 한글로 바뀌어 나온다')
ok(toLabel('QUALIFIED') === '적격' && toCode('미제출') === 'NOT_SUBMITTED', 'ADR-0005 · 매핑표 왕복')
ok(toLabel('접수 불가') === '접수 불가', 'ADR-0005 · 매핑에 없는 값은 지어내지 않고 그대로 통과한다')

const q = (await Suppliers.list({ size: 1000, judgement: ['적격'] })).content
ok(q.length === 31 && q.every(s => s.judgement === '적격'), '판정 필터 — 적격 31곳', q.length + '곳')

const sub = await Analysis.get('sub-1')
ok((sub.missingFields ?? []).length > 0, '31번 · 검토 화면 기본 건은 누락을 들고 있다 (확정 버튼이 잠겨야 한다)',
  `누락 ${sub.missingFields?.length} · 판정 ${sub.judgement} · 미등록 ${sub.unmappedParts?.length}`)
try {
  await Review.confirm('sub-1')
  ok(false, '31번 · 누락 건은 서버가 확정을 막는다')
} catch (e) {
  ok(e instanceof ApiError && e.status === 400, '31번 · 누락 건은 서버가 확정을 막는다', `${e.status} ${e.code}`)
}

console.log(fail ? `\n실패 ${fail}건` : `\n전부 통과`)
process.exit(fail ? 1 : 0)
