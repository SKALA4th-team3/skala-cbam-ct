package com.skala.cbam.submission;

import com.skala.cbam.submission.domain.Alert;
import com.skala.cbam.submission.domain.AlertStatus;
import com.skala.cbam.submission.domain.DataValueType;
import com.skala.cbam.submission.domain.ExtractionField;
import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.domain.UnregisteredPart;
import com.skala.cbam.submission.domain.UnregisteredPartStatus;
import com.skala.cbam.submission.domain.ValidationOutcome;
import com.skala.cbam.submission.dto.SubmissionRejectRequest;
import com.skala.cbam.submission.dto.SubmissionSearchCondition;
import com.skala.cbam.submission.error.SubmissionErrorCode;
import com.skala.cbam.submission.error.SubmissionException;
import com.skala.cbam.submission.repository.AlertRepository;
import com.skala.cbam.submission.repository.ExtractionFieldRepository;
import com.skala.cbam.submission.repository.SubmissionRepository;
import com.skala.cbam.submission.repository.UnregisteredPartRepository;
import com.skala.cbam.submission.service.SubmissionService;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CBAM-90 (29~32번) 이 실제 DB로 되는 경우 · 막는 경우를 다 지키는지 확인한다.
 * "돌아간다고 맞는 게 아니다" — 특히 31·32번은 막는 조건이 핵심이라 반드시 실패 케이스도 같이 본다.
 *
 * <p>{@code @Transactional} 로 각 테스트를 롤백한다 — {@code SupplierApiTest} 와 같은 관례다.
 * 이게 없으면 여기서 만든 협력업체가 커밋된 채 남아서 다른 테스트 클래스(예: 같은 이메일을
 * 쓰는 SupplierApiTest)와 409 DUPLICATE_CONTACT_EMAIL 로 충돌한다 — 실제로 겪은 문제다.
 */
@SpringBootTest
@Transactional
class SubmissionServiceTest {

    @Autowired
    private SubmissionService submissionService;
    @Autowired
    private SubmissionRepository submissionRepository;
    @Autowired
    private UnregisteredPartRepository unregisteredPartRepository;
    @Autowired
    private ExtractionFieldRepository extractionFieldRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private SupplierRepository supplierRepository;

    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    private Supplier daehan;

    @BeforeEach
    void seed() {
        // 테이블을 미리 비우지 않는다 — 클래스에 @Transactional 이 있어서 각 테스트가 끝나면
        // 자동으로 롤백된다. 사업자등록번호·이메일도 API 명세 예시("kim@daehan.co.kr" 등)와
        // 겹치지 않는 값을 쓴다 — 롤백을 깜빡한 다른 테스트와 우연히 부딪히지 않도록.
        daehan = supplierRepository.save(Supplier.builder()
                .businessRegistrationNumber("999-00-00001")
                .name("대한금속(테스트)")
                .countryCode("KR")
                .contactName("김철수")
                .contactEmail("submission-test@daehan.example")
                .contactPhone("02-1234-5678")
                .build());
    }

    private Submission saveSubmission(SubmissionStatus status, Judgement judgement, Severity severity) {
        return submissionRepository.save(Submission.builder()
                .supplier(daehan)
                .reportingMonth(MONTH)
                .status(status)
                .judgement(judgement)
                .severity(severity)
                .submittedAt(OffsetDateTime.now())
                .directEmissionTco2e(new BigDecimal("100.000"))
                .indirectEmissionTco2e(new BigDecimal("50.000"))
                .productionQuantityTon(new BigDecimal("10.000"))
                .build());
    }

