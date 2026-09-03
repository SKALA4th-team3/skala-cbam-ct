package com.skala.cbam.supplier.dto;

import com.skala.cbam.supplier.domain.SupplierStatus;

/**
 * 협력업체 리스트 조회 조건 (API 명세 №3).
 *
 * <p>컨트롤러가 문자열 파라미터를 검증·변환한 결과다. 서비스는 이미 유효한 값만 받는다.
 *
 * @param search           업체명 부분 일치. null 이면 전체
 * @param country          국가 코드 정확 일치. null 이면 전체
 * @param status           협력 상태. null 이면 전체
 * @param submissionStatus 적격 상태 필터(QUALIFIED|UNQUALIFIED|NOT_SUBMITTED). null 이면 전체
 * @param months           월별 제출 상태를 몇 개월치 반환할지
 */
public record SupplierSearchCondition(
        String search,
        String country,
        SupplierStatus status,
        String submissionStatus,
        int months
) {
}
