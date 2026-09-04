package com.skala.cbam.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 항목별 추출값 (요구사항 23·24번). 제출 데이터 상세 조회(30번)의 activityData 를 구성한다.
 *
 * <p>fieldCode 는 "partName"·"production"·"electricity"·"fuel_lng" 처럼 자유 문자열이다
 * (코드·Enum 정의 시트의 ActivityField — fuel_* 은 연료별로 늘어난다). 여기서 enum 으로 고정하지
 * 않는다 — AI 분석(22~27번, 아직 담당자 없음)이 실제로 어떤 필드를 뽑을지가 그쪽 구현에 달려 있다.
 *
 * <p>value_type 에 대응하는 normalized_* 컬럼 하나만 쓴다(ERD 규칙 13). 변환 실패 시엔 전부 비우고
 * conversionFailReason 만 채운다(ERD 규칙 14, 요구사항 24번 "비워 둔 채 사유를 남긴다").
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "extraction_field",
        indexes = {
                @Index(name = "ix_extraction_field_submission", columnList = "submission_id")
        }
)
public class ExtractionField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    /** 원문 첨부. 메일 본문에서 추출했으면 비어 있다. */
    @Column(name = "source_attachment_id")
    private Long sourceAttachmentId;

    @Column(name = "field_code", nullable = false, length = 100)
    private String fieldCode;

    @Column(name = "sequence_number", nullable = false)
    private Short sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private DataValueType valueType;

    @Column(name = "normalized_text")
    private String normalizedText;

    @Column(name = "normalized_decimal", precision = 24, scale = 8)
    private BigDecimal normalizedDecimal;

    @Column(name = "normalized_date")
    private LocalDate normalizedDate;

    @Column(name = "normalized_country_code", length = 2, columnDefinition = "CHAR(2)")
    private String normalizedCountryCode;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "raw_value", columnDefinition = "TEXT")
    private String rawValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "emission_scope", length = 10)
    private EmissionScope emissionScope;

    /** xlsx:Sheet1!C7 · csv:row=42,col=3 · pdf:page=2,bbox=[..] · body:offset=10-20 (공통 규약 10항) */
    @Column(name = "source_locator", length = 1000)
    private String sourceLocator;

    @Column(name = "conversion_failure_reason", length = 100)
    private String conversionFailureReason;

    @Builder
    private ExtractionField(Submission submission, Long sourceAttachmentId, String fieldCode,
                            Short sequenceNumber, DataValueType valueType, String normalizedText,
                            BigDecimal normalizedDecimal, LocalDate normalizedDate,
                            String normalizedCountryCode, String unit, String rawValue,
                            EmissionScope emissionScope, String sourceLocator, String conversionFailureReason) {
        this.submission = submission;
        this.sourceAttachmentId = sourceAttachmentId;
        this.fieldCode = fieldCode;
        this.sequenceNumber = sequenceNumber == null ? 1 : sequenceNumber;
        this.valueType = valueType;
        this.normalizedText = normalizedText;
        this.normalizedDecimal = normalizedDecimal;
        this.normalizedDate = normalizedDate;
        this.normalizedCountryCode = normalizedCountryCode;
        this.unit = unit;
        this.rawValue = rawValue;
        this.emissionScope = emissionScope;
        this.sourceLocator = sourceLocator;
        this.conversionFailureReason = conversionFailureReason;
    }

    /** value_type 에 맞는 표준값 하나를 꺼낸다. 변환 실패면 전부 null 이라 이것도 null 이 된다. */
    public Object normalizedValue() {
        return switch (valueType) {
            case TEXT -> normalizedText;
            case DECIMAL -> normalizedDecimal;
            case DATE -> normalizedDate;
            case COUNTRY_CODE -> normalizedCountryCode;
        };
    }
}
