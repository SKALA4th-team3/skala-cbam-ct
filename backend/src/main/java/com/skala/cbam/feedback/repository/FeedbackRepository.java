package com.skala.cbam.feedback.repository;

import com.skala.cbam.feedback.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 발송 이력 조회 (31번 + ADR-0008 전사 조회)는 {@link FeedbackSpecifications} 로 만든다 —
 * 전달된 필터만 조건이 된다. JPQL 의 {@code (:x is null or ...)} 는 PostgreSQL 에서 500 이 난다.
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long>, JpaSpecificationExecutor<Feedback> {
}
