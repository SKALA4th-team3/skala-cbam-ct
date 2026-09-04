package com.skala.cbam.products.service.port;

import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.products.domain.ProductPart;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public interface ProductRelatedDataProvider {

    List<ProductPartReference> getActivePartSuppliers(List<RequestedPart> requestedParts);

    List<ProductPartData> getProductPartData(List<ProductPart> productParts, YearMonth reportingMonth);

    record RequestedPart(Long partId, Long supplierId) {
    }

    record ProductPartReference(
            Long partId,
            String partName,
            Long supplierId,
            String supplierName,
            PartSupplier partSupplier
    ) {
    }

    record ProductPartData(
            Long partId,
            String partName,
            Long supplierId,
            String supplierName,
            Long partSupplierId,
            BigDecimal benchmarkFactor,
            String submissionStatus,
            BigDecimal emissionIntensity,
            Integer appliedFactorYear,
            BigDecimal defaultValueRatio
    ) {
    }
}
