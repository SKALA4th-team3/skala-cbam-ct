package com.skala.cbam.dashboard;

import com.skala.cbam.dashboard.dto.DashboardAlertsResponse;
import com.skala.cbam.dashboard.dto.DashboardResponse;
import com.skala.cbam.dashboard.dto.DashboardStatus;
import com.skala.cbam.dashboard.repository.DashboardAlertRepository;
import com.skala.cbam.dashboard.repository.DashboardSubmissionRepository;
import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.parts.entity.PartUnit;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.submission.domain.Alert;
import com.skala.cbam.submission.domain.AlertStatus;
import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.domain.ValidationOutcome;
import com.skala.cbam.dashboard.service.DashboardService;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CBAM-73 (38·39·40번) 이 실제로 DB에서 읽어와 맞는 값을 돌려주는지 확인한다.
 * "돌아간다고 맞는 게 아니다" — 시드 데이터를 넣고 응답 숫자까지 검증한다.
 *
 * <p><b>@Transactional 로 롤백한다.</b> 예전에는 @BeforeEach 에서 deleteAll() 로 청소했는데,
 * dev 프로필의 H2(jdbc:h2:mem:cbam;DB_CLOSE_DELAY=-1)는 같은 JVM 안에서 공유돼
 * 같은 컨텍스트에 올라온 남의 테스트(SupplierApiTest 등) 시드까지 지웠다.
 * 실행 순서에 따라 결과가 달라지는 테스트는 없느니만 못하다.
 */
