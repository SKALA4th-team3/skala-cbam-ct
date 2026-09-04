package com.skala.cbam.feedback.dto;

import com.skala.cbam.common.domain.TaskStatus;

/**
 * POST /api/v1/feedback-drafts 응답 (26행, 202 Accepted 봉투).
 *
 * <p>status 는 실제로는 항상 COMPLETED 다 — AI 없이 46번의 기본 템플릿으로 즉시 만들어서 동기로
 * 끝나기 때문이다. 그래도 taskId 는 만든다(계약대로) — 다만 GET /api/v1/tasks/{taskId}(19번, 폴링)가
 * 이번 스코프에 없어서 진짜 폴링은 안 된다.
 */
public record FeedbackDraftCreateResponse(String taskId, TaskStatus status, long targetCount) {
}
