package com.skala.cbam.submission.domain;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 미등록 부품 (요구사항 25·27·28번). 데이터 확정(31번)이 이걸 막는 조건으로 쓴다 —
 * "판정이 적격이고 미등록 부품이 없는 경우에만 확정 가능."
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "unregistered_part",
        indexes = {
                @Index(name = "ix_unregistered_part_submission_status", columnList = "submission_id, status")
        }
)
public class UnregisteredPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "raw_part_name", nullable = false, length = 500)
    private String rawPartName;

    /** 등록 후 연결된 부품×협력업체 조합. 부품 도메인이 아직 dev 에 없어 값만 보존한다. */
    @Column(name = "mapped_part_supplier_id")
    private Long mappedPartSupplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UnregisteredPartStatus status;

    @Builder
    private UnregisteredPart(Submission submission, String rawPartName, Long mappedPartSupplierId,
                             UnregisteredPartStatus status) {
        this.submission = submission;
        this.rawPartName = rawPartName;
        this.mappedPartSupplierId = mappedPartSupplierId;
        this.status = status;
    }
}
