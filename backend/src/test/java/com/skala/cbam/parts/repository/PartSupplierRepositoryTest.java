package com.skala.cbam.parts.repository;

import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartSupplierStatus;
import com.skala.cbam.parts.entity.PartUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PartSupplierRepositoryTest {

    @Autowired
    private PartsRepository partsRepository;

    @Autowired
    private PartSupplierRepository partSupplierRepository;

    @Test
    void 부품을_저장하면_공급관계에_독립_ID가_생성된다() {
        Part part = new Part("P-1001", "테스트 부품", "72081000", PartUnit.TON,
                new BigDecimal("1.0000"), Set.of(10L));

        Part saved = partsRepository.saveAndFlush(part);

        var relation = partSupplierRepository
                .findByPartIdAndSupplierIdAndStatus(saved.getId(), 10L, PartSupplierStatus.ACTIVE);
        assertThat(relation).isPresent();
        assertThat(relation.orElseThrow().getId()).isNotNull();
    }

    @Test
    void 공급관계를_삭제하고_다시_추가하면_같은_ID를_재활성화한다() {
        Part part = partsRepository.saveAndFlush(new Part(
                "P-1002", "재활성화 부품", "72081000", PartUnit.TON,
                new BigDecimal("1.0000"), Set.of(10L)));
        Long relationId = partSupplierRepository
                .findByPartIdAndSupplierIdAndStatus(part.getId(), 10L, PartSupplierStatus.ACTIVE)
                .orElseThrow()
                .getId();

        part.update(null, null, null, null, Set.of());
        partsRepository.saveAndFlush(part);
        assertThat(part.getSupplierIds()).isEmpty();

        part.update(null, null, null, null, Set.of(10L));
        partsRepository.saveAndFlush(part);

        var reactivated = partSupplierRepository
                .findByPartIdAndSupplierIdAndStatus(part.getId(), 10L, PartSupplierStatus.ACTIVE)
                .orElseThrow();
        assertThat(reactivated.getId()).isEqualTo(relationId);
    }
}
