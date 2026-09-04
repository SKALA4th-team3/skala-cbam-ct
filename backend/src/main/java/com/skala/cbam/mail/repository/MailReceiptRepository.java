package com.skala.cbam.mail.repository;

import com.skala.cbam.mail.domain.MailReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 접수 메일 저장소 (API 명세 №15~№18). */
public interface MailReceiptRepository extends JpaRepository<MailReceipt, Long>, JpaSpecificationExecutor<MailReceipt> {
}
