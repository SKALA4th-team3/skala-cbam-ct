package com.skala.cbam.supplier.domain;

/**
 * 협력 상태 (API 명세 v10 코드·Enum 정의 시트 SupplierStatus).
 *
 * <p>ACTIVE  = 협력 유지중 (요구사항 3번 상태 필터)
 * <p>INACTIVE = 협력 끊김. 마감 대상과 미제출 경보에서 제외되지만
 * <b>기존 제출 데이터는 삭제하지 않고 보존한다</b> (요구사항 6번).
 */
public enum SupplierStatus {
    ACTIVE,
    INACTIVE;

    /**
     * 요청으로 들어온 문자열을 상태로 바꾼다.
     *
     * <p>명세 №2 는 허용값이 아닌 status 에 400 INVALID_STATUS 를 요구한다.
     * enum 을 그대로 바인딩하면 Jackson 이 먼저 실패해 이 코드를 낼 수 없어,
     * 요청 DTO 는 문자열로 받고 여기서 판별한다.
     */
    public static SupplierStatus from(String value) {
        for (SupplierStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
