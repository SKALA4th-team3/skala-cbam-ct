package com.skala.cbam.supplier.dto;

import com.skala.cbam.supplier.domain.SupplierStatus;
import java.util.List;

/**
 * 협력업체 리스트 조회의 행 하나 (API 명세 №3 · 요구사항 3번).
 *
 * <p>monthlyStatus 는 업체별 <b>월별 제출 상태(최근 N개월)</b>다.
 * 제출 도메인이 소유한 값이라 {@link com.skala.cbam.supplier.service.port.SupplierRelatedDataProvider}
 * 가 채운다.
 */
public record SupplierSummaryResponse(
        Long id,
        String companyName,
        String country,
        SupplierStatus status,
        List<MonthlyStatus> monthlyStatus
) {

    /**
     * 한 달치 제출 현황. month 는 YYYY-MM(공통 규약 5항).
     *
     * <p>status 를 enum 이 아니라 String 으로 둔 이유: 이 값의 enum 은 제출 도메인 소유다.
     * 아직 없는 남의 enum 을 여기서 선점하지 않는다.
     */
    public record MonthlyStatus(
            String month,
            String status,
            int qualified,
            int unqualified,
            int notSubmitted
    ) {
    }
}
