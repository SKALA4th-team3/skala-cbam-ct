package com.skala.cbam.products.repository;

import com.skala.cbam.products.domain.Product;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> search(String search, String cnCode) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();
            if (search != null && !search.isBlank()) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")),
                        "%" + search.trim().toLowerCase() + "%"));
            }
            if (cnCode != null && !cnCode.isBlank()) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.equal(root.get("cnCode"), cnCode.trim()));
            }
            return predicate;
        };
    }
}
