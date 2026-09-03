package com.skala.cbam.dashboard;

import com.skala.cbam.dashboard.dto.DashboardAlertsResponse;
import com.skala.cbam.dashboard.dto.DashboardResponse;
import com.skala.cbam.dashboard.entity.Alert;
import com.skala.cbam.dashboard.entity.AlertStatus;
import com.skala.cbam.dashboard.entity.JudgementStatus;
import com.skala.cbam.dashboard.entity.LifecycleStatus;
import com.skala.cbam.dashboard.entity.Part;
import com.skala.cbam.dashboard.entity.PartSupplier;
import com.skala.cbam.dashboard.entity.SeverityCode;
import com.skala.cbam.dashboard.entity.Submission;
import com.skala.cbam.dashboard.entity.SubmissionStatus;
import com.skala.cbam.dashboard.entity.Supplier;
import com.skala.cbam.dashboard.repository.AlertRepository;
import com.skala.cbam.dashboard.repository.PartRepository;
import com.skala.cbam.dashboard.repository.PartSupplierRepository;
import com.skala.cbam.dashboard.repository.SubmissionRepository;
import com.skala.cbam.dashboard.repository.SupplierRepository;
import com.skala.cbam.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CBAM-73 (38·39·40번) 이 실제로 DB에서 읽어와 맞는 값을 돌려주는지 확인한다.
 * "돌아간다고 맞는 게 아니다" — 시드 데이터를 넣고 응답 숫자까지 검증한다.
 *
 * MockMvc 관련 모듈(spring-boot-webmvc-test-autoconfigure)이 지금 build.gradle의
 * 테스트 스타터 구성에서 안 잡혀서, HTTP 계층 대신 서비스 레이어를 직접 호출한다.
 * 컨트롤러는 파라미터를 그대로 서비스에 넘기기만 하는 얇은 계층이라 위험은 낮다고 봤다.
 */
@SpringBootTest
class DashboardApiTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private PartRepository partRepository;
    @Autowired
    private PartSupplierRepository partSupplierRepository;
    @Autowired
    private SubmissionRepository submissionRepository;
    @Autowired
    private AlertRepository alertRepository;

    private static final LocalDate REPORTING_MONTH = LocalDate.of(2026, 9, 1);
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    private Supplier seongjin;
    private Part part2;

    @BeforeEach
    void seed() {
        alertRepository.deleteAll();
        submissionRepository.deleteAll();
        partSupplierRepository.deleteAll();
        partRepository.deleteAll();
        supplierRepository.deleteAll();

        Supplier daehan = supplierRepository.save(
                Supplier.builder().name("대한금속").status(LifecycleStatus.ACTIVE).build());
        seongjin = supplierRepository.save(
                Supplier.builder().name("성진스틸").status(LifecycleStatus.ACTIVE).build());

        Part part1 = partRepository.save(Part.builder().name("열연강판").build());
        part2 = partRepository.save(Part.builder().name("봉강").build());

        // target1 = 대한금속 x 열연강판 → 이번 달 적격 제출 있음
        PartSupplier target1 = partSupplierRepository.save(
                PartSupplier.builder().supplier(daehan).part(part1).status(LifecycleStatus.ACTIVE).build());
        // target2 = 성진스틸 x 봉강 → 이번 달 제출 없음 (미제출)
        PartSupplier target2 = partSupplierRepository.save(
                PartSupplier.builder().supplier(seongjin).part(part2).status(LifecycleStatus.ACTIVE).build());

        submissionRepository.save(Submission.builder()
                .supplier(daehan)
                .partSupplier(target1)
                .reportingMonth(REPORTING_MONTH)
                .status(SubmissionStatus.CONFIRMED)
                .judgement(JudgementStatus.QUALIFIED)
                .submittedAt(LocalDateTime.of(2026, 9, 2, 14, 20))
                .build());

        alertRepository.save(Alert.builder()
                .partSupplier(target2)
                .reportingMonth(REPORTING_MONTH)
                .ruleId("R1")
                .severity(SeverityCode.HIGH)
                .message("마감 D-7 경과 미제출")
                .status(AlertStatus.OPEN)
                .validatedAt(LocalDateTime.of(2026, 9, 3, 9, 0))
                .build());
    }

    @Test
    void 대시보드_조회는_target_기준으로_적격_미제출을_집계한다() {
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
    void 경보_조회는_미제출_대상의_협력업체명과_부품명을_함께_반환한다() {
        DashboardAlertsResponse response = dashboardService.getAlerts(MONTH, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        DashboardAlertsResponse.AlertItem alert = response.content().get(0);
        assertThat(alert.ruleId()).isEqualTo("R1");
        assertThat(alert.severity()).isEqualTo(SeverityCode.HIGH);
        assertThat(alert.submissionId()).isNull();
        assertThat(alert.target().supplierId()).isEqualTo(seongjin.getId());
        assertThat(alert.target().partId()).isEqualTo(part2.getId());
        assertThat(alert.supplierName()).isEqualTo("성진스틸");
        assertThat(alert.partName()).isEqualTo("봉강");
    }
}
