package com.skala.cbam.feedback.domain;

import com.skala.cbam.common.domain.BaseTimeEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피드백 초안 버전. API 명세 №27~№29, 요구사항 44·45·46번. ERD 그대로 버전 이력을 보존한다
 * (ADR-0010 재검토 — 없애지 않기로 함).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "feedback_draft",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_feedback_draft_feedback_version",
                        columnNames = {"feedback_id", "version_number"})
        },
        indexes = {
                @Index(name = "ix_feedback_draft_feedback", columnList = "feedback_id")
        }
)
public class FeedbackDraft extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @Column(name = "version_number", nullable = false)
    private Short versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private DraftSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "style", nullable = false, length = 20)
    private FeedbackStyle style;

    @Column(name = "instruction", columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "fallback_applied", nullable = false)
    private boolean fallbackApplied;

    @Column(name = "fallback_template_id", length = 100)
    private String fallbackTemplateId;

    @Column(name = "edited_by", length = 100)
    private String editedBy;

    @Column(name = "edited_at")
    private OffsetDateTime editedAt;

    @Builder
    private FeedbackDraft(Feedback feedback, Short versionNumber, DraftSourceType sourceType,
                          FeedbackStyle style, String instruction, String subject, String body,
                          boolean fallbackApplied, String fallbackTemplateId) {
        this.feedback = feedback;
        this.versionNumber = versionNumber;
        this.sourceType = sourceType;
        this.style = style;
        this.instruction = instruction;
        this.subject = subject;
        this.body = body;
        this.fallbackApplied = fallbackApplied;
        this.fallbackTemplateId = fallbackTemplateId;
    }
}
