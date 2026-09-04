package com.skala.cbam.feedback.domain;

/** 2회차 이상 발송의 재발송 사유. 요구사항 52번 "실패 건이나 회신이 없는 건". */
public enum ResendReason {
    SEND_FAILED,
    NO_REPLY
}
