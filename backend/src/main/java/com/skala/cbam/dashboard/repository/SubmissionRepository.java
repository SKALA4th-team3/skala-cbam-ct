package com.skala.cbam.dashboard.repository;

import com.skala.cbam.dashboard.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * 38·40번 집계 대상 월의 제출 데이터 전부. supplier / part_supplier 는 같이 fetch 해서
     * 서비스 레이어에서 N+1 없이 그룹핑한다.
     */
    @Query("""
        select sub from Submission sub
        join fetch sub.supplier s
        left join fetch sub.partSupplier ps
        left join fetch ps.part p
        where sub.reportingMonth = :reportingMonth
        """)
    List<Submission> findAllByReportingMonthWithSupplier(@Param("reportingMonth") LocalDate reportingMonth);
}
