package com.skala.cbam.mail.error;

import java.util.Map;

/** 이메일 접수 업무 규칙 위반을 {@link MailErrorCode} 와 함께 던지는 예외. */
public class MailException extends RuntimeException {

    private final transient MailErrorCode errorCode;
    private final transient Map<String, Object> details;

    public MailException(MailErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public MailException(MailErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : details;
    }

    public MailErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
