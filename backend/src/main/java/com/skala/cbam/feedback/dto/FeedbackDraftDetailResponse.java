package com.skala.cbam.feedback.dto;

import com.skala.cbam.common.domain.FeedbackStatus;
import com.skala.cbam.feedback.domain.DraftSourceType;
import com.skala.cbam.feedback.domain.FeedbackStyle;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/v1/feedback-drafts/{id} 응답 (27행, 44·46번). */
public record FeedbackDraftDetailResponse(
        Long id,
        Long submissionId,
        Long supplierId,
        FeedbackStyle style,
        String subject,
        String body,
        int version,
        DraftSourceType source,
        FeedbackStatus status,
        boolean fallbackApplied,
        String fallbackTemplateId,
        List<JudgementReason> judgementReasons,
        List<VersionSummary> versions
) {
    /** 판정 근거를 초안과 나란히 보여준다(44번). submission 도메인이 없어 지금은 항상 빈 배열. */
    public record JudgementReason(String ruleId, String severity, String message) {
    }

    public record VersionSummary(int version, DraftSourceType source, OffsetDateTime createdAt) {
    }
}
