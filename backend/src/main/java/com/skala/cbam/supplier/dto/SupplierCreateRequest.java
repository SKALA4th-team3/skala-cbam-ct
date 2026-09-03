package com.skala.cbam.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 협력업체 등록 요청 (API 명세 №1 · 요구사항 1번).
 *
 * <p>phone 만 선택이고 나머지는 필수다. 위반은 400 INVALID_REQUEST 로 나간다.
 */
public record SupplierCreateRequest(

        @NotBlank(message = "협력업체명은 필수입니다")
        @Size(max = 120, message = "협력업체명은 120자를 넘을 수 없습니다")
        String companyName,

        @NotBlank(message = "사업자등록번호는 필수입니다")
        @Size(max = 40, message = "사업자등록번호는 40자를 넘을 수 없습니다")
        String businessRegistrationNumber,

        // ISO 3166-1 alpha-2. EU 회원국 제한은 완제품 수출국(exportCountries)에만 걸리고
        // 협력업체 소재 국가는 전 세계가 대상이므로 형식만 본다.
        @NotBlank(message = "국가는 필수입니다")
        @Pattern(regexp = "^[A-Z]{2}$", message = "국가는 ISO 3166-1 alpha-2 대문자 2자리여야 합니다")
        String country,

        @NotBlank(message = "담당자명은 필수입니다")
        @Size(max = 60, message = "담당자명은 60자를 넘을 수 없습니다")
        String contactName,

        @NotBlank(message = "담당자 이메일은 필수입니다")
        @Email(message = "담당자 이메일 형식이 올바르지 않습니다")
        @Size(max = 254, message = "담당자 이메일은 254자를 넘을 수 없습니다")
        String contactEmail,

        @Size(max = 30, message = "전화번호는 30자를 넘을 수 없습니다")
        String phone
) {
}
