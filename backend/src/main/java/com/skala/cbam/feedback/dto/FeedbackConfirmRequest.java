package com.skala.cbam.feedback.dto;

/**
 * PATCH /api/v1/feedback-drafts/{id} 요청 (29행) — <b>CBAM-93은 확정만 다룬다.</b>
 * status 가 "READY_TO_SEND" 가 아니면 막는다. 문안 수정(47번)·폐기(49번)는 이번 스코프에 없다.
 */
public record FeedbackConfirmRequest(String status) {
}
