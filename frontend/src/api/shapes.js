/* 필드 **이름**이 서버와 화면에서 다른 자리. 값 변환은 enums.js 가 한다 (ADR-0005).
   두 축을 섞지 않는다 — 이름은 여기, 값은 저기.

   실서버 계약을 직접 띄워 확인했다 (`GET /v3/api-docs` · 2026-09-04).
     요청 SupplierCreateRequest : companyName · businessRegistrationNumber · country · contactName · contactEmail · phone
     응답 SupplierSummaryResponse: id · companyName · country · status · monthlyStatus[]
   화면(과 목 데이터)은 name · bizNo · contact · email · tie · judgement · strip 을 쓴다.

   ⚠️ **응답에 없는 것은 지어내지 않고 null 로 둔다.** 명세 №3 이 주지 않는 값이다 —
      `city`(도시) · `item`(품목)은 목업 화면이 만든 값이고 요구사항 3번에 없다. */
import { toCode } from './enums'

/** 화면 폼 → 서버 등록 요청 (명세 №1 · 요구사항 1번) */
export const supplierToServer = f => ({
  companyName: f.name,
  businessRegistrationNumber: f.bizNo,
  country: toCode(f.country),          // 대한민국 → KR
  contactName: f.contact,
  contactEmail: f.email,
  phone: f.phone,
})

/* 월별 제출 상태 → 12개월 스트립 문자.
   SubmissionStrip 의 규칙은 `0` 문제없음 · `1` 부적격 · `2` 미제출 이다.
   **모르는 값을 「문제없음」으로 칠하지 않는다** — 모르면 눈에 띄는 쪽(미제출)에 둔다. */
const STRIP_CHAR = { QUALIFIED: '0', UNQUALIFIED: '1', NOT_SUBMITTED: '2' }
const stripOf = months =>
  (months ?? []).slice(-12).map(m => STRIP_CHAR[m.status] ?? '2').join('').padStart(12, '0')

/** 서버 목록 행 → 화면 행 (명세 №3).
    judgement 는 응답에 없다. **가장 최근 달의 제출 상태에서 끌어온다** —
    제출 행이 없으면 미제출이라는 것이 명세 자신의 표현이다(「제출 데이터 행이 없어 id=null + target」).
    ⚠️ 이 유도는 확인받은 것이 아니다. API-CONTRACT 에 적어 뒀다. */
export const supplierRowFromServer = r => ({
  id: r.id,
  name: r.companyName,
  country: r.country,                                  // KR → 값 변환은 enums.fromServer 가 한다
  tie: r.status,                                       // ACTIVE → 협력유지중
  judgement: r.monthlyStatus?.at(-1)?.status ?? 'NOT_SUBMITTED',
  strip: stripOf(r.monthlyStatus),
  city: null,                                          // 명세 №3 에 없다. 지어내지 않는다
  item: null,
})

/** 서버 상세 → 화면 상세 (명세 №4 · 요구사항 5번) */
export const supplierDetailFromServer = r => ({
  ...supplierRowFromServer(r),
  bizNo: r.businessRegistrationNumber,
  contact: r.contactName,
  email: r.contactEmail,
  phone: r.phone,
  strip: stripOf(r.monthlyStatus),
  parts: r.parts ?? [],
  submissions: r.submissions ?? [],
  alerts: r.alerts ?? [],
  feedbackHistories: r.feedbackHistories ?? [],
})
