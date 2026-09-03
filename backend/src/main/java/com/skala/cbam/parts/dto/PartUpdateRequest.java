package com.skala.cbam.parts.dto;

import java.math.BigDecimal;
import java.util.Set;

public record PartUpdateRequest(
        String partName,
        String cnCode,
        String unit,
        BigDecimal benchmarkFactor,
        Set<Long> supplierIds
) {
}
