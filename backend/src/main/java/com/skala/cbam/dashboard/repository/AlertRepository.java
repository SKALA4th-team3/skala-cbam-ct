package com.skala.cbam.dashboard.repository;

import com.skala.cbam.dashboard.entity.Alert;
import com.skala.cbam.dashboard.entity.AlertStatus;
import com.skala.cbam.dashboard.entity.SeverityCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** 24행 severity 블록 집계용 — "지금 열려 있는" 경보만 심각도별로 센다 (가정: status=OPEN 만 집계) */
    List<Alert> findAllByReportingMonthAndStatus(LocalDate reportingMonth, AlertStatus status);

    /**
     * 25번(경보 조회). 기본 정렬은 심각도 우선순위(HIGH → MEDIUM → LOW).
     * EnumType.STRING 컬럼을 그냥 order by 하면 알파벳순(HIGH,LOW,MEDIUM)이 되어버려서
     * CASE 로 우선순위를 직접 매긴다.
     */
    @Query(value = """
        select a from Alert a
        left join fetch a.partSupplier ps
        left join fetch ps.part p
        left join fetch ps.supplier s
        left join fetch a.submission sub
        left join fetch sub.supplier subSupplier
        left join fetch sub.partSupplier subPs
        left join fetch subPs.part subP
        where a.reportingMonth = :month
          and (:severity is null or a.severity = :severity)
          and (:ruleId is null or a.ruleId = :ruleId)
        order by case a.severity when com.skala.cbam.dashboard.entity.SeverityCode.HIGH then 0
                                  when com.skala.cbam.dashboard.entity.SeverityCode.MEDIUM then 1
                                  else 2 end asc,
                 a.validatedAt desc
        """,
        countQuery = """
        select count(a) from Alert a
        where a.reportingMonth = :month
          and (:severity is null or a.severity = :severity)
          and (:ruleId is null or a.ruleId = :ruleId)
        """)
    Page<Alert> search(@Param("month") LocalDate month,
                        @Param("severity") SeverityCode severity,
                        @Param("ruleId") String ruleId,
                        Pageable pageable);
}
