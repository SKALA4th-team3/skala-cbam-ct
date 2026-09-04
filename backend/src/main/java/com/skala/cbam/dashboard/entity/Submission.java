package com.skala.cbam.dashboard.entity;

import com.skala.cbam.supplier.domain.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERD submission 테이블의 대시보드(38·39·40번) 집계용 읽기 전용 투영.
 * 실제 제출 데이터 작성/조회(20~23번) 담당이 submission 패키지에 전체 필드를 가진
 * 정식 엔티티를 만들 것이다 — 합칠 때 정리 필요 (슬랙 공지함).
 *
 * <p>supplier 는 협력업체 도메인의 정식 엔티티를 참조한다 — PartSupplier 주석 참고.
 *
 * <p><b>created_at · updated_at 을 일부러 매핑하지 않는다.</b> 대시보드는 두 값을 한 군데도
 * 읽지 않는데, 매핑해 두면 같은 테이블을 보는 다른 도메인의 엔티티와 논리 컬럼명이 갈려
 * DuplicateMappingException 으로 <b>부팅 자체가 막힌다</b>
 * (Table [part] ... referred to by multiple logical column names: [createdAt], [created_at]).
 * 안 쓰는 컬럼까지 소유권을 주장하지 않는다.
 *
 * status(처리 상태) 와 judgement(판정 결과) 는 다른 축이다 — API 명세 20행 비고 참고.
 */
@Entity
@Table(name = "submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** 미등록 부품 제출 건은 비어 있을 수 있다 (ERD 규칙 8) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_supplier_id")
    private PartSupplier partSupplier;

    /** 보고 대상 월의 첫날 */
    @Column(name = "reporting_month", nullable = false)
    private LocalDate reportingMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "judgement", length = 20)
    private JudgementStatus judgement;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10)
    private SeverityCode severity;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
