package com.skala.cbam.parts.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * supplierIds는 Supplier 엔티티를 참조하지 않고 순수 id 목록으로만 저장한다.
 * Supplier 도메인이 아직 없어(다른 브랜치에서 작업 중) 컴파일·부팅이 가능하도록 임시로 이렇게 결합을 낮춰 둔 것이다.
 * Supplier 엔티티가 들어오면 이름 조회를 위해 실제 연관관계나 조인으로 교체해야 한다.
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

    @Column(nullable = false, unique = true)
    private String partName;

    @Column(nullable = false, length = 8)
    private String cnCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartUnit unit;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal benchmarkFactor;

    @ElementCollection
    @CollectionTable(name = "part_supplier", joinColumns = @JoinColumn(name = "part_id"))
    @Column(name = "supplier_id")
    private Set<Long> supplierIds = new HashSet<>();

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
        this.supplierIds = supplierIds == null ? new HashSet<>() : new HashSet<>(supplierIds);
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
            this.supplierIds = new HashSet<>(supplierIds);
        }
        this.updatedAt = OffsetDateTime.now();
    }
}
