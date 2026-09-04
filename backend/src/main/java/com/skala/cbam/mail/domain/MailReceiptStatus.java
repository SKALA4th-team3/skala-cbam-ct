package com.skala.cbam.mail.domain;

/** 접수 메일 상태. 코드·Enum 정의 시트 MailReceiptStatus 그대로. */
public enum MailReceiptStatus {
    WAITING,
    MATCHED,
    UNMATCHED,
    REJECTED,
    ANALYZED,
    ANALYSIS_FAILED
}
