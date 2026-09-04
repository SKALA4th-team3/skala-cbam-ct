package com.skala.cbam.parts.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public record PartUpdateRequest(
        String partName,
        String cnCode,
        String unit,
        BigDecimal benchmarkFactor,
        Integer benchmarkFactorYear,
        Set<@NotNull Long> supplierIds
) {
}
