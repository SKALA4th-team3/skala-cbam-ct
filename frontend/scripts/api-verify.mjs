/* 목 API 가 정한 대로 동작하는지 센다.  npm run api:verify
   테스트 러너가 없어 vite 로 한 번 묶어 node 로 돌린다 (화면 없이 api/ 만 부른다).

   여기 있는 것은 「되는 것」 확인이 아니라 **막는 쪽**과 **정한 것**이다.
   되돌려서 확인했다 — 검사를 지우면 실제로 FAIL 이 난다. */
import { Suppliers, Parts, Review, Analysis, Inbox, Deadlines, Feedback, ApiError } from '@/api'
import { toLabel, toCode } from '@/api/enums'

let fail = 0, n = 0
const ok = (cond, t, extra = '') => {
  n++; console.log((cond ? 'ok   ' : 'FAIL ') + t + (extra ? '  ' + extra : '')); if (!cond) fail++
}
/** 막혀야 하는 요청. 통과하면 그게 실패다 */
async function blocked(t, fn, expect) {
  try { await fn(); ok(false, t, '막히지 않고 통과했다') }
  catch (e) {
    const hit = e instanceof ApiError && (!expect || e.code === expect)
    ok(hit, t, `${e.status ?? ''} ${e.code ?? e.message}`)
  }
}

const H = s => console.log(`\n── ${s}`)

/* ── ADR-0007 · 협력업체 목록 기본 정렬 ─────────────────── */
H('협력업체 목록 (요구사항 3·4번 · ADR-0007)')
const all = (await Suppliers.list({ size: 1000 })).content
const names = all.map(s => s.name)
ok(names.length === 48 && JSON.stringify(names) === JSON.stringify([...names].sort((a, b) => a.localeCompare(b))),
  '기본 정렬이 업체명순이다', names.slice(0, 2).join(' · ') + ' …')

/* ── ADR-0005 · 상태값 경계 ─────────────────────────────── */
H('상태값 경계 (ADR-0005)')
const byLabel = (await Suppliers.list({ size: 1000, tie: ['협력유지중'] })).content
const byCode = (await Suppliers.list({ size: 1000, tie: ['ACTIVE'] })).content
ok(byLabel.length === 46, '한글 라벨로 필터하면 걸린다', byLabel.length + '곳')
ok(byCode.length === byLabel.length, '영문 enum 으로 필터해도 같은 결과다 (실서버가 보내는 값)', byCode.length + '곳')
ok(byLabel.every(s => s.tie === '협력유지중'), '응답의 상태값은 화면용 한글로 바뀌어 나온다')
ok(toLabel('QUALIFIED') === '적격' && toCode('미제출') === 'NOT_SUBMITTED', '매핑표 왕복')
ok(toLabel('접수 불가') === '접수 불가', '매핑에 없는 값은 지어내지 않고 그대로 통과한다')

/* ── 요구사항 1번 · 협력업체 등록 ───────────────────────── */
H('협력업체 등록 (요구사항 1번)')
const full = { name: '검증상사', bizNo: '999-99-99999', country: '대한민국',
               contact: '검증담당', email: 'verify@verify.example', phone: '02-000-0000' }
await blocked('여섯 항목 중 하나라도 비면 등록하지 않는다', () => Suppliers.create({ ...full, email: '' }), 'MISSING_REQUIRED_FIELD')
await blocked('빈 폼은 등록하지 않는다', () => Suppliers.create({}), 'MISSING_REQUIRED_FIELD')
await blocked('이메일 형식을 검사한다', () => Suppliers.create({ ...full, email: 'not-an-email' }), 'INVALID_EMAIL_FORMAT')

ok(all.every(s => s.email) && new Set(all.map(s => s.email)).size === all.length,
  '협력사 48곳 전부 담당자 이메일이 있고 서로 다르다 (19번 매칭 키)')
ok(all.every(s => s.bizNo) && new Set(all.map(s => s.bizNo)).size === all.length,
  '사업자 등록번호도 전부 있고 서로 다르다')
ok(all.every(s => s.email.endsWith('.example')),
  '목 이메일 도메인은 전부 .example 이다 (실제 주소를 넣지 않는다)')

