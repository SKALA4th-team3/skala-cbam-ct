package com.skala.cbam.feedback.domain;

/**
 * 피드백 및 리마인드 메일 발송 상태.
 *
 * TaskStatus는 비동기 작업 자체의 실행 상태이고,
 * DeliveryStatus는 사용자에게 표시할 발송 결과다.
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
