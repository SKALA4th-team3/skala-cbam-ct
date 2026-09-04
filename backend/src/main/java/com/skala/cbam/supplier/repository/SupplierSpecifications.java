package com.skala.cbam.supplier.repository;

import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** 협력업체 목록에 실제로 전달된 필터만 SQL 조건으로 만든다 (3번). */
public final class SupplierSpecifications {

    private SupplierSpecifications() {
    }

    public static Specification<Supplier> matches(String search, String country, SupplierStatus status,
                                                    Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (search != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")), "%" + search.toLowerCase(Locale.ROOT) + "%"));
            }
            if (country != null) {
                predicates.add(criteriaBuilder.equal(root.get("countryCode"), country));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (ids != null) {
                predicates.add(root.get("id").in(ids));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
