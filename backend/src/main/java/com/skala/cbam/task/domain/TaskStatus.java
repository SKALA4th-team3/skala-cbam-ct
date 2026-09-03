package com.skala.cbam.task.domain;

/**
 * 비동기 작업 실행 상태.
 *
 * 메일 분석, 재판정, 피드백 생성·재생성,
 * 피드백 발송 및 리마인드 발송 작업에 사용한다.
 */
public enum TaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
