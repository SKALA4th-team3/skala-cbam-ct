package com.skala.cbam.submission.dto;

import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.SubmissionStatus;
import java.time.OffsetDateTime;

/**
 * POST /api/v1/submissions/{id}/reject 응답 (API 명세 23행, 32번).
 *
 * <p>feedbackDraftTaskId 는 항상 null 이다 — 피드백 초안 생성(42~46번)이 아직 없어서다.
 * createFeedbackDraft=true 로 요청해도 지금은 아무 task 도 안 만든다. 가짜 taskId 는 안 채운다.
 */
public record SubmissionRejectResponse(
        Long submissionId,
        SubmissionStatus status,
        Judgement judgement,
        String reasonCode,
        String reason,
        String rejectedBy,
        OffsetDateTime rejectedAt,
        String feedbackDraftTaskId
) {
}
