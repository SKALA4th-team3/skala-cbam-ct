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

/* ─────────────────────────────────────────────────────────────
   부품 · 완제품 · 제출 데이터 — 실서버 응답을 화면 모양으로 옮긴다.
   실서버를 띄워(dev,mock) 응답을 직접 받아 확인했다. 2026-09-04.

   ⚠️ 응답에 없는 값은 null 로 둔다. 화면 목이 만들어 두었던 값이라도 지어내지 않는다.
   ───────────────────────────────────────────────────────────── */

/** '72081000' → '7208 1000'. 화면은 네 자리씩 끊어 보여 준다 */
const cnPretty = c => (c ? `${String(c).slice(0, 4)} ${String(c).slice(4)}` : null)
/** 화면이 CN 필터에 쓰는 앞 두 자리 묶음. 서버가 분류명을 주지 않아 코드만 쓴다 */
const cnGroupOf = c => (c ? `${String(c).slice(0, 2)} 류` : null)

/** 서버 부품 → 화면 행 (명세 №7·№8 · 요구사항 9·10번).
    화면은 factor 를 단위까지 붙은 문자열로 읽는다 — 서버는 숫자만 준다. */
export const partRowFromServer = r => ({
  id: r.id,
  name: r.partName,
  partCode: r.partCode,
  cn: cnPretty(r.cnCode),
  cnGroup: cnGroupOf(r.cnCode),
  // 서버는 공급 협력업체를 여러 곳 줄 수 있다. 화면 행은 한 곳만 보여 주므로 첫 곳을 쓰고 전체도 남긴다
  supplier: r.suppliers?.[0]?.name ?? null,
  supplierId: r.suppliers?.[0]?.supplierId ?? null,
  suppliers: r.suppliers ?? [],
  factor: r.benchmarkFactor == null ? null : `${Number(r.benchmarkFactor)} tCO₂e/t`,
  factorYear: r.benchmarkFactorYear ?? null,
  unit: r.unit,
})

/** 서버 부품 상세 → 화면 상세. 10번의 「협력업체별 확정 배출 데이터」가 suppliers[].confirmedData 에 온다 */
export const partDetailFromServer = r => ({
  ...partRowFromServer(r),
  confirmedData: (r.suppliers ?? []).flatMap(s =>
    (s.confirmedData ?? []).map(d => ({ ...d, supplier: s.name, supplierId: s.supplierId }))),
  usedIn: [],                                   // 명세 №8 응답에 없다. 지어내지 않는다
})

/** 서버 완제품 목록 행 → 화면 행 (명세 №11 · 요구사항 14번).
    화면은 mean(평균값)·actual(실측값)을 읽는다. 서버는 benchmarkEmission·actualEmission 이다.
    ⚠️ actual 은 미확정 부품이 있으면 null 이다 — 0 으로 바꾸지 않는다(15번). */
export const productRowFromServer = r => ({
  id: r.id,
  name: r.productName,
  cn: cnPretty(r.cnCode),
  cnGroup: cnGroupOf(r.cnCode),
  tons: r.annualExportTon,
  partCount: r.requiredPartCount,
  mean: r.benchmarkEmission,
  actual: r.actualEmission,
  ratio: r.gapRatio == null ? null : +(1 + Number(r.gapRatio)).toFixed(2),
  pendingCount: r.unconfirmedPartCount ?? 0,
  reportable: r.calculationStatus === 'COMPLETE',
  euCountry: null,                              // 목록 응답에 없다. 상세에서 온다
  bom: null,
})

/** 서버 완제품 상세 → 화면 상세 (명세 №12 · 요구사항 15번) */
export const productDetailFromServer = r => {
  const parts = (r.parts ?? []).map(p => ({
    part: p.partName,
    partId: p.partId,
    supplier: p.supplierName,
    input: p.inputQtyPerTon,
    state: p.status,                            // 값 변환은 enums.fromServer 가 한다
    intensity: p.emissionIntensity,
    contribution: p.contribution,
  }))
  return {
    id: r.id,
    name: r.productName,
    cn: cnPretty(r.cnCode),
    cnGroup: cnGroupOf(r.cnCode),
    tons: r.annualExportTon,
    euCountry: (r.exportCountries ?? []).join(' · ') || null,
    exportCountries: r.exportCountries ?? [],
    reportingMonth: r.reportingMonth,
    partCount: parts.length,
    mean: r.benchmarkEmission,
    actual: r.embeddedEmission,
    // 15번 — 미확정 부품이 하나라도 있으면 합계를 지어내지 않는다
    total: r.embeddedEmission,
    confirmed: parts.reduce((a, p) => a + (Number(p.contribution) || 0), 0) || null,
    reportable: r.calculationStatus === 'COMPLETE',
    pendingCount: (r.missingPartIds ?? []).length,
    blocking: (r.parts ?? []).filter(p => p.contribution == null)
      .map(p => `${p.partName} — 확정 배출데이터 없음`),
    appliedFactorYear: r.appliedFactorYear,
    parts,
    bom: parts,
  }
}

/** 서버 제출 목록 행 → 화면 행 (명세 №20 · 요구사항 29번) */
export const submissionRowFromServer = r => ({
  id: r.id,
  supplier: r.supplierName,
  supplierId: r.target?.supplierId ?? null,
  partId: r.target?.partId ?? null,
  item: r.partName,
  reportingMonth: r.target?.reportingMonth ?? null,
  period: r.target?.reportingMonth ?? null,
  submittedAt: r.submittedAt,
  status: r.status,                             // REVIEW_PENDING → 검토 대기 (enums)
  judgement: r.judgement,                       // QUALIFIED → 적격
  severity: r.severity,
  unmappedParts: Array(r.unregisteredPartCount ?? 0).fill('미등록 부품'),
  rule: null,                                   // 목록 응답에 없다 — 상세에서 온다
  why: null,
})
