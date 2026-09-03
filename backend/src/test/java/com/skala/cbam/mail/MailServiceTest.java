package com.skala.cbam.mail;

import com.skala.cbam.mail.domain.Attachment;
import com.skala.cbam.mail.domain.AttachmentProcessStatus;
import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.domain.MailReceiptStatus;
import com.skala.cbam.mail.dto.AttachmentContent;
import com.skala.cbam.mail.dto.MailReceiptDetailResponse;
import com.skala.cbam.mail.dto.MailReceiptMatchResponse;
import com.skala.cbam.mail.dto.MailReceiptSearchCondition;
import com.skala.cbam.mail.error.MailErrorCode;
import com.skala.cbam.mail.error.MailException;
import com.skala.cbam.mail.repository.AttachmentRepository;
import com.skala.cbam.mail.repository.MailReceiptRepository;
import com.skala.cbam.mail.service.MailService;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.dto.PageResponse;
import com.skala.cbam.supplier.repository.SupplierRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CBAM-79 (18~21번) 이 실제 DB · 실제 파일로 되는 경우 · 막는 경우를 다 지키는지 확인한다.
 * @Transactional 로 각 테스트를 롤백한다(다른 테스트 클래스와의 픽스처 충돌 방지, CBAM-90 에서 배운 것).
 */
@SpringBootTest
@Transactional
class MailServiceTest {

    @Autowired
    private MailService mailService;
    @Autowired
    private MailReceiptRepository mailReceiptRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private SupplierRepository supplierRepository;

    private Supplier daehan;

    @BeforeEach
    void seed() {
        daehan = supplierRepository.save(Supplier.builder()
                .businessRegistrationNumber("333-33-00001")
                .name("대한금속")
                .countryCode("KR")
                .contactName("김철수")
                .contactEmail("mail-test-daehan@example.com")
                .contactPhone("02-3333-1111")
                .build());
    }

    private MailReceipt saveReceipt(String messageId, MailReceiptStatus status) {
        return mailReceiptRepository.save(MailReceipt.builder()
                .messageId(messageId)
                .senderEmail("kim@daehan.co.kr")
                .subject("2026년 9월 CBAM 자료 회신")
                .body("본문")
                .status(status)
                .receivedAt(OffsetDateTime.now())
                .build());
    }

