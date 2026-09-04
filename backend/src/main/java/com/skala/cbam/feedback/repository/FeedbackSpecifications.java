package com.skala.cbam.feedback.repository;

import com.skala.cbam.common.domain.FeedbackStatus;
import com.skala.cbam.feedback.domain.Feedback;
import com.skala.cbam.feedback.domain.FeedbackType;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 발송 이력 목록에 <b>실제로 전달된 필터만</b> SQL 조건으로 만든다 (51·53번 · ADR-0008).
 *
 * <p>JPQL 에 모든 nullable 필터를 한 번에 넣지 않고 전달된 값만 조건으로 만든다.
 * {@code SupplierSpecifications} 와 같은 방식이다.
 */
public final class FeedbackSpecifications {

    private FeedbackSpecifications() {
    }

    public static Specification<Feedback> matches(Long supplierId, FeedbackType type, FeedbackStatus status,
                                                    OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> {
            // count 질의에는 fetch 를 걸지 않는다 — 걸면 "query specified join fetching" 오류가 난다
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("supplier", JoinType.INNER);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
