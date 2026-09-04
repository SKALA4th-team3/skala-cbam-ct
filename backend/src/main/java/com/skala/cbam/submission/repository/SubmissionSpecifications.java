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
 * <p>JPQL 에 모든 nullable 필터를 한 번에 넣지 않고 전달된 값만 조건으로 만든다.
 * {@code SupplierSpecifications} 와 같은 방식이다.
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
