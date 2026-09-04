package com.skala.cbam.dashboard.entity;

/** ERD submission_status enum — 제출 데이터 처리 상태 (judgement 적격/부적격과는 다른 축) */
public enum SubmissionStatus {
    REVIEW_PENDING,
    CONFIRMED,
    REJECTED,
    NOT_SUBMITTED
}
