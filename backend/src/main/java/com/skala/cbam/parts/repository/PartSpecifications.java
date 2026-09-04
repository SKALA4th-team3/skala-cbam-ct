package com.skala.cbam.parts.repository;

import com.skala.cbam.parts.entity.Part;
import org.springframework.data.jpa.domain.Specification;

public final class PartSpecifications {

    private PartSpecifications() {
    }

    public static Specification<Part> search(String search, Long supplierId, String cnCode) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (search != null && !search.isBlank()) {
                predicates = cb.and(predicates, cb.like(root.get("partName"), "%" + search + "%"));
            }
            if (cnCode != null && !cnCode.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("cnCode"), cnCode));
            }
            if (supplierId != null) {
                predicates = cb.and(predicates, cb.isMember(supplierId, root.get("supplierIds")));
            }
            return predicates;
        };
    }
}
