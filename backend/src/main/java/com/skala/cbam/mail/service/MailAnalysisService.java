package com.skala.cbam.mail.service;

import com.skala.cbam.ai.client.AiCallException;
import com.skala.cbam.ai.dto.ExtractionInput;
import com.skala.cbam.ai.dto.ExtractionResult;
import com.skala.cbam.ai.service.AiService;
import com.skala.cbam.common.domain.TaskStatus;
import com.skala.cbam.feedback.domain.Task;
import com.skala.cbam.feedback.domain.TaskType;
import com.skala.cbam.feedback.repository.TaskRepository;
import com.skala.cbam.mail.domain.Attachment;
import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.repository.AttachmentRepository;
import com.skala.cbam.mail.repository.MailReceiptRepository;
import com.skala.cbam.mail.service.port.AnalysisResultSink;
import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.task.domain.TaskResourceType;
import com.skala.cbam.task.service.TaskResourceRecorder;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 요구사항 22~25번 접수 자료 분석 (CBAM-100).
 *
 * <p>요구사항 20번 「연결 즉시 AI 분석을 자동 호출한다」의 그 호출이다. 명세 v10 은 v7 에서
 * <b>분석 요청 엔드포인트를 없앴다</b> — 분석은 접수 완료·수동 매칭 직후 자동 실행만 남았다.
 * 그래서 이 서비스에 컨트롤러가 없다. 결과는 №19 {@code GET /tasks/{taskId}} 로 받는다.
 *
 * <p><b>왜 비동기인가</b> — AI 호출이 수 초 걸린다. 매칭 응답(№18)이 그동안 막혀 있으면
 * 담당자는 버튼을 누르고 화면이 멎은 것을 본다. {@code taskId} 를 즉시 주고 화면이 폴링한다
 * (ADR-0012 ②).
 *
 * <p><b>왜 커밋 뒤에 도는가</b> — {@link TransactionalEventListener} 의 {@code AFTER_COMMIT} 이다.
 * 매칭 트랜잭션이 커밋되기 전에 다른 스레드가 돌면 그 스레드는 아직 없는 접수 건을 읽는다.
 */
