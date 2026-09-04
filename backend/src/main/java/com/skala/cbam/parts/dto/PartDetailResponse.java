package com.skala.cbam.parts.dto;

import com.skala.cbam.parts.entity.Part;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 부품 상세 (요구사항 10번).
 *
 * <p>10번은 "협력업체별 확정 배출 데이터를 리스트로"를 함께 요구한다. 확정 배출 데이터는
 * 제출(submission) 도메인 소유이고 아직 구현되지 않았다 — <b>그래서 confirmedData 는 빈 배열이다.</b>
 * 값이 없는 것이 아니라 <b>아직 채울 경로가 없다</b>는 뜻이다. 키를 지우지 않아 화면 계약은 지금도 성립한다.
 * TODO(Submission 도메인 구현 후): confirmedData 를 채운다.
 *
 * <p>suppliers 는 id 순으로 정렬한다 — 이유는 {@link PartSummaryResponse} 와 같다.
 */
public record PartDetailResponse(
        Long id,
        String partCode,
        String partName,
        String cnCode,
        String unit,
        BigDecimal benchmarkFactor,
        List<SupplierBrief> suppliers
) {
    /** 공급 협력업체. name 이 null 이면 그 id 의 협력업체를 찾지 못한 것이다. */
    public record SupplierBrief(
            Long supplierId,
            String name,
            List<Object> confirmedData
    ) {
    }

    public static PartDetailResponse from(Part part, Map<Long, String> supplierNames) {
        List<SupplierBrief> suppliers = part.getSupplierIds().stream()
                .sorted(Comparator.naturalOrder())
                .map(supplierId -> new SupplierBrief(supplierId, supplierNames.get(supplierId), List.of()))
                .toList();
        return new PartDetailResponse(
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getCnCode(),
                part.getUnit().name(),
                part.getBenchmarkFactor(),
                suppliers
        );
    }
}
