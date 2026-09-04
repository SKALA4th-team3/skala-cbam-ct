package com.skala.cbam.products.dto;

import com.skala.cbam.products.domain.ProductCalculationStatus;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ProductListResponse(
        YearMonth reportingMonth,
        List<Item> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record Item(
            Long id,
            String productName,
            String cnCode,
            BigDecimal annualExportTon,
            int requiredPartCount,
            BigDecimal benchmarkEmission,
            BigDecimal actualEmission,
            BigDecimal gapRatio,
            ProductCalculationStatus calculationStatus,
            int unconfirmedPartCount
    ) {
    }
}