    @Test
    void 목록_조회는_실제_제출건을_정확히_반환한다() {
        saveSubmission(SubmissionStatus.REVIEW_PENDING, Judgement.UNQUALIFIED, Severity.HIGH);

        var condition = new SubmissionSearchCondition(null, null, MONTH, null, null, null, null, null);
        var result = submissionService.listSubmissions(condition, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).supplierName()).isEqualTo("대한금속(테스트)");
        assertThat(result.content().get(0).status()).isEqualTo(SubmissionStatus.REVIEW_PENDING);
        assertThat(result.content().get(0).severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void 확정은_적격이고_미등록부품_없을때_된다() {
        Submission s = saveSubmission(SubmissionStatus.REVIEW_PENDING, Judgement.QUALIFIED, null);

        var response = submissionService.confirm(s.getId(), "demo");

        assertThat(response.status()).isEqualTo(SubmissionStatus.CONFIRMED);
        assertThat(response.confirmedBy()).isEqualTo("demo");
        assertThat(response.calculatedEmission().frozen()).isTrue();
    }

    @Test
    void 확정은_부적격이면_막힌다() {
        Submission s = saveSubmission(SubmissionStatus.REVIEW_PENDING, Judgement.UNQUALIFIED, Severity.HIGH);

        assertThatThrownBy(() -> submissionService.confirm(s.getId(), "demo"))
                .isInstanceOf(SubmissionException.class)
                .satisfies(e -> assertThat(((SubmissionException) e).errorCode())
                        .isEqualTo(SubmissionErrorCode.NOT_QUALIFIED));
    }

    @Test
    void 확정은_미등록부품이_남아있으면_막힌다() {
        Submission s = saveSubmission(SubmissionStatus.REVIEW_PENDING, Judgement.QUALIFIED, null);
        unregisteredPartRepository.save(UnregisteredPart.builder()
                .submission(s).rawPartName("hot rolled coil").status(UnregisteredPartStatus.OPEN).build());

        assertThatThrownBy(() -> submissionService.confirm(s.getId(), "demo"))
                .isInstanceOf(SubmissionException.class)
                .satisfies(e -> assertThat(((SubmissionException) e).errorCode())
                        .isEqualTo(SubmissionErrorCode.UNREGISTERED_PART_EXISTS));
    }

    @Test
    void 반려는_검토대기_상태에서_판정을_무조건_UNQUALIFIED로_고정한다() {
        // 일부러 QUALIFIED 로 시작 — 반려하면 무조건 UNQUALIFIED 로 덮어써지는지 보려는 것
        Submission s = saveSubmission(SubmissionStatus.REVIEW_PENDING, Judgement.QUALIFIED, Severity.LOW);

        var request = new SubmissionRejectRequest("REJECTED", "MISSING_REQUIRED_FIELD", "직접 배출량 누락", false);
        var response = submissionService.reject(s.getId(), request, "demo");

        assertThat(response.status()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(response.judgement()).isEqualTo(Judgement.UNQUALIFIED);
        assertThat(response.feedbackDraftTaskId()).isNull();
    }

    @Test
    void 반려는_이미_반려된_건을_다시_반려하면_막힌다() {
        Submission s = saveSubmission(SubmissionStatus.REJECTED, Judgement.UNQUALIFIED, Severity.HIGH);

        var request = new SubmissionRejectRequest("REJECTED", "X", "Y", false);
        assertThatThrownBy(() -> submissionService.reject(s.getId(), request, "demo"))
                .isInstanceOf(SubmissionException.class)
                .satisfies(e -> assertThat(((SubmissionException) e).errorCode())
                        .isEqualTo(SubmissionErrorCode.NOT_REJECTABLE));
    }

    @Test
    void 반려는_확정된_건이면_막힌다() {
        Submission s = saveSubmission(SubmissionStatus.CONFIRMED, Judgement.QUALIFIED, null);

        var request = new SubmissionRejectRequest("REJECTED", "X", "Y", false);
        assertThatThrownBy(() -> submissionService.reject(s.getId(), request, "demo"))
                .isInstanceOf(SubmissionException.class)
                .satisfies(e -> assertThat(((SubmissionException) e).errorCode())
                        .isEqualTo(SubmissionErrorCode.ALREADY_CONFIRMED));
    }

    @Test
    void 반려는_resultStatus가_이상하면_막힌다() {
        Submission s = saveSubmission(SubmissionStatus.REVIEW_PENDING, Judgement.UNQUALIFIED, Severity.HIGH);

        var request = new SubmissionRejectRequest("CONFIRMED", "X", "Y", false);
        assertThatThrownBy(() -> submissionService.reject(s.getId(), request, "demo"))
                .isInstanceOf(SubmissionException.class)
                .satisfies(e -> assertThat(((SubmissionException) e).errorCode())
                        .isEqualTo(SubmissionErrorCode.INVALID_RESULT_STATUS));
    }

    @Test
    void 상세조회는_추출값과_판정결과를_한번에_보여준다() {
        Submission s = saveSubmission(SubmissionStatus.REVIEW_PENDING, Judgement.UNQUALIFIED, Severity.HIGH);
        extractionFieldRepository.save(ExtractionField.builder()
                .submission(s).fieldCode("production").valueType(DataValueType.DECIMAL)
                .normalizedDecimal(new BigDecimal("1250")).unit("TON").rawValue("1,250 MT")
                .sourceAttachmentId(9001L).sourceLocator("xlsx:Sheet1!C7").build());
        alertRepository.save(Alert.builder()
                .submission(s).reportingMonth(MONTH).ruleId("R2").checkId("REQUIRED_FIELD")
                .outcome(ValidationOutcome.FAIL).severity(Severity.HIGH).message("필수 항목 누락")
                .status(AlertStatus.OPEN).validatedAt(OffsetDateTime.now()).build());

        var detail = submissionService.getDetail(s.getId());

        assertThat(detail.activityData()).containsKey("production");
        assertThat((BigDecimal) detail.activityData().get("production").value())
                .isEqualByComparingTo("1250");
        assertThat(detail.validation().rules()).hasSize(1);
        assertThat(detail.validation().rules().get(0).result()).isEqualTo(ValidationOutcome.FAIL);
        assertThat(detail.attachments()).hasSize(1);
        assertThat(detail.attachments().get(0).viewUrl()).isEqualTo("/api/v1/attachments/9001");
    }
}
