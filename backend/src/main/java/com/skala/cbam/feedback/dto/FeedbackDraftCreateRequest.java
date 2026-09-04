package com.skala.cbam.feedback.dto;

import java.util.List;

/**
 * POST /api/v1/feedback-drafts 요청 (API 명세 26행, 42·43번).
 * submissionIds·targets 둘 다 생략하면 해당 월의 부적격·미제출 전체가 대상이다(43번 일괄).
 * style 파싱(빈 값이면 FORMAL 기본)은 서비스에서 한다 — DTO 는 원시 값만 든다.
 */
public record FeedbackDraftCreateRequest(
        String reportingMonth,
        List<Long> submissionIds,
        List<Target> targets,
        String style
) {
    public record Target(Long supplierId, Long partId) {
    }
}
