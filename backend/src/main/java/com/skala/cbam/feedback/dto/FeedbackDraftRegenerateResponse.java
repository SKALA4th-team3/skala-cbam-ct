package com.skala.cbam.feedback.dto;

import com.skala.cbam.common.domain.TaskStatus;

/** POST /api/v1/feedback-drafts/{id}/regenerate 응답 (28행). status 는 항상 COMPLETED — 26행과 같은 이유. */
public record FeedbackDraftRegenerateResponse(String taskId, TaskStatus status, Long draftId, int nextVersion) {
}
