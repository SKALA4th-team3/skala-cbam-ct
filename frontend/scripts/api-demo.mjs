/* 시연 경로가 실서버로 실제로 도는지 센다.  npm run api:demo
   (백엔드가 `SPRING_PROFILES_ACTIVE=dev,mock` 으로 8080 에 떠 있어야 한다)

   목이 아니라 dev 의 BE 에 요청을 넣는다. 확인하는 것은 셋이다.
     · 붙였다고 한 엔드포인트가 정말 실서버로 나가는가 (wired)
     · shapes.js 의 이름 변환이 화면이 읽는 모양으로 오는가
     · 핵심 줄이 눌러지는가 — 29번 검토 → 31번 확정 → 15번 내재배출량 → 32번 반려 */
import { Parts, Products, Review, Suppliers, ApiError } from '@/api'
import { wired } from '@/api/client'

let fail = 0, n = 0
const ok = (c, t, e = '') => { n++; console.log((c ? 'ok   ' : 'FAIL ') + t + (e ? '  → ' + e : '')); if (!c) fail++ }
const H = s => console.log(`\n── ${s}`)

H('기준정보가 실서버에서 온다')
const sup = await Suppliers.list()
ok(wired.get('GET /suppliers') === 'real', 'GET /suppliers 가 실서버로 갔다')
ok(sup.content.length > 0, `협력업체 ${sup.content.length}건`, sup.content[0]?.name)

const parts = await Parts.list()
ok(wired.get('GET /parts') === 'real', 'GET /parts 가 실서버로 갔다')
ok(parts.content.length > 0, `부품 ${parts.content.length}건`, parts.content[0]?.name)
ok(parts.content[0]?.cn?.includes(' '), 'cnCode 가 화면 표기(네 자리 끊기)로 온다', parts.content[0]?.cn)
ok(parts.content[0]?.factor?.includes('tCO'), 'benchmarkFactor 가 단위 붙은 문자열로 온다', parts.content[0]?.factor)
ok(parts.content[0]?.supplier != null, '공급 협력업체 이름이 채워진다', parts.content[0]?.supplier)

const part = await Parts.get(parts.content[0].id)
ok(wired.get('GET /parts/{partId}') === 'real', 'GET /parts/{partId} 가 실서버로 갔다')
ok(part.name === parts.content[0].name, '상세의 부품명이 목록과 같다', part.name)

H('완제품 — 15번 내재배출량')
const done = await Products.list({ reportingMonth: '2026-08' })
ok(wired.get('GET /products') === 'real', 'GET /products 가 실서버로 갔다')
ok(done.content.length > 0, `완제품 ${done.content.length}건`, done.content[0]?.name)

const detail = await Products.get(done.content[0].id, { reportingMonth: '2026-08' })
ok(wired.get('GET /products/{productId}') === 'real', 'GET /products/{productId} 가 실서버로 갔다')
ok(detail.reportable === true, '2026-08 은 두 부품이 모두 확정이라 보고 가능하다', String(detail.reportable))
ok(detail.actual != null, '내재배출량이 실제 값으로 온다 (null 이 아니다)', String(detail.actual))
/* 실측이 평균보다 높은지 낮은지는 데이터가 정하는 것이라 단언하지 않는다.
   14번이 요구하는 것은 「대비가 계산돼 나오는가」다 — 둘 다 숫자여야 하고 비율이 있어야 한다. */
ok(typeof detail.mean === 'number' && typeof detail.actual === 'number',
  '평균값과 실측값이 둘 다 숫자로 온다 — 14번의 「평균값 대비 실측값」',
  `실측 ${detail.actual} / 평균 ${detail.mean}`)
ok(Math.abs(detail.actual / detail.mean - 1) < 0.5,
  '실측값이 평균값과 같은 자릿수다 (계산 축이 어긋나지 않았다)',
  `${(detail.actual / detail.mean * 100).toFixed(1)}%`)
ok(detail.parts.every(p => p.contribution != null), '모든 구성 부품이 기여분을 갖는다')

