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

    /**
     * 완제품 계산에 필요한 (부품×협력사) 한 건의 당월 데이터.
     *
     * <p>표시용과 계산용을 <b>일부러 다른 제출 건에서</b> 읽는다. 요구사항 №12 의 부품 세부 ④ 상태는
     * 「지금 어디까지 왔는가」라 최신 제출을 봐야 하고, №15 의 내재배출량은 「확정 배출데이터를 합산」이라
     * 확정 건만 봐야 한다. 확정 뒤 재제출(32번 반려 · 50번 피드백)이 들어오면 두 값이 갈린다 —
     * 상태는 REVIEW_PENDING 이면서 확정 원단위는 그대로 남는 것이 옳다.
     *
     * @param submissionStatus          당월 <b>최신</b> 제출의 상태. 제출이 없으면 NOT_SUBMITTED
     * @param confirmedEmissionIntensity 당월 <b>확정</b> 제출의 배출 원단위. 확정 건이 없으면 null
     * @param appliedFactorYear         당월 확정 제출의 값. 확정 건이 없으면 null
     * @param defaultValueRatio         당월 확정 제출의 값. 확정 건이 없으면 null
     * @param benchmarkFactor           부품의 벤치마크 팩터(평균값). 스키마상 null 이 가능하다
     */
    record ProductPartData(
            Long partId,
            String partName,
            Long supplierId,
            String supplierName,
            Long partSupplierId,
            BigDecimal benchmarkFactor,
            String submissionStatus,
            BigDecimal confirmedEmissionIntensity,
            Integer appliedFactorYear,
            BigDecimal defaultValueRatio
    ) {
    }
}
