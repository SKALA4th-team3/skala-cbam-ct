package com.skala.cbam.products.service;

import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.products.domain.Product;
import com.skala.cbam.products.dto.ProductCreateRequest;
import com.skala.cbam.products.error.ProductErrorCode;
import com.skala.cbam.products.error.ProductException;
import com.skala.cbam.products.repository.ProductsRepository;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.ProductPartReference;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductsServiceTest {

    @Mock
    private ProductsRepository productsRepository;

    @Mock
    private ProductRelatedDataProvider relatedDataProvider;

    @Mock
    private PartSupplier partSupplier;

    private ProductsService productsService;

    @BeforeEach
    void setUp() {
        productsService = new ProductsService(productsRepository, relatedDataProvider);
    }

    @Test
    void 유효한_요청이면_완제품과_하위_데이터를_저장한다() {
        ProductCreateRequest request = validRequest();
        when(relatedDataProvider.getActivePartSuppliers(any())).thenReturn(List.of(
                new ProductPartReference(1L, "열연강판", 10L, "대성금속", partSupplier)));
        when(productsRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = productsService.create(request);

        verify(productsRepository).save(any(Product.class));
        assertThat(response.productName()).isEqualTo("자동차 차체");
        assertThat(response.exportCountries()).containsExactly("DE");
        assertThat(response.parts()).singleElement().satisfies(part -> {
            assertThat(part.partName()).isEqualTo("열연강판");
            assertThat(part.supplierName()).isEqualTo("대성금속");
            assertThat(part.status()).isNull();
        });
    }

    @Test
    void 수출국이_중복이면_저장하지_않는다() {
        ProductCreateRequest request = new ProductCreateRequest(
                "자동차 차체", "87082990", List.of("DE", "DE"),
                new BigDecimal("100.00"), validRequest().parts());

        assertError(request, ProductErrorCode.DUPLICATE_EXPORT_COUNTRY);
    }

    @Test
    void EU_회원국이_아니면_저장하지_않는다() {
        ProductCreateRequest request = new ProductCreateRequest(
                "자동차 차체", "87082990", List.of("KR"),
                new BigDecimal("100.00"), validRequest().parts());

        assertError(request, ProductErrorCode.INVALID_EU_COUNTRY);
    }

    @Test
    void 동일한_부품_협력업체_조합이_중복이면_저장하지_않는다() {
        var part = validRequest().parts().getFirst();
        ProductCreateRequest request = new ProductCreateRequest(
                "자동차 차체", "87082990", List.of("DE"),
                new BigDecimal("100.00"), List.of(part, part));

        assertError(request, ProductErrorCode.DUPLICATE_PRODUCT_PART);
    }

    private void assertError(ProductCreateRequest request, ProductErrorCode errorCode) {
        assertThatThrownBy(() -> productsService.create(request))
                .isInstanceOf(ProductException.class)
                .satisfies(error -> assertThat(((ProductException) error).getErrorCode())
                        .isEqualTo(errorCode));
        verify(productsRepository, never()).save(any());
        verify(relatedDataProvider, never()).getActivePartSuppliers(any());
    }

    private ProductCreateRequest validRequest() {
        return new ProductCreateRequest(
                "자동차 차체",
                "87082990",
                List.of("DE"),
                new BigDecimal("100.00"),
                List.of(new ProductCreateRequest.PartRequest(
                        1L, 10L, new BigDecimal("1.250"))));
    }
}
