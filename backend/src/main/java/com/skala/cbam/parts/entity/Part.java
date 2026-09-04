package com.skala.cbam.parts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
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
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 부품과 협력업체의 공급 관계는 독립 엔티티인 {@link PartSupplier}로 관리한다.
 * 외부 API는 기존 계약대로 supplierIds를 주고받으며, 엔티티 내부에서 관계 객체로 변환한다.
 */
@Entity
@Table(name = "part")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String partCode;

    /**
     * ERD 의 컬럼 이름은 {@code name} 이므로 명시적으로 매핑한다.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String partName;

    @Column(nullable = false, length = 8, columnDefinition = "CHAR(8)")
    private String cnCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartUnit unit;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal benchmarkFactor;

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL)
    private Set<PartSupplier> suppliers = new HashSet<>();

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Part(String partCode, String partName, String cnCode, PartUnit unit,
                BigDecimal benchmarkFactor, Set<Long> supplierIds) {
        this.partCode = partCode;
        this.partName = partName;
        this.cnCode = cnCode;
        this.unit = unit;
        this.benchmarkFactor = benchmarkFactor;
        replaceSuppliers(supplierIds);
        this.createdAt = OffsetDateTime.now();
    }

    public void update(String partName, String cnCode, PartUnit unit,
                        BigDecimal benchmarkFactor, Set<Long> supplierIds) {
        if (partName != null) {
            this.partName = partName;
        }
        if (cnCode != null) {
            this.cnCode = cnCode;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (benchmarkFactor != null) {
            this.benchmarkFactor = benchmarkFactor;
        }
        if (supplierIds != null) {
            replaceSuppliers(supplierIds);
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public Set<Long> getSupplierIds() {
        return suppliers.stream()
                .filter(PartSupplier::isActive)
                .map(PartSupplier::getSupplierId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void replaceSuppliers(Set<Long> supplierIds) {
        Set<Long> requested = supplierIds == null ? Set.of() : Set.copyOf(supplierIds);

        suppliers.forEach(relation -> {
            if (requested.contains(relation.getSupplierId())) {
                relation.activate();
            } else {
                relation.deactivate();
            }
        });

        Set<Long> existing = suppliers.stream()
                .map(PartSupplier::getSupplierId)
                .collect(Collectors.toSet());
        requested.stream()
                .filter(supplierId -> !existing.contains(supplierId))
                .map(supplierId -> new PartSupplier(this, supplierId))
                .forEach(suppliers::add);
    }
}
