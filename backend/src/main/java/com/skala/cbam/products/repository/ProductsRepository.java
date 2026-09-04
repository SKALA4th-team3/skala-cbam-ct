package com.skala.cbam.products.repository;

import com.skala.cbam.products.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductsRepository extends JpaRepository<Product, Long> {
}
