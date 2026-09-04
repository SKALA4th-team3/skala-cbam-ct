package com.skala.cbam.feedback.domain;

import com.skala.cbam.common.domain.BaseTimeEntity;
import com.skala.cbam.common.domain.DeliveryStatus;
import com.skala.cbam.common.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.skala.cbam.task.domain.TaskResourceType;

/**
 * ERD task 테이블. 공통 규약 6항의 202 비동기 작업 봉투가 이 테이블을 가리킨다.
 *
 * <p><b>이 프로젝트에 아직 task 도메인 담당이 없다.</b> 이 클래스는 CBAM-88(피드백)이 필요한
 * 컬럼만 최소로 채운 것이다 — 메일 분석(ANALYZE_MAIL_RECEIPT)·재판정(REVALIDATE_SUBMISSION) 같은
 * 다른 타입을 쓸 도메인이 생기면 이 엔티티를 그대로 확장해서 써야 한다(중복 매핑 금지 — Supplier
 * 때 겪은 문제 반복하지 않기). status·createdAt·updatedAt 은 CBAM-33 공용 타입을 쓴다.
 *
 * <p><b>GET /api/v1/tasks/{taskId}(19번, 폴링) 는 이번 스코프에 없다.</b> 초안 생성·재생성은
 * 실제로는 동기(fallback 템플릿)라 완료 상태로 즉시 저장하고, 발송은 실제 메일 게이트웨이가 없어
 * 즉시 성공/실패를 결정해 저장한다 — 둘 다 "나중에 누가 폴링해도 맞는 값"이 남도록 정확히 채운다.
 *
 * <p>{@link #deliveryStatus} 는 SEND_FEEDBACK·SEND_REMINDER(발송 계열) 작업에서만 채운다 —
 * {@code status}(TaskStatus)는 이 작업 자체의 실행 상태이고, deliveryStatus는 사용자에게 보여줄
 * 발송 결과다(공용 DeliveryStatus 설계 그대로). 초안 생성·재생성 같은 비발송 작업은 null 로 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "task",
        indexes = {
                @Index(name = "ix_task_status_created", columnList = "status, created_at"),
                @Index(name = "ix_task_feedback_type_created", columnList = "feedback_id, type, created_at"),
                @Index(name = "ix_task_feedback_attempt", columnList = "feedback_id, attempt_number")
        }
)
public class Task extends BaseTimeEntity {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id")
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_draft_id")
    private FeedbackDraft feedbackDraft;

    /** 22~25번 분석 작업이 읽은 접수 메일. ERD 의 task.mail_receipt_id. */
    @Column(name = "mail_receipt_id")
    private Long mailReceiptId;

    /** 재판정 작업이 다시 본 제출 건. ERD 의 task.submission_id. */
    @Column(name = "submission_id")
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    /** 발송 계열 작업에서만 채운다(위 클래스 설명 참고). */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "progress_total", nullable = false)
    private int progressTotal;

    @Column(name = "progress_done", nullable = false)
    private int progressDone;

    @Column(name = "progress_failed", nullable = false)
    private int progressFailed;

    @Column(name = "fallback_applied", nullable = false)
    private boolean fallbackApplied;

    /**
     * 이 작업이 만들어 낸 자원의 종류와 id — API 명세 v10 №19 의 {@code resourceType}·{@code resourceIds}.
     *
     * <p><b>ERD 가 이미 갖고 있던 컬럼이다</b>(ADR-0012). 단수 FK 로는 43번 일괄 생성이 만든
     * 초안 N 개를 가리킬 수 없어서 목록으로 둔 것이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 50)
    private TaskResourceType resourceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_ids")
    private List<Long> resourceIds;

    /** 25번 — 이 분석이 남긴 미등록 부품 수. ANALYZE_MAIL_RECEIPT 에서만 0 이 아니다. */
    @Column(name = "unregistered_part_count", nullable = false)
    private int unregisteredPartCount;

    /** SEND 계열 작업의 발송 시도 순번. */
    @Column(name = "attempt_number")
    private Short attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "resend_reason", length = 20)
    private ResendReason resendReason;

    @Column(name = "recipient_email", length = 254)
    private String recipientEmail;

    @Column(name = "external_message_id", length = 255)
    private String externalMessageId;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Builder
    private Task(Feedback feedback, FeedbackDraft feedbackDraft, Long mailReceiptId, Long submissionId,
                TaskType type, TaskStatus status,
                int progressTotal, boolean fallbackApplied, Short attemptNumber, ResendReason resendReason,
                String recipientEmail, String requestedBy) {
        this.id = "tsk-" + UUID.randomUUID().toString().substring(0, 8);
        this.feedback = feedback;
        this.feedbackDraft = feedbackDraft;
        this.mailReceiptId = mailReceiptId;
        this.submissionId = submissionId;
        this.resourceIds = List.of();
        this.type = type;
        this.status = status;
        this.progressTotal = progressTotal;
        this.progressDone = 0;
        this.progressFailed = 0;
        this.fallbackApplied = fallbackApplied;
        this.attemptNumber = attemptNumber;
        this.resendReason = resendReason;
        this.recipientEmail = recipientEmail;
        this.requestedBy = requestedBy;
        this.startedAt = now();
    }

    /**
     * 이 작업이 무엇을 만들었는지 남긴다 (№19 의 resourceType·resourceIds).
     *
     * <p>이 호출을 빠뜨리면 화면은 방금 만든 것을 찾지 못한다 — PR #31 리뷰에서 겪은 문제다.
     * 만든 것이 없으면 종류도 남기지 않는다. 빈 목록에 종류만 붙어 있으면 화면이
     * 「만들어졌는데 id 를 못 받았다」로 오해한다.
     */
    public void recordResult(TaskResourceType resourceType, List<Long> resourceIds) {
        List<Long> ids = resourceIds == null ? List.of()
                : resourceIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        this.resourceIds = ids;
        this.resourceType = ids.isEmpty() ? null : resourceType;
    }

    /** 25번 — 분석이 남긴 미등록 부품 수. */
    public void recordUnregisteredPartCount(int count) {
        this.unregisteredPartCount = Math.max(0, count);
    }

    public void completeSuccessfully() {
        this.status = TaskStatus.COMPLETED;
        this.progressDone = this.progressTotal;
        this.completedAt = now();
    }

    /** 발송 성공(SEND 계열). deliveryStatus 도 함께 SENT 로 채운다. */
    public void completeSend(String externalMessageId) {
        this.externalMessageId = externalMessageId;
        this.sentAt = now();
        this.deliveryStatus = DeliveryStatus.SENT;
        completeSuccessfully();
    }

    /** 발송 실패도 이걸로 처리한다 — 이 메서드가 불리면 deliveryStatus 도 FAILED 로 채운다. */
    public void fail(String errorCode, String errorMessage) {
        failWithoutDelivery(errorCode, errorMessage);
        this.deliveryStatus = DeliveryStatus.FAILED;
    }

    /**
     * 발송이 아닌 작업의 실패 (메일 분석·재판정·초안 생성).
     *
     * <p>{@link #deliveryStatus} 를 건드리지 않는다 — 이 클래스 설명대로 그 값은 발송 계열에서만
     * 채운다. 분석 실패에 「발송 실패」가 함께 찍히면 발송 이력(51·53번)이 그것을 세게 된다.
     */
    public void failWithoutDelivery(String errorCode, String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.progressFailed = this.progressTotal;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = now();
    }

    /** PENDING 으로 만든 작업이 실제로 돌기 시작했다 (202 로 시작한 비동기 작업). */
    public void markProcessing() {
        this.status = TaskStatus.PROCESSING;
        this.startedAt = now();
    }
}
