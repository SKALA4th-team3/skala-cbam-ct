package com.skala.cbam.mail.domain;

/** 첨부파일 추출 처리 상태. ERD attachment_process_status 그대로. */
public enum AttachmentProcessStatus {
    PENDING,
    EXTRACTED,
    UNSUPPORTED,
    FAILED
}