await Suppliers.create(full)
await blocked('같은 사업자 등록번호는 두 번 등록되지 않는다',
  () => Suppliers.create({ ...full, email: 'other@other.example' }), 'DUPLICATE_BUSINESS_NUMBER')
await blocked('같은 담당자 이메일은 두 번 등록되지 않는다',
  () => Suppliers.create({ ...full, bizNo: '888-88-88888' }), 'DUPLICATE_CONTACT_EMAIL')
try {
  await Suppliers.create({ ...full, bizNo: '777-77-77777' })
  ok(false, '중복 오류는 어느 칸이 문제인지 알려준다')
} catch (e) { ok(e.details?.fields?.includes('email'), '중복 오류는 어느 칸이 문제인지 details.fields 로 알려준다', JSON.stringify(e.details)) }

/* ── 요구사항 7번 · CN 코드 8자리 ───────────────────────── */
H('부품 등록 (요구사항 7번)')
const partRows = (await Parts.list({ size: 1000 })).content
ok(partRows.every(p => /^\d{8}$/.test(p.cn.replace(/\s/g, ''))),
  '목 부품의 CN 코드가 전부 8자리다', partRows[0].cn)
/* `자사 (포항)` 처럼 협력업체가 아닌 공급처는 supplierId 가 없는 것이 맞다 — 없는 id 를 지어내지 않는다.
   확인하는 것은 「등록된 협력업체를 공급처로 둔 부품에는 id 가 반드시 있다」이다. */
const supNames = new Set(all.map(s => s.name))
const fromSuppliers = partRows.filter(p => supNames.has(p.supplier))
ok(fromSuppliers.length > 0 && fromSuppliers.every(p => p.supplierId),
  '협력업체가 공급하는 부품은 그 협력업체의 id 를 함께 갖는다', `${fromSuppliers.length}개`)
ok(partRows.filter(p => !supNames.has(p.supplier)).every(p => p.supplierId === null),
  '협력업체가 아닌 공급처(자사 공정)는 id 를 지어내지 않고 null 로 둔다',
  partRows.filter(p => !p.supplierId).map(p => p.supplier)[0])
await blocked('6자리 CN 코드는 막는다', () => Parts.create({ name: 'cn6', cn: '7207 11' }), 'INVALID_CN_CODE')
await blocked('9자리 CN 코드는 막는다', () => Parts.create({ name: 'cn9', cn: '720711001' }), 'INVALID_CN_CODE')
const p8 = await Parts.create({ name: '검증부품', cn: '7207 1100' })
ok(p8.cn === '7207 1100', '8자리 CN 코드는 통과한다')
await blocked('같은 부품명은 두 번 등록되지 않는다', () => Parts.create({ name: '검증부품', cn: '7208 5100' }), 'DUPLICATE_PART_NAME')

/* ── 요구사항 19번 · 발신자 매칭 ────────────────────────── */
H('이메일 접수 (요구사항 19번)')
const mail = (await Inbox.list()).content
const known = mail.filter(m => m.supplier)
ok(known.length > 0 && known.every(m => all.some(s => s.email === m.from)),
  '식별된 접수 건의 발신 주소는 실제로 어느 협력사의 담당자 이메일과 같다',
  known.map(m => m.from).join(' · '))
const un = mail.find(m => !m.supplier)
ok(un && !all.some(s => s.email === un.from), '「미확인」 건의 발신 주소는 어느 협력사와도 맞지 않는다', un?.from)
await blocked('없는 협력업체로는 연결되지 않는다', () => Inbox.assign(un.id, '없는회사'), 'SUPPLIER_NOT_FOUND')
const assigned = await Inbox.assign(un.id, '성진스틸')
ok(assigned.supplier === '성진스틸' && assigned.state === '검토 대기', '담당자가 지정하면 검토 대기로 넘어간다')

