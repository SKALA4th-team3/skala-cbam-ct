package com.skala.cbam.feedback.dto;

/** POST /api/v1/feedback-drafts/{id}/send 요청 (30행). 최초 발송은 {}, 재발송은 reason 필수. */
public record FeedbackSendRequest(String reason) {
}
