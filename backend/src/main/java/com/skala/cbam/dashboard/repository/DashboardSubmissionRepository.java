package com.skala.cbam.dashboard.repository;

import com.skala.cbam.submission.domain.Submission;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 38·40번 집계용 조회. 제출 도메인의 정식 {@link Submission} 을 그대로 쓴다. */
public interface DashboardSubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * 38·40번 집계 대상 월의 제출 데이터 전부. supplier 는 같이 fetch 해서 N+1 을 막는다.
     *
     * <p>{@code partSupplierId} 는 연관이 아니라 값이라 fetch 할 것이 없다 —
     * 서비스가 모수 목록에서 id 로 찾는다.
     */
    @Query("""
        select sub from Submission sub
        join fetch sub.supplier s
        where sub.reportingMonth = :reportingMonth
        """)
    List<Submission> findAllByReportingMonthWithSupplier(@Param("reportingMonth") LocalDate reportingMonth);
}
