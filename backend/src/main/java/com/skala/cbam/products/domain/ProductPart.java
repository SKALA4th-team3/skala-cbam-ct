package com.skala.cbam.products.domain;

import com.skala.cbam.common.domain.BaseTimeEntity;
import com.skala.cbam.parts.entity.PartSupplier;
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

import java.math.BigDecimal;

@Entity
@Table(name = "product_part", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_part_supplier", columnNames = {"product_id", "part_supplier_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPart extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_supplier_id", nullable = false)
    private PartSupplier partSupplier;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal inputQtyPerTon;

    ProductPart(Product product, PartSupplier partSupplier, BigDecimal inputQtyPerTon) {
        this.product = product;
        this.partSupplier = partSupplier;
        this.inputQtyPerTon = inputQtyPerTon;
    }

    void updateInputQtyPerTon(BigDecimal inputQtyPerTon) {
        this.inputQtyPerTon = inputQtyPerTon;
    }
}
