package com.skala.cbam.products.service;

import com.skala.cbam.products.domain.Product;
import com.skala.cbam.products.dto.ProductCreateRequest;
import com.skala.cbam.products.dto.ProductCreateResponse;
import com.skala.cbam.products.error.ProductErrorCode;
import com.skala.cbam.products.error.ProductException;
import com.skala.cbam.products.repository.ProductsRepository;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.ProductPartReference;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.RequestedPart;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductsService {

    private static final Set<String> EU_COUNTRY_CODES = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DE", "DK", "EE",
            "ES", "FI", "FR", "GR", "HU", "IE", "IT", "LT", "LU",
            "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK");

    private final ProductsRepository productsRepository;
    private final ProductRelatedDataProvider relatedDataProvider;

    public ProductsService(
            ProductsRepository productsRepository,
            ProductRelatedDataProvider relatedDataProvider) {
        this.productsRepository = productsRepository;
        this.relatedDataProvider = relatedDataProvider;
    }

    @Transactional
    public ProductCreateResponse create(ProductCreateRequest request) {
        validateRequest(request);

        List<RequestedPart> requestedParts = request.parts().stream()
                .map(part -> new RequestedPart(part.partId(), part.supplierId()))
                .toList();
        List<ProductPartReference> references = relatedDataProvider
                .getActivePartSuppliers(requestedParts);

        Product product = new Product(
                request.productName(), request.cnCode(), request.annualExportTon());
        request.exportCountries().forEach(product::addExportCountry);
        for (int index = 0; index < request.parts().size(); index++) {
            product.addPart(
                    references.get(index).partSupplier(),
                    request.parts().get(index).inputQtyPerTon());
        }

        Product saved = productsRepository.save(product);
        return toResponse(saved, request, references);
    }

    private void validateRequest(ProductCreateRequest request) {
        if (request.cnCode() == null || !request.cnCode().matches("\\d{8}")) {
            throw new ProductException(ProductErrorCode.INVALID_CN_CODE);
        }
        if (request.annualExportTon() == null
                || request.annualExportTon().signum() < 0
                || exceedsDigits(request.annualExportTon(), 10, 2)) {
            throw new ProductException(ProductErrorCode.OUT_OF_RANGE,
                    Map.of("field", "annualExportTon"));
        }
        for (ProductCreateRequest.PartRequest part : request.parts()) {
            if (part.inputQtyPerTon() == null
                    || part.inputQtyPerTon().signum() <= 0
                    || exceedsDigits(part.inputQtyPerTon(), 7, 3)) {
                throw new ProductException(ProductErrorCode.OUT_OF_RANGE,
                        Map.of("field", "parts.inputQtyPerTon"));
            }
        }
        validateExportCountries(request.exportCountries());
        validateProductParts(request.parts());
    }

    private void validateExportCountries(List<String> countryCodes) {
        Set<String> unique = new HashSet<>(countryCodes);
        if (unique.size() != countryCodes.size()) {
            throw new ProductException(ProductErrorCode.DUPLICATE_EXPORT_COUNTRY);
        }
        Set<String> invalid = countryCodes.stream()
                .filter(code -> !EU_COUNTRY_CODES.contains(code))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!invalid.isEmpty()) {
            throw new ProductException(ProductErrorCode.INVALID_EU_COUNTRY,
                    Map.of("invalidCountryCodes", invalid));
        }
    }

    private void validateProductParts(List<ProductCreateRequest.PartRequest> parts) {
        Set<RequestedPart> unique = new HashSet<>();
        for (ProductCreateRequest.PartRequest part : parts) {
            RequestedPart key = new RequestedPart(part.partId(), part.supplierId());
            if (!unique.add(key)) {
                throw new ProductException(ProductErrorCode.DUPLICATE_PRODUCT_PART,
                        Map.of("partId", part.partId(), "supplierId", part.supplierId()));
            }
        }
    }

    private boolean exceedsDigits(BigDecimal value, int maxIntegerDigits, int maxFractionDigits) {
        BigDecimal normalized = value.stripTrailingZeros();
        int fractionDigits = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        return integerDigits > maxIntegerDigits || fractionDigits > maxFractionDigits;
    }

    private ProductCreateResponse toResponse(
            Product product,
            ProductCreateRequest request,
            List<ProductPartReference> references) {
        List<ProductCreateResponse.PartResponse> parts = java.util.stream.IntStream
                .range(0, request.parts().size())
                .mapToObj(index -> {
                    ProductCreateRequest.PartRequest requested = request.parts().get(index);
                    ProductPartReference reference = references.get(index);
                    return new ProductCreateResponse.PartResponse(
                            reference.partId(),
                            reference.partName(),
                            reference.supplierId(),
                            reference.supplierName(),
                            requested.inputQtyPerTon(),
                            null);
                })
                .toList();

        return new ProductCreateResponse(
                product.getId(),
                product.getName(),
                product.getCnCode(),
                request.exportCountries(),
                product.getAnnualExportTon(),
                parts);
    }
}
