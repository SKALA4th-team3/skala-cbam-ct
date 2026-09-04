package com.skala.cbam.mail.dto;

import com.skala.cbam.mail.domain.MailReceiptStatus;
import java.time.OffsetDateTime;

/**
 * PATCH /api/v1/mail-receipts/{id}/supplier 응답 (API 명세 18행, 83번).
 *
 * <p>analyzeTaskId 는 항상 null 이다 — "연결 즉시 AI 분석을 자동 호출한다"는 요구사항 20번의
 * AI 분석 도메인(22~27번, CBAM-84)이 아직 없다. 가짜 taskId 는 만들지 않는다.
 */
public record MailReceiptMatchResponse(
        Long receiptId,
        Long supplierId,
        MailReceiptStatus status,
        String linkedBy,
        OffsetDateTime linkedAt,
        String analyzeTaskId
) {
}
