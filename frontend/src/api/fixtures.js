/* 화면별 목 데이터. seed.js(협력사·부품)와 달리 화면 하나에서만 쓰는 것들이다. */

export const PRODUCTS = [
  { id: 'valve-a', name: '밸브 A형', cn: '8481 80', cnGroup: '8481 밸브', tons: 6200, partCount: 3, actual: 3524.4, ratio: 1.12, judgement: '적격' },
  { id: 'valve-b', name: '밸브 B형', cn: '8481 80', cnGroup: '8481 밸브', tons: 2900, partCount: 2, actual: 1135.5, ratio: 1.31, judgement: '부적격' },
  { id: 'flange',  name: '플랜지',   cn: '7307 19', cnGroup: '7307 관 연결구', tons: 900, partCount: 2, actual: 152.6, ratio: 0.94, judgement: '적격' },
]

export const EMISSIONS = {
  'hr-2400': {
    product: '열간압연 강판 HR-2400', cn: '7208 51', reportable: false,
    total: 4812.5, confirmed: 3180.2, pending: 1632.3,
    parts: [
      { name: '슬래브', supplier: '성진스틸', input: 1.08, factor: 1.92, state: '확정' },
      { name: '열간압연 전력', supplier: '자사 (포항)', input: 0.42, factor: 0.42, state: '확정' },
      { name: '아연도금 증기', supplier: '한빛철강', input: 0.02, factor: null, state: '미확정' },
    ],
    blocking: ['아연도금 증기 — 벤치마크 미등록'],
  },
}

export const INBOX = [
  { id: 'sub-1', at: '14:21', supplier: '성진스틸', from: 'cs.kim@sungjin.co.kr', subject: '3분기 배출량', files: 'xlsx 1 · pdf 2', state: '분석 중', tone: 'processing' },
  { id: 'sub-2', at: '11:04', supplier: '대한화학', from: 'park@daehan-chem.kr', subject: '[회신] 3분기', files: 'pdf 1', state: '검토 대기', tone: 'expiring' },
  { id: 'sub-3', at: '09:38', supplier: '우진포장', from: 'jy@woojin.kr', subject: '3분기 자료', files: 'xlsx 1', state: '완료', tone: 'complete' },
  { id: 'sub-4', at: '08:12', supplier: null, from: 'unknown@nowhere.kr', subject: '자료 보냅니다', files: 'xlsx 1', state: '미확인', tone: 'missing' },
]

/** UC-05 가 뱉는 표준화 결과 — 원문과 표준값을 나란히 둔다 (NFR-04: 추정 금지)
    명세 31 이 확정을 막는 조건 셋(누락·판정·미등록 부품)을 전부 데이터로 들고 있어야
    화면이 그것을 검사할 수 있다. sub-9 는 「막히는 쪽」을 눈으로 보기 위한 건이다. */
const SUB_1 = {
  id: 'sub-1', supplier: '성진스틸', period: '2026 3분기', taskId: null,
  steps: ['업로드', '텍스트 추출', '항목 매핑', '단위 환산', '적격성 검증'],
  rows: [
    { field: 'electricity',      raw: '전력사용량: 982,000 kWh', value: '982',   unit: 'MWh', note: '환산 kWh→MWh', tone: 'complete' },
    { field: 'fuel_anthracite',  raw: '무연탄 1,250 t',          value: '1,250', unit: 't',   note: '확인',          tone: 'complete' },
    { field: 'cn_code',          raw: '제품: 열연강판 (두께 2.4)', value: '7208 51', unit: '',  note: '추론 · 신뢰도 0.86', tone: 'expiring' },
    { field: 'steam',            raw: '증기 사용량 4,200',        value: '4,200', unit: '?',   note: 'R5 단위 불명확', tone: 'anomaly' },
    { field: 'fuel_natural_gas', raw: '연료 사용량: (기재 없음)',  value: null,   unit: '',    note: 'R2 missingFields 등재', tone: 'missing' },
  ],
  missingFields: ['fuel_natural_gas'],
  judgement: '적격',            // 명세 상태값: 검토 대기 · 적격 · 부적격 · 미제출
  unmappedParts: [],            // 명세 25 — 등록 부품과 매칭되지 않은 품명
}

