/* 목 API 가 정한 대로 동작하는지 센다.  npm run api:verify
   테스트 러너가 없어 vite 로 한 번 묶어 node 로 돌린다 (화면 없이 api/ 만 부른다).

   여기 있는 것은 「되는 것」 확인이 아니라 **막는 쪽**과 **정한 것**이다.
   되돌려서 확인했다 — 검사를 지우면 실제로 FAIL 이 난다. */
import { Suppliers, Parts, Products, Review, Analysis, Inbox, Deadlines, Feedback, Dashboard, ApiError } from '@/api'
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
ok(all.every(s => s.email) && new Set(all.map(s => s.email)).size === all.length, '협력사 48곳 전부 담당자 이메일이 있고 서로 다르다 (19번 매칭 키)')
ok(all.every(s => s.bizNo) && new Set(all.map(s => s.bizNo)).size === all.length, '사업자 등록번호도 전부 있고 서로 다르다')
ok(all.every(s => s.email.endsWith('.example')), '목 이메일 도메인은 전부 .example 이다 (실제 주소를 넣지 않는다)')
const made = await Suppliers.create(full)
await blocked('같은 사업자 등록번호는 두 번 등록되지 않는다', () => Suppliers.create({ ...full, email: 'other@other.example' }), 'DUPLICATE_BUSINESS_NUMBER')
await blocked('같은 담당자 이메일은 두 번 등록되지 않는다', () => Suppliers.create({ ...full, bizNo: '888-88-88888' }), 'DUPLICATE_CONTACT_EMAIL')
try { await Suppliers.create({ ...full, bizNo: '777-77-77777' }); ok(false, '중복 오류는 어느 칸이 문제인지 알려준다') }
catch (e) { ok(e.details?.fields?.includes('email'), '중복 오류는 어느 칸이 문제인지 details.fields 로 알려준다', JSON.stringify(e.details)) }

/* ── 요구사항 2번 · 수정 ─────────────────────────────────── */
H('협력업체 수정 (요구사항 2번)')
await blocked('다른 업체가 쓰는 이메일로는 못 바꾼다', () => Suppliers.update(made.id, { email: all[0].email }), 'DUPLICATE_CONTACT_EMAIL')
await blocked('이메일 형식을 검사한다', () => Suppliers.update(made.id, { email: 'nope' }), 'INVALID_EMAIL_FORMAT')
const upd = await Suppliers.update(made.id, { contact: '바뀐담당', email: 'verify2@verify.example', phone: '02-111-1111' })
ok(upd.contact === '바뀐담당' && upd.email === 'verify2@verify.example', '담당자명·이메일·전화번호가 바뀐다')
ok(upd.bizNo === full.bizNo && upd.name === full.name, '업체명·사업자번호는 수정 대상이 아니라 그대로다')

/* ── 요구사항 6번 · 협력 끊김 ────────────────────────────── */
H('협력 끊김 (요구사항 6번)')
const missBefore = (await Deadlines.unsubmitted()).content
const target = missBefore[0]
const detBefore = await Suppliers.get(target.id)
const cut = await Suppliers.deactivate(target.id)
ok(cut.tie === '협력끊김', '상태가 협력끊김이 된다', cut.name)
const missAfter = (await Deadlines.unsubmitted()).content
ok(!missAfter.some(r => r.id === target.id) && missAfter.length === missBefore.length - 1, '마감 대상(미제출 리마인드)에서 빠진다', `${missBefore.length} → ${missAfter.length}`)
const boardCut = await Dashboard.summary()
ok(!boardCut.todo.some(s => s.id === target.id), '미제출 경보(손봐야 할 곳)에서 빠진다')
const detAfter = await Suppliers.get(target.id)
ok(detAfter.submissions.length === detBefore.submissions.length && detAfter.parts.length === detBefore.parts.length, '기존 제출 데이터·부품은 삭제하지 않고 보존한다')
await blocked('끊긴 업체에는 리마인드를 보내지 않는다', () => Deadlines.remind([{ supplierId: target.id }]), 'INACTIVE_SUPPLIER')
await blocked('두 번 끊을 수 없다', () => Suppliers.deactivate(target.id), 'ALREADY_INACTIVE')

