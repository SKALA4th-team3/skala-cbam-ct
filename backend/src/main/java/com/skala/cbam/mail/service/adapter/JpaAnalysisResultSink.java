package com.skala.cbam.mail.service.adapter;

import com.skala.cbam.ai.dto.ExtractionResult;
import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.repository.MailReceiptRepository;
import com.skala.cbam.mail.service.port.AnalysisResultSink;
import com.skala.cbam.submission.domain.DataValueType;
import com.skala.cbam.submission.domain.EmissionScope;
import com.skala.cbam.submission.domain.ExtractionField;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.domain.UnregisteredPart;
import com.skala.cbam.submission.domain.UnregisteredPartStatus;
import com.skala.cbam.submission.repository.ExtractionFieldRepository;
import com.skala.cbam.submission.repository.SubmissionRepository;
import com.skala.cbam.submission.repository.UnregisteredPartRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 22~25번 분석 결과를 제출 도메인에 저장한다 (CBAM-100 ← CBAM-90).
 *
 * <p><b>판정하지 않는다.</b> {@code judgement}·{@code severity}·{@code eligibilityStatus} 를 비운 채로
 * 저장한다 — 33~37번은 규칙이지 AI 가 아니다(ADR-0010 ①). 상태는 {@code REVIEW_PENDING} 이고
 * 담당자 검토 목록(29번)에 그대로 올라온다.
 *
 * <p><b>배출량 칸을 채우지 않는다.</b> 협력사가 준 것은 연료·전력 <i>사용량</i>이고
 * tCO2e 로 바꾸려면 배출계수(34번)가 필요하다. 사용량을 배출량 칸에 넣으면 신고 수치가 틀린다 —
 * 원문이 tCO2e 라고 적어 준 값만 넣는다.
 */
@Component
@RequiredArgsConstructor
public class JpaAnalysisResultSink implements AnalysisResultSink {