const SUB_9 = {
  id: 'sub-9', supplier: '한빛철강', period: '2026 3분기', taskId: null,
  steps: SUB_1.steps,
  rows: [
    { field: 'electricity', raw: '전력사용량: 410,000 kWh', value: '410', unit: 'MWh', note: '환산 kWh→MWh', tone: 'complete' },
    { field: 'part_name',   raw: '품명: 아연도금 증기',      value: '아연도금 증기', unit: '', note: 'R6 등록 부품과 매칭 실패', tone: 'missing' },
  ],
  missingFields: [],
  judgement: '부적격',
  unmappedParts: ['아연도금 증기'],
}

/* 세 조건을 모두 통과하는 건 — 없으면 「확정 → 집계 반영」을 한 번도 볼 수 없다 */
const SUB_7 = {
  id: 'sub-7', supplier: '우진포장', period: '2026 3분기', taskId: null,
  steps: SUB_1.steps,
  rows: [
    { field: 'electricity',     raw: '전력사용량: 128,000 kWh', value: '128', unit: 'MWh', note: '환산 kWh→MWh', tone: 'complete' },
    { field: 'fuel_anthracite', raw: '무연탄 210 t',            value: '210', unit: 't',   note: '확인',          tone: 'complete' },
    { field: 'cn_code',         raw: '제품: 골판지 상자',        value: '4819 10', unit: '', note: '확인',         tone: 'complete' },
  ],
  missingFields: [],
  judgement: '적격',
  unmappedParts: [],
}

export const SUBMISSIONS = { 'sub-1': SUB_1, 'sub-7': SUB_7, 'sub-9': SUB_9 }
export const SUBMISSION = SUB_1

export const QUEUE = [
  { id: 'sub-1', supplier: '성진스틸',    item: '열연강판 · CN 7208', rule: 'R2', why: '필수 2개 누락',                 severity: 'HIGH' },
  { id: 'sub-9', supplier: '한빛철강',    item: '봉강 · CN 7214',     rule: 'R1', why: '마감 경과, 자료 없음',           severity: 'HIGH' },
  { id: 'sub-3', supplier: '한성금속',    item: '주물 · CN 7325',     rule: 'R3', why: '스캔 품질 미달 · 자료 적격성 불가', severity: 'MEDIUM' },
  { id: 'sub-4', supplier: '화신알루미늄', item: '압출재 · CN 7604',   rule: 'R4', why: '평균값 대비 +31%',              severity: 'MEDIUM' },
  { id: 'sub-5', supplier: '태양주물',    item: '철강 · CN 7325',     rule: 'R7', why: '이전 기간 대비 58% 증가',        severity: 'MEDIUM' },
  { id: 'sub-6', supplier: '대양금속',    item: '도금강판 · CN 7210', rule: 'R5', why: '단위 불명확',                   severity: 'LOW' },
  { id: 'sub-7', supplier: '우진포장', item: '골판지 · CN 4819', rule: null, why: '규칙에 걸린 것 없음 · 확정 가능', severity: 'LOW' },
]

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

/** 명세 42번 비고 — R1~R2 High / R3~R4·R7 Medium / R5~R6 Low */
export const SEVERITIES = [
  { rule: 'R1', name: '미제출',            desc: '마감일이 지났는데 제출 자료가 없음',        action: 'AI 작성 안내문',  severity: 'HIGH' },
  { rule: 'R2', name: '필수 항목 누락',     desc: '부품 · 생산량 · 직접 배출량 중 빈 값',      action: 'AI 작성 안내문',  severity: 'HIGH' },
  { rule: 'R3', name: '자료 적격성 불가',   desc: '암호 · 파싱 실패 · 스캔 품질 미달',         action: 'AI 재요청문',    severity: 'MEDIUM' },
  { rule: 'R4', name: '평균값 대비 이상치', desc: '동일 품목 평균값 ± 30% 초과',              action: 'AI 확인 요청문',  severity: 'MEDIUM' },
  { rule: 'R5', name: '단위 불명확',       desc: 'unitUncertain = true',                    action: '담당자 확인',    severity: 'LOW' },
  { rule: 'R6', name: '배출 유형 불일치',   desc: '공정 유형과 배출원 매핑 불일치',            action: '담당자 확인',    severity: 'LOW' },
  { rule: 'R7', name: '이전 기간 대비 변동', desc: '직전 마감일 값 대비 50% 이상 변동',        action: 'AI 확인 요청문',  severity: 'MEDIUM' },
]

export const DEADLINES = [
  { month: '2026-09-30', ok: 31, reject: 12, missing: 5, left: 'D-27', state: '진행 중', tone: 'processing', now: true },
  { month: '2026-08-31', ok: 44, reject: 3,  missing: 1, left: '마감',  state: '종료',   tone: 'complete' },
  { month: '2026-07-31', ok: 46, reject: 2,  missing: 0, left: '마감',  state: '종료',   tone: 'complete' },
]

