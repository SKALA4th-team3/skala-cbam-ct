package com.skala.cbam.products.service;

import com.skala.cbam.products.domain.Product;
import com.skala.cbam.products.domain.Product.PartComposition;
import com.skala.cbam.products.domain.ProductCalculationStatus;
import com.skala.cbam.products.dto.ProductCreateRequest;
import com.skala.cbam.products.dto.ProductCreateResponse;
import com.skala.cbam.products.dto.ProductDetailResponse;
import com.skala.cbam.products.dto.ProductListResponse;
import com.skala.cbam.products.dto.ProductUpdateRequest;
import com.skala.cbam.products.dto.ProductUpdateResponse;
import com.skala.cbam.products.error.ProductErrorCode;
import com.skala.cbam.products.error.ProductException;
import com.skala.cbam.products.repository.ProductSpecifications;
import com.skala.cbam.products.repository.ProductsRepository;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.ProductPartData;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.ProductPartReference;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.RequestedPart;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductsService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> EU_COUNTRY_CODES = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DE", "DK", "EE",
            "ES", "FI", "FR", "GR", "HU", "IE", "IT", "LT", "LU",
            "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK");

    private final ProductsRepository productsRepository;
    private final ProductRelatedDataProvider relatedDataProvider;

    public ProductsService(ProductsRepository productsRepository,
                           ProductRelatedDataProvider relatedDataProvider) {
        this.productsRepository = productsRepository;
        this.relatedDataProvider = relatedDataProvider;
    }

    @Transactional
    public ProductCreateResponse create(ProductCreateRequest request) {
        validateCreateRequest(request);
        List<ProductPartReference> references = resolveReferences(request.parts().stream()
                .map(part -> new RequestedPart(part.partId(), part.supplierId()))
                .toList());
        Product product = new Product(
                request.productName(), request.cnCode(), request.annualExportTon());
        request.exportCountries().forEach(product::addExportCountry);
        for (int index = 0; index < request.parts().size(); index++) {
            product.addPart(references.get(index).partSupplier(),
                    request.parts().get(index).inputQtyPerTon());
        }
        Product saved = productsRepository.save(product);
        return toCreateResponse(saved, relatedDataProvider.getProductPartData(
                saved.getParts(), currentMonth()));
    }

    @Transactional
    public ProductUpdateResponse update(Long productId, ProductUpdateRequest request) {
        Product product = getOrThrow(productId);
        if (request.annualExportTon() != null) {
            validateAnnualExportTon(request.annualExportTon());
            product.update(request.annualExportTon());
        }
        if (request.exportCountries() != null) {
            validateExportCountries(request.exportCountries());
            product.replaceExportCountries(request.exportCountries());
        }
        if (request.parts() != null) {
            validateUpdateParts(request.parts());
            List<ProductPartReference> references = resolveReferences(request.parts().stream()
                    .map(part -> new RequestedPart(part.partId(), part.supplierId()))
                    .toList());
            List<PartComposition> compositions = java.util.stream.IntStream
                    .range(0, request.parts().size())
                    .mapToObj(index -> new PartComposition(
                            references.get(index).partSupplier(),
                            request.parts().get(index).inputQtyPerTon()))
                    .toList();
            product.replaceParts(compositions);
        }
        Product saved = productsRepository.saveAndFlush(product);
        List<ProductPartData> data = relatedDataProvider.getProductPartData(
                saved.getParts(), currentMonth());
        List<ProductUpdateResponse.PartResponse> parts = java.util.stream.IntStream
                .range(0, saved.getParts().size())
                .mapToObj(index -> new ProductUpdateResponse.PartResponse(
                        data.get(index).partId(), data.get(index).supplierId(),
                        saved.getParts().get(index).getInputQtyPerTon(),
                        data.get(index).submissionStatus()))
                .toList();
        return new ProductUpdateResponse(saved.getId(), saved.getAnnualExportTon(),
                countryCodes(saved), parts, saved.getUpdatedAt());
    }

    public ProductListResponse list(String search, String cnCode, String reportingMonth,
                                    ProductCalculationStatus calculationStatus,
                                    Pageable pageable) {
        validatePageable(pageable);
        YearMonth month = parseReportingMonth(reportingMonth);
        Sort sort = productSort(pageable.getSort());
        List<ProductListResponse.Item> matched = productsRepository
                .findAll(ProductSpecifications.search(search, cnCode), sort)
                .stream()
                .map(product -> toListItem(product, month))
                .filter(item -> calculationStatus == null
                        || item.calculationStatus() == calculationStatus)
                .toList();
        int from = Math.min((int) pageable.getOffset(), matched.size());
        int to = Math.min(from + pageable.getPageSize(), matched.size());
        int totalPages = matched.isEmpty() ? 0
                : (int) Math.ceil((double) matched.size() / pageable.getPageSize());
        return new ProductListResponse(month, matched.subList(from, to),
                pageable.getPageNumber(), pageable.getPageSize(), matched.size(), totalPages);
    }

    public ProductDetailResponse getDetail(Long productId, String reportingMonth) {
        Product product = getOrThrow(productId);
        YearMonth month = parseReportingMonth(reportingMonth);
        List<ProductPartData> data = relatedDataProvider.getProductPartData(
                product.getParts(), month);
        Calculation calculation = calculate(product, data);
        List<ProductDetailResponse.PartResponse> parts = java.util.stream.IntStream
                .range(0, product.getParts().size())
                .mapToObj(index -> {
                    ProductPartData item = data.get(index);
                    BigDecimal contribution = isConfirmed(item)
                            ? multiply(item.confirmedEmissionIntensity(),
                                    product.getParts().get(index).getInputQtyPerTon())
                            : null;
                    return new ProductDetailResponse.PartResponse(
                            item.partId(), item.partName(), item.supplierId(),
                            item.supplierName(), product.getParts().get(index).getInputQtyPerTon(),
                            item.submissionStatus(), item.confirmedEmissionIntensity(),
                            contribution);
                })
                .toList();
        List<Long> missingPartIds = data.stream()
                .filter(item -> !isConfirmed(item))
                .map(ProductPartData::partId)
                .distinct()
                .toList();
        return new ProductDetailResponse(
                product.getId(), product.getName(), product.getCnCode(), countryCodes(product),
                product.getAnnualExportTon(), month, calculation.actualEmission(),
                calculation.status(), calculation.benchmarkEmission(),
                calculation.appliedFactorYear(), calculation.defaultValueRatio(),
                parts, missingPartIds);
    }

    private ProductListResponse.Item toListItem(Product product, YearMonth month) {
        Calculation calculation = calculate(product,
                relatedDataProvider.getProductPartData(product.getParts(), month));
        // 평균값을 모르거나(벤치마크 미등록 부품) 0 이면 「평균값 대비」를 낼 수 없다.
        BigDecimal gapRatio = calculation.actualEmission() == null
                || calculation.benchmarkEmission() == null
                || calculation.benchmarkEmission().signum() == 0
                ? null
                : calculation.actualEmission().subtract(calculation.benchmarkEmission())
                        .divide(calculation.benchmarkEmission(), 4, RoundingMode.HALF_UP);
        return new ProductListResponse.Item(
                product.getId(), product.getName(), product.getCnCode(),
                product.getAnnualExportTon(), product.getParts().size(),
                calculation.benchmarkEmission(), calculation.actualEmission(), gapRatio,
                calculation.status(), calculation.unconfirmedPartCount());
    }

    private Calculation calculate(Product product, List<ProductPartData> data) {
        BigDecimal benchmark = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        int unconfirmed = 0;
        // 벤치마크 팩터가 없는 부품이 하나라도 있으면 제품의 평균값 합계를 알 수 없다.
        // 14번의 핵심이 「평균값 대비 실측값」이라 0 으로 때우면 비교가 조용히 틀어진다 — 비워서 내보낸다.
        boolean benchmarkKnown = true;
        Set<Integer> factorYears = new HashSet<>();
        Set<BigDecimal> defaultValueRatios = new HashSet<>();
        for (int index = 0; index < product.getParts().size(); index++) {
            BigDecimal quantity = product.getParts().get(index).getInputQtyPerTon();
            ProductPartData item = data.get(index);
            if (item.benchmarkFactor() == null) {
                benchmarkKnown = false;
            } else {
                benchmark = benchmark.add(multiply(item.benchmarkFactor(), quantity));
            }
            if (!isConfirmed(item)) {
                unconfirmed++;
                continue;
            }
            BigDecimal contribution = multiply(item.confirmedEmissionIntensity(), quantity);
            actual = actual.add(contribution);
            if (item.defaultValueRatio() != null) {
                defaultValueRatios.add(item.defaultValueRatio().stripTrailingZeros());
            }
            if (item.appliedFactorYear() != null) {
                factorYears.add(item.appliedFactorYear());
            }
        }
        boolean complete = unconfirmed == 0;
        Integer factorYear = complete && factorYears.size() == 1
                ? factorYears.iterator().next() : null;
        BigDecimal defaultValueRatio = complete && defaultValueRatios.size() == 1
                ? defaultValueRatios.iterator().next() : null;
        return new Calculation(
                benchmarkKnown ? benchmark.setScale(4, RoundingMode.HALF_UP) : null,
                complete ? actual.setScale(4, RoundingMode.HALF_UP) : null,
                complete ? ProductCalculationStatus.COMPLETE : ProductCalculationStatus.INCOMPLETE,
                unconfirmed, factorYear, defaultValueRatio);
    }

    /**
     * 계산에 넣을 수 있는 부품인지. 표시 상태(REVIEW_PENDING 등)가 아니라 <b>확정 배출데이터의 유무</b>로
     * 판단한다 — 확정 뒤 재제출이 들어와도 이미 확정된 값은 살아 있어야 한다(요구사항 №15·№31).
     * 확정 건이어도 생산량이 0 이면 원단위를 낼 수 없어 null 이고, 그때는 미확정으로 센다.
     */
    private boolean isConfirmed(ProductPartData data) {
        return data.confirmedEmissionIntensity() != null;
    }

    private BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return left.multiply(right).setScale(4, RoundingMode.HALF_UP);
    }

    private List<ProductPartReference> resolveReferences(List<RequestedPart> requestedParts) {
        return relatedDataProvider.getActivePartSuppliers(requestedParts);
    }

    private Product getOrThrow(Long productId) {
        return productsRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private void validateCreateRequest(ProductCreateRequest request) {
        if (request.cnCode() == null || !request.cnCode().matches("\\d{8}")) {
            throw new ProductException(ProductErrorCode.INVALID_CN_CODE);
        }
        validateAnnualExportTon(request.annualExportTon());
        validateCreateParts(request.parts());
        validateExportCountries(request.exportCountries());
    }

    private void validateAnnualExportTon(BigDecimal value) {
        if (value == null || value.signum() < 0 || exceedsDigits(value, 10, 2)) {
            throw new ProductException(ProductErrorCode.OUT_OF_RANGE,
                    Map.of("field", "annualExportTon"));
        }
    }

    private void validateCreateParts(List<ProductCreateRequest.PartRequest> parts) {
        validatePartValues(parts.stream().map(part -> new PartValues(
                part.partId(), part.supplierId(), part.inputQtyPerTon())).toList());
    }

    private void validateUpdateParts(List<ProductUpdateRequest.PartRequest> parts) {
        validatePartValues(parts.stream().map(part -> new PartValues(
                part.partId(), part.supplierId(), part.inputQtyPerTon())).toList());
    }

    private void validatePartValues(List<PartValues> parts) {
        Set<RequestedPart> unique = new HashSet<>();
        for (PartValues part : parts) {
            if (part.inputQtyPerTon() == null || part.inputQtyPerTon().signum() <= 0
                    || exceedsDigits(part.inputQtyPerTon(), 7, 3)) {
                throw new ProductException(ProductErrorCode.OUT_OF_RANGE,
                        Map.of("field", "parts.inputQtyPerTon"));
            }
            RequestedPart key = new RequestedPart(part.partId(), part.supplierId());
            if (!unique.add(key)) {
                throw new ProductException(ProductErrorCode.DUPLICATE_PRODUCT_PART,
                        Map.of("partId", part.partId(), "supplierId", part.supplierId()));
            }
        }
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

    private YearMonth parseReportingMonth(String value) {
        if (value == null || value.isBlank()) {
            return currentMonth();
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ProductException(ProductErrorCode.INVALID_PARAMETER,
                    Map.of("field", "reportingMonth"));
        }
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1
                || pageable.getPageSize() > 100) {
            throw new ProductException(ProductErrorCode.INVALID_PARAMETER,
                    Map.of("field", "page/size"));
        }
    }

    private Sort productSort(Sort requested) {
        List<Sort.Order> orders = requested.stream().map(order -> {
            String property = switch (order.getProperty()) {
                case "productName" -> "name";
                case "cnCode", "annualExportTon" -> order.getProperty();
                default -> throw new ProductException(ProductErrorCode.INVALID_PARAMETER,
                        Map.of("field", "sort", "value", order.getProperty()));
            };
            return new Sort.Order(order.getDirection(), property);
        }).toList();
        return Sort.by(orders);
    }

    private boolean exceedsDigits(BigDecimal value, int integers, int fractions) {
        BigDecimal normalized = value.stripTrailingZeros();
        int fractionDigits = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        return integerDigits > integers || fractionDigits > fractions;
    }

    private List<String> countryCodes(Product product) {
        return product.getExportCountries().stream()
                .map(country -> country.getCountryCode())
                .toList();
    }

    private ProductCreateResponse toCreateResponse(Product product, List<ProductPartData> data) {
        List<ProductCreateResponse.PartResponse> parts = java.util.stream.IntStream
                .range(0, product.getParts().size())
                .mapToObj(index -> new ProductCreateResponse.PartResponse(
                        data.get(index).partId(), data.get(index).partName(),
                        data.get(index).supplierId(), data.get(index).supplierName(),
                        product.getParts().get(index).getInputQtyPerTon(),
                        data.get(index).submissionStatus()))
                .toList();
        return new ProductCreateResponse(product.getId(), product.getName(), product.getCnCode(),
                countryCodes(product), product.getAnnualExportTon(), parts);
    }

    private YearMonth currentMonth() {
        return YearMonth.now(SEOUL);
    }

    private record PartValues(Long partId, Long supplierId, BigDecimal inputQtyPerTon) {
    }

    private record Calculation(BigDecimal benchmarkEmission, BigDecimal actualEmission,
                               ProductCalculationStatus status, int unconfirmedPartCount,
                               Integer appliedFactorYear, BigDecimal defaultValueRatio) {
    }
}
