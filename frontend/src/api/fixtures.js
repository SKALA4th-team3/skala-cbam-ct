/* 화면별 목 데이터. seed.js(협력사·부품)와 달리 화면 하나에서만 쓰는 것들이다.
   숫자는 되도록 seed 에서 «세어서» 만든다 — 손으로 적은 숫자는 seed 를 고치면 어긋난다. */
import { suppliers, parts } from '@/mocks/seed'

/** 목의 «오늘». fixtures 의 날짜가 전부 2026년 9월 초에 맞춰져 있어 실제 시계를 쓰면 매일 어긋난다.
 *  실서버가 붙으면 서버가 준 dDay·deadline 을 쓴다 — 이 값은 목에서만 산다. */
export const NOW = '2026-09-03'

/** 담당자 이메일은 seed 가 유일한 출처다 — 요구사항 19번의 매칭 키다.
 *  실제 주소를 쓰지 않기 위해 seed 의 도메인은 전부 `.example` 이다. */
const mailOf = name => suppliers.find(s => s.name === name)?.email ?? null
/** 같은 도메인의 없는 사서함 — 발송 실패(550)를 보여주기 위한 것 */
const deadBox = name => 'no-such-box@' + (mailOf(name) ?? '@unknown.example').split('@')[1]

/* ── 완제품 (명세 12·14·15번) ──────────────────────────────
   14번 표시 항목 — ① 제품명 ② CN코드 ③ 연간 수출량 ④ 필요 부품 개수 ⑤ 평균값과 실측값.
   ⚠️ 평균값(mean)은 명세대로 하드코딩이다. ratio 는 실측÷평균이라 손으로 적지 않는다.
   부품 세부(bom)는 부품명·투입량(t/t)뿐이다 — 협력사·팩터·상태는 seed 의 부품에서 그때그때 끌어온다. */
export const PRODUCTS = [
  { id: 'valve-a', name: '밸브 A형', cn: '8481 8081', cnGroup: '8481 밸브', euCountry: '네덜란드', tons: 6200,
    bom: [{ part: '슬래브', input: 1.08 }, { part: '빌릿', input: 0.35 }, { part: '열간압연 전력', input: 0.42 }],
    mean: 3146.8, actual: 3524.4 },
  { id: 'valve-b', name: '밸브 B형', cn: '8481 8081', cnGroup: '8481 밸브', euCountry: '독일', tons: 2900,
    bom: [{ part: '슬래브', input: 0.98 }, { part: '아연도금 증기', input: 0.02 }],
    mean: 866.8, actual: 1135.5 },
  { id: 'flange', name: '플랜지', cn: '7307 1910', cnGroup: '7307 관 연결구', euCountry: '네덜란드', tons: 900,
    bom: [{ part: '봉강', input: 1.12 }, { part: '포장재', input: 0.03 }],
    mean: 162.3, actual: 152.6 },
]

/* ── 이메일 접수 (명세 18~21번) ────────────────────────────
   상태값은 명세 「상태값」 절 그대로다 — 접수 대기 · 미확인 · 접수 불가 · 분석 실패.
   분석이 끝난 건은 제출 데이터 상태(검토 대기 …)를 그대로 보여준다.
   전에는 「분석 중」「완료」처럼 명세에 없는 이름을 썼다.
   원문(body)과 첨부(files)는 21번·30번이 「같은 화면에서 연다」고 해서 들고 있는다. */
