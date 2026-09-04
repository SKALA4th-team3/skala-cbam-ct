package com.skala.cbam.feedback.dto;

import com.skala.cbam.common.domain.TaskStatus;

/**
 * POST /api/v1/feedback-drafts/{id}/send 응답 (30행, 202 봉투).
 *
 * <p>status 는 진짜 결과다(COMPLETED 또는 FAILED) — 다른 202 API와 달리 이건 fallback 이 아니라
 * 실제 SMTP 발송을 시도하고 그 결과를 즉시 반영한다. .env 에 MAIL_SMTP_* 가 안 채워져 있으면
 * 진짜로 502 MAIL_GATEWAY_ERROR 가 난다 — 명세가 원래 요구하는 그 에러 코드다.
 */
public record FeedbackSendResponse(String taskId, TaskStatus status, Long draftId, String recipient, int attempt) {
}
