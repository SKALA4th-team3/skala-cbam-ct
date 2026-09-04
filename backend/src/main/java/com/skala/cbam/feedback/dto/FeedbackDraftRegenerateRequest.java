package com.skala.cbam.feedback.dto;

/** POST /api/v1/feedback-drafts/{id}/regenerate 요청 (28행, 45번). */
public record FeedbackDraftRegenerateRequest(String instruction, String style) {
}
