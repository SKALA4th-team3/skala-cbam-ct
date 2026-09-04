package com.skala.cbam.mail.dto;

import com.skala.cbam.mail.domain.MailReceiptStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/v1/mail-receipts 의 content[] 항목 (API 명세 15행, 요구사항 21번). */
public record MailReceiptListItem(
        Long id,
        Long supplierId,
        String supplierName,
        String senderEmail,
        OffsetDateTime receivedAt,
        MailReceiptStatus status,
        long attachmentCount,
        List<Long> submissionIds
) {
}
