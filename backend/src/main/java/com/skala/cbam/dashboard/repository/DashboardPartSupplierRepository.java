package com.skala.cbam.dashboard.repository;

import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.parts.entity.PartSupplierStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 38·40번 집계의 모수(母數)를 읽는다.
 *
 * <p>부품 도메인의 정식 엔티티 {@link PartSupplier} 를 그대로 쓴다 — 대시보드가 자기 사본을
 * 갖고 있으면 같은 테이블을 두 엔티티가 매핑해 부팅이 막힌다(실제로 그랬다).
 * 인터페이스 이름에 {@code Dashboard} 를 붙인 것도 같은 이유다 — 스프링 빈 이름은
 * 인터페이스의 단순 이름에서 나와서, {@code PartSupplierRepository} 가 둘이면 충돌한다.
 */
public interface DashboardPartSupplierRepository extends JpaRepository<PartSupplier, Long> {

    /**
     * 대시보드 counts/ratio 의 모수 = "ACTIVE part_supplier 전체" (요구사항 13번 targets 와 같은 개념).
     * ERD 노트: "미제출 target = ACTIVE part_supplier 중 대상 월의 유효 submission이 없는 조합"
     *
     * <p>협력이 끊긴 업체(supplier.status = INACTIVE)는 제외한다 — 요구사항 6번이
     * 「마감 대상과 미제출 경보에서 제외된다」로 못 박은 쪽이다. part_supplier.status 와
     * supplier.status 는 다른 축이라, 업체가 끊겨도 공급 관계 행은 ACTIVE 로 남아 있을 수 있다.
     * 두 조건을 함께 걸지 않으면 끊긴 업체가 계속 미제출로 집계된다.
     *
     * <p>{@code supplierId} 가 연관이 아니라 값이라 조인 대신 {@code exists} 로 건다.
     */
    @Query("""
        select ps from PartSupplier ps
        join fetch ps.part p
        where ps.status = :status
          and exists (
            select 1 from Supplier s
            where s.id = ps.supplierId
              and s.status = com.skala.cbam.supplier.domain.SupplierStatus.ACTIVE)
        """)
    List<PartSupplier> findActiveTargets(@Param("status") PartSupplierStatus status);
}
