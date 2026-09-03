package com.skala.cbam.submission.domain;

/** 규제 근거로 사용 가능한 자료인지. NOT_USABLE 이면 R3(자료 적격성 불가)가 발동한다. */
public enum EligibilityStatus {
    USABLE,
    NOT_USABLE
}
