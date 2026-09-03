package com.skala.cbam.supplier.error;

import org.springframework.http.HttpStatus;

/**
 * 협력업체 API(№1~№4)가 던지는 에러 code.
 *
 * <p>API 명세 v10 공통 규약 3항이 "화면이 분기 처리할 수 있도록 사유마다 서로 다른 code 를 부여한다"고
 * 정한다. 같은 상태 코드에 여러 사유를 자연어로 묶지 않는다.
 *
 * <p><b>협력업체 도메인 전용이다.</b> 공통 규약은 code 를 전역 집합처럼 적어 두었지만,
 * 5명이 병렬로 가는 동안 모든 도메인이 하나의 enum 을 고치면 그 파일이 상시 충돌 지점이 된다.
 * 부품·완제품·제출 담당자는 각자 자기 패키지에 같은 모양의 enum 을 두면 된다 —
 * 응답 계약(code 문자열)은 명세가 정하지 그 코드가 어느 enum 에 있는지는 화면과 무관하다.
 */
public enum SupplierErrorCode {

    /** 400 — 필수값 누락·형식 오류 (№1 · №2). */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다"),

    /** 400 — 잘못된 페이지·필터 값, months 형식·범위 오류 (№3 · №4). */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다"),

    /** 400 — status 가 ACTIVE·INACTIVE 가 아님 (№2). */
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "협력 상태는 ACTIVE 또는 INACTIVE 여야 합니다"),

    /** 404 — 협력업체 없음 (№2 · №4). */
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "협력업체를 찾을 수 없습니다"),

    /** 409 — 사업자등록번호 중복 (№1). */
    DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT, "이미 등록된 사업자등록번호입니다"),

    /** 409 — 담당자 이메일 중복 (№1 · №2). */
    DUPLICATE_CONTACT_EMAIL(HttpStatus.CONFLICT, "이미 등록된 담당자 이메일입니다");

    private final HttpStatus status;
    private final String defaultMessage;

    SupplierErrorCode(HttpStatus status, String defaultMessage) {
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
