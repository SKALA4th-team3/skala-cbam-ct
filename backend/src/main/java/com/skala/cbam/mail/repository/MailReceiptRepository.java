package com.skala.cbam.mail.repository;

import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.domain.MailReceiptStatus;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 접수 메일 저장소 (API 명세 №15~№18). */
public interface MailReceiptRepository extends JpaRepository<MailReceipt, Long> {

    /**
     * 접수 이력 조회 (15번). null 인 조건은 걸지 않는다 — SupplierRepository.search 와 같은 관례.
     */
    @Query("""
            select m from MailReceipt m
            where (:supplierId is null or m.supplier.id = :supplierId)
              and (:status is null or m.status = :status)
              and (:receivedFrom is null or m.receivedAt >= :receivedFrom)
              and (:receivedTo is null or m.receivedAt <= :receivedTo)
            """)
    Page<MailReceipt> search(@Param("supplierId") Long supplierId,
                              @Param("status") MailReceiptStatus status,
                              @Param("receivedFrom") OffsetDateTime receivedFrom,
                              @Param("receivedTo") OffsetDateTime receivedTo,
                              Pageable pageable);
}
