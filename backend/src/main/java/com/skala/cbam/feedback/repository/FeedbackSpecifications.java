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
 * <p><b>왜 JPQL 의 {@code (:x is null or ...)} 를 안 쓰나</b> — PostgreSQL 이 그 형태의
 * 바인딩 파라미터 타입을 추론하지 못해 {@code could not determine data type of parameter $7} 로
 * 500 이 난다. H2 에서는 통과해서 테스트로는 안 잡히고, 실서버에 올려야 드러난다.
 * {@code SupplierSpecifications} 가 같은 이유로 먼저 이 방식으로 옮겨 갔다.
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
