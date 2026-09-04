package com.skala.cbam.mail.repository;

import com.skala.cbam.mail.domain.Attachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByMailReceiptId(Long mailReceiptId);

    long countByMailReceiptId(Long mailReceiptId);
}
