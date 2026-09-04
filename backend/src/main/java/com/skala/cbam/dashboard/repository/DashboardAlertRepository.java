package com.skala.cbam.dashboard.repository;

import com.skala.cbam.submission.domain.Alert;
import com.skala.cbam.submission.domain.AlertStatus;
import com.skala.cbam.submission.domain.Severity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 39·25번 경보 조회. 제출 도메인의 정식 {@link Alert} 를 그대로 쓴다. */
public interface DashboardAlertRepository extends JpaRepository<Alert, Long> {

    /**
     * 협력 끊김 업체의 <b>미제출</b> 경보를 걸러내는 조건 (요구사항 6번).
     *
     * <p>미제출 경보는 submission 이 없고 part_supplier 만 있는 행이다. 판정 경보(submission 이
     * 있는 행)는 제외하지 않는다 — 6번은 같은 문장에서 「기존 제출 데이터는 삭제하지 않고
     * 보존한다」고 하므로, 이미 낸 제출에 붙은 경보까지 숨기면 보존한 데이터를 화면에서 잃는다.
     *
     * <p>part_supplier 도 submission 도 없는 행은 걸러내지 않는다 — 있어선 안 되는 행을
     * 조용히 감추면 원인을 못 찾는다.
     *
     * <p>{@code partSupplierId} 가 연관이 아니라 값이라 조인 대신 {@code exists} 로 건다.
     */
    String NOT_INACTIVE_SUPPLIER_UNSUBMITTED = """
         and (a.submission is not null
              or a.partSupplierId is null
              or exists (
                select 1 from PartSupplier ps, Supplier s
                where ps.id = a.partSupplierId
                  and s.id = ps.supplierId
                  and s.status = com.skala.cbam.supplier.domain.SupplierStatus.ACTIVE))
        """;

    /**
     * 24행 severity 블록 집계용 — "지금 열려 있는" 경보만 심각도별로 센다 (가정: status=OPEN 만 집계).
     *
     * <p>목록(search)과 같은 6번 필터를 건다. 두 곳이 어긋나면 severity 합계와 목록 건수가 달라진다.
     */
    @Query("""
        select a from Alert a
        where a.reportingMonth = :reportingMonth
          and a.status = :status
        """ + NOT_INACTIVE_SUPPLIER_UNSUBMITTED)
    List<Alert> findAllByReportingMonthAndStatus(@Param("reportingMonth") LocalDate reportingMonth,
                                                 @Param("status") AlertStatus status);

    /**
     * 25번(경보 조회). 기본 정렬은 심각도 우선순위(HIGH → MEDIUM → LOW).
     * EnumType.STRING 컬럼을 그냥 order by 하면 알파벳순(HIGH,LOW,MEDIUM)이 되어버려서
     * CASE 로 우선순위를 직접 매긴다.
     */
    @Query(value = """
        select a from Alert a
        left join fetch a.submission sub
        left join fetch sub.supplier subSupplier
        where a.reportingMonth = :month
          and (:severity is null or a.severity = :severity)
          and (:ruleId is null or a.ruleId = :ruleId)
        """ + NOT_INACTIVE_SUPPLIER_UNSUBMITTED + """
        order by case a.severity when com.skala.cbam.submission.domain.Severity.HIGH then 0
                                  when com.skala.cbam.submission.domain.Severity.MEDIUM then 1
                                  else 2 end asc,
                 a.validatedAt desc
        """,
        countQuery = """
        select count(a) from Alert a
        where a.reportingMonth = :month
          and (:severity is null or a.severity = :severity)
          and (:ruleId is null or a.ruleId = :ruleId)
        """ + NOT_INACTIVE_SUPPLIER_UNSUBMITTED)
    Page<Alert> search(@Param("month") LocalDate month,
                        @Param("severity") Severity severity,
                        @Param("ruleId") String ruleId,
                        Pageable pageable);
}
