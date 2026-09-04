package com.skala.cbam.products.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductCreateResponse(
        Long id,
        String productName,
        String cnCode,
        List<String> exportCountries,
        BigDecimal annualExportTon,
        List<PartResponse> parts
) {

    public record PartResponse(
            Long partId,
            String partName,
            Long supplierId,
            String supplierName,
            BigDecimal inputQtyPerTon,
            String status
    ) {
    }
}