package com.skala.cbam.submission.dto;

/**
 * POST /api/v1/submissions/{id}/reject 요청 (API 명세 23행, 32번).
 *
 * <p>resultStatus 를 문자열로 받는다 — SubmissionStatus enum 으로 바로 받으면 CONFIRMED·REVIEW_PENDING
 * 같은 값도 역직렬화가 성공해버려서, "REJECTED·NOT_SUBMITTED 외 값"이라는 명세의 400
 * INVALID_RESULT_STATUS 를 깔끔하게 낼 수 없다. 검증은 서비스에서 한다.
 */
public record SubmissionRejectRequest(
        String resultStatus,
        String reasonCode,
        String reason,
        Boolean createFeedbackDraft
) {
}
