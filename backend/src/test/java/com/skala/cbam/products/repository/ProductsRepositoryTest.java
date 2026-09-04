package com.skala.cbam.products.repository;

import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.parts.entity.PartUnit;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.products.domain.Product;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductsRepositoryTest {

    @Autowired
    private ProductsRepository productsRepository;

    @Autowired
    private PartsRepository partsRepository;

    @Test
    void Product를_저장하면_수출국과_구성부품도_함께_저장된다() {
        Part part = partsRepository.saveAndFlush(new Part(
                "P-9101", "저장 테스트 부품", "72081000", PartUnit.TON,
                new BigDecimal("1.0000"), Set.of(10L)));
        PartSupplier relation = part.getSuppliers().iterator().next();

        Product product = new Product("저장 테스트 제품", "87082990", new BigDecimal("200.00"));
        product.addExportCountry("DE");
        product.addPart(relation, new BigDecimal("1.500"));

        Product saved = productsRepository.saveAndFlush(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getExportCountries()).singleElement()
                .satisfies(country -> assertThat(country.getId()).isNotNull());
        assertThat(saved.getParts()).singleElement().satisfies(productPart -> {
            assertThat(productPart.getId()).isNotNull();
            assertThat(productPart.getPartSupplier().getId()).isEqualTo(relation.getId());
        });
    }
}
