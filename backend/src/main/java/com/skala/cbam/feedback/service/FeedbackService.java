package com.skala.cbam.feedback.service;

import com.skala.cbam.ai.client.AiCallException;
import com.skala.cbam.ai.dto.DraftInput;
import com.skala.cbam.ai.dto.DraftResult;
import com.skala.cbam.ai.prompt.DraftStyle;
import com.skala.cbam.ai.service.AiService;
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
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p><b>초안은 AI 가 쓴다</b>(CBAM-99, 42~45번). {@link AiService} 가 실패하거나 근거 밖 항목을
 * 요구하면 요구사항 46번대로 <b>기본 템플릿</b>으로 되돌리고 {@code fallbackApplied} 를 남긴다 —
 * 화면이 「그대로 보내지 말고 고쳐서 확정하라」고 말할 수 있게 하기 위해서다.
 * AI 키가 없어도(dev·테스트) 그 경로로 그대로 동작한다.
 *
 * <p>생성·재생성은 지금도 동기라 status=COMPLETED 로 응답한다. 만들어진 초안 id 는
 * {@code Task.recordResult()} 로 남겨 №19 작업 조회의 {@code resourceIds} 가 돌려준다(ADR-0012) —
 * 전에는 화면이 발송 이력을 훑어 방금 만든 초안을 찾아야 했다.
 *
 * <p>발송(send)만 다르다 — 실제 {@link JavaMailSender} 로 진짜 발송을 시도한다. .env 에 MAIL_SMTP_*
 * 가 없으면 진짜로 502 MAIL_GATEWAY_ERROR 가 난다. 이건 명세가 원래 요구하는 에러 코드라 가짜로
 * 흉내 낸 게 아니다.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String FALLBACK_TEMPLATE_ID = "basic-ko-v1";
    private static final int RESEND_WAIT_DAYS = 3;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackDraftRepository feedbackDraftRepository;
    private final TaskRepository taskRepository;
    private final SupplierRepository supplierRepository;
    private final SubmissionRelatedDataProvider submissionRelatedDataProvider;
    private final AiService aiService;
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
                contexts.add(new TargetContext(info.supplierId(), info.partSupplierId(),
                        info.submissionId(), info.rejectionReason(), info));
            }
        }
        if (hasTargets) {
            // 미제출 대상 — 제출 데이터 행이 없어 반려 사유도 근거도 없다
            for (var t : request.targets()) {
                contexts.add(new TargetContext(t.supplierId(), null, null, null, null));
            }
        }
        if (!hasSubmissionIds && !hasTargets) {
            // 43번 일괄 — 이번 달 부적격·미제출 전체. submission 도메인이 없어 지금은 항상 빈 목록.
            for (var info : submissionRelatedDataProvider.findDraftableSubmissions(month)) {
                contexts.add(new TargetContext(info.supplierId(), info.partSupplierId(),
                        info.submissionId(), info.rejectionReason(), info));
            }
        }

        if (contexts.isEmpty()) {
            throw new FeedbackException(FeedbackErrorCode.NO_TARGET);
        }

        List<Long> createdFeedbackIds = new ArrayList<>();
        boolean anyFallback = false;

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

            GeneratedDraft draft = generateDraft(supplier.getName(), style, null, ctx, month);
            anyFallback |= draft.fallbackApplied();

            feedbackDraftRepository.save(FeedbackDraft.builder()
                    .feedback(feedback)
                    .versionNumber((short) 1)
                    .sourceType(draft.sourceType())
                    .style(style)
                    .subject(draft.subject())
                    .body(draft.body())
                    .fallbackApplied(draft.fallbackApplied())
                    .fallbackTemplateId(draft.fallbackApplied() ? FALLBACK_TEMPLATE_ID : null)
                    .build());

            createdFeedbackIds.add(feedback.getId());
        }

        Task task = Task.builder()
                .type(TaskType.GENERATE_FEEDBACK_DRAFT)
                .status(TaskStatus.PROCESSING)
                .progressTotal(contexts.size())
                .fallbackApplied(anyFallback)
                .requestedBy(operatorId)
                .build();
        // 43번 일괄은 초안 N 개를 만들고 Task 는 하나다 — 단수 FK 로는 못 가리킨다 (ADR-0012)
        task.recordResult(TaskResourceType.FEEDBACK, createdFeedbackIds);
        task.completeSuccessfully();
        taskRepository.save(task);

        return new FeedbackDraftCreateResponse(task.getId(), TaskStatus.COMPLETED, contexts.size());
    }

    /** 초안 하나를 만드는 데 필요한 것 전부. {@code info} 가 null 이면 미제출 대상이라 근거가 없다. */
    private record TargetContext(Long supplierId, Long partSupplierId, Long submissionId,
                                 String rejectionReason, SubmissionRelatedDataProvider.SubmissionInfo info) {
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

        // 재생성도 원래 근거로 다시 쓴다 — 추가 지시는 말투와 강조점만 바꾼다 (45번)
        TargetContext ctx = contextOf(feedback);
        YearMonth month = feedback.getReportingMonth() == null
                ? null : YearMonth.from(feedback.getReportingMonth());
        GeneratedDraft generated = generateDraft(
                feedback.getSupplier().getName(), style, request.instruction(), ctx, month);

        FeedbackDraft draft = feedbackDraftRepository.save(FeedbackDraft.builder()
                .feedback(feedback)
                .versionNumber(nextVersion)
                .sourceType(generated.sourceType())
                .style(style)
                .instruction(request.instruction())
                .subject(generated.subject())
                .body(generated.body())
                .fallbackApplied(generated.fallbackApplied())
                .fallbackTemplateId(generated.fallbackApplied() ? FALLBACK_TEMPLATE_ID : null)
                .build());

        Task task = Task.builder()
                .feedback(feedback).feedbackDraft(draft)
                .type(TaskType.REGENERATE_FEEDBACK_DRAFT)
                .status(TaskStatus.PROCESSING)
                .progressTotal(1)
                .fallbackApplied(generated.fallbackApplied())
                .requestedBy(operatorId)
                .build();
        task.recordResult(TaskResourceType.FEEDBACK_DRAFT, List.of(draft.getId()));
        task.completeSuccessfully();
        taskRepository.save(task);

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

    /** 만들어진 초안 하나. {@code fallbackApplied} 가 true 면 AI 가 아니라 기본 템플릿이다 (46번). */
    private record GeneratedDraft(String subject, String body,
                                  DraftSourceType sourceType, boolean fallbackApplied) {

        static GeneratedDraft fromAi(String supplierName, DraftResult result) {
            // 프롬프트가 협력업체명과 「CBAM」 을 빼고 쓰게 돼 있다 — 서버가 붙인다
            return new GeneratedDraft(
                    "[CBAM] " + supplierName + " " + result.subject().strip(),
                    result.bodyText(), DraftSourceType.AI, false);
        }

        static GeneratedDraft fromTemplate(GeneratedText text) {
            return new GeneratedDraft(text.subject(), text.body(), DraftSourceType.FALLBACK_TEMPLATE, true);
        }
    }

    /**
     * 42~45번 초안 하나를 만든다. <b>AI 로 쓰고, 안 되면 기본 템플릿으로 되돌린다</b>(46번).
     *
     * <p>기본 템플릿으로 가는 경우는 넷이다. 셋은 명세가 말한 실패이고, 하나는 실패가 아니다.
     * <ul>
     *   <li>AI 키가 없다 — dev·테스트가 이 경로다</li>
     *   <li>호출이 실패했거나 시간이 초과됐다 ({@link AiCallException})</li>
     *   <li>돌려준 초안이 <b>근거 밖 항목을 요구했다</b> — 스키마로 막히지 않아 서버가 대조한다</li>
     *   <li><b>근거가 아예 없다</b> — 미제출 대상이거나 CBAM-90 이 아직 안 붙어 반려 사유가 비었다.
     *       없는 근거로 문장을 지어내게 하지 않는다</li>
     * </ul>
     *
     * <p>실패해도 예외를 올리지 않는다. 초안이 없는 것보다 고쳐 쓸 템플릿이라도 있는 편이 낫고,
     * 화면은 {@code fallbackApplied} 를 보고 「그대로 보내지 말라」고 말한다.
     */
    private GeneratedDraft generateDraft(String supplierName, FeedbackStyle style,
                                         String instruction, TargetContext ctx, YearMonth month) {
        GeneratedText template = generateTemplateText(supplierName, style, instruction, ctx.rejectionReason());

        if (!aiService.isAvailable()) {
            return GeneratedDraft.fromTemplate(template);
        }

        DraftInput input = toDraftInput(supplierName, style, instruction, ctx, month);
        if (input.hasNoBasis()) {
            log.info("초안 근거가 없어 기본 템플릿으로 간다 (submissionId={})", ctx.submissionId());
            return GeneratedDraft.fromTemplate(template);
        }

        try {
            DraftResult result = aiService.draft(input);
            if (result != null) {
                return GeneratedDraft.fromAi(supplierName, result);
            }
            log.warn("AI 초안이 근거를 벗어나 기본 템플릿으로 간다 (submissionId={})", ctx.submissionId());
        } catch (AiCallException e) {
            // 46번 — 실패를 표시하고 기본 템플릿을 대신 제공한다
            log.warn("AI 초안 생성 실패({}) — 기본 템플릿으로 간다: {}", e.errorCode(), e.getMessage());
        }
        return GeneratedDraft.fromTemplate(template);
    }

    private DraftInput toDraftInput(String supplierName, FeedbackStyle style,
                                    String instruction, TargetContext ctx, YearMonth month) {
        var info = ctx.info();
        List<DraftInput.MissingItem> missing = info == null ? List.of()
                : info.missingFields().stream()
                        .map(f -> new DraftInput.MissingItem(f.key(), f.label(), f.rawValue(), f.note()))
                        .toList();

        return new DraftInput(
                supplierName,
                month == null ? null : month.toString(),
                null, // 회신 기한 — 명세에 초안 생성 요청의 기한 필드가 없다. 지어내지 않는다
                info == null ? null : info.judgement(),
                info == null ? null : info.ruleId(),
                info == null ? null : info.ruleName(),
                ctx.rejectionReason(),
                missing,
                info == null ? List.of() : info.unregisteredPartNames(),
                ctx.rejectionReason(),
                DraftStyle.valueOf(style.name()),
                instruction);
    }

    /** 재생성이 원래 근거를 다시 읽는다. 제출 도메인이 없으면 근거도 없다 — 그때는 템플릿이다. */
    private TargetContext contextOf(Feedback feedback) {
        Long submissionId = feedback.getSubmissionId();
        var info = submissionId == null ? null
                : submissionRelatedDataProvider.findSubmissionInfo(submissionId).orElse(null);
        return new TargetContext(
                feedback.getSupplier().getId(), feedback.getPartSupplierId(), submissionId,
                info == null ? null : info.rejectionReason(), info);
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