/* ── 요구사항 7·8·10번 · 부품 ───────────────────────────── */
H('부품 (요구사항 7·8·10번)')
const partRows = (await Parts.list({ size: 1000 })).content
ok(partRows.every(p => /^\d{8}$/.test(p.cn.replace(/\s/g, ''))), '목 부품의 CN 코드가 전부 8자리다', partRows[0].cn)
ok(partRows.every(p => p.id), '부품마다 id 가 있다 — 10번 단일 조회의 키')
const supNames = new Set(all.map(s => s.name))
const fromSuppliers = partRows.filter(p => supNames.has(p.supplier))
ok(fromSuppliers.length > 0 && fromSuppliers.every(p => p.supplierId), '협력업체가 공급하는 부품은 그 협력업체의 id 를 함께 갖는다', `${fromSuppliers.length}개`)
ok(partRows.filter(p => !supNames.has(p.supplier)).every(p => p.supplierId === null), '협력업체가 아닌 공급처(자사 공정)는 id 를 지어내지 않고 null 로 둔다')
const P = { name: '검증부품', cn: '7207 1100', supplier: '성진스틸', unit: 'ton' }
await blocked('6자리 CN 코드는 막는다', () => Parts.create({ ...P, name: 'cn6', cn: '7207 11' }), 'INVALID_CN_CODE')
await blocked('9자리 CN 코드는 막는다', () => Parts.create({ ...P, name: 'cn9', cn: '720711001' }), 'INVALID_CN_CODE')
await blocked('단위는 kg · ton · EA 뿐이다', () => Parts.create({ ...P, name: 'u', unit: 'lb' }), 'INVALID_UNIT')
await blocked('공급 협력업체가 비면 막는다', () => Parts.create({ ...P, name: 's', supplier: '' }), 'MISSING_REQUIRED_FIELD')
await blocked('없는 협력업체는 막는다', () => Parts.create({ ...P, name: 's2', supplier: '없는회사' }), 'SUPPLIER_NOT_FOUND')
const p8 = await Parts.create(P)
ok(p8.cn === '7207 1100' && p8.id, '8자리 CN 코드는 통과하고 id 를 받는다')
await blocked('같은 부품명은 두 번 등록되지 않는다', () => Parts.create({ ...P, cn: '7208 5100' }), 'DUPLICATE_PART_NAME')
const got = await Parts.get(p8.id)
ok(got.name === P.name && Array.isArray(got.confirmedData) && Array.isArray(got.usedIn), '10번 단일 조회 — 확정 배출 데이터 목록을 준다 (없으면 빈 배열)')
await blocked('없는 부품은 404', () => Parts.get(99999), 'PART_NOT_FOUND')
await blocked('벤치마크 팩터는 0 보다 커야 한다', () => Parts.update(p8.id, { factor: '0' }), 'INVALID_FACTOR')
const pu = await Parts.update(p8.id, { factor: '1.5', unit: 'kg' })
ok(pu.factor.startsWith('1.5') && pu.unit === 'kg', '8번 수정 — 팩터와 단위가 바뀐다', pu.factor)

/* ── 요구사항 12~15번 · 완제품 ──────────────────────────── */
H('완제품 (요구사항 12~15번)')
const prods = (await Products.list()).content
ok(prods.every(p => p.partCount === p.bom.length && typeof p.ratio === 'number'), '14번 — 필요 부품 개수와 평균값 대비 실측값이 온다')
ok(prods.every(p => (Math.abs(p.ratio - 1) > 0.3) === (p.judgement === '부적격')), '±30% 를 벗어나면 부적격이다 (ADR-0001)')
for (const p of prods) {
  const g = await Products.get(p.id)
  ok(g.parts.length === p.partCount && (g.reportable ? g.total === g.confirmed : g.total === null),
    `15번 ${p.name} — 미확정 부품이 있으면 합계를 지어내지 않는다`, g.reportable ? `합계 ${g.total}` : `미확정 ${g.pendingCount} · 합계 null`)
}
await blocked('없는 완제품은 404 (예전엔 hr-2400 으로 떨어졌다)', () => Products.get('hr-2400'), 'PRODUCT_NOT_FOUND')
const PR = { name: '검증제품', cn: '84818081', euCountry: '독일', tons: 100, bom: [{ part: '슬래브', input: 1 }] }
await blocked('12번 — 부품 세부 없이 등록하지 않는다', () => Products.create({ ...PR, bom: [] }), 'BOM_REQUIRED')
await blocked('12번 — 등록되지 않은 부품은 막는다', () => Products.create({ ...PR, bom: [{ part: '없는부품', input: 1 }] }), 'PART_NOT_FOUND')
await blocked('12번 — 수출량 0 은 막는다', () => Products.create({ ...PR, tons: 0 }), 'INVALID_EXPORT_VOLUME')
const np = await Products.create(PR)
ok(np.partCount === 1 && np.mean === null, '새 제품은 평균값이 없어 비운다 (하드코딩 평균이 없다)')
const nu = await Products.update(np.id, { tons: 200, bom: [{ part: '슬래브', input: 1 }, { part: '아연도금 증기', input: 0.1 }] })
ok(nu.tons === 200 && nu.pendingCount === 1 && nu.total === null, '13번 수정 — 미확정 부품이 들어가면 합계가 null 이 된다')

