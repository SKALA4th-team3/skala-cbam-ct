package com.skala.cbam.products.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ProductUpdateResponse(
        Long id,
        BigDecimal annualExportTon,
        List<String> exportCountries,
        List<PartResponse> parts,
        OffsetDateTime updatedAt
) {
    public record PartResponse(
            Long partId,
            Long supplierId,
            BigDecimal inputQtyPerTon,
            String status
    ) {
    }
}
