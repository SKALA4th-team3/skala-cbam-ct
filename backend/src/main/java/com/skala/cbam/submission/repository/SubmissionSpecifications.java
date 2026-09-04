package com.skala.cbam.submission.repository;

import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 검토 목록에 <b>실제로 전달된 필터만</b> SQL 조건으로 만든다 (29번).
 *
 * <p><b>왜 JPQL 의 {@code (:x is null or ...)} 를 안 쓰나</b> — PostgreSQL 이 그 형태의
 * 바인딩 파라미터 타입을 추론하지 못해 {@code could not determine data type of parameter $11} 로
 * 500 이 난다. H2 에서는 통과해서 테스트로는 안 잡히고, 실서버에 올려야 드러난다.
 * {@code SupplierSpecifications} 가 같은 이유로 먼저 이 방식으로 옮겨 갔다.
 */
public final class SubmissionSpecifications {

    private SubmissionSpecifications() {
    }

    public static Specification<Submission> matches(Long supplierId, LocalDate reportingMonth,
                                                      SubmissionStatus status, Judgement judgement,
                                                      Severity severity,
                                                      OffsetDateTime submittedFrom, OffsetDateTime submittedTo) {
        return (root, query, cb) -> {
            // count 질의에는 fetch 를 걸지 않는다 — 걸면 "query specified join fetching" 오류가 난다
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("supplier", JoinType.INNER);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (reportingMonth != null) {
                predicates.add(cb.equal(root.get("reportingMonth"), reportingMonth));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (judgement != null) {
                predicates.add(cb.equal(root.get("judgement"), judgement));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (submittedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), submittedFrom));
            }
            if (submittedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), submittedTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
