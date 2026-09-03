package com.skala.cbam.parts.dto;

import com.skala.cbam.parts.entity.Part;

import java.math.BigDecimal;
import java.util.List;

/**
 * suppliers[].name과 confirmedData는 각각 Supplier·Submission 도메인이 없어 채울 수 없다.
 * TODO(Supplier 머지 후): name 조인 추가. TODO(Submission 도메인 구현 후): confirmedData 채우기.
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
    public record SupplierBrief(
            Long supplierId,
            String name,
            List<Object> confirmedData
    ) {
    }

    public static PartDetailResponse from(Part part) {
        List<SupplierBrief> suppliers = part.getSupplierIds().stream()
                .map(supplierId -> new SupplierBrief(supplierId, null, List.of()))
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
