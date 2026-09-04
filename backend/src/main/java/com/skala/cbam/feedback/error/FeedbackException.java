package com.skala.cbam.feedback.error;

import java.util.Map;

public class FeedbackException extends RuntimeException {

    private final transient FeedbackErrorCode errorCode;
    private final transient Map<String, Object> details;

    public FeedbackException(FeedbackErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public FeedbackException(FeedbackErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : details;
    }

    public FeedbackErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
