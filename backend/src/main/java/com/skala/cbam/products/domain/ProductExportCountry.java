package com.skala.cbam.products.domain;

import com.skala.cbam.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_export_country", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_export_country", columnNames = {"product_id", "country_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductExportCountry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    ProductExportCountry(Product product, String countryCode) {
        this.product = product;
        this.countryCode = countryCode;
    }
}
