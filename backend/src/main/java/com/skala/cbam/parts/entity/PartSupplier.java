package com.skala.cbam.parts.entity;

import com.skala.cbam.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "part_supplier",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_part_supplier_part_supplier",
                columnNames = {"part_id", "supplier_id"}),
        indexes = @Index(
                name = "ix_part_supplier_supplier_status",
                columnList = "supplier_id, status")
)
public class PartSupplier extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PartSupplierStatus status;

    PartSupplier(Part part, Long supplierId) {
        this.part = part;
        this.supplierId = supplierId;
        this.status = PartSupplierStatus.ACTIVE;
    }

    public void activate() {
        this.status = PartSupplierStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = PartSupplierStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == PartSupplierStatus.ACTIVE;
    }
}
