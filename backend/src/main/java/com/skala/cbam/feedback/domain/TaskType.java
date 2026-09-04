package com.skala.cbam.feedback.domain;

/**
 * ERD task_type 전체. 이 중 GENERATE_FEEDBACK_DRAFT·REGENERATE_FEEDBACK_DRAFT·SEND_FEEDBACK 만
 * CBAM-88 스코프에서 쓴다. 나머지(메일 분석·재판정·리마인드 발송)는 그 도메인이 만들 때 같이 쓴다.
 */
public enum TaskType {
    ANALYZE_MAIL_RECEIPT,
    REVALIDATE_SUBMISSION,
    SEND_REMINDER,
    GENERATE_FEEDBACK_DRAFT,
    REGENERATE_FEEDBACK_DRAFT,
    SEND_FEEDBACK
}