@SpringBootTest
@Transactional
@DisplayName("대시보드 API")
class DashboardApiTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private PartsRepository partsRepository;
    @Autowired
    private DashboardSubmissionRepository submissionRepository;
    @Autowired
    private DashboardAlertRepository alertRepository;
    @Autowired
    private WebApplicationContext context;

    private static final java.time.ZoneOffset SEOUL = java.time.ZoneOffset.ofHours(9);
    private static final LocalDate REPORTING_MONTH = LocalDate.of(2026, 9, 1);
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    private MockMvc mockMvc;
    private Supplier seongjin;
    private Part part2;

    @BeforeEach
    void seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        Supplier daehan = supplierRepository.save(supplier("대한금속", "111-11-11111", "kim@daehan.co.kr"));
        seongjin = supplierRepository.save(supplier("성진스틸", "222-22-22222", "lee@seongjin.co.kr"));

        // Part 를 저장하면 supplierIds 만큼 PartSupplier 가 cascade 로 함께 만들어진다
        Part part1 = partsRepository.save(part("PT-DASH-1", "열연강판", "72081000", daehan.getId()));
        part2 = partsRepository.save(part("PT-DASH-2", "봉강", "72139110", seongjin.getId()));

        // target1 = 대한금속 x 열연강판 → 이번 달 적격 제출 있음
        PartSupplier target1 = onlyTarget(part1);
        // target2 = 성진스틸 x 봉강 → 이번 달 제출 없음 (미제출)
        PartSupplier target2 = onlyTarget(part2);

        submissionRepository.save(Submission.builder()
                .supplier(daehan)
                .partSupplierId(target1.getId())
                .reportingMonth(REPORTING_MONTH)
                .status(SubmissionStatus.CONFIRMED)
                .judgement(Judgement.QUALIFIED)
                .submittedAt(at(2026, 9, 2, 14, 20))
                .build());

        alertRepository.save(unsubmittedAlert(target2, "마감 D-7 경과 미제출"));
    }

    @Test
    @DisplayName("대시보드 조회는 target 기준으로 적격·미제출을 집계한다")
    void aggregatesByTarget() {
        DashboardResponse response = dashboardService.getDashboard(MONTH, null);

        assertThat(response.month()).isEqualTo("2026-09");
        assertThat(response.counts().total()).isEqualTo(2);
        assertThat(response.counts().qualified()).isEqualTo(1);
        assertThat(response.counts().unqualified()).isEqualTo(0);
        assertThat(response.counts().notSubmitted()).isEqualTo(1);
        assertThat(response.ratio().qualified()).isEqualTo(0.5);
        assertThat(response.severity().get("HIGH")).isEqualTo(1L);
        assertThat(response.suppliers()).hasSize(2);
    }

    @Test
    @DisplayName("경보 조회는 미제출 대상의 협력업체명과 부품명을 함께 반환한다")
    void returnsAlertTargetNames() {
        DashboardAlertsResponse response = dashboardService.getAlerts(MONTH, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        DashboardAlertsResponse.AlertItem alert = response.content().get(0);
        assertThat(alert.ruleId()).isEqualTo("R1");
        assertThat(alert.severity()).isEqualTo(Severity.HIGH);
        assertThat(alert.submissionId()).isNull();
        assertThat(alert.target().supplierId()).isEqualTo(seongjin.getId());
        assertThat(alert.target().partId()).isEqualTo(part2.getId());
        assertThat(alert.supplierName()).isEqualTo("성진스틸");
        assertThat(alert.partName()).isEqualTo("봉강");
    }

    // ── 요구사항 6번: 협력 끊김 업체는 마감 대상·미제출 경보에서 제외된다 (막는 쪽) ──

    @Test
    @DisplayName("협력 끊김 업체는 part_supplier 가 ACTIVE 여도 마감 대상 모수에서 빠진다")
    void excludesInactiveSupplierFromTargets() {
        seedInactiveSupplierWithActiveTarget();

        DashboardResponse response = dashboardService.getDashboard(MONTH, null);

        // 끊긴 업체를 세면 total 3 · notSubmitted 2 가 된다. 6번은 「제외된다」로 못 박은 쪽이다.
        assertThat(response.counts().total()).isEqualTo(2);
        assertThat(response.counts().notSubmitted()).isEqualTo(1);
        assertThat(response.suppliers())
                .extracting(DashboardResponse.SupplierSummary::companyName)
                .doesNotContain("폐업금속");
    }

    @Test
    @DisplayName("협력 끊김 업체의 미제출 경보는 목록과 심각도 집계 양쪽에서 빠진다")
    void excludesInactiveSupplierFromUnsubmittedAlerts() {
        seedInactiveSupplierWithActiveTarget();

        DashboardAlertsResponse alerts = dashboardService.getAlerts(MONTH, null, null, 0, 20);
        assertThat(alerts.totalElements()).isEqualTo(1);
        assertThat(alerts.content())
                .extracting(DashboardAlertsResponse.AlertItem::supplierName)
                .doesNotContain("폐업금속");

        // 목록에서 뺀 경보를 severity 합계에는 남겨 두면 두 숫자가 어긋난다.
        assertThat(dashboardService.getDashboard(MONTH, null).severity().get("HIGH")).isEqualTo(1L);
    }

    // ── 38번 「업체별 상태와 건수」: 업체가 목록에서 사라지지 않는다 (막는 쪽) ──

    @Test
    @DisplayName("ACTIVE target 이 없어도 이번 달 제출이 있으면 업체가 목록에 남는다")
    void keepsSupplierWithSubmissionButNoActiveTarget() {
        Supplier noTarget = supplierRepository.save(
                supplier("무연계철강", "444-44-44444", "choi@notarget.co.kr"));
        submissionRepository.save(Submission.builder()
                .supplier(noTarget)
                .reportingMonth(REPORTING_MONTH)
                .status(SubmissionStatus.REVIEW_PENDING)
                .submittedAt(at(2026, 9, 2, 9, 0))
                .build());

        DashboardResponse response = dashboardService.getDashboard(MONTH, null);

        // targets 만 순회하면 이 업체는 제출을 했는데도 화면에서 통째로 사라진다.
        assertThat(response.suppliers())
                .extracting(DashboardResponse.SupplierSummary::companyName)
                .contains("무연계철강");
        // 모수는 target 단위 그대로다 — 목록에 실린다고 counts 가 늘지는 않는다 (ADR-0005)
        assertThat(response.counts().total()).isEqualTo(2);

        DashboardResponse.SupplierSummary row = response.suppliers().stream()
                .filter(s -> s.companyName().equals("무연계철강")).findFirst().orElseThrow();
        assertThat(row.submissionCount()).isEqualTo(1);
        assertThat(row.pendingCount()).isEqualTo(1);
        // 제출한 업체를 「미제출」로 표시하면 안 된다
        assertThat(row.status()).isNotEqualTo(DashboardStatus.NOT_SUBMITTED);
    }

    @Test
    @DisplayName("협력 끊김 업체는 제출이 있어도 목록에 들어오지 않는다")
    void stillExcludesInactiveSupplierThatSubmitted() {
        Supplier closed = supplier("폐업금속", "333-33-33333", "park@closed.co.kr");
        closed.deactivate("거래 종료");
        Supplier saved = supplierRepository.save(closed);
        submissionRepository.save(Submission.builder()
                .supplier(saved)
                .reportingMonth(REPORTING_MONTH)
                .status(SubmissionStatus.CONFIRMED)
                .judgement(Judgement.QUALIFIED)
                .submittedAt(at(2026, 9, 2, 9, 0))
                .build());

        assertThat(dashboardService.getDashboard(MONTH, null).suppliers())
                .extracting(DashboardResponse.SupplierSummary::companyName)
                .doesNotContain("폐업금속");
    }

    // ── 잘못된 파라미터는 500 이 아니라 400 으로 막는다 (공통 규약 3항) ──

    @Test
    @DisplayName("month 형식이 틀리면 400 INVALID_PARAMETER 로 막는다")
    void rejectsMalformedMonth() throws Exception {
        for (String bad : new String[] {"abc", "2026-13", "2026/09"}) {
            mockMvc.perform(get("/api/v1/dashboard").param("month", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
    }

    @Test
    @DisplayName("page·size 가 범위를 벗어나면 400 INVALID_PARAMETER 로 막는다")
    void rejectsOutOfRangePaging() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/alerts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/v1/dashboard/alerts").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("month 를 주지 않으면 현재월로 200 을 반환한다")
    void defaultsToCurrentMonth() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(YearMonth.now().toString()));
    }

    // ── 시드 보조 ──────────────────────────────────────────────────

    /** 끊긴 업체 + ACTIVE 인 공급 관계 + 미제출 경보. supplier.status 와 part_supplier.status 는 다른 축이다. */
    private void seedInactiveSupplierWithActiveTarget() {
        Supplier closed = supplier("폐업금속", "333-33-33333", "park@closed.co.kr");
        closed.deactivate("거래 종료");
        closed = supplierRepository.save(closed);

        Part part3 = partsRepository.save(part("PT-DASH-3", "형강", "72161000", closed.getId()));
        PartSupplier target3 = onlyTarget(part3);

        alertRepository.save(unsubmittedAlert(target3, "끊긴 업체 미제출"));
    }

    /** 서울 기준 시각. 정식 엔티티는 OffsetDateTime 을 쓴다. */
    private static OffsetDateTime at(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, SEOUL);
    }

    /** 이 테스트가 만드는 부품은 공급 협력업체가 하나뿐이다. */
    private static PartSupplier onlyTarget(Part part) {
        return part.getSuppliers().iterator().next();
    }

    private static Part part(String code, String name, String cnCode, Long supplierId) {
        return new Part(code, name, cnCode, PartUnit.TON, new java.math.BigDecimal("1.8000"),
                java.util.Set.of(supplierId));
    }

    private static Supplier supplier(String name, String businessNumber, String email) {
        return Supplier.builder()
                .businessRegistrationNumber(businessNumber)
                .name(name)
                .countryCode("KR")
                .contactName("담당자")
                .contactEmail(email)
                .contactPhone("02-0000-0000")
                .build();
    }

    private static Alert unsubmittedAlert(PartSupplier target, String message) {
        return Alert.builder()
                .partSupplierId(target.getId())
                .checkId("SUBMISSION_MISSING")
                .outcome(ValidationOutcome.FAIL)
                .reportingMonth(REPORTING_MONTH)
                .ruleId("R1")
                .severity(Severity.HIGH)
                .message(message)
                .status(AlertStatus.OPEN)
                .validatedAt(at(2026, 9, 3, 9, 0))
                .build();
    }
}
