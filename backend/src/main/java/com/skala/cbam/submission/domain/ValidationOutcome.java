package com.skala.cbam.submission.domain;

/** 판정 규칙 검사 결과. SKIPPED 는 "이전 데이터가 없어서 건너뜀"(요구사항 35번) 같은 경우다. */
public enum ValidationOutcome {
    PASS,
    FAIL,
    SKIPPED
}
