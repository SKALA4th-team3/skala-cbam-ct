package com.skala.cbam.products.service;

import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.products.domain.Product;
import com.skala.cbam.products.dto.ProductCreateRequest;
import com.skala.cbam.products.error.ProductErrorCode;
import com.skala.cbam.products.error.ProductException;
import com.skala.cbam.products.repository.ProductsRepository;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.ProductPartReference;
import com.skala.cbam.products.service.port.ProductRelatedDataProvider.ProductPartData;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
        when(relatedDataProvider.getProductPartData(any(), any())).thenReturn(List.of(
                new ProductPartData(1L, "열연강판", 10L, "대성금속", 100L,
                        new BigDecimal("1.2500"), "NOT_SUBMITTED", null, null, null)));

        var response = productsService.create(request);

        verify(productsRepository).save(any(Product.class));
        assertThat(response.productName()).isEqualTo("자동차 차체");
        assertThat(response.exportCountries()).containsExactly("DE");
        assertThat(response.parts()).singleElement().satisfies(part -> {
            assertThat(part.partName()).isEqualTo("열연강판");
            assertThat(part.supplierName()).isEqualTo("대성금속");
            assertThat(part.status()).isEqualTo("NOT_SUBMITTED");
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

    @Test
    void 모든_부품의_당월_제출이_확정이면_내재배출량을_계산한다() {
        Product product = new Product("자동차 차체", "87082990", new BigDecimal("100.00"));
        product.addExportCountry("DE");
        product.addPart(partSupplier, new BigDecimal("1.250"));
        when(productsRepository.findById(1L)).thenReturn(Optional.of(product));
        when(relatedDataProvider.getProductPartData(any(), any())).thenReturn(List.of(
                new ProductPartData(1L, "열연강판", 10L, "대성금속", 100L,
                        new BigDecimal("1.2500"), "CONFIRMED", new BigDecimal("2.0000"),
                        2026, new BigDecimal("0.2000"))));

        var response = productsService.getDetail(1L, "2026-09");

        assertThat(response.calculationStatus().name()).isEqualTo("COMPLETE");
        assertThat(response.embeddedEmission()).isEqualByComparingTo("2.5000");
        assertThat(response.benchmarkEmission()).isEqualByComparingTo("1.5625");
        assertThat(response.missingPartIds()).isEmpty();
        assertThat(response.parts()).singleElement()
                .satisfies(part -> assertThat(part.contribution()).isEqualByComparingTo("2.5000"));
    }

    @Test
    void 확정_뒤_재제출이_들어와도_확정된_내재배출량은_유지한다() {
        Product product = new Product("자동차 차체", "87082990", new BigDecimal("100.00"));
        product.addExportCountry("DE");
        product.addPart(partSupplier, new BigDecimal("1.250"));
        when(productsRepository.findById(1L)).thenReturn(Optional.of(product));
        // 최신 제출은 심사 대기(재제출)지만 당월 확정 건이 남아 있는 상태 — 어댑터가 둘을 갈라서 준다.
        when(relatedDataProvider.getProductPartData(any(), any())).thenReturn(List.of(
                new ProductPartData(1L, "열연강판", 10L, "대성금속", 100L,
                        new BigDecimal("1.2500"), "REVIEW_PENDING", new BigDecimal("2.0000"),
                        2026, new BigDecimal("0.2000"))));

        var response = productsService.getDetail(1L, "2026-09");

        assertThat(response.calculationStatus().name()).isEqualTo("COMPLETE");
        assertThat(response.embeddedEmission()).isEqualByComparingTo("2.5000");
        assertThat(response.missingPartIds()).isEmpty();
        assertThat(response.parts()).singleElement().satisfies(part -> {
            assertThat(part.status()).isEqualTo("REVIEW_PENDING");
            assertThat(part.emissionIntensity()).isEqualByComparingTo("2.0000");
            assertThat(part.contribution()).isEqualByComparingTo("2.5000");
        });
    }

    @Test
    void 확정_건이_없으면_최신_제출의_원단위를_쓰지_않고_비워_둔다() {
        Product product = new Product("자동차 차체", "87082990", new BigDecimal("100.00"));
        product.addExportCountry("DE");
        product.addPart(partSupplier, new BigDecimal("1.250"));
        when(productsRepository.findById(1L)).thenReturn(Optional.of(product));
        when(relatedDataProvider.getProductPartData(any(), any())).thenReturn(List.of(
                new ProductPartData(1L, "열연강판", 10L, "대성금속", 100L,
                        new BigDecimal("1.2500"), "REVIEW_PENDING", null, null, null)));

        var response = productsService.getDetail(1L, "2026-09");

        assertThat(response.calculationStatus().name()).isEqualTo("INCOMPLETE");
        assertThat(response.embeddedEmission()).isNull();
        assertThat(response.missingPartIds()).containsExactly(1L);
        assertThat(response.parts()).singleElement().satisfies(part -> {
            assertThat(part.emissionIntensity()).isNull();
            assertThat(part.contribution()).isNull();
        });
    }

    @Test
    void 벤치마크_팩터가_없는_부품이_있어도_상세_조회가_터지지_않는다() {
        Product product = new Product("자동차 차체", "87082990", new BigDecimal("100.00"));
        product.addExportCountry("DE");
        product.addPart(partSupplier, new BigDecimal("1.250"));
        when(productsRepository.findById(1L)).thenReturn(Optional.of(product));
        when(relatedDataProvider.getProductPartData(any(), any())).thenReturn(List.of(
                new ProductPartData(1L, "열연강판", 10L, "대성금속", 100L,
                        null, "CONFIRMED", new BigDecimal("2.0000"), 2026, new BigDecimal("0.2000"))));

        var response = productsService.getDetail(1L, "2026-09");

        // 평균값은 모르므로 비운다. 확정 배출데이터는 있으니 실측값과 COMPLETE 는 그대로다.
        assertThat(response.benchmarkEmission()).isNull();
        assertThat(response.embeddedEmission()).isEqualByComparingTo("2.5000");
        assertThat(response.calculationStatus().name()).isEqualTo("COMPLETE");
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
