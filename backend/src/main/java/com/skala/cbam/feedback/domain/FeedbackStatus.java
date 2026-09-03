package com.skala.cbam.feedback.domain;

/**
 * 피드백 초안 검토 상태.
 *
 * 발송 결과는 DeliveryStatus로 별도 관리한다.
 */
public enum FeedbackStatus {
    DRAFT,
    READY_TO_SEND,
    DISCARDED
}