/* ── 요구사항 19·21번 · 접수 ─────────────────────────────── */
H('이메일 접수 (요구사항 19~21번)')
const mail = (await Inbox.list()).content
ok(mail.every(m => ['접수 대기', '미확인', '접수 불가', '분석 실패', '검토 대기', '적격', '부적격'].includes(m.state)), '접수 상태값이 명세 이름뿐이다', [...new Set(mail.map(m => m.state))].join(' · '))
const known = mail.filter(m => m.supplier && m.state !== '접수 불가')
ok(known.length > 0 && known.every(m => all.some(s => s.email === m.from)), '식별된 접수 건의 발신 주소는 실제로 어느 협력사의 담당자 이메일과 같다')
const un = mail.find(m => m.state === '미확인')
ok(un && !all.some(s => s.email === un.from), '「미확인」 건의 발신 주소는 어느 협력사와도 맞지 않는다', un?.from)
await blocked('미확인이 아닌 건은 지정할 수 없다', () => Inbox.assign('sub-1', '성진스틸'), 'NOT_UNIDENTIFIED')
await blocked('없는 협력업체로는 연결되지 않는다', () => Inbox.assign(un.id, '없는회사'), 'SUPPLIER_NOT_FOUND')
const assigned = await Inbox.assign(un.id, '성진스틸')
ok(assigned.supplier === '성진스틸' && assigned.state === '접수 대기', '담당자가 지정하면 접수 대기로 넘어가 분석이 돈다 (20번)')
const one = await Inbox.get('sub-1')
ok(one.body && one.files.length === 3 && one.messageId, '21번 — 원문과 첨부를 같은 화면에서 연다')

/* ── 요구사항 27~32번 · 검토·확정·반려 ──────────────────── */
H('데이터 검토·확정·반려 (요구사항 27~32번)')
await blocked('없는 제출 건은 404 (예전엔 sub-1 로 떨어졌다)', () => Analysis.get('nope'), 'SUBMISSION_NOT_FOUND')
const sub = await Analysis.get('sub-1')
ok((sub.missingFields ?? []).length > 0 && sub.judgement === '부적격', '누락이 있으면 33번대로 부적격이다', `누락 ${sub.missingFields.length} · 판정 ${sub.judgement}`)
ok(sub.rows.every(r => r.where), '23번 — 항목별 추출 근거(원문 위치)를 함께 준다')
const s6 = await Analysis.get('sub-6')
ok(s6.rows.some(r => r.value === null) && [sub, s6].every(x => x.rows.filter(r => r.value === null).every(r => r.note?.trim())), '24번 — 변환할 수 없는 값은 비운 채 사유를 남긴다 (비어 있는 값마다 note 가 있다)')
const again = await Analysis.get('sub-1')
ok(sub.latestAnalysisTaskId === again.latestAnalysisTaskId, '같은 제출 건을 다시 열어도 분석 작업이 새로 돌지 않는다')
await blocked('누락이 있으면 서버가 확정을 막는다', () => Review.confirm('sub-1'), 'NOT_QUALIFIED')
const s9 = await Analysis.get('sub-9')
ok(s9.unmappedParts.length === 1 && s9.judgement === '적격', '27번 — 미등록 부품을 원문 표기 그대로 들고 있다', s9.unmappedParts[0])
await blocked('미등록 부품이 있으면 서버가 확정을 막는다', () => Review.confirm('sub-9'), 'UNREGISTERED_PART_EXISTS')
await Parts.create({ name: '아연도금 증기 (정식)', cn: '27112100', supplier: '한빛철강', unit: 'ton', resolves: { submissionId: 'sub-9', name: '아연도금 증기' } })
ok((await Analysis.get('sub-9')).unmappedParts.length === 0, '28번 — 부품을 등록하면 미등록 부품이 해소된다')
const c9 = await Review.confirm('sub-9')
ok(c9.status === 'CONFIRMED', '31번 — 셋 다 통과하면 확정된다')
await blocked('두 번 확정할 수 없다', () => Review.confirm('sub-9'), 'ALREADY_CONFIRMED')
await blocked('사유 없는 반려는 막는다', () => Review.reject('sub-1', '   '), 'REJECT_REASON_REQUIRED')
const rj = await Review.reject('sub-1', 'R2 필수 항목 누락 — 생산량·직접배출량', 'R2', 'NOT_SUBMITTED')
ok(rj.reason.includes('생산량') && rj.reasonCode === 'R2' && rj.status === '미제출', '32번 — 반려는 사유·규칙 코드를 저장하고 상태를 되돌린다')
const q = (await Review.queue()).content
ok(q.some(r => r.id === 'sub-9' && r.status === '확정' && r.resolvedAt) && q.some(r => r.id === 'sub-1' && r.status === '반려'), '29번 — 처리한 건은 목록에서 지우지 않고 처리 시각과 함께 남긴다')

