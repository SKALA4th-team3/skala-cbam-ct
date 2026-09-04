package com.skala.cbam.submission.domain;

/** 위험 탐지 규칙(R1~R7)의 심각도. R1·R2=HIGH, R3·R4·R7=MEDIUM, R5·R6=LOW (코드·Enum 정의 시트). */
public enum Severity {
    HIGH,
    MEDIUM,
    LOW
}
