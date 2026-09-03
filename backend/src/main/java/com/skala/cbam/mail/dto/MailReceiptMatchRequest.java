package com.skala.cbam.mail.dto;

/** PATCH /api/v1/mail-receipts/{id}/supplier 요청 (API 명세 18행, 요구사항 19·21번). */
public record MailReceiptMatchRequest(Long supplierId) {
}
