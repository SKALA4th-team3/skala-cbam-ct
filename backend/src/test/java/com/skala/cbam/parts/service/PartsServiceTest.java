package com.skala.cbam.parts.service;

import com.skala.cbam.parts.dto.PartCreateRequest;
import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.exception.PartBusinessException;
import com.skala.cbam.parts.exception.PartErrorCode;
import com.skala.cbam.parts.repository.PartsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartsServiceTest {

    @Mock
    private PartsRepository partsRepository;

    private PartsService partsService;

    @BeforeEach
    void setUp() {
        partsService = new PartsService(partsRepository);
    }

    private PartCreateRequest validRequest() {
        return new PartCreateRequest("P-0001", "열연강판", "72081000", "TON",
                new BigDecimal("1.8500"), Set.of(1L));
    }

    @Test
    void 부품코드가_중복이면_DUPLICATE_PART_CODE_예외를_던진다() {
        when(partsRepository.existsByPartCode("P-0001")).thenReturn(true);

        assertThatThrownBy(() -> partsService.create(validRequest()))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.DUPLICATE_PART_CODE));
    }

    @Test
    void 부품명이_중복이면_DUPLICATE_PART_NAME_예외를_던진다() {
        when(partsRepository.existsByPartCode("P-0001")).thenReturn(false);
        when(partsRepository.existsByPartName("열연강판")).thenReturn(true);

        assertThatThrownBy(() -> partsService.create(validRequest()))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.DUPLICATE_PART_NAME));
    }

    @Test
    void CN코드가_8자리_숫자가_아니면_INVALID_CN_CODE_예외를_던진다() {
        PartCreateRequest request = new PartCreateRequest("P-0001", "열연강판", "7208100",
                "TON", new BigDecimal("1.85"), Set.of());

        assertThatThrownBy(() -> partsService.create(request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.INVALID_CN_CODE));
    }

    @Test
    void 허용되지_않은_단위이면_INVALID_UNIT_예외를_던진다() {
        PartCreateRequest request = new PartCreateRequest("P-0001", "열연강판", "72081000",
                "LB", new BigDecimal("1.85"), Set.of());

        assertThatThrownBy(() -> partsService.create(request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.INVALID_UNIT));
    }

    @Test
    void benchmarkFactor가_음수이면_OUT_OF_RANGE_예외를_던진다() {
        PartCreateRequest request = new PartCreateRequest("P-0001", "열연강판", "72081000",
                "TON", new BigDecimal("-1"), Set.of());

        assertThatThrownBy(() -> partsService.create(request))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.OUT_OF_RANGE));
    }

    @Test
    void 유효한_요청이면_부품을_등록한다() {
        when(partsRepository.existsByPartCode(any())).thenReturn(false);
        when(partsRepository.existsByPartName(any())).thenReturn(false);
        when(partsRepository.save(any(Part.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = partsService.create(validRequest());

        assertThat(response.partCode()).isEqualTo("P-0001");
        assertThat(response.unit()).isEqualTo("TON");
        assertThat(response.supplierIds()).containsExactly(1L);
    }

    @Test
    void 존재하지_않는_부품을_조회하면_PART_NOT_FOUND_예외를_던진다() {
        when(partsRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> partsService.getDetail(999L))
                .isInstanceOf(PartBusinessException.class)
                .satisfies(ex -> assertThat(((PartBusinessException) ex).getErrorCode()).isEqualTo(PartErrorCode.PART_NOT_FOUND));
    }
}
