package com.skala.cbam.supplier.dto;

import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import java.time.OffsetDateTime;

/**
 * 협력업체 등록 응답 (API 명세 №1). 필드 순서와 이름은 명세의 201 응답 예시를 그대로 따른다.
 */
public record SupplierCreateResponse(
        Long id,
        String companyName,
        String businessRegistrationNumber,
        String country,
        String contactName,
        String contactEmail,
        String phone,
        SupplierStatus status,
        OffsetDateTime createdAt
) {

    public static SupplierCreateResponse from(Supplier supplier) {
        return new SupplierCreateResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getBusinessRegistrationNumber(),
                supplier.getCountryCode(),
                supplier.getContactName(),
                supplier.getContactEmail(),
                supplier.getContactPhone(),
                supplier.getStatus(),
                supplier.getCreatedAt()
        );
    }
}