const F = (name, size) => ({ name, size })
export const INBOX = [
  { id: 'sub-1', at: '14:21', receivedAt: '2026-09-02T14:21:00', supplier: '성진스틸', from: mailOf('성진스틸'),
    subject: '3분기 배출량', files: [F('3Q_배출량.xlsx', '82 KB'), F('공정도.pdf', '1.2 MB'), F('계량기록.pdf', '640 KB')],
    state: '접수 대기', tone: 'processing', messageId: '<a1f3@seongjin.example>',
    body: '안녕하세요, 성진스틸 김민수입니다.\n3분기 배출량 자료 첨부해 보냅니다.\n전력사용량 982,000 kWh, 무연탄 1,250 t 이고 증기는 4,200 입니다.\n확인 부탁드립니다.' },
  { id: 'sub-2', at: '11:04', receivedAt: '2026-09-02T11:04:00', supplier: '대한화학', from: mailOf('대한화학'),
    subject: '[회신] 3분기', files: [F('emission_q3.pdf', '310 KB')],
    state: '검토 대기', tone: 'expiring', messageId: '<77c0@daehan.example>',
    body: '요청하신 3분기 자료입니다. 산세 처리제·황산 생산량과 배출량을 정리했습니다.' },
  { id: 'sub-7', at: '09:38', receivedAt: '2026-09-02T09:38:00', supplier: '우진포장', from: mailOf('우진포장'),
    subject: '3분기 자료', files: [F('우진_3Q.xlsx', '44 KB')],
    state: '검토 대기', tone: 'expiring', messageId: '<0b9e@ujin.example>',
    body: '3분기 골판지 상자 생산량 및 전력 사용량 자료 보내드립니다.' },
  { id: 'sub-3', at: '09:12', receivedAt: '2026-09-02T09:12:00', supplier: '한성금속', from: mailOf('한성금속'),
    subject: '자료 송부', files: [F('scan_0902.pdf', '4.8 MB')],
    state: '분석 실패', tone: 'missing', messageId: '<c31d@hanseong.example>',
    reason: '스캔 품질 미달 — 표를 읽지 못했습니다 (R3). 재요청문을 보냅니다',
    body: '(스캔본 첨부)' },
  { id: 'rc-5', at: '08:12', receivedAt: '2026-09-02T08:12:00', supplier: null, from: 'unknown@not-registered.example',
    subject: '자료 보냅니다', files: [F('data.xlsx', '51 KB')],
    state: '미확인', tone: 'missing', messageId: '<9f2a@not-registered.example>',
    reason: '발신 주소가 어느 협력사의 담당자 이메일과도 맞지 않습니다 (19번)',
    body: '자료 첨부합니다.' },
  { id: 'rc-6', at: '07:55', receivedAt: '2026-09-02T07:55:00', supplier: '태양주물', from: mailOf('태양주물'),
    subject: '3분기', files: [],
    state: '접수 불가', tone: 'missing', messageId: '<41ee@taeyang.example>',
    reason: '첨부가 없습니다 (20번) — xlsx · csv · pdf 중 하나가 필요합니다',
    body: '자료는 다음 주에 보내겠습니다.' },
]

/* ── 제출 데이터 (명세 23~37번) ────────────────────────────
   UC-05 가 뱉는 표준화 결과 — 원문과 표준값을 나란히 둔다 (30번).
   31번이 확정을 막는 조건 셋(누락·판정·미등록 부품)을 전부 데이터로 들고 있어야
   화면이 그것을 검사할 수 있다.
     sub-1  누락(R2)+단위 불명확(R5) → 33번대로 부적격. 확정이 막히는 쪽
     sub-9  미등록 부품(R6)만 남은 건 — 28번으로 부품을 등록하면 확정할 수 있게 되는 흐름
     sub-7  세 조건을 전부 통과하는 건 — 없으면 「확정 → 집계 반영」을 한 번도 볼 수 없다
     sub-3  분석 실패(R3) — 22번·46번(AI 실패 → 기본 템플릿)이 이 건에서 보인다 */
const STEPS = ['업로드', '텍스트 추출', '항목 매핑', '단위 환산', '적격성 검증']
const SUB = (o) => ({ steps: STEPS, missingFields: [], unmappedParts: [], attachments: [], analysisFailed: false, ...o })