    @Test
    void 목록_조회는_협력업체명과_첨부개수를_함께_반환한다() {
        MailReceipt receipt = saveReceipt("<msg-1@mail.com>", MailReceiptStatus.WAITING);
        attachmentRepository.save(Attachment.builder()
                .mailReceipt(receipt).originalFilename("cbam.xlsx").storageUri("/tmp/nope")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .sizeBytes(1024L).processStatus(AttachmentProcessStatus.EXTRACTED).build());

        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<?> result = mailService.listReceipts(
                new MailReceiptSearchCondition(null, null, null, null), pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void 상세조회는_첨부의_열람_다운로드_URL을_함께_반환한다() {
        MailReceipt receipt = saveReceipt("<msg-2@mail.com>", MailReceiptStatus.ANALYSIS_FAILED);
        Attachment attachment = attachmentRepository.save(Attachment.builder()
                .mailReceipt(receipt).originalFilename("cbam.xlsx").storageUri("/tmp/nope")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .sizeBytes(1024L).processStatus(AttachmentProcessStatus.EXTRACTED).build());

        MailReceiptDetailResponse detail = mailService.getDetail(receipt.getId());

        assertThat(detail.status()).isEqualTo(MailReceiptStatus.ANALYSIS_FAILED);
        assertThat(detail.attachments()).hasSize(1);
        MailReceiptDetailResponse.AttachmentItem item = detail.attachments().get(0);
        assertThat(item.fileType()).isEqualTo("xlsx");
        assertThat(item.viewUrl()).isEqualTo("/api/v1/attachments/" + attachment.getId() + "?disposition=inline");
        assertThat(item.downloadUrl()).isEqualTo("/api/v1/attachments/" + attachment.getId() + "?disposition=attachment");
    }

    @Test
    void 상세조회는_없는_접수건이면_막힌다() {
        assertThatThrownBy(() -> mailService.getDetail(999_999L))
                .isInstanceOf(MailException.class)
                .satisfies(e -> assertThat(((MailException) e).errorCode())
                        .isEqualTo(MailErrorCode.MAIL_RECEIPT_NOT_FOUND));
    }

    @Test
    void 첨부_다운로드는_실제_파일을_스트리밍한다() throws IOException {
        Path tempFile = Files.createTempFile("cbam-test", ".csv");
        Files.writeString(tempFile, "part,production\n열연강판,1250\n");
        MailReceipt receipt = saveReceipt("<msg-3@mail.com>", MailReceiptStatus.WAITING);
        Attachment attachment = attachmentRepository.save(Attachment.builder()
                .mailReceipt(receipt).originalFilename("cbam.csv").storageUri(tempFile.toString())
                .mimeType("text/csv").sizeBytes(Files.size(tempFile))
                .processStatus(AttachmentProcessStatus.EXTRACTED).build());

        AttachmentContent content = mailService.getAttachmentContent(attachment.getId());

        assertThat(content.mimeType()).isEqualTo("text/csv");
        assertThat(content.originalFilename()).isEqualTo("cbam.csv");
        assertThat(content.resource().exists()).isTrue();
        Files.deleteIfExists(tempFile);
    }

    @Test
    void 첨부_다운로드는_없는_첨부면_막힌다() {
        assertThatThrownBy(() -> mailService.getAttachmentContent(999_999L))
                .isInstanceOf(MailException.class)
                .satisfies(e -> assertThat(((MailException) e).errorCode())
                        .isEqualTo(MailErrorCode.ATTACHMENT_NOT_FOUND));
    }

    @Test
    void 수동_매칭은_미확인_건을_활성_업체와_연결한다() {
        MailReceipt receipt = saveReceipt("<msg-4@mail.com>", MailReceiptStatus.UNMATCHED);

        MailReceiptMatchResponse response = mailService.match(receipt.getId(), daehan.getId(), "demo");

        assertThat(response.status()).isEqualTo(MailReceiptStatus.MATCHED);
        assertThat(response.supplierId()).isEqualTo(daehan.getId());
        assertThat(response.linkedBy()).isEqualTo("demo");
        assertThat(response.analyzeTaskId()).isNull();
    }

    @Test
    void 수동_매칭은_이미_매칭된_건이면_막힌다() {
        MailReceipt receipt = saveReceipt("<msg-5@mail.com>", MailReceiptStatus.UNMATCHED);
        mailService.match(receipt.getId(), daehan.getId(), "demo");

        assertThatThrownBy(() -> mailService.match(receipt.getId(), daehan.getId(), "demo"))
                .isInstanceOf(MailException.class)
                .satisfies(e -> assertThat(((MailException) e).errorCode())
                        .isEqualTo(MailErrorCode.ALREADY_MATCHED));
    }

    @Test
    void 수동_매칭은_협력끊김_업체면_막힌다() {
        daehan.deactivate("거래 종료");
        supplierRepository.save(daehan);
        MailReceipt receipt = saveReceipt("<msg-6@mail.com>", MailReceiptStatus.UNMATCHED);

        assertThatThrownBy(() -> mailService.match(receipt.getId(), daehan.getId(), "demo"))
                .isInstanceOf(MailException.class)
                .satisfies(e -> assertThat(((MailException) e).errorCode())
                        .isEqualTo(MailErrorCode.INACTIVE_SUPPLIER));
    }

    @Test
    void 수동_매칭은_없는_업체면_막힌다() {
        MailReceipt receipt = saveReceipt("<msg-7@mail.com>", MailReceiptStatus.UNMATCHED);

        assertThatThrownBy(() -> mailService.match(receipt.getId(), 999_999L, "demo"))
                .isInstanceOf(MailException.class)
                .satisfies(e -> assertThat(((MailException) e).errorCode())
                        .isEqualTo(MailErrorCode.SUPPLIER_NOT_FOUND));
    }
}
