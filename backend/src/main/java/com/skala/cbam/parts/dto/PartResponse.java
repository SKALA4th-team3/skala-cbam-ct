package com.skala.cbam.parts.dto;

import com.skala.cbam.parts.entity.Part;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

public record PartResponse(
        Long id,
        String partCode,
        String partName,
        String cnCode,
        String unit,
        BigDecimal benchmarkFactor,
        Set<Long> supplierIds,
        OffsetDateTime updatedAt
) {
    public static PartResponse from(Part part) {
        return new PartResponse(
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getCnCode(),
                part.getUnit().name(),
                part.getBenchmarkFactor(),
                part.getSupplierIds(),
                part.getUpdatedAt()
        );
    }
}
