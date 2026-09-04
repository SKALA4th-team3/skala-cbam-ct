package com.skala.cbam.products.dto;

import com.skala.cbam.products.domain.ProductCalculationStatus;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String productName,
        String cnCode,
        List<String> exportCountries,
        BigDecimal annualExportTon,
        YearMonth reportingMonth,
        BigDecimal embeddedEmission,
        ProductCalculationStatus calculationStatus,
        BigDecimal benchmarkEmission,
        Integer appliedFactorYear,
        BigDecimal defaultValueRatio,
        List<PartResponse> parts,
        List<Long> missingPartIds
) {
    public record PartResponse(
            Long partId,
            String partName,
            Long supplierId,
            String supplierName,
            BigDecimal inputQtyPerTon,
            String status,
            BigDecimal emissionIntensity,
            BigDecimal contribution
    ) {
    }
}
