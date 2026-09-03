package com.skala.cbam.dashboard.repository;

import com.skala.cbam.dashboard.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

/** 대시보드 패키지 안에서만 쓰는 최소 레포지토리. 협력업체 CRUD(1~4번) 쪽 정식 구현과는 별개. */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
