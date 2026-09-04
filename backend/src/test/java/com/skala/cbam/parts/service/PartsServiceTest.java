package com.skala.cbam.parts.service;

import com.skala.cbam.parts.dto.PartCreateRequest;
import com.skala.cbam.parts.dto.PartUpdateRequest;
import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartUnit;
import com.skala.cbam.parts.exception.PartBusinessException;
import com.skala.cbam.parts.exception.PartErrorCode;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartsServiceTest {

    @Mock
    private PartsRepository partsRepository;

    @Mock
    private SupplierRepository supplierRepository;

    private PartsService partsService;

    @BeforeEach
    void setUp() {
        partsService = new PartsService(partsRepository, supplierRepository);
    }

    /** id 를 가진 협력업체를 만든다. Supplier 는 id 를 세터로 열지 않아 리플렉션으로 넣는다. */
    private static Supplier supplierWithId(long id, String name) {
        Supplier supplier = Supplier.builder()
                .businessRegistrationNumber("000-00-0000" + id)
                .name(name)
                .countryCode("KR")
                .contactName("담당자")
                .contactEmail("s" + id + "@example.test")
                .build();
        try {
            var field = Supplier.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(supplier, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return supplier;
    }

    private PartCreateRequest validRequest() {
        return new PartCreateRequest("P-0001", "열연강판", "72081000", "TON",
                new BigDecimal("1.8500"), 2026, Set.of(1L));
    }

    @Test
    void 부품코드가_중복이면_DUPLICATE_PART_CODE_예외를_던진다() {
        when(supplierRepository.findAllById(anyIterable()))
                .thenReturn(List.of(supplierWithId(1L, "대성금속")));
        when(partsRepository.existsByPartCode("P-0001")).thenReturn(true);

        assertThatThrownBy(() -> partsService.create(validRequest()))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.DUPLICATE_PART_CODE));
    }

    @Test
    void 부품명이_중복이면_DUPLICATE_PART_NAME_예외를_던진다() {
        when(supplierRepository.findAllById(anyIterable()))
                .thenReturn(List.of(supplierWithId(1L, "대성금속")));
        when(partsRepository.existsByPartCode("P-0001")).thenReturn(false);
        when(partsRepository.existsByPartName("열연강판")).thenReturn(true);

        assertThatThrownBy(() -> partsService.create(validRequest()))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.DUPLICATE_PART_NAME));
    }

    @Test
    void CN코드가_8자리_숫자가_아니면_INVALID_CN_CODE_예외를_던진다() {
        PartCreateRequest request = new PartCreateRequest("P-0001", "열연강판", "7208100",
                "TON", new BigDecimal("1.85"), 2026, Set.of());

        assertThatThrownBy(() -> partsService.create(request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.INVALID_CN_CODE));
    }

    @Test
    void 허용되지_않은_단위이면_INVALID_UNIT_예외를_던진다() {
        PartCreateRequest request = new PartCreateRequest("P-0001", "열연강판", "72081000",
                "LB", new BigDecimal("1.85"), 2026, Set.of());

        assertThatThrownBy(() -> partsService.create(request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.INVALID_UNIT));
    }

    @Test
    void benchmarkFactor가_음수이면_OUT_OF_RANGE_예외를_던진다() {
        PartCreateRequest request = new PartCreateRequest("P-0001", "열연강판", "72081000",
                "TON", new BigDecimal("-1"), 2026, Set.of());

        assertThatThrownBy(() -> partsService.create(request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.OUT_OF_RANGE));
    }

    @Test
    void 유효한_요청이면_부품을_등록한다() {
        when(supplierRepository.findAllById(anyIterable()))
                .thenReturn(List.of(supplierWithId(1L, "대성금속")));
        when(partsRepository.existsByPartCode(any())).thenReturn(false);
        when(partsRepository.existsByPartName(any())).thenReturn(false);
        when(partsRepository.save(any(Part.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = partsService.create(validRequest());

        assertThat(response.partCode()).isEqualTo("P-0001");
        assertThat(response.unit()).isEqualTo("TON");
        assertThat(response.supplierIds()).containsExactly(1L);
    }

    @Test
    void 존재하지_않는_협력업체를_공급사로_지정하면_SUPPLIER_NOT_FOUND_예외를_던진다() {
        // 1L 만 있고 99L 은 없다
        when(supplierRepository.findAllById(anyIterable()))
                .thenReturn(List.of(supplierWithId(1L, "대성금속")));
        PartCreateRequest request = new PartCreateRequest("P-0001", "열연강판", "72081000", "TON",
                new BigDecimal("1.8500"), 2026, Set.of(1L, 99L));

        assertThatThrownBy(() -> partsService.create(request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> {
                    PartBusinessException pe = (PartBusinessException) ex;
                    assertThat(pe.getErrorCode()).isEqualTo(PartErrorCode.SUPPLIER_NOT_FOUND);
                    assertThat(pe.getDetails()).containsEntry("missingSupplierIds", List.of(99L));
                });
    }

    @Test
    void 수정에서도_존재하지_않는_협력업체는_막는다() {
        when(partsRepository.findById(1L)).thenReturn(java.util.Optional.of(
                new Part("P-0001", "열연강판", "72081000", PartUnit.TON, new BigDecimal("1.8500"), 2026, Set.of())));
        when(supplierRepository.findAllById(anyIterable())).thenReturn(List.of());

        var request = new PartUpdateRequest(null, null, null, null, null, Set.of(99L));

        assertThatThrownBy(() -> partsService.update(1L, request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode())
                        .isEqualTo(PartErrorCode.SUPPLIER_NOT_FOUND));
    }

    @Test
    void 상세_조회는_협력업체_이름을_채운다() {
        when(partsRepository.findById(1L)).thenReturn(java.util.Optional.of(
                new Part("P-0001", "열연강판", "72081000", PartUnit.TON, new BigDecimal("1.8500"), 2026, Set.of(1L))));
        when(supplierRepository.findAllById(anyIterable()))
                .thenReturn(List.of(supplierWithId(1L, "대성금속")));

        var detail = partsService.getDetail(1L);

        assertThat(detail.suppliers()).singleElement()
                .satisfies(s -> {
                    assertThat(s.supplierId()).isEqualTo(1L);
                    assertThat(s.name()).isEqualTo("대성금속");
                    // 확정 배출 데이터는 Submission 도메인이 없어 아직 채울 경로가 없다
                    assertThat(s.confirmedData()).isEmpty();
                });
    }

    @Test
    void 존재하지_않는_부품을_조회하면_PART_NOT_FOUND_예외를_던진다() {
        when(partsRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> partsService.getDetail(999L))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.PART_NOT_FOUND));
    }
}
