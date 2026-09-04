package com.skala.cbam.mail.repository;

import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.domain.MailReceiptStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** 접수 이력 목록에 실제로 전달된 필터만 SQL 조건으로 만든다 (15번). */
public final class MailReceiptSpecifications {

    private MailReceiptSpecifications() {
    }

    public static Specification<MailReceipt> matches(Long supplierId, MailReceiptStatus status,
                                                       OffsetDateTime receivedFrom, OffsetDateTime receivedTo) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (supplierId != null) {
                predicates.add(criteriaBuilder.equal(root.get("supplier").get("id"), supplierId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (receivedFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("receivedAt"), receivedFrom));
            }
            if (receivedTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("receivedAt"), receivedTo));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
