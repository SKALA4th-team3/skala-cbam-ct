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
     */
    @Query("""
        select ps from PartSupplier ps
        join fetch ps.part p
        join fetch ps.supplier s
        where ps.status = :status
        """)
    List<PartSupplier> findAllWithPartAndSupplierByStatus(@Param("status") LifecycleStatus status);
}
