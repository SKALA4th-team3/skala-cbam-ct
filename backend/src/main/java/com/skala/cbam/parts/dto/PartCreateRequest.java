package com.skala.cbam.parts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public record PartCreateRequest(
        @NotBlank String partCode,
        @NotBlank String partName,
        @NotBlank String cnCode,
        @NotBlank String unit,
        @NotNull BigDecimal benchmarkFactor,
        Set<Long> supplierIds
) {
}
