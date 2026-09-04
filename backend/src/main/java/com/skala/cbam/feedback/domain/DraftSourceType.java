package com.skala.cbam.feedback.domain;

/**
 * 초안 출처. AI 제공자가 아직 미정이라(요구사항 46번 fallback 경로) 지금은 항상
 * {@link #FALLBACK_TEMPLATE} 로만 생성된다 — AI 연동이 붙으면 {@link #AI} 경로가 추가된다.
 */
public enum DraftSourceType {
    AI,
    FALLBACK_TEMPLATE,
    HUMAN_EDIT
}
