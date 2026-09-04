package com.skala.cbam.submission.repository;

import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 제출 데이터 저장소 (API 명세 №20~№23).
 *
 * <p>목록 조회(29번)는 실제 제출 + 미제출 가상 행을 합쳐서 정렬·페이징해야 하는데, 가상 행은
 * DB에 없는 계산값이라 DB 레벨 페이징이 불가능하다. 그래서 이 메서드는 조건에 맞는 실제 행을
 * 전부 가져오고, 정렬·페이징은 서비스에서 가상 행과 합친 뒤 메모리에서 한다.
 * (지금 규모에서는 괜찮지만, 제출 데이터가 아주 많아지면 다시 볼 조건이다.)
 *
 * <p>partId 필터는 여기서 걸지 않는다 — partId → partSupplierId 변환이 부품 도메인 Port를
 * 거쳐야 해서, 그 결과를 서비스가 받아 메모리에서 거른다.
 */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("""
            select s from Submission s
            join fetch s.supplier sup
            where (:supplierId is null or sup.id = :supplierId)
              and (:reportingMonth is null or s.reportingMonth = :reportingMonth)
              and (:status is null or s.status = :status)
              and (:judgement is null or s.judgement = :judgement)
              and (:severity is null or s.severity = :severity)
              and (:submittedFrom is null or s.submittedAt >= :submittedFrom)
              and (:submittedTo is null or s.submittedAt <= :submittedTo)
            """)
    List<Submission> search(@Param("supplierId") Long supplierId,
                             @Param("reportingMonth") LocalDate reportingMonth,
                             @Param("status") SubmissionStatus status,
                             @Param("judgement") Judgement judgement,
                             @Param("severity") Severity severity,
                             @Param("submittedFrom") OffsetDateTime submittedFrom,
                             @Param("submittedTo") OffsetDateTime submittedTo);

    @Query("select s from Submission s join fetch s.supplier where s.id = :id")
    Optional<Submission> findByIdWithSupplier(@Param("id") Long id);
}
