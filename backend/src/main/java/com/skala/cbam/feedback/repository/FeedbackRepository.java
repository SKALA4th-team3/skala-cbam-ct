package com.skala.cbam.feedback.repository;

import com.skala.cbam.common.domain.FeedbackStatus;
import com.skala.cbam.feedback.domain.Feedback;
import com.skala.cbam.feedback.domain.FeedbackType;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** 발송 이력 조회 (31번 + ADR-0008 전사 조회). supplierId 는 선택 — null 이면 전사. */
    @Query("""
            select f from Feedback f
            join fetch f.supplier s
            where (:supplierId is null or s.id = :supplierId)
              and (:type is null or f.type = :type)
              and (:status is null or f.status = :status)
              and (:from is null or f.createdAt >= :from)
              and (:to is null or f.createdAt <= :to)
            """)
    Page<Feedback> search(@Param("supplierId") Long supplierId,
                           @Param("type") FeedbackType type,
                           @Param("status") FeedbackStatus status,
                           @Param("from") OffsetDateTime from,
                           @Param("to") OffsetDateTime to,
                           Pageable pageable);
}
