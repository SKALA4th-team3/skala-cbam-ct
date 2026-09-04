package com.skala.cbam.dashboard.repository;

import com.skala.cbam.dashboard.entity.LifecycleStatus;
import com.skala.cbam.dashboard.entity.PartSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartSupplierRepository extends JpaRepository<PartSupplier, Long> {

    /**
     * 대시보드 counts/ratio 의 모수(母數) = "ACTIVE part_supplier 전체" (요구사항 13번 targets 와 같은 개념).
     * ERD 노트: "미제출 target = ACTIVE part_supplier 중 대상 월의 유효 submission이 없는 조합"
     *
     * <p>협력이 끊긴 업체(supplier.status = INACTIVE)는 제외한다 — 요구사항 6번이
     * 「마감 대상과 미제출 경보에서 제외된다」로 못 박은 쪽이다. part_supplier.status 와
     * supplier.status 는 다른 축이라, 업체가 끊겨도 공급 관계 행은 ACTIVE 로 남아 있을 수 있다.
     * 두 조건을 함께 걸지 않으면 끊긴 업체가 계속 미제출로 집계된다.
     */
    @Query("""
        select ps from PartSupplier ps
        join fetch ps.part p
        join fetch ps.supplier s
        where ps.status = :status
          and s.status = com.skala.cbam.supplier.domain.SupplierStatus.ACTIVE
        """)
    List<PartSupplier> findAllWithPartAndSupplierByStatus(@Param("status") LifecycleStatus status);
}
