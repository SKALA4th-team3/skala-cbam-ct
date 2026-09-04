package com.skala.cbam.feedback.domain;

import com.skala.cbam.common.domain.BaseTimeEntity;
import com.skala.cbam.common.domain.FeedbackStatus;
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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피드백/리마인드 업무 건. API 명세 №26~№31, 요구사항 42~53번. API 응답의 draftId 가 곧 이 id다.
 *
 * <p>ADR-0010 재검토 결과(2026-09-04): feedback_draft 버전 이력을 없애자는 이야기가 있었지만
 * 정규화·요구사항(45·47번 "버전 보관") 양쪽 다 원안이 맞아서 <b>ERD 그대로</b> 유지한다 — 이 클래스는
 * 문안을 안 들고 있고 {@link FeedbackDraft} 를 가리키는 포인터({@link #confirmedDraftId})만 갖는다.
 *
 * <p>submission·partSupplier 는 아직 이 브랜치에 없는 도메인이라(CBAM-90 PR #22 미병합, parts 도메인
 * 미병합) 참조만 하고 엔티티로 묶지 않는다 — Supplier/Submission 도메인의 Port 패턴과 같은 이유.
 *
 * <p><b>status 는 CBAM-33 공용 {@link FeedbackStatus}를 쓴다</b>(DRAFT/REVISED/READY_TO_SEND/DISCARDED
 * — SENT 없음). 발송 성공 여부는 여기 담지 않고 {@link Task}의 발송 이력으로 따로 판단한다 —
 * 공용 {@code DeliveryStatus}의 설계 의도(발송 결과는 별도 관리)를 그대로 따른 것이다. 이 클래스는
 * createdAt·updatedAt 을 공용 {@link BaseTimeEntity}에서 물려받는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "feedback",
        indexes = {
                @Index(name = "ix_feedback_supplier_month_type", columnList = "supplier_id, reporting_month, type"),
                @Index(name = "ix_feedback_submission", columnList = "submission_id"),
                @Index(name = "ix_feedback_status_created", columnList = "status, created_at")
        }
)
public class Feedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** 미제출 또는 부적격 대상 부품×협력업체 조합. parts 도메인 미병합이라 값만 보존한다. */
    @Column(name = "part_supplier_id")
    private Long partSupplierId;

    /** 부적격 제출 대상. 미제출 리마인드면 비어 있다. submission 도메인 미병합이라 값만 보존한다. */
    @Column(name = "submission_id")
    private Long submissionId;

    /** 확정되어 잠긴 최종 초안 버전. 확정 전엔 비어 있다. */
    @Column(name = "confirmed_draft_id")
    private Long confirmedDraftId;

    @Column(name = "reporting_month", nullable = false)
    private LocalDate reportingMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private FeedbackType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FeedbackStatus status;

    /** 확정 시 잠기는 수신자 이메일 스냅샷. */
    @Column(name = "recipient_email", length = 254)
    private String recipientEmail;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "discarded_by", length = 100)
    private String discardedBy;

    @Column(name = "discarded_at")
    private OffsetDateTime discardedAt;

    @Column(name = "discard_reason", columnDefinition = "TEXT")
    private String discardReason;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Builder
    private Feedback(Supplier supplier, Long partSupplierId, Long submissionId, LocalDate reportingMonth,
                     FeedbackType type, String createdBy) {
        this.supplier = supplier;
        this.partSupplierId = partSupplierId;
        this.submissionId = submissionId;
        this.reportingMonth = reportingMonth;
        this.type = type;
        this.status = FeedbackStatus.DRAFT;
        this.createdBy = createdBy;
    }

    /**
     * 확정(48번). 수신자·confirmedDraftId 를 잠근다. 수정·폐기는 이번 스코프에 없다(CBAM-93).
     *
     * <p>발송 완료는 이 엔티티의 status 를 더 바꾸지 않는다 — READY_TO_SEND 에 머문다. "보냈는지"는
     * 공용 {@code DeliveryStatus} 설계를 따라 {@link Task}의 발송 이력에서 판단한다.
     */
    public void confirm(Long confirmedDraftId, String recipientEmail, String confirmedBy) {
        this.confirmedDraftId = confirmedDraftId;
        this.recipientEmail = recipientEmail;
        this.status = FeedbackStatus.READY_TO_SEND;
        this.confirmedBy = confirmedBy;
        this.lockedAt = now();
    }
}
