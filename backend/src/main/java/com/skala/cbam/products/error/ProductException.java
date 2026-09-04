package com.skala.cbam.products.error;

import java.util.Map;

public class ProductException extends RuntimeException {

    private final ProductErrorCode errorCode;
    private final Map<String, Object> details;

    public ProductException(ProductErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), Map.of());
    }

    public ProductException(ProductErrorCode errorCode, Map<String, Object> details) {
        this(errorCode, errorCode.getDefaultMessage(), details);
    }

    public ProductException(ProductErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ProductErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