H('막는 쪽 — 미확정 부품이 있으면 합계를 지어내지 않는다 (15번)')
const open = await Products.get(done.content[0].id, { reportingMonth: '2026-09' })
ok(open.actual == null, '2026-09 는 미확정 부품이 있어 내재배출량이 비어 있다', String(open.actual))
ok(open.pendingCount > 0, `미확정 부품 ${open.pendingCount}건이 표시된다`)
ok(open.blocking.length > 0, '무엇 때문에 못 내는지 사유가 온다', open.blocking[0])

H('29번 검토 목록')
const queue = await Review.queue({ reportingMonth: '2026-09' })
ok(wired.get('GET /submissions') === 'real', 'GET /submissions 가 실서버로 갔다')
ok(queue.content.length > 0, `검토 대상 ${queue.content.length}건`)
const pending = queue.content.find(r => r.judgement === '적격' && r.status === '검토 대기')
const bad = queue.content.find(r => r.judgement === '부적격')
ok(pending != null, '적격 · 검토 대기 건이 있다 — 31번 확정을 눌러 볼 수 있다', pending?.supplier)
ok(bad != null, '부적격 건이 있다 — 32번 반려를 눌러 볼 수 있다', `${bad?.supplier} ${bad?.severity}`)
ok(queue.content[0]?.item != null, 'partName 이 화면의 item 으로 온다', queue.content[0]?.item)

H('31번 확정 — 적격 건만 확정된다')
const confirmed = await Review.confirm(pending.id)
ok(wired.get('POST /submissions/{submissionId}/confirm') === 'real', '확정이 실서버로 갔다')
ok(confirmed.status === '확정', '상태가 확정으로 바뀐다', confirmed.status)
ok(confirmed.calculatedEmission?.appliedFactorYear != null,
  '확정 시점 배출계수 연도가 스냅샷으로 찍힌다', String(confirmed.calculatedEmission?.appliedFactorYear))
ok(confirmed.calculatedEmission?.frozen === true, '계산값이 동결된다')

try {
  await Review.confirm(bad.id)
  ok(false, '부적격 건 확정이 막혀야 하는데 통과했다')
} catch (e) {
  ok(e instanceof ApiError && e.code === 'NOT_QUALIFIED',
    '부적격 건은 확정이 막힌다 — 31번 「적격인 경우에만」', `${e.status} ${e.code}`)
}

H('32번 반려')
const rejected = await Review.reject(bad.id, '배출 원단위가 평균값 대비 87.5% 높습니다', 'OUTLIER', 'REJECTED')
ok(wired.get('POST /submissions/{submissionId}/reject') === 'real', '반려가 실서버로 갔다')
ok(rejected.judgement === '부적격', '판정이 부적격으로 고정된다', rejected.judgement)
ok(rejected.reason?.includes('87.5'), '반려 사유가 그대로 저장된다')

H('확정한 건이 실제로 확정 상태로 남는다')
/* 방금 확정한 부품이 어느 완제품 BOM 에 들어 있는지는 데이터가 정한다 — 특정 제품을 찍어 보지 않는다.
   대신 ① 그 건이 목록에서 확정으로 보이고 ② 두 번 확정되지 않는지를 센다. */
const requeued = await Review.queue({ reportingMonth: '2026-09' })
const same = requeued.content.find(r => r.id === pending.id)
ok(same == null || same.status === '확정', '확정한 건이 검토 대기로 돌아오지 않는다', same?.status ?? '(목록에서 빠짐)')
try {
  await Review.confirm(pending.id)
  ok(false, '두 번 확정되면 안 된다')
} catch (e) {
  ok(e instanceof ApiError && e.code === 'ALREADY_CONFIRMED',
    '이미 확정된 건은 다시 확정되지 않는다', `${e.status} ${e.code}`)
}

console.log(`\n검사 ${n}건 · 실패 ${fail}건`)
process.exit(fail ? 1 : 0)