export const SUBMISSIONS = {
  'sub-1': SUB({
    id: 'sub-1', supplier: '성진스틸', supplierId: 1, period: '2026 3분기', reportingMonth: '2026-09',
    submittedAt: '2026-09-02T14:21:00', item: '열연강판 · CN 7208', rule: 'R2', why: '필수 항목 누락 · 단위 불명확', severity: 'HIGH',
    attachments: INBOX[0].files, revision: 2,
    rows: [
      { field: 'electricity',      raw: '전력사용량: 982,000 kWh', value: '982',   unit: 'MWh', note: '환산 kWh→MWh', tone: 'complete', where: 'xlsx 시트1 B4' },
      { field: 'fuel_anthracite',  raw: '무연탄 1,250 t',          value: '1,250', unit: 't',   note: '확인',          tone: 'complete', where: 'xlsx 시트1 B6' },
      { field: 'cn_code',          raw: '제품: 열연강판 (두께 2.4)', value: '7208 5100', unit: '', note: '추론 · 신뢰도 0.86', tone: 'expiring', where: '본문 2행' },
      { field: 'steam',            raw: '증기 사용량 4,200',        value: '4,200', unit: null,  note: 'R5 단위 불명확 — 변환하지 않고 비운다 (24번)', tone: 'anomaly', where: '본문 3행' },
      { field: 'fuel_natural_gas', raw: '연료 사용량: (기재 없음)',  value: null,   unit: '',    note: 'R2 필수 항목 누락', tone: 'missing', where: '—' },
    ],
    missingFields: ['fuel_natural_gas'],
    judgement: '부적격',           // 33번 — 누락이 있으면 부적격
  }),
  'sub-9': SUB({
    id: 'sub-9', supplier: '한빛철강', supplierId: 2, period: '2026 3분기', reportingMonth: '2026-09',
    submittedAt: '2026-09-01T16:40:00', item: '아연도금 증기 · CN 2711', rule: 'R6', why: '미등록 부품 1건', severity: 'LOW',
    attachments: [F('hanbit_q3.xlsx', '63 KB')], revision: 1,
    rows: [
      { field: 'electricity', raw: '전력사용량: 410,000 kWh', value: '410', unit: 'MWh', note: '환산 kWh→MWh', tone: 'complete', where: 'xlsx 시트1 B3' },
      { field: 'part_name',   raw: '품명: 아연도금 증기',      value: '아연도금 증기', unit: '', note: 'R6 등록 부품과 매칭 실패 — 원문 표기 그대로 (27번)', tone: 'missing', where: 'xlsx 시트1 A7' },
      { field: 'production',  raw: '생산량 3,200 t',          value: '3,200', unit: 't', note: '확인', tone: 'complete', where: 'xlsx 시트1 B5' },
    ],
    /* R6 가 걸렸을 때 판정을 부적격으로 볼지는 미결이다(REQUIREMENTS 「R1~R7 → 상태」).
       여기서는 적격으로 두어 «미등록 부품만 해소하면 확정된다» 는 28번 흐름이 보이게 한다. 확정된 값이 아니다. */
    judgement: '적격',
    unmappedParts: ['아연도금 증기'],
  }),
  'sub-7': SUB({
    id: 'sub-7', supplier: '우진포장', supplierId: 6, period: '2026 3분기', reportingMonth: '2026-09',
    submittedAt: '2026-09-02T09:38:00', item: '골판지 · CN 4819', rule: null, why: '규칙에 걸린 것 없음', severity: null,
    attachments: INBOX[2].files, revision: 1,
    rows: [
      { field: 'electricity',     raw: '전력사용량: 128,000 kWh', value: '128', unit: 'MWh', note: '환산 kWh→MWh', tone: 'complete', where: 'xlsx B2' },
      { field: 'fuel_anthracite', raw: '무연탄 210 t',            value: '210', unit: 't',   note: '확인',          tone: 'complete', where: 'xlsx B4' },
      { field: 'cn_code',         raw: '제품: 골판지 상자',        value: '4819 1000', unit: '', note: '확인',      tone: 'complete', where: '본문 1행' },
    ],
    judgement: '적격',
  }),
  'sub-3': SUB({
    id: 'sub-3', supplier: '한성금속', supplierId: 3, period: '2026 3분기', reportingMonth: '2026-09',
    submittedAt: '2026-09-02T09:12:00', item: '주물 · CN 7325', rule: 'R3', why: '스캔 품질 미달 · 자료 적격성 불가', severity: 'MEDIUM',
    attachments: INBOX[3].files, revision: 1,
    rows: [], judgement: '부적격', analysisFailed: true,
    failure: { code: 'PARSE_FAILED', message: '스캔 품질이 낮아 표를 읽지 못했습니다 (R3)' },
  }),
  'sub-4': SUB({
    id: 'sub-4', supplier: '화신알루미늄', supplierId: 4, period: '2026 3분기', reportingMonth: '2026-09',
    submittedAt: '2026-09-01T10:05:00', item: '압출재 · CN 7604', rule: 'R4', why: '평균값 대비 +31%', severity: 'MEDIUM',
    attachments: [F('hwashin_q3.xlsx', '71 KB')], revision: 1,
    rows: [
      { field: 'production',  raw: '생산량 1,900 t',          value: '1,900', unit: 't',   note: '확인', tone: 'complete', where: 'xlsx B3' },
      { field: 'electricity', raw: '전력 2,410 MWh',          value: '2,410', unit: 'MWh', note: '확인', tone: 'complete', where: 'xlsx B4' },
      { field: 'intensity',   raw: '(계산) 1.27 tCO₂e/t',    value: '1.27',  unit: 'tCO₂e/t', note: 'R4 동일 품목 평균 0.97 대비 +31% (허용 ±30%)', tone: 'anomaly', where: '34번 계산' },
    ],
    judgement: '부적격',
  }),
  'sub-5': SUB({
    id: 'sub-5', supplier: '태양주물', supplierId: 5, period: '2026 3분기', reportingMonth: '2026-09',
    submittedAt: '2026-08-31T18:20:00', item: '철강 · CN 7325', rule: 'R7', why: '이전 기간 대비 58% 증가', severity: 'MEDIUM',
    attachments: [F('taeyang_q3.csv', '12 KB')], revision: 1,
    rows: [
      { field: 'production', raw: '생산량 850 t',  value: '850',  unit: 't',   note: '확인', tone: 'complete', where: 'csv 2행' },
      { field: 'emission',   raw: '직접배출 1,940', value: '1,940', unit: 'tCO₂e', note: 'R7 직전 마감 1,228 대비 +58% (설정 50%)', tone: 'anomaly', where: 'csv 3행' },
    ],
    judgement: '부적격',
  }),
  'sub-6': SUB({
    id: 'sub-6', supplier: '대양금속', supplierId: 7, period: '2026 3분기', reportingMonth: '2026-09',
    submittedAt: '2026-08-30T15:00:00', item: '도금강판 · CN 7210', rule: 'R5', why: '단위 불명확', severity: 'LOW',
    attachments: [F('daeyang.xlsx', '38 KB')], revision: 1,
    rows: [
      { field: 'production', raw: '생산 2,100',  value: null, unit: null, note: 'R5 단위가 없어 변환하지 않는다 — 비운 채 사유만 남긴다 (24번)', tone: 'anomaly', where: 'xlsx B2' },
      { field: 'electricity', raw: '전력 1,020 MWh', value: '1,020', unit: 'MWh', note: '확인', tone: 'complete', where: 'xlsx B3' },
    ],
    judgement: '부적격',
  }),
}
export const SUBMISSION = SUBMISSIONS['sub-1']

