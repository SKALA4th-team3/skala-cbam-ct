package com.skala.cbam.submission.error;

import org.springframework.http.HttpStatus;

/**
 * 제출 데이터 API(№20~№23)가 던지는 에러 code.
 *
 * <p>협력업체 도메인({@code SupplierErrorCode})과 같은 이유로 도메인 전용 enum 을 둔다 —
 * 5명이 병렬로 가는 동안 공용 enum 하나를 모두가 고치면 그 파일이 상시 충돌 지점이 된다.
 */
public enum SubmissionErrorCode {

    /** 400 — 필터·페이지 파라미터 형식 오류 (№20). */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다"),

    /** 404 — 제출 데이터 없음 (№21·№22·№23). */
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출 데이터를 찾을 수 없습니다"),

    /** 400 — 판정이 적격이 아님 (№22 확정). */
    NOT_QUALIFIED(HttpStatus.BAD_REQUEST, "판정이 적격이 아니어서 확정할 수 없습니다"),

    /** 400 — 미등록 부품이 남아 있음 (№22 확정). details 에 unregisteredPartIds 를 담는다. */
    UNREGISTERED_PART_EXISTS(HttpStatus.BAD_REQUEST, "미등록 부품이 있어 확정할 수 없습니다"),

    /** 409 — 이미 확정된 건에 확정·반려 재요청 (№22·№23). */
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 제출 데이터입니다"),

    /** 400 — 검토 대기 상태가 아니어서 반려할 수 없음 (№23). */
    NOT_REJECTABLE(HttpStatus.BAD_REQUEST, "반려할 수 없는 상태입니다"),

    /** 400 — resultStatus 가 REJECTED·NOT_SUBMITTED 가 아님 (№23). */
    INVALID_RESULT_STATUS(HttpStatus.BAD_REQUEST, "resultStatus 는 REJECTED 또는 NOT_SUBMITTED 여야 합니다"),

    /**
     * 400 — 확정 시점에 적용할 배출계수 연도(part.benchmark_factor_year)를 못 구함 (№22 확정).
     * 부품 도메인이 아직 없어 못 구하는 경우가 지금은 전부다. PR #22 리뷰 지적으로 추가함 —
     * 모르는 값을 현재 연도로 대체해 영구 확정하면 "모르면 비우고 사유를 남긴다"는 원칙과
     * 어긋나고, 나중에 부품 도메인이 붙어도 이미 확정된 값을 못 고친다.
     */
    BENCHMARK_FACTOR_YEAR_UNKNOWN(HttpStatus.BAD_REQUEST, "확정에 필요한 배출계수 연도를 아직 확인할 수 없습니다");

    private final HttpStatus status;
    private final String defaultMessage;

    SubmissionErrorCode(HttpStatus status, String defaultMessage) {
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
