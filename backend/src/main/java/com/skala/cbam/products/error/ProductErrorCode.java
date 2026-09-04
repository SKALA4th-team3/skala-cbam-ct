package com.skala.cbam.products.error;

import org.springframework.http.HttpStatus;

/** 완제품 등록 API(요구사항 12번) 전용 오류 코드. */
public enum ProductErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "필수값 누락 또는 형식 오류입니다."),
    INVALID_CN_CODE(HttpStatus.BAD_REQUEST, "CN코드는 8자리 숫자여야 합니다."),
    INVALID_EU_COUNTRY(HttpStatus.BAD_REQUEST, "수출 대상 국가는 EU 회원국이어야 합니다."),
    OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "값이 허용 범위를 벗어났습니다."),
    DUPLICATE_EXPORT_COUNTRY(HttpStatus.CONFLICT, "수출 대상 국가가 중복되었습니다."),
    DUPLICATE_PRODUCT_PART(HttpStatus.CONFLICT, "동일한 부품과 협력업체 조합이 중복되었습니다."),
    PART_NOT_FOUND(HttpStatus.NOT_FOUND, "부품을 찾을 수 없습니다."),
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "협력업체를 찾을 수 없습니다."),
    PART_SUPPLIER_NOT_FOUND(HttpStatus.BAD_REQUEST, "활성 부품-협력업체 공급 관계를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ProductErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
