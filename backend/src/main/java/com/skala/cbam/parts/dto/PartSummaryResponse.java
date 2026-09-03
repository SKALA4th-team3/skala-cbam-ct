package com.skala.cbam.parts.dto;

import com.skala.cbam.parts.entity.Part;

import java.math.BigDecimal;
import java.util.Set;

/**
 * suppliers[].name은 Supplier 도메인이 머지되기 전까지 채울 수 없어
 * supplierIds만 반환한다. TODO(Supplier 머지 후): id -> name 조인 추가.
 */
public record PartSummaryResponse(
        Long id,
        String partCode,
        String partName,
        String cnCode,
        String unit,
        BigDecimal benchmarkFactor,
        Set<Long> supplierIds
) {
    public static PartSummaryResponse from(Part part) {
        return new PartSummaryResponse(
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getCnCode(),
                part.getUnit().name(),
                part.getBenchmarkFactor(),
                part.getSupplierIds()
        );
    }
}
