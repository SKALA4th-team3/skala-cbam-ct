package com.skala.cbam.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 접수 메일의 원본 첨부파일. API 명세 №17(열람·다운로드), 요구사항 20·30번.
 *
 * <p>submission 이 아니라 mail_receipt 에 딸린다(ERD 관계) — 제출 데이터 상세(30번, CBAM-90)가
 * 이 테이블 대신 extraction_field 를 우회해서 쓴 이유가 이 클래스가 없었기 때문이었다.
 * 이제 이 클래스가 생겼으니 CBAM-90 쪽 attachments 조회를 mail_receipt_id 기준으로 바꿔야 한다
 * (지금 이 PR 범위는 아님 — 별도로 정리).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "attachment",
        indexes = {
                @Index(name = "ix_attachment_mail_receipt", columnList = "mail_receipt_id"),
                @Index(name = "ix_attachment_process_status", columnList = "process_status")
        }
)
public class Attachment {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mail_receipt_id", nullable = false)
    private MailReceipt mailReceipt;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    /** 원본 파일 저장 위치. 로컬 파일시스템 경로 또는 향후 오브젝트 스토리지 URI. */
    @Column(name = "storage_uri", nullable = false, length = 1000)
    private String storageUri;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_status", nullable = false, length = 20)
    private AttachmentProcessStatus processStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Attachment(MailReceipt mailReceipt, String originalFilename, String storageUri,
                       String mimeType, Long sizeBytes, AttachmentProcessStatus processStatus) {
        this.mailReceipt = mailReceipt;
        this.originalFilename = originalFilename;
        this.storageUri = storageUri;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.processStatus = processStatus == null ? AttachmentProcessStatus.PENDING : processStatus;
    }

    @PrePersist
    void onPrePersist() {
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = now();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.SECONDS);
    }
}