    private static final Logger log = LoggerFactory.getLogger(JpaAnalysisResultSink.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String EMISSION_UNIT = "tCO2e";

    private final MailReceiptRepository mailReceiptRepository;
    private final SubmissionRepository submissionRepository;
    private final ExtractionFieldRepository extractionFieldRepository;
    private final UnregisteredPartRepository unregisteredPartRepository;
    private final EntityManager entityManager;

    @Override
    public Outcome save(Long mailReceiptId, ExtractionResult result) {
        MailReceipt receipt = mailReceiptRepository.findById(mailReceiptId).orElse(null);
        if (receipt == null || receipt.getSupplier() == null) {
            // 협력업체가 매칭되지 않은 접수 건은 제출 데이터를 만들 수 없다(supplier_id NOT NULL).
            // 19·21번 매칭이 먼저다 — 억지로 만들지 않는다
            log.warn("협력업체가 매칭되지 않아 제출 데이터를 만들지 않는다: mailReceiptId={}", mailReceiptId);
            return Outcome.empty();
        }

        String countryCode = countryCodeOf(result);
        Submission submission = submissionRepository.save(Submission.builder()
                .mailReceiptId(mailReceiptId)
                .supplier(receipt.getSupplier())
                .reportingMonth(reportingMonthOf(result, receipt))
                .productionQuantityTon(decimalOf(result, "production"))
                .productionCountryCode(knownCountry(countryCode) ? countryCode : null)
                .directEmissionTco2e(emissionSumOf(result, EmissionScope.DIRECT))
                .indirectEmissionTco2e(emissionSumOf(result, EmissionScope.INDIRECT))
                // 판정은 규칙(33~37번)이 채운다. AI 는 여기를 비운 채로 둔다
                .status(SubmissionStatus.REVIEW_PENDING)
                .submittedAt(receipt.getReceivedAt() == null ? OffsetDateTime.now(SEOUL) : receipt.getReceivedAt())
                .build());

        saveExtractionFields(submission, result);

        List<UnregisteredPart> parts = result.unregisteredParts().stream()
                .map(p -> UnregisteredPart.builder()
                        .submission(submission)
                        .rawPartName(p.rawPartName())
                        // 담당자가 28번에서 등록하면 RESOLVED 로 바뀐다
                        .status(UnregisteredPartStatus.OPEN)
                        .build())
                .toList();
        unregisteredPartRepository.saveAll(parts);

        log.info("분석 결과를 저장했다: submissionId={}, 항목 {}개, 미등록 부품 {}개",
                submission.getId(), result.items().size(), parts.size());
        return new Outcome(List.of(submission.getId()), parts.size());
    }

    /**
     * 추출 항목을 {@code extraction_field} 행으로 옮긴다.
     *
     * <p><b>값도 없고 변환 실패도 아닌 항목은 행을 만들지 않는다.</b> ERD 의
     * {@code ck_extraction_normalized_type} 이 「변환 실패가 아니면 normalized_* 중 하나가 반드시
     * 있어야 한다」고 정하고 있어서다. 원문에 값이 없는 것(R2)은 <b>행의 부재</b>로 나타나고,
     * 33번 필수 항목 검증이 그 부재를 본다. 값은 있는데 못 옮긴 것(R5)은 행이 남고
     * {@code conversion_failure_reason} 이 이유를 말한다 — 둘이 섞이지 않는다.
     */
    private void saveExtractionFields(Submission submission, ExtractionResult result) {
        List<ExtractionField> fields = new ArrayList<>();
        short sequence = 1;

        for (ExtractionResult.Item item : result.items()) {
            boolean failedConversion = item.conversionFailReason() != null;
            if (item.value() == null && !failedConversion) {
                continue;
            }

            ExtractionField.ExtractionFieldBuilder builder = ExtractionField.builder()
                    .submission(submission)
                    .sourceAttachmentId(item.source() == null ? null : item.source().attachmentId())
                    .fieldCode(item.key())
                    .sequenceNumber(sequence++)
                    .unit(item.unit())
                    .rawValue(item.rawValue())
                    .emissionScope(emissionScopeOf(item))
                    .sourceLocator(item.source() == null ? null : item.source().locator())
                    .conversionFailureReason(item.conversionFailReason());

            if (failedConversion) {
                // 변환 실패면 normalized_* 를 모두 비워야 한다 (ck_extraction_failure_empty).
                // 값 종류는 알 수 없으니 원문 그대로만 남긴다
                builder.valueType(DataValueType.TEXT);
            } else {
                applyNormalizedValue(builder, item);
            }
            fields.add(builder.build());
        }
        extractionFieldRepository.saveAll(fields);
    }

    /** 값 하나를 {@code value_type} 에 맞는 칸 하나에만 넣는다 (ck_extraction_normalized_type). */
    private void applyNormalizedValue(ExtractionField.ExtractionFieldBuilder builder,
                                      ExtractionResult.Item item) {
        Object value = item.value();
        if (value instanceof Number number) {
            builder.valueType(DataValueType.DECIMAL).normalizedDecimal(new BigDecimal(number.toString()));
            return;
        }

        String text = String.valueOf(value);
        if ("productionCountry".equals(item.key()) && knownCountry(text)) {
            builder.valueType(DataValueType.COUNTRY_CODE).normalizedCountryCode(text);
            return;
        }
        LocalDate month = monthOf(text);
        if ("documentMonth".equals(item.key()) && month != null) {
            builder.valueType(DataValueType.DATE).normalizedDate(month);
            return;
        }
        builder.valueType(DataValueType.TEXT).normalizedText(text);
    }

    private EmissionScope emissionScopeOf(ExtractionResult.Item item) {
        if (item.emissionScope() == null) {
            return null;
        }
        try {
            return EmissionScope.valueOf(item.emissionScope());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 원문이 <b>tCO2e 로 적어 준 값만</b> 더한다.
     *
     * <p>연료 사용량(GJ·TON)이나 전력 사용량(MWh)을 여기 넣으면 안 된다 — 배출계수를 곱하는 것은
     * 34번의 일이고, 사용량을 배출량으로 저장하면 41번 집계와 신고 수치가 통째로 틀린다.
     */
    private BigDecimal emissionSumOf(ExtractionResult result, EmissionScope scope) {
        BigDecimal sum = null;
        for (ExtractionResult.Item item : result.items()) {
            if (item.value() instanceof Number number
                    && scope.name().equals(item.emissionScope())
                    && EMISSION_UNIT.equalsIgnoreCase(item.unit())) {
                BigDecimal value = new BigDecimal(number.toString());
                sum = sum == null ? value : sum.add(value);
            }
        }
        return sum;
    }

    private BigDecimal decimalOf(ExtractionResult result, String key) {
        return result.items().stream()
                .filter(i -> key.equals(i.key()) && i.value() instanceof Number)
                .map(i -> new BigDecimal(i.value().toString()))
                .findFirst().orElse(null);
    }

    private String countryCodeOf(ExtractionResult result) {
        return result.items().stream()
                .filter(i -> "productionCountry".equals(i.key()) && i.value() instanceof String)
                .map(i -> ((String) i.value()).trim().toUpperCase(Locale.ROOT))
                .filter(code -> code.matches("[A-Z]{2}"))
                .findFirst().orElse(null);
    }

    /**
     * 자료가 말한 대상 월. 없으면 접수 월로 둔다 — {@code reporting_month} 는 NOT NULL 이고
     * 그 달의 1일이어야 한다({@code ck_submission_month}).
     */
    private LocalDate reportingMonthOf(ExtractionResult result, MailReceipt receipt) {
        LocalDate fromDocument = result.items().stream()
                .filter(i -> "documentMonth".equals(i.key()) && i.value() instanceof String)
                .map(i -> monthOf((String) i.value()))
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (fromDocument != null) {
            return fromDocument;
        }
        LocalDate received = receipt.getReceivedAt() == null
                ? LocalDate.now(SEOUL) : receipt.getReceivedAt().atZoneSameInstant(SEOUL).toLocalDate();
        return received.withDayOfMonth(1);
    }

    private LocalDate monthOf(String value) {
        try {
            return YearMonth.parse(value.trim()).atDay(1);
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    /**
     * {@code country} 기준정보에 있는 코드인가.
     *
     * <p>{@code production_country_code} 와 {@code normalized_country_code} 가 그 테이블을 참조한다 —
     * 모델이 지어낸 코드를 그대로 넣으면 <b>제약 위반으로 분석 전체가 롤백된다.</b>
     * 확인할 수 없는 환경(테이블이 없는 테스트용 H2)에서는 제약도 없으므로 통과시킨다.
     */
    private boolean knownCountry(String code) {
        if (code == null || !code.matches("[A-Z]{2}")) {
            return false;
        }
        try {
            return !entityManager
                    .createNativeQuery("select code from country where code = :code")
                    .setParameter("code", code)
                    .setMaxResults(1)
                    .getResultList()
                    .isEmpty();
        } catch (RuntimeException e) {
            log.debug("country 기준정보를 확인할 수 없어 코드를 그대로 쓴다: {}", code);
            return true;
        }
    }
}
