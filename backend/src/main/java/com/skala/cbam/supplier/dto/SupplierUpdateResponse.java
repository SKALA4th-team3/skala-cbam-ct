package com.skala.cbam.supplier.dto;

import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import java.time.OffsetDateTime;

/**
 * 협력업체 수정 · 협력 끊김 응답 (API 명세 №2).
 *
 * <p>excludedSubmissionCount 는 협력 끊김으로 <b>마감 대상에서 제외된</b> 건수,
 * preservedSubmissionCount 는 <b>삭제하지 않고 보존한</b> 기존 제출 건수다(요구사항 6번).
 * 두 값 모두 제출 도메인이 소유하므로 {@link com.skala.cbam.supplier.service.port.SupplierRelatedDataProvider}
 * 가 채운다.
 */
public record SupplierUpdateResponse(
        Long id,
        String contactName,
        String contactEmail,
        String phone,
        SupplierStatus status,
        int excludedSubmissionCount,
        int preservedSubmissionCount,
        OffsetDateTime updatedAt
) {

    public static SupplierUpdateResponse of(Supplier supplier, int excludedCount, int preservedCount) {
        return new SupplierUpdateResponse(
                supplier.getId(),
                supplier.getContactName(),
                supplier.getContactEmail(),
                supplier.getContactPhone(),
                supplier.getStatus(),
                excludedCount,
                preservedCount,
                supplier.getUpdatedAt()
        );
    }
}