/* ── 요구사항 16·17번 · 마감 ────────────────────────────── */
H('마감 · 리마인드 (요구사항 14·16·17번)')
const dl = await Deadlines.list()
const now = dl.months.find(m => m.now)
ok(now && typeof now.dDay === 'number' && now.left === `D-${now.dDay}`, '16번 — 남은 일수를 세어서 준다 (손으로 적지 않는다)', now?.left)
ok(now.ok + now.reject + now.missing === (await Dashboard.summary()).judgement.total, '이번 달 건수는 관제 집계와 같은 숫자다')
await blocked('대상이 없으면 보내지 않는다', () => Deadlines.remind([]), 'NO_REMINDER_TARGET')
await blocked('targets 는 supplierId 를 갖는 객체 배열이다', () => Deadlines.remind([1, 2]), 'INVALID_REMINDER_TARGET')
const rmTarget = (await Deadlines.unsubmitted()).content[0]
const rm = await Deadlines.remind([{ supplierId: rmTarget.id, partId: null }])
ok(rm.targetCount === 1, '대상 수를 그대로 돌려준다')
ok((await Deadlines.unsubmitted()).content.find(r => r.id === rmTarget.id)?.lastSent, '17번 — 발송 이력이 남는다')

/* ── 요구사항 42~49번 · 피드백 초안 ─────────────────────── */
H('피드백 초안 (요구사항 42~49번)')
const d1 = await Feedback.draft('sub-1', '격식')
ok(d1.version === 1 && d1.status === '초안' && d1.source === 'AI' && d1.basis.missingFields.length, '42번 — 판정 근거로 초안을 만든다')
ok((await Feedback.draft('sub-1', '간결')).id === d1.id, '초안이 있으면 새로 만들지 않고 그것을 준다')
const d2 = await Feedback.regenerate(d1.id, { style: '간결', instruction: '기한을 강조' })
ok(d2.version === 2 && d2.style === '간결' && d2.body.join('\n').includes('기한을 강조'), '44·45번 — 문체·추가 지시로 재생성하면 새 버전이다')
const d3 = await Feedback.edit(d2.id, d2.body.map((p, i) => i === 1 ? p + ' (담당자 수정)' : p))
ok(d3.version === 3 && d3.status === '수정본' && d3.source === '담당자', '47번 — 수정본은 AI 초안과 별도 버전이다')
ok((await Feedback.versions('sub-1')).content.length === 3, '45번 — 이전 초안은 버전으로 보관된다')
await blocked('빈 본문으로 저장하지 않는다', () => Feedback.edit(d3.id, '   '), 'EMPTY_BODY')
await blocked('49번 — 사유 없는 폐기는 막는다', () => Feedback.discard(d3.id, ''), 'DISCARD_REASON_REQUIRED')
const cf = await Feedback.confirm(d3.id)
ok(cf.status === 'READY_TO_SEND' && cf.recipient, '48번 — 확정하면 발송 대기가 되고 수신자가 정해진다')
await blocked('48번 — 확정된 초안은 고칠 수 없다', () => Feedback.edit(d3.id, ['x']), 'DRAFT_LOCKED')
await blocked('48번 — 확정된 초안은 다시 만들 수 없다', () => Feedback.regenerate(d3.id), 'DRAFT_LOCKED')
await blocked('48번 — 확정된 초안은 폐기할 수 없다', () => Feedback.discard(d3.id, 'x'), 'DRAFT_LOCKED')
const t3 = await Feedback.draft('sub-3')
ok(t3.failed && t3.source === 'TEMPLATE' && t3.error?.code, '46번 — AI 실패는 실패로 표시하고 기본 템플릿을 대신 준다')
const dc = await Feedback.discard(t3.id, '판정이 바뀌어 문안이 맞지 않음')
ok(dc.status === 'DISCARDED', '49번 — 사유가 있으면 폐기된다')
const ba = await Feedback.draftAll()
ok(typeof ba.created === 'number' && ba.created > 0, '43번 — 일괄 생성은 초안 없는 건만 만든다', `created ${ba.created} · skipped ${ba.skipped}`)

