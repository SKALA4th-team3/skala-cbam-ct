package com.skala.cbam.feedback.dto;

import com.skala.cbam.common.domain.FeedbackStatus;
import java.time.OffsetDateTime;

/** PATCH /api/v1/feedback-drafts/{id} 확정 응답 (29행, 48번). */
public record FeedbackConfirmResponse(
        Long draftId,
        FeedbackStatus status,
        String recipient,
        String confirmedBy,
        OffsetDateTime lockedAt
) {
}
