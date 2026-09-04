package com.skala.cbam.feedback.repository;

import com.skala.cbam.feedback.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 발송 이력 조회 (31번 + ADR-0008 전사 조회)는 {@link FeedbackSpecifications} 로 만든다 —
 * 전달된 필터만 조건에 포함해 불필요한 null 파라미터 바인딩을 피한다.
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long>, JpaSpecificationExecutor<Feedback> {
}