/** 검토 대기 목록 (29번) — 제출 데이터에서 센다. 따로 적지 않는다.
 *  미제출(R1)은 제출 «행이 없어» 여기 안 온다 — 마감 화면(16·17번)이 다룬다. */
export const QUEUE = Object.values(SUBMISSIONS).map(s => ({
  id: s.id, supplier: s.supplier, supplierId: s.supplierId, item: s.item, rule: s.rule, why: s.why,
  severity: s.severity, judgement: s.judgement, submittedAt: s.submittedAt, status: '검토 대기',
}))

/** UC-08 — 명세 33·34·35 의 검증 3종 */
export const CHECKS = [
  { key: 'required', tag: '필수', tone: 'missing', title: '필수 항목 검증',
    desc: '부품 · 생산량 · 직접 배출량이 비어 있거나, 제출일자의 월이 현재 월과 다르면 부적격.',
    options: ['켬', '끔'], value: '켬' },
  { key: 'mean', tag: '이상치', tone: 'anomaly', title: '평균값 비교',
    desc: '배출 원단위(배출량 ÷ 생산량)를 동일 품목 평균값과 비교. 허용 범위를 벗어나면 부적격.',
    options: ['± 20%', '± 30%', '± 50%'], value: '± 30%' },
  { key: 'delta', tag: '변동', tone: 'expiring', title: '이전 기간 대비 변동 검사',
    desc: '같은 협력업체 · 부품의 직전 마감일 값과 비교. 이전 데이터가 없으면 건너뜁니다.',
    options: ['30% 이상', '50% 이상', '70% 이상'], value: '50% 이상' },
]

