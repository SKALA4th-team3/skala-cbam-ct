package com.skala.cbam.products.service.adapter;

import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.parts.entity.PartSupplierStatus;
import com.skala.cbam.parts.repository.PartSupplierRepository;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.products.error.ProductErrorCode;
import com.skala.cbam.products.error.ProductException;
import com.skala.cbam.products.domain.ProductPart;
import com.skala.cbam.products.repository.ProductSubmissionRepository;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import java.time.YearMonth;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JpaProductRelatedDataProvider implements ProductRelatedDataProvider {

    private final PartsRepository partsRepository;
    private final PartSupplierRepository partSupplierRepository;
    private final SupplierRepository supplierRepository;
    private final ProductSubmissionRepository productSubmissionRepository;

    public JpaProductRelatedDataProvider(
            PartsRepository partsRepository,
            PartSupplierRepository partSupplierRepository,
            SupplierRepository supplierRepository,
            ProductSubmissionRepository productSubmissionRepository) {
        this.partsRepository = partsRepository;
        this.partSupplierRepository = partSupplierRepository;
        this.supplierRepository = supplierRepository;
        this.productSubmissionRepository = productSubmissionRepository;
    }

    @Override
    public List<ProductPartData> getProductPartData(
            List<ProductPart> productParts, YearMonth reportingMonth) {
        if (productParts.isEmpty()) {
            return List.of();
        }
        Set<Long> supplierIds = productParts.stream()
                .map(productPart -> productPart.getPartSupplier().getSupplierId())
                .collect(Collectors.toSet());
        Map<Long, Supplier> suppliersById = byId(
                supplierRepository.findAllById(supplierIds), Supplier::getId);
        Set<Long> relationIds = productParts.stream()
                .map(productPart -> productPart.getPartSupplier().getId())
                .collect(Collectors.toSet());

        Map<Long, Submission> latestSubmissionByRelation = new LinkedHashMap<>();
        productSubmissionRepository
                .findByPartSupplierIdInAndReportingMonthOrderBySubmittedAtDesc(
                        relationIds, reportingMonth.atDay(1))
                .forEach(submission -> latestSubmissionByRelation.putIfAbsent(
                        submission.getPartSupplierId(), submission));

        return productParts.stream().map(productPart -> {
            PartSupplier relation = productPart.getPartSupplier();
            Part part = relation.getPart();
            Supplier supplier = suppliersById.get(relation.getSupplierId());
            Submission submission = latestSubmissionByRelation.get(relation.getId());
            return new ProductPartData(
                    part.getId(), part.getPartName(), relation.getSupplierId(),
                    supplier == null ? null : supplier.getName(), relation.getId(),
                    part.getBenchmarkFactor(),
                    submission == null ? "NOT_SUBMITTED" : submission.getStatus().name(),
                    submission == null ? null : submission.calculateEmissionIntensity(),
                    submission == null ? null : submission.getAppliedFactorYear(),
                    submission == null ? null : submission.getDefaultValueRatio());
        }).toList();
    }

    @Override
    public List<ProductPartReference> getActivePartSuppliers(List<RequestedPart> requestedParts) {
        Set<Long> partIds = requestedParts.stream()
                .map(RequestedPart::partId)
                .collect(Collectors.toSet());
        Set<Long> supplierIds = requestedParts.stream()
                .map(RequestedPart::supplierId)
                .collect(Collectors.toSet());

        Map<Long, Part> partsById = byId(partsRepository.findAllById(partIds), Part::getId);
        Set<Long> missingPartIds = difference(partIds, partsById.keySet());
        if (!missingPartIds.isEmpty()) {
            throw new ProductException(ProductErrorCode.PART_NOT_FOUND,
                    Map.of("missingPartIds", missingPartIds));
        }

        Map<Long, Supplier> suppliersById = byId(supplierRepository.findAllById(supplierIds), Supplier::getId);
        Set<Long> missingSupplierIds = difference(supplierIds, suppliersById.keySet());
        if (!missingSupplierIds.isEmpty()) {
            throw new ProductException(ProductErrorCode.SUPPLIER_NOT_FOUND,
                    Map.of("missingSupplierIds", missingSupplierIds));
        }

        Map<RelationKey, PartSupplier> relationsByKey = partSupplierRepository
                .findAllActiveRelations(partIds, supplierIds, PartSupplierStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        relation -> new RelationKey(relation.getPart().getId(), relation.getSupplierId()),
                        Function.identity()));

        return requestedParts.stream()
                .map(requested -> toReference(requested, partsById, suppliersById, relationsByKey))
                .toList();
    }

    private ProductPartReference toReference(
            RequestedPart requested,
            Map<Long, Part> partsById,
            Map<Long, Supplier> suppliersById,
            Map<RelationKey, PartSupplier> relationsByKey) {
        RelationKey key = new RelationKey(requested.partId(), requested.supplierId());
        PartSupplier relation = relationsByKey.get(key);
        if (relation == null) {
            throw new ProductException(ProductErrorCode.PART_SUPPLIER_NOT_FOUND,
                    Map.of("partId", requested.partId(), "supplierId", requested.supplierId()));
        }
        return new ProductPartReference(
                requested.partId(),
                partsById.get(requested.partId()).getPartName(),
                requested.supplierId(),
                suppliersById.get(requested.supplierId()).getName(),
                relation);
    }

    private static <T> Map<Long, T> byId(Collection<T> values, Function<T, Long> idExtractor) {
        return values.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
    }

    private static Set<Long> difference(Set<Long> requested, Set<Long> found) {
        return requested.stream()
                .filter(id -> !found.contains(id))
                .collect(Collectors.toUnmodifiableSet());
    }

    private record RelationKey(Long partId, Long supplierId) {
    }
}
