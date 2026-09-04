package com.skala.cbam.dashboard.entity;

import com.skala.cbam.supplier.domain.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD part_supplier 교차 엔티티. 경보(alert)의 target(supplierId · partId) 계산에 쓴다.
 *
 * <p>supplier 는 {@link com.skala.cbam.supplier.domain.Supplier}(협력업체 도메인의 정식 엔티티)를
 * 그대로 참조한다. 대시보드용 사본을 따로 두면 같은 supplier 테이블을 두 엔티티가 매핑해
 * Spring Data 가 빈 이름 supplierRepository 를 두고 충돌한다(컨텍스트 자체가 안 뜬다).
 *
 * <p>⚠️ part · part_supplier 의 소유 도메인은 아직 팀 미결정이다. 부품(7~10번) 쪽에도
 * 같은 테이블을 보는 매핑이 있어 합치기 전에 소유자를 정해야 한다.
 *
 * <p><b>created_at · updated_at 을 일부러 매핑하지 않는다.</b> 대시보드는 두 값을 한 군데도
 * 읽지 않는데, 매핑해 두면 같은 테이블을 보는 다른 도메인의 엔티티와 논리 컬럼명이 갈려
 * DuplicateMappingException 으로 <b>부팅 자체가 막힌다</b>
 * (Table [part] ... referred to by multiple logical column names: [createdAt], [created_at]).
 * 안 쓰는 컬럼까지 소유권을 주장하지 않는다.
 */
@Entity
@Table(name = "part_supplier")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PartSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LifecycleStatus status;
}
