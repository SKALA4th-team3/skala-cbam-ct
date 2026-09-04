package com.skala.cbam.submission.domain;

import com.skala.cbam.supplier.domain.Supplier;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 부품별 월간 제출 데이터. API 명세 №20~№23, 요구사항 29~32번.
 *
 * <p>supplierId 는 실제 {@link Supplier} 엔티티(협력업체 도메인, 이미 dev 에 있음)를 그대로 참조한다.
 * partSupplierId 는 참조만 하고 엔티티로 묶지 않는다 — 부품(parts) 도메인이 아직 dev 에 없어서다
 * (CBAM-59 브랜치에 구현돼 있지만 미병합). 부품명·공급업체명 같은 표시값은
 * {@link com.skala.cbam.submission.service.port.PartRelatedDataProvider} 로 채운다.
 *
 * <p>status(처리 단계) 와 judgement(판정 결과) 는 다른 축이다 — 확정(31번)은 이 둘을 함께 검사한다.
 *
 * <p>시각은 Supplier 컨벤션과 동일하게 Asia/Seoul 고정, 초 단위로 자른다(공통 규약 5항).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "submission",
        indexes = {
                @Index(name = "ix_submission_supplier_month_status",
                        columnList = "supplier_id, reporting_month, status"),
                @Index(name = "ix_submission_part_supplier_month_status",
                        columnList = "part_supplier_id, reporting_month, status"),
                @Index(name = "ix_submission_judgement_severity_submitted",
                        columnList = "judgement, severity, submitted_at")
        }
)
public class Submission {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 제출을 생성한 수신 메일. 이메일 접수 도메인이 아직 없어 값만 보존한다. */
    @Column(name = "mail_receipt_id")
    private Long mailReceiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** 매핑된 부품×협력업체 조합. 미등록 부품 제출 건은 비어 있다(ERD 규칙 8). */
    @Column(name = "part_supplier_id")
    private Long partSupplierId;

    /** 보고 대상 월의 첫날. */
    @Column(name = "reporting_month", nullable = false)
    private LocalDate reportingMonth;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "production_quantity_ton", precision = 14, scale = 3)
    private BigDecimal productionQuantityTon;

    @Column(name = "production_country_code", length = 2, columnDefinition = "CHAR(2)")
    private String productionCountryCode;

    @Column(name = "direct_emission_tco2e", precision = 14, scale = 3)
    private BigDecimal directEmissionTco2e;

    @Column(name = "indirect_emission_tco2e", precision = 14, scale = 3)
    private BigDecimal indirectEmissionTco2e;

    @Column(name = "default_value_ratio", precision = 5, scale = 4)
    private BigDecimal defaultValueRatio;

    /** 확정 시점 스냅샷. factorFrozenAt 이 null 이 아니면 확정된 계산값이라는 뜻이다. */
    @Column(name = "applied_factor_year")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer appliedFactorYear;

    @Column(name = "factor_frozen_at")
    private OffsetDateTime factorFrozenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "judgement", length = 20)
    private Judgement judgement;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", length = 20)
    private EligibilityStatus eligibilityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10)
    private Severity severity;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejection_reason_code", length = 50)
    private String rejectionReasonCode;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Submission(Long mailReceiptId, Supplier supplier, Long partSupplierId,
                       LocalDate reportingMonth, String documentType,
                       BigDecimal productionQuantityTon, String productionCountryCode,
                       BigDecimal directEmissionTco2e, BigDecimal indirectEmissionTco2e,
                       BigDecimal defaultValueRatio, SubmissionStatus status, Judgement judgement,
                       EligibilityStatus eligibilityStatus, Severity severity,
                       OffsetDateTime submittedAt) {
        this.mailReceiptId = mailReceiptId;
        this.supplier = supplier;
        this.partSupplierId = partSupplierId;
        this.reportingMonth = reportingMonth;
        this.documentType = documentType;
        this.productionQuantityTon = productionQuantityTon;
        this.productionCountryCode = productionCountryCode;
        this.directEmissionTco2e = directEmissionTco2e;
        this.indirectEmissionTco2e = indirectEmissionTco2e;
        this.defaultValueRatio = defaultValueRatio;
        this.status = status;
        this.judgement = judgement;
        this.eligibilityStatus = eligibilityStatus;
        this.severity = severity;
        this.submittedAt = submittedAt;
    }

    /** 배출 원단위 = (직접+간접) / 생산량. 저장하지 않고 조회 시 계산한다(공통 규약 11항). */
    public BigDecimal calculateEmissionIntensity() {
        if (directEmissionTco2e == null || indirectEmissionTco2e == null
                || productionQuantityTon == null || productionQuantityTon.signum() == 0) {
            return null;
        }
        return directEmissionTco2e.add(indirectEmissionTco2e)
                .divide(productionQuantityTon, 4, java.math.RoundingMode.HALF_UP);
    }

    public boolean isFrozen() {
        return factorFrozenAt != null;
    }

    /**
     * 데이터 확정(31번). 막는 조건(judgement=QUALIFIED, 미등록 부품 없음)은 서비스가 미리 검사한다 —
     * 미등록 부품 존재 여부는 이 엔티티가 알 수 없는 다른 테이블의 데이터라서다.
     */
    public void confirm(String operatorId, int appliedFactorYear) {
        this.status = SubmissionStatus.CONFIRMED;
        this.confirmedBy = operatorId;
        this.confirmedAt = now();
        this.appliedFactorYear = appliedFactorYear;
        this.factorFrozenAt = now();
    }

    /**
     * 반려(32번). judgement 는 무엇이 저장돼 있었든 무조건 UNQUALIFIED 로 고정한다 —
     * 요구사항 32번 원문 그대로.
     */
    public void reject(SubmissionStatus resultStatus, String reasonCode, String reason, String operatorId) {
        this.status = resultStatus;
        this.judgement = Judgement.UNQUALIFIED;
        this.rejectionReasonCode = reasonCode;
        this.rejectionReason = reason;
        this.rejectedBy = operatorId;
        this.rejectedAt = now();
    }

    @PrePersist
    void onPrePersist() {
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = now();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.SECONDS);
    }
}
