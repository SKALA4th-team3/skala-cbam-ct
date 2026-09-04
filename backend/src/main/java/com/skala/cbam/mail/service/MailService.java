package com.skala.cbam.mail.service;

import com.skala.cbam.mail.domain.Attachment;
import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.domain.MailReceiptStatus;
import com.skala.cbam.mail.dto.AttachmentContent;
import com.skala.cbam.mail.dto.MailReceiptDetailResponse;
import com.skala.cbam.mail.dto.MailReceiptListItem;
import com.skala.cbam.mail.dto.MailReceiptMatchResponse;
import com.skala.cbam.mail.dto.MailReceiptSearchCondition;
import com.skala.cbam.mail.error.MailErrorCode;
import com.skala.cbam.mail.error.MailException;
import com.skala.cbam.mail.repository.AttachmentRepository;
import com.skala.cbam.mail.repository.MailReceiptRepository;
import com.skala.cbam.mail.service.port.MailRelatedDataProvider;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import com.skala.cbam.supplier.dto.PageResponse;
import com.skala.cbam.supplier.repository.SupplierRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 접수 API 서비스 (15~18번, CBAM-79).
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private final MailReceiptRepository mailReceiptRepository;
    private final AttachmentRepository attachmentRepository;
    private final SupplierRepository supplierRepository;
    private final MailRelatedDataProvider mailRelatedDataProvider;
    private final MailAnalysisService mailAnalysisService;

    // ── 15번: 접수 이력 조회 ────────────────────────────────────────

    public PageResponse<MailReceiptListItem> listReceipts(MailReceiptSearchCondition condition, Pageable pageable) {
        Page<MailReceipt> page = mailReceiptRepository.search(
                condition.supplierId(), condition.status(), condition.receivedFrom(), condition.receivedTo(), pageable);

        List<MailReceiptListItem> content = page.getContent().stream().map(this::toListItem).toList();
        return PageResponse.of(page, content);
    }

    private MailReceiptListItem toListItem(MailReceipt receipt) {
        Supplier supplier = receipt.getSupplier();
        long attachmentCount = attachmentRepository.countByMailReceiptId(receipt.getId());
        List<Long> submissionIds = mailRelatedDataProvider.findSubmissionIds(receipt.getId());

        return new MailReceiptListItem(
                receipt.getId(),
                supplier == null ? null : supplier.getId(),
                supplier == null ? null : supplier.getName(),
                receipt.getSenderEmail(),
                receipt.getReceivedAt(),
                receipt.getStatus(),
                attachmentCount,
                submissionIds
        );
    }

    // ── 16번: 접수 메일 상세 조회 ───────────────────────────────────

    public MailReceiptDetailResponse getDetail(Long receiptId) {
        MailReceipt receipt = mailReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new MailException(MailErrorCode.MAIL_RECEIPT_NOT_FOUND));

        List<MailReceiptDetailResponse.AttachmentItem> attachments =
                attachmentRepository.findByMailReceiptId(receiptId).stream()
                        .map(this::toAttachmentItem)
                        .toList();

        return new MailReceiptDetailResponse(
                receipt.getId(),
                receipt.getMessageId(),
                receipt.getSenderEmail(),
                receipt.getSupplier() == null ? null : receipt.getSupplier().getId(),
                receipt.getStatus(),
                receipt.getSubject(),
                receipt.getBody(),
                receipt.getReceivedAt(),
                receipt.getLatestAnalysisTaskId(),
                receipt.getFailureReason(),
                attachments
        );
    }

    private MailReceiptDetailResponse.AttachmentItem toAttachmentItem(Attachment attachment) {
        return new MailReceiptDetailResponse.AttachmentItem(
                attachment.getId(),
                attachment.getOriginalFilename(),
                fileTypeOf(attachment.getOriginalFilename()),
                attachment.getSizeBytes(),
                "/api/v1/attachments/" + attachment.getId() + "?disposition=inline",
                "/api/v1/attachments/" + attachment.getId() + "?disposition=attachment"
        );
    }

    private String fileTypeOf(String filename) {
        int lastDot = filename == null ? -1 : filename.lastIndexOf('.');
        return lastDot < 0 ? "" : filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    // ── 17번: 첨부파일 열람·다운로드 ─────────────────────────────────

    public AttachmentContent getAttachmentContent(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new MailException(MailErrorCode.ATTACHMENT_NOT_FOUND));

        Resource resource = new FileSystemResource(attachment.getStorageUri());
        if (!resource.exists() || !resource.isReadable()) {
            // storage_uri 를 가리키는 실제 파일이 없다 — DB 행은 있는데 원본이 없는 경우도
            // "찾을 수 없다"로 같이 처리한다. 파일 시스템 내부 사정을 클라이언트가 알 필요는 없다.
            throw new MailException(MailErrorCode.ATTACHMENT_NOT_FOUND);
        }

        return new AttachmentContent(
                resource, attachment.getMimeType(), attachment.getOriginalFilename(), attachment.getSizeBytes());
    }

    // ── 18번: 발신자-협력업체 수동 매칭 ──────────────────────────────

    @Transactional
    public MailReceiptMatchResponse match(Long receiptId, Long supplierId, String linkedBy) {
        MailReceipt receipt = mailReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new MailException(MailErrorCode.MAIL_RECEIPT_NOT_FOUND));

        if (receipt.getStatus() != MailReceiptStatus.UNMATCHED) {
            throw new MailException(MailErrorCode.ALREADY_MATCHED);
        }

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new MailException(MailErrorCode.SUPPLIER_NOT_FOUND));
        if (supplier.getStatus() == SupplierStatus.INACTIVE) {
            throw new MailException(MailErrorCode.INACTIVE_SUPPLIER);
        }

        receipt.match(supplier, linkedBy);

        // 요구사항 20번 "연결 즉시 AI 분석을 자동 호출한다" (CBAM-100).
        // 분석은 커밋 뒤 별도 스레드에서 돈다 — 여기서는 taskId 만 받아 즉시 돌려주고,
        // 화면은 그것으로 №19 GET /tasks/{taskId} 를 폴링한다.
        String analyzeTaskId = mailAnalysisService.scheduleAnalysis(receipt, linkedBy);

        return new MailReceiptMatchResponse(
                receipt.getId(), supplier.getId(), receipt.getStatus(),
                receipt.getLinkedBy(), receipt.getLinkedAt(), analyzeTaskId);
    }
}
