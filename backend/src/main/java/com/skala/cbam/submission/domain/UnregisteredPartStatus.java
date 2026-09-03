package com.skala.cbam.submission.domain;

/** 미등록 부품 처리 상태. RESOLVED 면 정식 부품에 연결 완료된 것이다. */
public enum UnregisteredPartStatus {
    OPEN,
    RESOLVED
}
