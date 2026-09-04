package com.skala.cbam.mail.error;

import org.springframework.http.HttpStatus;

/** 이메일 접수 API(№15~№18)가 던지는 에러 code. 도메인 전용 enum(SupplierErrorCode와 같은 이유). */
public enum MailErrorCode {

    /** 400 — 필터·disposition 파라미터 형식 오류 (№15·№17). */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다"),

    /** 404 — 접수 건 없음 (№16·№18). */
    MAIL_RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "접수 메일을 찾을 수 없습니다"),

    /** 404 — 첨부파일 없음 (№17). */
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다"),

    /** 404 — 협력업체 없음 (№18). */
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "협력업체를 찾을 수 없습니다"),

    /** 400 — 비활성(협력끊김) 업체를 매칭 대상으로 지정 (№18). */
    INACTIVE_SUPPLIER(HttpStatus.BAD_REQUEST, "협력 끊김 상태인 업체는 매칭할 수 없습니다"),

    /** 409 — 이미 매칭된 접수 건 (№18). */
    ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 매칭된 접수 건입니다");

    private final HttpStatus status;
    private final String defaultMessage;

    MailErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