/** 위험 탐지 규칙 R1~R7 — REQUIREMENTS.md 의 표 그대로. 심각도는 42번 비고와 전부 일치한다.
 *  전에는 R5 설명이 「unitUncertain = true」, R6 이름이 「배출 유형 불일치」로 명세와 달랐다. */
export const SEVERITIES = [
  { rule: 'R1', name: '미제출',              desc: '마감일이 지났는데 제출 자료가 없음',                 action: 'AI 작성 안내문',    severity: 'HIGH' },
  { rule: 'R2', name: '필수 항목 누락',       desc: '부품 · 생산량 · 직접 배출량 중 빈 값',               action: 'AI 작성 안내문',    severity: 'HIGH' },
  { rule: 'R3', name: '자료 적격성 불가',     desc: '암호 · 파싱 실패 · 스캔 품질 미달',                  action: 'AI 재요청문',      severity: 'MEDIUM' },
  { rule: 'R4', name: '평균값 대비 이상치',   desc: '동일 품목 평균값 ± 30% 초과',                       action: 'AI 확인 요청문',    severity: 'MEDIUM' },
  { rule: 'R5', name: '단위 불명확',         desc: '표준 단위로 변환할 수 없는 값',                      action: 'AI 확인 요청문',    severity: 'LOW' },
  { rule: 'R6', name: '미등록 부품',         desc: '추출 품명이 등록 부품과 매칭되지 않음',              action: '담당자 수동 등록',   severity: 'LOW' },
  { rule: 'R7', name: '이전 기간 대비 급변', desc: '직전 마감일 값 대비 50% 이상 변동',                 action: 'AI 확인 요청문',    severity: 'MEDIUM' },
]

/* ── 제출 마감 (명세 16·17번) ──────────────────────────────
   마감일은 월별 말일 고정. 남은 일수는 NOW 에서 센다 — 「D-27」을 손으로 적지 않는다. */
export const DEADLINES = [
  { month: '2026-09', deadline: '2026-09-30', now: true },
  { month: '2026-08', deadline: '2026-08-31', ok: 44, reject: 3, missing: 1 },
  { month: '2026-07', deadline: '2026-07-31', ok: 46, reject: 2, missing: 0 },
]

/** 리마인드 이력 — 협력사별 마지막 발송. 대상(미제출 업체)은 seed 에서 세고 여기서는 이력만 가진다 */
export const REMINDER_LOG = {
  성진스틸: { lastSent: '2026-08-28', months: 5 },
  한빛철강: { lastSent: '2026-08-28', months: 5 },
  태양주물: { lastSent: null, months: 1 },
  포항정밀: { lastSent: '2026-09-01', months: 1 },
  동양특수강: { lastSent: null, months: 2 },
}

/* ── 발송 이력 (명세 50~53번) ──────────────────────────────
   53번 — 업체별 발송 이력(발송일, 제목, 상태, 회신 여부). 실패 사유는 51번. */
export const DISPATCH = [
  { id: 'fb-1', draftId: 'fd-1', submissionId: 'sub-1', rule: 'R2', supplier: '성진스틸',    to: mailOf('성진스틸'),    subject: '3분기 자료 보완 요청',   sentAt: null,                  confirmedAt: '2026-09-02T15:10', state: '발송 대기', tone: 'expiring', replied: null,  attempts: 0, note: '잠김' },
  { id: 'fb-6', draftId: 'fd-6', submissionId: 'sub-6', rule: 'R5', supplier: '대양금속',    to: mailOf('대양금속'),    subject: '단위 확인 요청',         sentAt: null,                  confirmedAt: '2026-09-02T15:12', state: '발송 대기', tone: 'expiring', replied: null,  attempts: 0, note: '잠김' },
  { id: 'fb-3', draftId: 'fd-3', submissionId: 'sub-4', rule: 'R4', supplier: '화신알루미늄', to: mailOf('화신알루미늄'), subject: '배출 원단위 확인 요청',  sentAt: '2026-09-01T09:22',    confirmedAt: '2026-09-01T09:00', state: '발송 성공', tone: 'complete', replied: true,  attempts: 1, note: '회신 있음' },
  { id: 'fb-4', draftId: 'fd-4', submissionId: 'sub-5', rule: 'R7', supplier: '태양주물',    to: deadBox('태양주물'),   subject: '변동 확인 요청',         sentAt: '2026-09-01T11:40',    confirmedAt: '2026-09-01T11:30', state: '발송 실패', tone: 'missing',  replied: null,  attempts: 1, note: '550 Mailbox not found', failReason: '주소 오류 (550 Mailbox not found)' },
  { id: 'fb-5', draftId: 'fd-5', submissionId: 'sub-3', rule: 'R3', supplier: '한성금속',    to: mailOf('한성금속'),    subject: '자료 재요청',            sentAt: '2026-08-28T14:05',    confirmedAt: '2026-08-28T14:00', state: '회신 없음', tone: 'anomaly',  replied: false, attempts: 1, note: '6일 경과' },
]

