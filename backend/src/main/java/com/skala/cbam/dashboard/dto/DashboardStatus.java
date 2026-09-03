package com.skala.cbam.dashboard.dto;

/**
 * API 명세 24행의 협력업체별/전체 상태 3분류 — QUALIFIED|UNQUALIFIED|NOT_SUBMITTED.
 * DB의 judgement_status(2값) · submission_status(4값) 와는 다른, 화면 표시용 계산값이다.
 */
public enum DashboardStatus {
    QUALIFIED,
    UNQUALIFIED,
    NOT_SUBMITTED
}
