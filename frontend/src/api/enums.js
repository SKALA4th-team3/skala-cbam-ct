/* 상태값 경계 — 서버는 영문 enum, 화면은 한글.
   ADR-0005 로 정했다. 변환은 이 파일과 api/index.js 두 곳에서만 일어난다.
     · 응답: 영문 enum → 한글 라벨 (fromServer)
     · 요청: 한글 라벨 → 영문 enum (toServer)
   화면 코드와 요구사항 명세는 한글 그대로 유지된다.

   ⚠️ 매핑에 없는 값은 지어내지 않고 그대로 통과시킨다.
      요구사항 24번과 같은 규칙이다 — 모르면 채우지 말고 비운 채 지나간다.
      그래서 이 함수들은 목(한글)에도 실서버(영문)에도 안전하다. */

/** 협력업체 거래 상태 — 요구사항 「상태값」 · API 명세 v10 SupplierStatus */
export const SUPPLIER_TIE = {
  ACTIVE: '협력유지중',
  INACTIVE: '협력끊김',
}

/** 제출 데이터 판정 — 요구사항 「상태값」 · API 명세 v10 SubmissionStatus */
export const SUBMISSION_JUDGEMENT = {
  REVIEW_PENDING: '검토 대기',
  QUALIFIED: '적격',
  UNQUALIFIED: '부적격',
  NOT_SUBMITTED: '미제출',
}

/** 심각도 — 요구사항도 영문이라 변환할 것이 없다 (R1~R7 → 36번이 부여한다) */
export const SEVERITY = ['HIGH', 'MEDIUM', 'LOW']

/** 국가 — 서버는 ISO 3166-1 alpha-2 코드를 쓴다. 화면과 요구사항 명세는 한글이다.
 *  실서버에 확인했다: `?country=KR` 200 · `?country=대한민국` 400.
 *  목 데이터에 있는 세 곳만 넣는다 — 쓰지 않는 나라를 미리 채워 두지 않는다. */
export const COUNTRY = {
  KR: '대한민국',
  VN: '베트남',
  ID: '인도네시아',
}

/** 접수 상태 — API 명세 v10 №15 MailReceiptStatus.
 *  원본을 받아 확인했다 (docs/product/API_SPEC_V10_RECONCILE.md ⑤).
 *
 *  ⚠️ 명세의 enum 은 **여섯**인데 요구사항 「상태값」 절은 **넷**만 이름 붙였다.
 *     MATCHED(발신자 매칭됨)·ANALYZED(분석 완료)의 한글 이름을 팀이 정해야 한다 —
 *     아래 둘은 그때까지 쓰는 임시 이름이다.
 *
 *  ⚠️ `REJECTED` 를 여기 넣지 않았다. **№20 SubmissionStatus 의 REJECTED(반려)와 값이 같다.**
 *     지금 매핑표는 값 하나에 라벨 하나라 둘을 같이 넣으면 반려가 「접수 불가」로 보인다.
 *     필드 이름을 보고 갈라야 하는데(mail-receipts 의 status 인지 submissions 의 status 인지)
 *     그 구조를 넣을지는 실서버가 붙을 때 정한다 — 그전까지 접수 불가는 한글 그대로 오간다. */
export const MAIL_RECEIPT_STATUS = {
  WAITING: '접수 대기',
  MATCHED: '분석 대기',            // ⚠️ 「상태값」 절에 없는 이름 — 임시
  UNMATCHED: '미확인',
  ANALYZED: '분석 완료',           // ⚠️ 「상태값」 절에 없는 이름 — 임시
  ANALYSIS_FAILED: '분석 실패',
}

/** 분석 실패 사유 — 명세 №16 failureReason.
 *  ⚠️ R3 「스캔 품질 미달」을 담을 코드가 이 넷에 없다. 팀 확인 대기 */
export const ANALYSIS_FAILURE = {
  ENCRYPTED_FILE: '암호가 걸린 파일',
  PARSE_FAILED: '파싱 실패',
  UNSUPPORTED_FORMAT: '지원하지 않는 형식',
  NO_ATTACHMENT: '첨부 없음',
}

/* ⚠️ 아직 매핑하지 못한 것 — 지어내지 않고 비워 둔다.
     · 피드백 4값 — 초안 · 수정본 · 발송 대기 · 폐기 (요구사항 44~48번)
       명세 №27·№29 는 DRAFT · READY_TO_SEND · DISCARDED 와 source=AI|HUMAN_EDIT 를 쓰는데,
       요구사항의 「수정본」이 status 인지 source 인지 갈리지 않는다. 팀 확인 대기 */

/** enum → 한글 */
const LABEL = { ...SUPPLIER_TIE, ...SUBMISSION_JUDGEMENT, ...COUNTRY, ...MAIL_RECEIPT_STATUS }
/** 한글 → enum */
const CODE = Object.fromEntries(Object.entries(LABEL).map(([code, label]) => [label, code]))

/** 값 하나를 화면 표시값으로. 모르는 값은 그대로 */
export const toLabel = v => LABEL[v] ?? v
/** 값 하나를 서버로 보낼 enum 으로. 모르는 값은 그대로 */
export const toCode = v => CODE[v] ?? v

/** 변환 대상 필드 이름 — 이 필드만 훑는다. 숫자·문장은 건드리지 않는다.
    `country` 도 여기 있다 — 상태값은 아니지만 같은 경계에서 코드↔한글이 오간다 */
const STATUS_FIELDS = new Set(['tie', 'judgement', 'state', 'status', 'resultStatus', 'severity', 'country'])

const walk = (v, map) => {
  if (Array.isArray(v)) return v.map(x => walk(x, map))
  if (v && typeof v === 'object') {
    const out = {}
    for (const [k, val] of Object.entries(v))
      out[k] = STATUS_FIELDS.has(k) && typeof val === 'string' ? map(val)
        : (val && typeof val === 'object' ? walk(val, map) : val)
    return out
  }
  return v
}

/** 서버 응답을 화면 표시값으로. 목(한글)에는 아무 일도 일어나지 않는다 */
export const fromServer = body => walk(body, toLabel)

/** 화면이 고른 필터·정렬을 서버로 보낼 값으로. 배열 필터도 함께 바꾼다 */
export const toServer = (query = {}) => {
  const out = {}
  for (const [k, v] of Object.entries(query))
    out[k] = STATUS_FIELDS.has(k) || Array.isArray(v)
      ? (Array.isArray(v) ? v.map(toCode) : toCode(v))
      : v
  return out
}
