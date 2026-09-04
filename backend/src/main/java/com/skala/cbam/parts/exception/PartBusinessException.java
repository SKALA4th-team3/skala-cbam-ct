package com.skala.cbam.parts.exception;

import java.util.Map;

public class PartBusinessException extends RuntimeException {

    private final PartErrorCode errorCode;
    private final Map<String, Object> details;

    public PartBusinessException(PartErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), Map.of());
    }

    public PartBusinessException(PartErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public PartErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