/* ── 요구사항 31·32번 · 확정과 반려 ─────────────────────── */
H('데이터 확정·반려 (요구사항 31·32번)')
const sub = await Analysis.get('sub-1')
ok((sub.missingFields ?? []).length > 0, '검토 화면 기본 건은 누락을 들고 있다 (확정 버튼이 잠겨야 한다)',
  `누락 ${sub.missingFields?.length} · 판정 ${sub.judgement} · 미등록 ${sub.unmappedParts?.length}`)
const again = await Analysis.get('sub-1')
ok(sub.latestAnalysisTaskId === again.latestAnalysisTaskId,
  '같은 제출 건을 다시 열어도 분석 작업이 새로 돌지 않는다', sub.latestAnalysisTaskId)
await blocked('누락이 있으면 서버가 확정을 막는다', () => Review.confirm('sub-1'), 'NOT_QUALIFIED')
await blocked('사유 없는 반려는 막는다', () => Review.reject('sub-1', '   '), 'REJECT_REASON_REQUIRED')
const rj = await Review.reject('sub-1', 'R2 필수 항목 누락 — 생산량·직접배출량', 'R2', 'NOT_SUBMITTED')
ok(rj.reason.includes('생산량') && rj.reasonCode === 'R2',
  '반려는 사유와 규칙 코드를 그대로 저장한다', `${rj.reasonCode} · ${rj.reason}`)
/* 상태값은 ADR-0005 대로 화면용 한글로 바뀌어 나온다 — 서버로 보낸 NOT_SUBMITTED 가 「미제출」로 돌아온다.
   reasonCode(R2) 는 상태값이 아니라 규칙 코드라 변환되지 않는다. */
ok(rj.status === '미제출' && rj.judgement === '부적격',
  '반려 응답의 상태값도 경계를 지나 한글로 나온다', `${rj.status} · ${rj.judgement}`)

/* ── 요구사항 14번 · 리마인드 ───────────────────────────── */
H('리마인드 발송 (요구사항 14번)')
await blocked('대상이 없으면 보내지 않는다', () => Deadlines.remind([]), 'NO_REMINDER_TARGET')
await blocked('targets 는 supplierId 를 갖는 객체 배열이다', () => Deadlines.remind([1, 2]), 'INVALID_REMINDER_TARGET')
const rm = await Deadlines.remind([{ supplierId: 1, partId: null }])
ok(rm.targetCount === 1 && rm.reportingMonth === '2026-09', '대상 수와 마감월을 그대로 돌려준다')

/* ── ADR-0008 · 발송 이력 전체 조회 ─────────────────────── */
H('발송 이력 (요구사항 51·53번 · ADR-0008)')
const hist = (await Feedback.list()).content
ok(hist.length > 1 && new Set(hist.map(r => r.supplier)).size > 1,
  '전체 조회는 협력업체를 고르지 않아도 전사 목록을 준다', `${hist.length}건 · ${new Set(hist.map(r => r.supplier)).size}곳`)
const failedOnly = (await Feedback.list({ status: '발송 실패' })).content
ok(failedOnly.length > 0 && failedOnly.every(r => r.state === '발송 실패'),
  '51번 — status 로 발송 실패만 걸러 온다 (48곳을 하나씩 부르지 않는다)', `${failedOnly.length}건`)
const first = all.find(s => hist.some(r => r.supplier === s.name))
const one = (await Feedback.list({ supplierId: first.id })).content
ok(one.length > 0 && one.every(r => r.supplier === first.name),
  'supplierId 를 주면 №31 처럼 그 업체 것만 준다 (협력업체 상세가 그렇게 부른다)',
  `${first.name} · ${one.length}건`)

/* ── 요구사항 30번 · 재발송 사유 ────────────────────────── */
H('피드백 재발송 (요구사항 30번)')
await blocked('사유 없는 재발송은 막는다', () => Feedback.resend('fb-4', ''), 'RESEND_REASON_REQUIRED')
const rs = await Feedback.resend('fb-4', 'SEND_FAILED')
ok(rs.attempt === 2, '재발송은 시도 회차를 올린다', 'attempt=' + rs.attempt)

console.log(fail ? `\n검사 ${n}건 · 실패 ${fail}건` : `\n검사 ${n}건 · 전부 통과`)
if (fail) process.exit(1)
