package com.skala.cbam.mail.dto;

import com.skala.cbam.mail.domain.MailReceiptStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/v1/mail-receipts/{id} 응답 (API 명세 16행, 요구사항 20·21·22번). */
public record MailReceiptDetailResponse(
        Long id,
        String messageId,
        String senderEmail,
        Long supplierId,
        MailReceiptStatus status,
        String subject,
        String body,
        OffsetDateTime receivedAt,
        String latestAnalysisTaskId,
        String failureReason,
        List<AttachmentItem> attachments
) {
    /** fileType 은 originalFilename 확장자에서 뽑는다 (mime_type 그대로 노출하지 않는다). */
    public record AttachmentItem(
            Long id,
            String fileName,
            String fileType,
            long sizeBytes,
            String viewUrl,
            String downloadUrl
    ) {
    }
}