/* ── 피드백 초안 (명세 42~46번) ────────────────────────────
   초안은 판정 근거(37번)에서 만든다. 없는 값을 채우자고 요구하지 않고, 무엇이 왜 비었는지만 적는다.
   문체 3종(44번)은 같은 근거를 다르게 «말할» 뿐이다. */
const FIELD_KO = {
  fuel_natural_gas: '천연가스 사용량', steam: '증기 사용량', production: '생산량',
  electricity: '전력 사용량', emission: '직접 배출량', part_name: '부품명', intensity: '배출 원단위',
}
const DUE = '10월 15일'

export function draftBody(sub, style = '격식', instruction = '') {
  const gaps = (sub.missingFields ?? []).map(f => `${FIELD_KO[f] ?? f} — 제출 자료에 기재가 없습니다`)
  const rowIssues = (sub.rows ?? []).filter(r => r.tone === 'anomaly' || r.tone === 'missing')
    .filter(r => !(sub.missingFields ?? []).includes(r.field))
    .map(r => `${FIELD_KO[r.field] ?? r.field} — ${r.note}`)
  const unmapped = (sub.unmappedParts ?? []).map(p => `「${p}」 — 등록된 부품과 맞지 않습니다. 정식 품명을 알려주세요`)
  const items = [...gaps, ...rowIssues, ...unmapped]
  const list = items.length ? items.map(i => '- ' + i).join('\n') : `- ${sub.why}`
  const extra = instruction.trim() ? `\n${instruction.trim()}` : ''

  if (style === '간결') return [
    `${sub.supplier} 담당자님`,
    `${sub.period} 자료에서 ${items.length || 1}개 항목 확인이 필요합니다.`,
    list,
    `${DUE}까지 회신 부탁드립니다. 엑셀 그대로 보내주셔도 됩니다.${extra}`,
    'CBAM CT',
  ]
  if (style === '친근') return [
    `${sub.supplier} 담당자님, 안녕하세요.`,
    `보내주신 ${sub.period} 자료 잘 받았습니다. ${items.length || 1}가지만 더 확인하면 마무리됩니다.`,
    list,
    `${DUE}까지만 알려주시면 됩니다. 편하신 형식으로 주셔도 괜찮습니다.${extra}`,
    '고맙습니다.\nCBAM CT 드림',
  ]
  return [
    `${sub.supplier} 담당자님께`,
    `${sub.period} 배출량 산정을 위해 보내주신 자료를 검토했습니다. 아래 ${items.length || 1}개 항목이 확인되지 않아 산정을 마무리하지 못하고 있습니다.`,
    list,
    `${DUE}까지 회신해 주시면 신고에 반영됩니다. 형식은 기존에 보내주신 그대로면 충분합니다.${extra}`,
    '감사합니다.\nCBAM CT 드림',
  ]
}

/** 46번 — AI 가 실패했을 때 대신 내는 기본 템플릿. 근거 없는 문장을 만들지 않는다 —
 *  무엇이 문제인지는 규칙 코드와 사유만으로 말한다. */
export const TEMPLATE_BODY = sub => [
  `${sub.supplier} 담당자님께`,
  `${sub.period} 제출 자료를 확인하는 과정에서 다음 사항이 확인되었습니다.`,
  `- ${sub.rule ? sub.rule + ' · ' : ''}${sub.why}`,
  `${DUE}까지 자료를 다시 보내주시기 바랍니다. 문의는 이 메일로 회신해 주십시오.`,
  'CBAM CT',
]

/** 22번 — 분석 실패 건에 붙는 안내 (R3 재요청문의 근거) */
export const inboxOf = id => INBOX.find(m => m.id === id) ?? null
export { mailOf, parts as SEED_PARTS }
