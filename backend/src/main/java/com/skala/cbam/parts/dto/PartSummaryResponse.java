package com.skala.cbam.parts.dto;

import com.skala.cbam.parts.entity.Part;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 부품 리스트 한 행 (요구사항 9번).
 *
 * <p>suppliers 는 id 순으로 정렬한다. 부품이 협력업체를 Set 으로 들고 있어 그대로 내보내면
 * 같은 부품인데도 응답마다 순서가 달라진다 — 화면과 테스트가 모두 흔들린다.
 */
public record PartSummaryResponse(
        Long id,
        String partCode,
        String partName,
        String cnCode,
        String unit,
        BigDecimal benchmarkFactor,
        List<SupplierBrief> suppliers
) {
    /** 공급 협력업체. name 이 null 이면 그 id 의 협력업체를 찾지 못한 것이다. */
    public record SupplierBrief(Long supplierId, String name) {
    }

    public static PartSummaryResponse from(Part part, Map<Long, String> supplierNames) {
        List<SupplierBrief> suppliers = part.getSupplierIds().stream()
                .sorted(Comparator.naturalOrder())
                .map(supplierId -> new SupplierBrief(supplierId, supplierNames.get(supplierId)))
                .toList();
        return new PartSummaryResponse(
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getCnCode(),
                part.getUnit().name(),
                part.getBenchmarkFactor(),
                suppliers
        );
    }
}
