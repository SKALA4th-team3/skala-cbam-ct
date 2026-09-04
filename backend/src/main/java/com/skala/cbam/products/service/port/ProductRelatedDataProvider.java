package com.skala.cbam.products.service.port;

import com.skala.cbam.parts.entity.PartSupplier;
import java.util.List;

public interface ProductRelatedDataProvider {

    List<ProductPartReference> getActivePartSuppliers(List<RequestedPart> requestedParts);

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
}
