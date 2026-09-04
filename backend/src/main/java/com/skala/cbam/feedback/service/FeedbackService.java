package com.skala.cbam.feedback.service;

import com.skala.cbam.common.domain.DeliveryStatus;
import com.skala.cbam.common.domain.FeedbackStatus;
import com.skala.cbam.common.domain.TaskStatus;
import com.skala.cbam.feedback.domain.DraftSourceType;
import com.skala.cbam.feedback.domain.Feedback;
import com.skala.cbam.feedback.domain.FeedbackDraft;
import com.skala.cbam.feedback.domain.FeedbackStyle;
import com.skala.cbam.feedback.domain.FeedbackType;
import com.skala.cbam.feedback.domain.ResendReason;
import com.skala.cbam.feedback.domain.Task;
import com.skala.cbam.feedback.domain.TaskType;
import com.skala.cbam.feedback.dto.FeedbackConfirmRequest;
import com.skala.cbam.feedback.dto.FeedbackConfirmResponse;
import com.skala.cbam.feedback.dto.FeedbackDraftCreateRequest;
import com.skala.cbam.feedback.dto.FeedbackDraftCreateResponse;
import com.skala.cbam.feedback.dto.FeedbackDraftDetailResponse;
import com.skala.cbam.feedback.dto.FeedbackDraftRegenerateRequest;
import com.skala.cbam.feedback.dto.FeedbackDraftRegenerateResponse;
import com.skala.cbam.feedback.dto.FeedbackHistoryItem;
import com.skala.cbam.feedback.dto.FeedbackHistorySearchCondition;
import com.skala.cbam.feedback.dto.FeedbackSendRequest;
import com.skala.cbam.feedback.dto.FeedbackSendResponse;
import com.skala.cbam.feedback.error.FeedbackErrorCode;
import com.skala.cbam.feedback.error.FeedbackException;
import com.skala.cbam.feedback.repository.FeedbackDraftRepository;
import com.skala.cbam.feedback.repository.FeedbackRepository;
import com.skala.cbam.feedback.repository.TaskRepository;
import com.skala.cbam.feedback.service.port.SubmissionRelatedDataProvider;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.dto.PageResponse;
import com.skala.cbam.supplier.repository.SupplierRepository;
import com.skala.cbam.task.domain.TaskResourceType;
import com.skala.cbam.task.service.TaskResourceRecorder;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드백 API 서비스 (42~53번 중 47·49번 제외, CBAM-88).
 *
 * <p>AI 제공자가 미정이라 초안 생성·재생성은 항상 요구사항 46번의 기본 템플릿 경로로만 동작한다.
 * 생성·재생성은 실제로 동기라 status=COMPLETED 로 응답한다.
 *
 * <p>만들어진 초안 id 는 {@link TaskResourceRecorder} 로 남긴다 — №19 작업 조회가 그것을
 * {@code resourceIds} 로 돌려준다(ADR-0011). 전에는 화면이 발송 이력을 훑어 방금 만든 초안을
 * 찾아야 했다(PR #31 리뷰에서 확인한 문제).
 *
 * <p>발송(send)만 다르다 — 실제 {@link JavaMailSender} 로 진짜 발송을 시도한다. .env 에 MAIL_SMTP_*
 * 가 없으면 진짜로 502 MAIL_GATEWAY_ERROR 가 난다. 이건 명세가 원래 요구하는 에러 코드라 가짜로
 * 흉내 낸 게 아니다.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String FALLBACK_TEMPLATE_ID = "basic-ko-v1";
    private static final int RESEND_WAIT_DAYS = 3;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackDraftRepository feedbackDraftRepository;
    private final TaskRepository taskRepository;
    private final SupplierRepository supplierRepository;
    private final SubmissionRelatedDataProvider submissionRelatedDataProvider;
    private final TaskResourceRecorder taskResourceRecorder;
    private final JavaMailSender mailSender;

    // ── 42·43번: 초안 생성 (개별 + 일괄) ──────────────────────────────

    @Transactional
    public FeedbackDraftCreateResponse createDrafts(FeedbackDraftCreateRequest request, String operatorId) {
        YearMonth month = parseMonth(request.reportingMonth());
        FeedbackStyle style = parseStyle(request.style());

        List<TargetContext> contexts = new ArrayList<>();

        boolean hasSubmissionIds = request.submissionIds() != null && !request.submissionIds().isEmpty();
        boolean hasTargets = request.targets() != null && !request.targets().isEmpty();

        if (hasSubmissionIds) {
            for (Long submissionId : request.submissionIds()) {
                var info = submissionRelatedDataProvider.findSubmissionInfo(submissionId)
                        .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.SUBMISSION_NOT_FOUND));
                if (info.qualified()) {
                    throw new FeedbackException(FeedbackErrorCode.NOT_DRAFTABLE);
                }
                contexts.add(new TargetContext(
                        info.supplierId(), info.partSupplierId(), info.submissionId(), info.rejectionReason()));
            }
        }
        if (hasTargets) {
            // 미제출 대상 — 제출 데이터 행이 없어 반려 사유도 없다
            for (var t : request.targets()) {
                contexts.add(new TargetContext(t.supplierId(), null, null, null));
            }
        }
        if (!hasSubmissionIds && !hasTargets) {
            // 43번 일괄 — 이번 달 부적격·미제출 전체. submission 도메인이 없어 지금은 항상 빈 목록.
            for (var info : submissionRelatedDataProvider.findDraftableSubmissions(month)) {
                contexts.add(new TargetContext(
                        info.supplierId(), info.partSupplierId(), info.submissionId(), info.rejectionReason()));
            }
        }

        if (contexts.isEmpty()) {
            throw new FeedbackException(FeedbackErrorCode.NO_TARGET);
        }

        List<Long> createdFeedbackIds = new ArrayList<>();

        for (TargetContext ctx : contexts) {
            Supplier supplier = supplierRepository.findById(ctx.supplierId())
                    .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.SUBMISSION_NOT_FOUND,
                            "협력업체를 찾을 수 없습니다", Map.of()));

            Feedback feedback = feedbackRepository.save(Feedback.builder()
                    .supplier(supplier)
                    .partSupplierId(ctx.partSupplierId())
                    .submissionId(ctx.submissionId())
                    .reportingMonth(month.atDay(1))
                    .type(FeedbackType.FEEDBACK)
                    .createdBy(operatorId)
                    .build());

            GeneratedText text = generateTemplateText(supplier.getName(), style, null, ctx.rejectionReason());
            feedbackDraftRepository.save(FeedbackDraft.builder()
                    .feedback(feedback)
                    .versionNumber((short) 1)
                    .sourceType(DraftSourceType.FALLBACK_TEMPLATE)
                    .style(style)
                    .subject(text.subject())
                    .body(text.body())
                    .fallbackApplied(true)
                    .fallbackTemplateId(FALLBACK_TEMPLATE_ID)
                    .build());

            createdFeedbackIds.add(feedback.getId());
        }

        Task task = Task.builder()
                .type(TaskType.GENERATE_FEEDBACK_DRAFT)
                .status(TaskStatus.PROCESSING)
                .progressTotal(contexts.size())
                .fallbackApplied(true)
                .requestedBy(operatorId)
                .build();
        task.completeSuccessfully();
        taskRepository.save(task);

        // 43번 일괄은 초안 N 개를 만들고 Task 는 하나다 — 단수 FK 로는 못 가리킨다 (ADR-0011)
        taskResourceRecorder.record(task.getId(), TaskResourceType.FEEDBACK, createdFeedbackIds);

        return new FeedbackDraftCreateResponse(task.getId(), TaskStatus.COMPLETED, contexts.size());
    }

    private record TargetContext(Long supplierId, Long partSupplierId, Long submissionId, String rejectionReason) {
    }

    // ── 44·46번: 초안 조회 ─────────────────────────────────────────

    public FeedbackDraftDetailResponse getDetail(Long draftId, Integer version) {
        Feedback feedback = feedbackRepository.findById(draftId)
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.FEEDBACK_DRAFT_NOT_FOUND));

        List<FeedbackDraft> versions = feedbackDraftRepository.findByFeedbackIdOrderByVersionNumberDesc(draftId);
        if (versions.isEmpty()) {
            throw new FeedbackException(FeedbackErrorCode.FEEDBACK_DRAFT_NOT_FOUND);
        }
        FeedbackDraft target = version == null ? versions.get(0)
                : versions.stream().filter(d -> d.getVersionNumber() == version.shortValue()).findFirst()
                        .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.VERSION_NOT_FOUND));

        List<FeedbackDraftDetailResponse.VersionSummary> versionSummaries = versions.stream()
                .map(d -> new FeedbackDraftDetailResponse.VersionSummary(
                        d.getVersionNumber(), d.getSourceType(), d.getCreatedAt()))
                .toList();

        return new FeedbackDraftDetailResponse(
                feedback.getId(), feedback.getSubmissionId(), feedback.getSupplier().getId(),
                target.getStyle(), target.getSubject(), target.getBody(), target.getVersionNumber(),
                target.getSourceType(), feedback.getStatus(), target.isFallbackApplied(),
                target.getFallbackTemplateId(),
                List.of(), // judgementReasons — submission 도메인 없어 항상 빈 배열
                versionSummaries);
    }

    // ── 45번: 초안 재생성 ──────────────────────────────────────────

    @Transactional
    public FeedbackDraftRegenerateResponse regenerate(
            Long draftId, FeedbackDraftRegenerateRequest request, String operatorId) {
        Feedback feedback = feedbackRepository.findById(draftId)
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.FEEDBACK_DRAFT_NOT_FOUND));

        if (feedback.getStatus() != FeedbackStatus.DRAFT) {
            throw new FeedbackException(FeedbackErrorCode.NOT_REGENERATABLE);
        }

        FeedbackStyle style = parseStyle(request.style());
        short nextVersion = (short) (feedbackDraftRepository.countByFeedbackId(draftId) + 1);

        GeneratedText text = generateTemplateText(
                feedback.getSupplier().getName(), style, request.instruction(), null);

        FeedbackDraft draft = feedbackDraftRepository.save(FeedbackDraft.builder()
                .feedback(feedback)
                .versionNumber(nextVersion)
                .sourceType(DraftSourceType.FALLBACK_TEMPLATE)
                .style(style)
                .instruction(request.instruction())
                .subject(text.subject())
                .body(text.body())
                .fallbackApplied(true)
                .fallbackTemplateId(FALLBACK_TEMPLATE_ID)
                .build());

        Task task = Task.builder()
                .feedback(feedback).feedbackDraft(draft)
                .type(TaskType.REGENERATE_FEEDBACK_DRAFT)
                .status(TaskStatus.PROCESSING)
                .progressTotal(1)
                .fallbackApplied(true)
                .requestedBy(operatorId)
                .build();
        task.completeSuccessfully();
        taskRepository.save(task);
        taskResourceRecorder.record(task.getId(), TaskResourceType.FEEDBACK_DRAFT, draft.getId());

        return new FeedbackDraftRegenerateResponse(task.getId(), TaskStatus.COMPLETED, feedback.getId(), nextVersion);
    }

    // ── 48번: 확정 (CBAM-93 — 수정·폐기는 스코프 밖) ──────────────────

    @Transactional
    public FeedbackConfirmResponse confirm(Long draftId, FeedbackConfirmRequest request, String operatorId) {
        if (!"READY_TO_SEND".equals(request.status())) {
            throw new FeedbackException(FeedbackErrorCode.NOT_CONFIRMABLE,
                    "이 엔드포인트는 확정(status=READY_TO_SEND)만 지원합니다 — 수정·폐기는 CBAM-93 스코프 밖입니다",
                    Map.of());
        }

        Feedback feedback = feedbackRepository.findById(draftId)
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.FEEDBACK_DRAFT_NOT_FOUND));

        if (feedback.getStatus() == FeedbackStatus.READY_TO_SEND) {
            throw new FeedbackException(FeedbackErrorCode.ALREADY_CONFIRMED);
        }
        if (feedback.getStatus() != FeedbackStatus.DRAFT) {
            throw new FeedbackException(FeedbackErrorCode.NOT_CONFIRMABLE);
        }

        FeedbackDraft latest = feedbackDraftRepository.findTopByFeedbackIdOrderByVersionNumberDesc(draftId)
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.NOT_CONFIRMABLE,
                        "초안이 없어 확정할 수 없습니다", Map.of()));

        String recipientEmail = feedback.getSupplier().getContactEmail();
        feedback.confirm(latest.getId(), recipientEmail, operatorId);

        return new FeedbackConfirmResponse(
                feedback.getId(), feedback.getStatus(), recipientEmail, operatorId, feedback.getLockedAt());
    }

    // ── 50·52번: 발송·재발송 ───────────────────────────────────────

    /**
     * MAIL_GATEWAY_ERROR 는 일부러 rollback 대상에서 뺐다 — 실패한 발송 시도도 Task 로
     * 남겨야 발송 이력(51·53번)에 FAILED 로 잡히고 재발송 사유 검증(52번)도 맞게 동작한다.
     * 그냥 @Transactional 이면 예외가 올라가는 순간 방금 저장한 실패 Task 까지 롤백되어
     * 이력이 조용히 사라진다 — 실서버 확인 중 실제로 겪은 문제다.
     */
    @Transactional(noRollbackFor = FeedbackException.class)
    public FeedbackSendResponse send(Long draftId, FeedbackSendRequest request, String operatorId) {
        Feedback feedback = feedbackRepository.findById(draftId)
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.FEEDBACK_DRAFT_NOT_FOUND));

        if (feedback.getStatus() != FeedbackStatus.READY_TO_SEND) {
            throw new FeedbackException(FeedbackErrorCode.NOT_CONFIRMED);
        }

        int attempt = (int) taskRepository.countByFeedbackIdAndType(draftId, TaskType.SEND_FEEDBACK) + 1;
        ResendReason resendReason = null;

        if (attempt > 1) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new FeedbackException(FeedbackErrorCode.RESEND_REASON_REQUIRED);
            }
            try {
                resendReason = ResendReason.valueOf(request.reason());
            } catch (IllegalArgumentException e) {
                throw new FeedbackException(FeedbackErrorCode.INVALID_RESEND_REASON);
            }
            if (resendReason == ResendReason.NO_REPLY) {
                taskRepository.findTopByFeedbackIdAndTypeOrderByAttemptNumberDesc(draftId, TaskType.SEND_FEEDBACK)
                        .filter(t -> t.getStatus() == TaskStatus.COMPLETED && t.getSentAt() != null)
                        .ifPresent(last -> {
                            OffsetDateTime resendableFrom = last.getSentAt().plusDays(RESEND_WAIT_DAYS);
                            if (OffsetDateTime.now(SEOUL).isBefore(resendableFrom)) {
                                throw new FeedbackException(FeedbackErrorCode.RESEND_TOO_EARLY);
                            }
                        });
            }
        }

        FeedbackDraft confirmedDraft = feedbackDraftRepository.findById(feedback.getConfirmedDraftId())
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.NOT_CONFIRMED));

        Task task = Task.builder()
                .feedback(feedback).feedbackDraft(confirmedDraft)
                .type(TaskType.SEND_FEEDBACK)
                .status(TaskStatus.PROCESSING)
                .progressTotal(1)
                .attemptNumber((short) attempt)
                .resendReason(resendReason)
                .recipientEmail(feedback.getRecipientEmail())
                .requestedBy(operatorId)
                .build();

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(feedback.getRecipientEmail());
            message.setSubject(confirmedDraft.getSubject());
            message.setText(confirmedDraft.getBody());
            mailSender.send(message);

            task.completeSend("msg-" + task.getId());
            taskRepository.save(task);

            return new FeedbackSendResponse(
                    task.getId(), TaskStatus.COMPLETED, feedback.getId(), feedback.getRecipientEmail(), attempt);
        } catch (MailException e) {
            task.fail("MAIL_GATEWAY_ERROR", e.getMessage());
            taskRepository.save(task);
            throw new FeedbackException(FeedbackErrorCode.MAIL_GATEWAY_ERROR,
                    FeedbackErrorCode.MAIL_GATEWAY_ERROR.defaultMessage(), Map.of());
        }
    }

    // ── 51·53번: 발송 이력 조회 ────────────────────────────────────

    public PageResponse<FeedbackHistoryItem> listHistories(
            FeedbackHistorySearchCondition condition, Pageable pageable) {
        Page<Feedback> page = feedbackRepository.search(
                condition.supplierId(), condition.type(), condition.status(),
                condition.from(), condition.to(), pageable);

        List<FeedbackHistoryItem> content = page.getContent().stream().map(this::toHistoryItem).toList();
        return PageResponse.of(page, content);
    }

    private FeedbackHistoryItem toHistoryItem(Feedback feedback) {
        List<Task> sendTasks = taskRepository.findByFeedbackIdAndTypeOrderByAttemptNumberDesc(
                feedback.getId(), TaskType.SEND_FEEDBACK);

        List<FeedbackHistoryItem.DeliveryItem> deliveries = sendTasks.stream()
                .map(t -> new FeedbackHistoryItem.DeliveryItem(
                        t.getAttemptNumber() == null ? 0 : t.getAttemptNumber(),
                        t.getSentAt(), t.getDeliveryStatus(), t.getErrorCode(), t.getResendReason()))
                .toList();

        // 이 건의 최신 발송 결과 — 한 번도 시도하지 않았으면 PENDING (공용 DeliveryStatus 설계 그대로)
        DeliveryStatus latestStatus = sendTasks.isEmpty() ? DeliveryStatus.PENDING : sendTasks.get(0).getDeliveryStatus();

        OffsetDateTime lastSentAt = sendTasks.stream()
                .map(Task::getSentAt).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        OffsetDateTime resendableFrom = lastSentAt == null ? null : lastSentAt.plusDays(RESEND_WAIT_DAYS);

        String subject = feedbackDraftRepository.findTopByFeedbackIdOrderByVersionNumberDesc(feedback.getId())
                .map(FeedbackDraft::getSubject).orElse(null);

        // replyStatus 등은 항상 NO_REPLY/null — 메일 수신 스케줄러(18번)가 없어 회신을 감지할 경로가 없다
        return new FeedbackHistoryItem(
                feedback.getId(), feedback.getSubmissionId(), feedback.getType(), subject, latestStatus,
                "NO_REPLY", lastSentAt, null, null, resendableFrom, deliveries);
    }

    // ── 공통 ──────────────────────────────────────────────────────

    private record GeneratedText(String subject, String body) {
    }

    private GeneratedText generateTemplateText(
            String supplierName, FeedbackStyle style, String instruction, String reasonText) {
        String greeting = switch (style) {
            case FORMAL -> "안녕하십니까, " + supplierName + " 담당자님.";
            case CONCISE -> supplierName + " 담당자님,";
            case FRIENDLY -> "안녕하세요! " + supplierName + " 담당자님~";
        };
        String reasonLine = reasonText == null
                ? "제출 기한이 지났는데 아직 자료가 도착하지 않아 안내드립니다."
                : "제출해 주신 자료 검토 결과, 다음 사유로 보완이 필요합니다: " + reasonText;
        String instructionLine = (instruction == null || instruction.isBlank()) ? "" : "\n\n추가 안내: " + instruction;
        String closing = switch (style) {
            case FORMAL -> "\n\n확인 후 빠른 시일 내 회신 부탁드립니다. 감사합니다.";
            case CONCISE -> "\n\n확인 부탁드립니다.";
            case FRIENDLY -> "\n\n확인해주시면 감사하겠습니다 :)";
        };
        String subject = "[CBAM] " + supplierName + " 배출데이터 제출 안내";
        String body = greeting + "\n\n" + reasonLine + instructionLine + closing;
        return new GeneratedText(subject, body);
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new FeedbackException(FeedbackErrorCode.INVALID_PARAMETER,
                    "reportingMonth 는 YYYY-MM 형식이어야 합니다", Map.of());
        }
    }

    private FeedbackStyle parseStyle(String value) {
        if (value == null || value.isBlank()) {
            return FeedbackStyle.FORMAL;
        }
        try {
            return FeedbackStyle.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new FeedbackException(FeedbackErrorCode.INVALID_PARAMETER,
                    "style 값이 올바르지 않습니다", Map.of());
        }
    }
}
