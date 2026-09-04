package com.skala.cbam.mail.domain;

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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 접수 메일. API 명세 №15~№18, 요구사항 18~21번.
 *
 * <p>messageId 로 중복 접수를 막는다(요구사항 18번 "같은 메일은 Message-ID 기준으로 한 번만 접수").
 * supplier 는 자동 매칭(19번, AI 분석 도메인 소관) 또는 수동 매칭(83번, 이 클래스의 {@link #match})으로
 * 채워진다 — 접수 시점엔 비어 있을 수 있다(UNMATCHED).
 *
 * <p>Supplier 컨벤션 그대로: {@code domain} 패키지, {@code @PrePersist}/{@code @PreUpdate} 로
 * Asia/Seoul 초 단위 타임스탬프를 직접 관리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "mail_receipt",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mail_receipt_message_id", columnNames = "message_id")
        },
        indexes = {
                @Index(name = "ix_mail_receipt_supplier_received", columnList = "supplier_id, received_at"),
                @Index(name = "ix_mail_receipt_status_received", columnList = "status, received_at"),
                @Index(name = "ix_mail_receipt_sender_received", columnList = "sender_email, received_at")
        }
)
public class MailReceipt {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 자동 또는 수동으로 매칭된 협력업체. 매칭 전엔 비어 있다(UNMATCHED). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** 회신 추적 대상 SEND 계열 작업. task 도메인이 없어 값만 보존한다. */
    @Column(name = "reply_to_task_id", length = 50)
    private String replyToTaskId;

    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "sender_email", nullable = false, length = 254)
    private String senderEmail;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MailReceiptStatus status;

    /** ENCRYPTED_FILE · PARSE_FAILED · UNSUPPORTED_FORMAT · NO_ATTACHMENT 등 */
    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(name = "linked_by", length = 100)
    private String linkedBy;

    @Column(name = "linked_at")
    private OffsetDateTime linkedAt;

    /** AI 분석 자동 실행 작업의 최신 taskId. task 도메인이 없어 값만 보존한다. */
    @Column(name = "latest_analysis_task_id", length = 50)
    private String latestAnalysisTaskId;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private MailReceipt(String messageId, String senderEmail, String subject, String body,
                        MailReceiptStatus status, String failureReason, String latestAnalysisTaskId,
                        OffsetDateTime receivedAt) {
        this.messageId = messageId;
        this.senderEmail = senderEmail;
        this.subject = subject;
        this.body = body;
        this.status = status == null ? MailReceiptStatus.UNMATCHED : status;
        this.failureReason = failureReason;
        this.latestAnalysisTaskId = latestAnalysisTaskId;
        this.receivedAt = receivedAt;
    }

    /**
     * 발신자-협력업체 수동 매칭 (요구사항 19·21번, 83번).
     *
     * <p>호출부(서비스)가 이미 매칭됐는지(409 ALREADY_MATCHED), 업체가 INACTIVE 인지(400)를
     * 먼저 검사한다 — 그 판단에 필요한 업체 활성 상태를 이 엔티티가 갖고 있지 않아서다.
     */
    public void match(Supplier supplier, String linkedBy) {
        this.supplier = supplier;
        this.status = MailReceiptStatus.MATCHED;
        this.linkedBy = linkedBy;
        this.linkedAt = now();
    }

    /**
     * 요구사항 20번 — 연결 즉시 AI 분석을 자동 호출한다. 그 작업의 id 를 붙든다.
     *
     * <p>재분석하면 <b>최신 것으로 덮는다</b> — 화면은 이 값 하나로 №19 를 폴링한다.
     * 이전 작업의 결과는 task_resource 에 남아 있어 잃어버리지 않는다.
     */
    public void startAnalysis(String analysisTaskId) {
        this.latestAnalysisTaskId = analysisTaskId;
    }

    /** 22번 분석 성공. 실패했던 건을 다시 분석해 성공하면 failureReason 도 지운다. */
    public void completeAnalysis() {
        this.status = MailReceiptStatus.ANALYZED;
        this.failureReason = null;
    }

    /** 22번 분석 실패. failureReason 은 №16 의 네 값 중 하나다 — 새 코드를 만들지 않는다. */
    public void failAnalysis(String failureReason) {
        this.status = MailReceiptStatus.ANALYSIS_FAILED;
        this.failureReason = failureReason;
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
