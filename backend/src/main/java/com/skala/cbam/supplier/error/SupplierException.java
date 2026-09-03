package com.skala.cbam.supplier.error;

import java.util.Map;

/**
 * 협력업체 업무 규칙 위반을 {@link SupplierErrorCode} 와 함께 던지는 예외.
 *
 * <p>ErrorCode 가 HTTP 상태까지 들고 있으므로 던지는 쪽은 상태 코드를 신경 쓰지 않는다.
 * 응답 변환은 {@link com.skala.cbam.supplier.controller.SupplierApiExceptionHandling} 한곳에서만 한다.
 */
public class SupplierException extends RuntimeException {

    private final transient SupplierErrorCode errorCode;
    private final transient Map<String, Object> details;

    public SupplierException(SupplierErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public SupplierException(SupplierErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : details;
    }

    public SupplierErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