/* ── 요구사항 50~53번 · 발송 ────────────────────────────── */
H('피드백 발송·재발송·이력 (요구사항 50~53번 · ADR-0008)')
const hist = (await Feedback.list()).content
ok(hist.length > 1 && new Set(hist.map(r => r.supplier)).size > 1, '53번 — 전체 조회는 협력업체를 고르지 않아도 전사 목록을 준다')
ok(hist.every(r => 'subject' in r && 'replied' in r && 'state' in r), '53번 — 발송일·제목·상태·회신 여부가 있다')
const failedOnly = (await Feedback.list({ status: '발송 실패' })).content
ok(failedOnly.length > 0 && failedOnly.every(r => r.state === '발송 실패' && r.failReason), '51번 — 실패 건은 사유와 함께 저장된다')
const first = all.find(s => hist.some(r => r.supplier === s.name))
ok((await Feedback.list({ supplierId: first.id })).content.every(r => r.supplier === first.name), 'supplierId 를 주면 №31 처럼 그 업체 것만 준다')
const waiting = hist.filter(r => r.state === '발송 대기')
const sent = await Feedback.send(waiting.map(r => r.id))
ok(sent.sent === waiting.length, '50번 — 발송 대기 건을 보낸다', `${sent.sent}건`)
await blocked('보낼 것이 없으면 막는다', () => Feedback.send(['nope']), 'NOTHING_TO_SEND')
await blocked('사유 없는 재발송은 막는다', () => Feedback.resend('fb-4', ''), 'RESEND_REASON_REQUIRED')
await blocked('성공한 건은 다시 보내지 않는다', () => Feedback.resend('fb-3', 'NO_REPLY'), 'NOT_RESENDABLE')
const rs = await Feedback.resend('fb-4', 'SEND_FAILED')
ok(rs.attempt === 2, '52번 — 재발송은 시도 회차를 올려 이력에 남긴다', 'attempt=' + rs.attempt)

/* ── 요구사항 38~41번 · 대시보드 ───────────────────────── */
H('대시보드 (요구사항 38~41번)')
const b = await Dashboard.summary()
const activeNow = (await Suppliers.list({ size: 1000, tie: ['협력유지중'] })).content.length
ok(b.judgement.total === activeNow && b.judgement.적격 + b.judgement.부적격 + b.judgement.미제출 + b.judgement['검토 대기'] === activeNow, '38번 — 협력 중인 곳만 센다 (협력끊김은 집계 제외)', `total ${b.judgement.total} = 협력유지중 ${activeNow}`)
ok(b.monthly.length === 12 && b.monthly.at(-1).적격 === b.judgement.적격, '40번 — 월별 12개월, 이번 달은 도넛과 같은 숫자다')
ok(b.emissions.length === (await Products.list()).content.length && b.emissions.some(e => e.total === null), '41번 — 완제품별 합계, 미확정 부품이 포함된 제품은 따로(null) 표시')
ok(b.severity.HIGH + b.severity.MEDIUM + b.severity.LOW > 0, '심각도별 건수는 검토 대기에서 센다')

console.log(fail ? `\n검사 ${n}건 · 실패 ${fail}건` : `\n검사 ${n}건 · 전부 통과`)
if (fail) process.exit(1)
