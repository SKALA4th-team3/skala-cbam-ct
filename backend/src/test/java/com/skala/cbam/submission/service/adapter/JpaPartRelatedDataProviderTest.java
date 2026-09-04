package com.skala.cbam.submission.service.adapter;

import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartUnit;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.submission.service.port.PartRelatedDataProvider;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaPartRelatedDataProvider.class)
class JpaPartRelatedDataProviderTest {

    @Autowired
    private PartsRepository partsRepository;

    @Autowired
    private PartRelatedDataProvider dataProvider;

    @Test
    void 활성_공급관계를_미제출_대상으로_반환한다() {
        Part part = partsRepository.saveAndFlush(new Part(
                "P-SUB-1", "제출 연동 부품", "72081000", PartUnit.TON,
                new BigDecimal("1.0000"), Set.of(10L, 20L)));

        var targets = dataProvider.findActiveTargets(10L, part.getId());

        assertThat(targets).singleElement().satisfies(target -> {
            assertThat(target.partSupplierId()).isNotNull();
            assertThat(target.supplierId()).isEqualTo(10L);
            assertThat(target.partId()).isEqualTo(part.getId());
            assertThat(target.partName()).isEqualTo("제출 연동 부품");
        });
    }

    @Test
    void 비활성_공급관계는_미제출_대상에서_제외한다() {
        Part part = partsRepository.saveAndFlush(new Part(
                "P-SUB-2", "비활성 연동 부품", "72081000", PartUnit.TON,
                new BigDecimal("1.0000"), Set.of(10L)));
        part.update(null, null, null, null, Set.of());
        partsRepository.saveAndFlush(part);

        assertThat(dataProvider.findActiveTargets(10L, part.getId())).isEmpty();
    }

    @Test
    void 공급관계_ID로_부품정보를_반환한다() {
        Part part = partsRepository.saveAndFlush(new Part(
                "P-SUB-3", "상세 연동 부품", "72081000", PartUnit.TON,
                new BigDecimal("1.0000"), Set.of(10L)));
        Long relationId = part.getSuppliers().iterator().next().getId();

        var info = dataProvider.findPartInfo(relationId);

        assertThat(info).isPresent();
        assertThat(info.orElseThrow().partId()).isEqualTo(part.getId());
        assertThat(info.orElseThrow().partName()).isEqualTo("상세 연동 부품");
        assertThat(info.orElseThrow().benchmarkFactorYear()).isNull();
    }
}
