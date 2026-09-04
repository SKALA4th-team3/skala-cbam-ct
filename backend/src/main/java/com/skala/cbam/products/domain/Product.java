package com.skala.cbam.products.domain;

import com.skala.cbam.common.domain.BaseTimeEntity;
import com.skala.cbam.parts.entity.PartSupplier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 8)
    private String cnCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal annualExportTon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductExportCountry> exportCountries = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductPart> parts = new ArrayList<>();

    public Product(String name, String cnCode, BigDecimal annualExportTon) {
        this.name = name;
        this.cnCode = cnCode;
        this.annualExportTon = annualExportTon;
        this.status = ProductStatus.ACTIVE;
    }

    public void addExportCountry(String countryCode) {
        exportCountries.add(new ProductExportCountry(this, countryCode));
    }

    public void addPart(PartSupplier partSupplier, BigDecimal inputQtyPerTon) {
        parts.add(new ProductPart(this, partSupplier, inputQtyPerTon));
    }

    public void update(BigDecimal annualExportTon) {
        if (annualExportTon != null) {
            this.annualExportTon = annualExportTon;
        }
    }

    public void replaceExportCountries(List<String> countryCodes) {
        Set<String> requested = Set.copyOf(countryCodes);
        exportCountries.removeIf(country -> !requested.contains(country.getCountryCode()));
        Set<String> existing = exportCountries.stream()
                .map(ProductExportCountry::getCountryCode)
                .collect(Collectors.toSet());
        countryCodes.stream()
                .filter(code -> !existing.contains(code))
                .forEach(this::addExportCountry);
    }

    public void replaceParts(List<PartComposition> compositions) {
        Set<Long> requestedIds = compositions.stream()
                .map(composition -> composition.partSupplier().getId())
                .collect(Collectors.toSet());
        parts.removeIf(part -> !requestedIds.contains(part.getPartSupplier().getId()));
        for (PartComposition composition : compositions) {
            parts.stream()
                    .filter(part -> part.getPartSupplier().getId()
                            .equals(composition.partSupplier().getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            part -> part.updateInputQtyPerTon(composition.inputQtyPerTon()),
                            () -> addPart(composition.partSupplier(), composition.inputQtyPerTon()));
        }
    }

    public record PartComposition(PartSupplier partSupplier, BigDecimal inputQtyPerTon) {
    }
}