@Service
@RequiredArgsConstructor
public class MailAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(MailAnalysisService.class);
    private static final DateTimeFormatter RECEIVED_AT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final MailReceiptRepository mailReceiptRepository;
    private final AttachmentRepository attachmentRepository;
    private final PartsRepository partsRepository;
    private final TaskRepository taskRepository;
    private final TaskResourceRecorder taskResourceRecorder;
    private final AttachmentTextExtractor attachmentTextExtractor;
    private final AiService aiService;
    private final AnalysisResultSink analysisResultSink;
    private final ApplicationEventPublisher events;

    /**
     * 분석을 예약하고 {@code taskId} 를 즉시 돌려준다 (20번). 부르는 쪽의 트랜잭션에 참여한다.
     *
     * <p>매칭이 롤백되면 이 작업도 함께 사라지고 이벤트도 발행되지 않는다 — 없는 접수 건을
     * 분석하는 작업이 남지 않는다.
     *
     * @return 만들어진 작업 id. 화면이 이것으로 №19 를 폴링한다
     */
    @Transactional
    public String scheduleAnalysis(MailReceipt receipt, String requestedBy) {
        Task task = taskRepository.save(Task.builder()
                .type(TaskType.ANALYZE_MAIL_RECEIPT)
                .status(TaskStatus.PENDING)
                .progressTotal(1)
                .fallbackApplied(false)
                .requestedBy(requestedBy)
                .build());

        receipt.startAnalysis(task.getId());
        // 어느 접수 건을 분석했는지 남긴다 — 결과 submission 이 없어도 추적할 수 있다
        taskResourceRecorder.record(task.getId(), TaskResourceType.MAIL_RECEIPT, receipt.getId());

        events.publishEvent(new AnalysisRequested(task.getId(), receipt.getId()));
        return task.getId();
    }

    /** 커밋된 뒤에 별도 스레드에서 돈다. 실패해도 매칭 트랜잭션에는 영향이 없다. */
    @Async
    @TransactionalEventListener
    public void onAnalysisRequested(AnalysisRequested event) {
        try {
            runAnalysis(event.taskId(), event.mailReceiptId());
        } catch (RuntimeException e) {
            // 여기서 예외가 새면 아무도 못 본다 — 작업을 실패로 남기는 것까지가 이 메서드의 몫이다
            log.error("분석 작업이 예상치 못하게 실패했다: taskId={}", event.taskId(), e);
            markFailed(event.taskId(), AiCallException.AI_ERROR, e.getMessage());
        }
    }

    /**
     * 22~25번을 실제로 수행한다. <b>테스트는 이 메서드를 직접 부른다</b> — 비동기를 거치지 않아야
     * 결과를 확정적으로 확인할 수 있다.
     */
    @Transactional
    public void runAnalysis(String taskId, Long mailReceiptId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        MailReceipt receipt = mailReceiptRepository.findById(mailReceiptId).orElse(null);
        if (task == null || receipt == null) {
            log.warn("분석 대상이 사라졌다: taskId={}, mailReceiptId={}", taskId, mailReceiptId);
            return;
        }
        task.markProcessing();

        List<Attachment> attachments = attachmentRepository.findByMailReceiptId(mailReceiptId);
        AttachmentTextExtractor.Result extracted = attachmentTextExtractor.extract(attachments);

        ExtractionInput input = new ExtractionInput(
                supplierNameOf(receipt),
                receipt.getReceivedAt() == null ? null : receipt.getReceivedAt().format(RECEIVED_AT),
                null, // 대상 월은 자료에서 읽는다(documentMonth) — 접수 시점엔 모른다. 지어내지 않는다
                receipt.getBody(),
                extracted.texts(),
                registeredParts());

        if (input.hasNothingToRead()) {
            // 본문도 없고 읽어낸 첨부도 없다 — 모델을 부르지 않는다
            String reason = extracted.hadAttachments()
                    ? ExtractionResult.PARSE_FAILED   // 첨부는 있었는데 하나도 못 읽었다
                    : "NO_ATTACHMENT";
            finishAsFailed(task, receipt, reason, "읽어낼 본문도 첨부도 없습니다");
            return;
        }

        ExtractionResult.Normalized normalized;
        try {
            normalized = aiService.extract(input);
        } catch (AiCallException e) {
            // 명세 №19 의 errorCode — AI_TIMEOUT · AI_ERROR. 접수 상태도 분석 실패로 둔다
            log.warn("AI 분석 호출 실패({}): taskId={}", e.errorCode(), taskId, e);
            task.failWithoutDelivery(e.errorCode(), e.getMessage());
            receipt.failAnalysis(ExtractionResult.PARSE_FAILED);
            return;
        }

        ExtractionResult result = normalized.result();
        if (result.isFailed()) {
            // 모델이 「읽지 못했다」고 정상 응답한 경우. 호출 오류와 다르다
            finishAsFailed(task, receipt, result.failureReason(), "자료에서 배출 항목을 읽어내지 못했습니다");
            return;
        }

        AnalysisResultSink.Outcome outcome = analysisResultSink.save(mailReceiptId, result);
        taskResourceRecorder.record(task.getId(), TaskResourceType.SUBMISSION, outcome.submissionIds());

        receipt.completeAnalysis();
        task.completeSuccessfully();
        log.info("분석 완료: taskId={}, 항목 {}개, 미등록 부품 {}개, 서버 정정 {}건",
                taskId, result.items().size(), result.unregisteredParts().size(), normalized.repairs().size());
    }

    private void finishAsFailed(Task task, MailReceipt receipt, String failureReason, String message) {
        String reason = (failureReason == null || failureReason.isBlank())
                ? ExtractionResult.PARSE_FAILED : failureReason;
        task.failWithoutDelivery(reason, message);
        receipt.failAnalysis(reason);
        log.info("분석 실패로 마감: taskId={}, reason={}", task.getId(), reason);
    }

    private void markFailed(String taskId, String errorCode, String message) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.failWithoutDelivery(errorCode, message);
            taskRepository.save(task);
        });
    }

    private String supplierNameOf(MailReceipt receipt) {
        Supplier supplier = receipt.getSupplier();
        return supplier == null ? null : supplier.getName();
    }

    /**
     * 25번 — 등록 부품 목록. <b>매번 넣는다.</b> 안 넣으면 모델이 그럴듯한 부품명을 지어내고,
     * 그러면 「미등록 부품」이 한 번도 나오지 않는다 — 담당자는 매칭이 잘 됐다고 믿게 된다.
     * 실제 호출로 확인한 것이다.
     */
    private List<ExtractionInput.RegisteredPart> registeredParts() {
        return partsRepository.findAll().stream()
                .map(part -> new ExtractionInput.RegisteredPart(
                        part.getId(), part.getPartName(), part.getCnCode(), null))
                .toList();
    }

    /** 커밋 뒤에 분석을 시작시키는 신호. */
    public record AnalysisRequested(String taskId, Long mailReceiptId) {
    }
}
