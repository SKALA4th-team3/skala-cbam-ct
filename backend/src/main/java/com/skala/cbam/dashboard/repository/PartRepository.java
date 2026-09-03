package com.skala.cbam.dashboard.repository;

import com.skala.cbam.dashboard.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;

/** 대시보드 패키지 안에서만 쓰는 최소 레포지토리. 부품 CRUD(5~8번) 쪽 정식 구현과는 별개. */
public interface PartRepository extends JpaRepository<Part, Long> {
}
