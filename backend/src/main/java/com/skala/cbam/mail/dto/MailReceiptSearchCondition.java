package com.skala.cbam.mail.dto;

import com.skala.cbam.mail.domain.MailReceiptStatus;
import java.time.OffsetDateTime;

/** GET /api/v1/mail-receipts 의 필터 조건 (15번). */
public record MailReceiptSearchCondition(
        Long supplierId,
        MailReceiptStatus status,
        OffsetDateTime receivedFrom,
        OffsetDateTime receivedTo
) {
}