export const REMINDERS = [
  { id: 1, name: '성진스틸',   email: 'cs.kim@sungjin.co.kr',  lastSent: '2026-08-28',  overdue: '5개월',  late: true,  checked: true },
  { id: 2, name: '한빛철강',   email: 'lee@hanbit-steel.kr',   lastSent: '2026-08-28',  overdue: '5개월',  late: true,  checked: true },
  { id: 5, name: '태양주물',   email: 'ceo@taeyang.kr',        lastSent: '보낸 적 없음', overdue: '이번 달', late: false, checked: true },
  { id: 6, name: '포항정밀',   email: 'admin@pohangjm.kr',     lastSent: '2026-09-01',  overdue: '이번 달', late: false, checked: false },
  { id: 7, name: '동양특수강', email: 'sales@dyss.co.kr',      lastSent: '보낸 적 없음', overdue: '2개월',  late: true,  checked: false },
]

export const DISPATCH = [
  { id: 'fb-1', rule: 'R2', supplier: '성진스틸',    line: 'cs.kim@sungjin.co.kr · 3분기 자료 보완 요청', when: '확정 2026-09-02 15:10', state: '발송 대기', tone: 'expiring', note: '잠김' },
  { id: 'fb-2', rule: 'R1', supplier: '한빛철강',    line: 'lee@hanbit-steel.kr · 3분기 자료 미제출 안내', when: '확정 2026-09-02 15:10', state: '발송 대기', tone: 'expiring', note: '잠김' },
  { id: 'fb-6', rule: 'R5', supplier: '대양금속',    line: 'yun@daeyang.kr · 단위 확인 요청',              when: '확정 2026-09-02 15:12', state: '발송 대기', tone: 'expiring', note: '잠김' },
  { id: 'fb-3', rule: 'R4', supplier: '화신알루미늄', line: 'kim@hwashin-al.kr · 배출 원단위 확인 요청',   when: '발송 2026-09-01 09:22', state: '발송 성공', tone: 'complete', note: '회신 있음' },
  { id: 'fb-4', rule: 'R7', supplier: '태양주물',    line: 'no-such-box@taeyang.kr · 변동 확인 요청',     when: '550 Mailbox not found',  state: '발송 실패', tone: 'missing',  note: '재발송 1회' },
  { id: 'fb-5', rule: 'R2', supplier: '한성금속',    line: 'choi@hansung.kr · 3분기 자료 보완 요청',      when: '발송 2026-08-28 14:05', state: '회신 없음', tone: 'anomaly',  note: '6일 경과' },
]

/** 명세 44 — 문체 3종. 근거 없이 문장을 만들지 않는다 */
export const TONES = {
  격식: [
    '성진스틸 담당자님께',
    '2026년 3분기 배출량 산정을 위해 보내주신 자료를 검토했습니다. 아래 2개 항목이 확인되지 않아 산정을 마무리하지 못하고 있습니다.',
    '- 천연가스 사용량 — 제출 자료에 기재가 없습니다.\n- 증기 사용량 단위 — 4,200 의 단위(t 또는 GJ)가 적혀 있지 않습니다.',
    '10월 15일까지 회신해 주시면 3분기 신고에 반영됩니다. 형식은 기존에 보내주신 엑셀 그대로면 충분합니다.',
    '감사합니다.\nCBAM CT 드림',
  ],
  간결: [
    '성진스틸 담당자님',
    '3분기 자료에서 2개 항목이 비어 있습니다.',
    '- 천연가스 사용량: 기재 없음\n- 증기 사용량 단위: t 인지 GJ 인지 불명',
    '10월 15일까지 회신 부탁드립니다. 엑셀 그대로 보내주셔도 됩니다.',
    'CBAM CT',
  ],
  친근: [
    '성진스틸 담당자님, 안녕하세요.',
    '보내주신 3분기 자료 잘 받았습니다. 두 가지만 더 확인하면 마무리됩니다.',
    '- 천연가스 사용량이 자료에 안 보입니다.\n- 증기 4,200 의 단위가 t 인지 GJ 인지 헷갈립니다.',
    '10월 15일까지만 알려주시면 됩니다. 편하신 형식으로 주셔도 괜찮습니다.',
    '고맙습니다.\nCBAM CT 드림',
  ],
}
