package com.skala.cbam.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "요청 형식이 올바르지 않습니다."
    ),

    INVALID_PARAMETER(
        HttpStatus.BAD_REQUEST,
        "INVALID_PARAMETER",
        "요청 파라미터가 올바르지 않습니다."
    ),

    SUPPLIER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SUPPLIER_NOT_FOUND",
        "협력업체를 찾을 수 없습니다."
    ),

    PART_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "PART_NOT_FOUND",
        "부품을 찾을 수 없습니다."
    ),

    PRODUCT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "PRODUCT_NOT_FOUND",
        "완제품을 찾을 수 없습니다."
    ),

    MAIL_GATEWAY_ERROR(
        HttpStatus.BAD_GATEWAY,
        "MAIL_GATEWAY_ERROR",
        "메일 발송 서비스에 연결할 수 없습니다."
    ),

    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "서버 내부 오류가 발생했습니다."
    );

    // 클라이언트에 반환할 HTTP 상태, 오류 코드, 오류 메시지를 저장한다.
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    // 각 오류 코드에 HTTP 상태와 오류 정보를 설정한다.
    ErrorCode(
        HttpStatus httpStatus,
        String code,
        String message
    ) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    // 오류에 해당하는 HTTP 상태를 반환한다.
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    // 클라이언트가 오류를 구분할 수 있는 오류 코드를 반환한다.
    public String getCode() {
        return code;
    }

    // 클라이언트에 전달할 오류 메시지를 반환한다.
    public String getMessage() {
        return message;
    }
}