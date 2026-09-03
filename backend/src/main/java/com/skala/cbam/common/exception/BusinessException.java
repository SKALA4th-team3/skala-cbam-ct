package com.skala.cbam.common.exception;

import java.util.Map;

public class BusinessException extends RuntimeException {

    // 발생한 업무 오류의 상태 코드와 메시지를 담고 있다.
    private final ErrorCode errorCode;

    // 오류와 함께 전달할 부가 정보를 담고 있다.
    private final Map<String, Object> details;

    // 부가 정보 없이 업무 예외를 생성한다.
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    // 오류 코드와 부가 정보를 포함한 업무 예외를 생성한다.
    public BusinessException(
        ErrorCode errorCode,
        Map<String, Object> details
    ) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = Map.copyOf(details);
    }

    // 기존 예외의 원인을 보존하면서 새로운 업무 예외로 변환한다.
    public BusinessException(
        ErrorCode errorCode,
        Throwable cause
    ) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    // 업무 예외에 저장된 오류 코드를 반환한다.
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    // 업무 예외에 저장된 부가 정보를 반환한다.
    public Map<String, Object> getDetails() {
        return details;
    }
}