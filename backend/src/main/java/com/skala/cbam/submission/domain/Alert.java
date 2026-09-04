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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD alert 테이블. 검사(rule_id + check_id) 하나당 한 행이다.
 *
 * <p>제출 데이터 상세(30번)의 validation.rules[].checks[] 와 대시보드 경보 조회(39번)가 같은
 * 테이블을 쓴다. 대시보드 쪽(CBAM-73)은 아직 자체 임시 Alert 를 갖고 있는데, 이 엔티티가
 * 정식 버전이니 나중에 그쪽을 이걸로 교체해야 한다 (작업 로그에 남김).
 *
 * <p>partSupplierId 는 부품 도메인이 없어 값만 보존한다(Port 패턴과 동일한 이유).
 * 미제출(R1) 경보는 submission 이 비어 있다 — 30번 상세조회에서는 submissionId 로 걸러서
 * 조회하므로 이 경우는 나오지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "alert",
        indexes = {
                @Index(name = "ix_alert_submission_rule_check", columnList = "submission_id, rule_id, check_id"),
                @Index(name = "ix_alert_part_supplier_month_rule_check",
                        columnList = "part_supplier_id, reporting_month, rule_id, check_id")
        }
)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(name = "part_supplier_id")
    private Long partSupplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unregistered_part_id")
    private UnregisteredPart unregisteredPart;

    @Column(name = "reporting_month", nullable = false)
    private LocalDate reportingMonth;

    /** R1~R7 */
    @Column(name = "rule_id", nullable = false, length = 10)
    private String ruleId;

    /** REQUIRED_FIELD · DOCUMENT_MONTH_MISMATCH · AVG_DEVIATION · PERIOD_CHANGE 등 */
    @Column(name = "check_id", nullable = false, length = 50)
    private String checkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 10)
    private ValidationOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "observed_value", length = 255)
    private String observedValue;

    @Column(name = "reference_value", length = 255)
    private String referenceValue;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "validated_at", nullable = false)
    private OffsetDateTime validatedAt;

    @Builder
    private Alert(Submission submission, Long partSupplierId, UnregisteredPart unregisteredPart,
                 LocalDate reportingMonth, String ruleId, String checkId, ValidationOutcome outcome,
                 Severity severity, String observedValue, String referenceValue, String message,
                 AlertStatus status, OffsetDateTime validatedAt) {
        this.submission = submission;
        this.partSupplierId = partSupplierId;
        this.unregisteredPart = unregisteredPart;
        this.reportingMonth = reportingMonth;
        this.ruleId = ruleId;
        this.checkId = checkId;
        this.outcome = outcome;
        this.severity = severity;
        this.observedValue = observedValue;
        this.referenceValue = referenceValue;
        this.message = message;
        this.status = status;
        this.validatedAt = validatedAt;
    }
}
