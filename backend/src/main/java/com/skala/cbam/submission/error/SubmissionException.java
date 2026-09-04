package com.skala.cbam.submission.error;

import java.util.Map;

/**
 * 제출 데이터 업무 규칙 위반을 {@link SubmissionErrorCode} 와 함께 던지는 예외.
 * 응답 변환은 {@link com.skala.cbam.submission.controller.SubmissionApiExceptionHandling} 한곳에서만 한다.
 */
public class SubmissionException extends RuntimeException {

    private final transient SubmissionErrorCode errorCode;
    private final transient Map<String, Object> details;

    public SubmissionException(SubmissionErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public SubmissionException(SubmissionErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : details;
    }

    public SubmissionErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
