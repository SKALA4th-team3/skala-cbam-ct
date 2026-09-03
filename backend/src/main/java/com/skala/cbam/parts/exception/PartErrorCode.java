package com.skala.cbam.parts.exception;

import org.springframework.http.HttpStatus;

/**
 * 부품 API 전용 에러 코드. 공통 에러 스키마(개요·공통 규약 시트 3항)는 다른 팀원이
 * com.skala.cbam.global 에 올릴 예정이라, 그게 머지되기 전까지 parts 패키지 안에서만 쓴다.
 * TODO(global 공통 인프라 머지 후): 이 enum은 지우고 global의 공통 ErrorCode로 교체한다.
 */
public enum PartErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "필수값 누락 또는 형식 오류입니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "잘못된 필터·페이지 값입니다."),
    INVALID_CN_CODE(HttpStatus.BAD_REQUEST, "CN코드는 8자리 숫자여야 합니다."),
    INVALID_UNIT(HttpStatus.BAD_REQUEST, "허용 단위(KG|TON|EA)가 아닙니다."),
    OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "값이 허용 범위를 벗어났습니다."),
    DUPLICATE_PART_CODE(HttpStatus.CONFLICT, "이미 등록된 부품코드입니다."),
    DUPLICATE_PART_NAME(HttpStatus.CONFLICT, "이미 등록된 부품명입니다."),
    PART_NOT_FOUND(HttpStatus.NOT_FOUND, "부품을 찾을 수 없습니다."),

    /**
     * 404 — 공급 협력업체로 지정한 id 가 없음 (7번 · 8번).
     * 어느 id 가 없는지는 details.missingSupplierIds 로 돌려준다.
     */
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "협력업체를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    PartErrorCode(HttpStatus status, String defaultMessage) {
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
