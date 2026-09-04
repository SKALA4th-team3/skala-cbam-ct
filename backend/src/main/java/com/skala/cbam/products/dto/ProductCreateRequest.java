package com.skala.cbam.products.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ProductCreateRequest(

        @NotBlank(message = "제품명은 필수입니다")
        @Size(max = 120, message = "제품명은 120자를 넘을 수 없습니다")
        String productName,

        @NotBlank(message = "CN코드는 필수입니다")
        @Pattern(regexp = "^\\d{8}$", message = "CN코드는 숫자 8자리여야 합니다")
        String cnCode,

        @NotEmpty(message = "수출 대상 국가는 하나 이상 필요합니다")
        List<
                @NotBlank
                @Pattern(
                        regexp = "^[A-Z]{2}$",
                        message = "국가 코드는 대문자 2자리여야 합니다"
                )
                String
        > exportCountries,

        @NotNull(message = "연간 수출량은 필수입니다")
        @DecimalMin(value = "0.00", message = "연간 수출량은 0 이상이어야 합니다")
        @Digits(integer = 10, fraction = 2,
                message = "연간 수출량은 정수 10자리, 소수 2자리까지 가능합니다")
        BigDecimal annualExportTon,

        @NotEmpty(message = "구성 부품은 하나 이상 필요합니다")
        List<@Valid PartRequest> parts
) {

    public record PartRequest(

            @NotNull(message = "부품 ID는 필수입니다")
            Long partId,

            @NotNull(message = "협력사 ID는 필수입니다")
            Long supplierId,

            @NotNull(message = "투입량은 필수입니다")
            @DecimalMin(
                    value = "0.000",
                    inclusive = false,
                    message = "투입량은 0보다 커야 합니다"
            )
            @Digits(integer = 7, fraction = 3,
                    message = "투입량은 정수 7자리, 소수 3자리까지 가능합니다")
            BigDecimal inputQtyPerTon
    ) {
    }
}