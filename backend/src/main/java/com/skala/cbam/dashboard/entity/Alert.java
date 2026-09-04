package com.skala.cbam.dashboard.entity;

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
 * ERD alert 테이블. 39번(경보 조회)의 원천. 검사 하나(rule_id + check_id)당 한 행.
 *
 * unregistered_part_id FK는 39번 응답(target: supplierId·partId·reportingMonth)에
 * 안 쓰여서 지금은 매핑하지 않았다 — 미등록 부품 관련 경보의 target.partId는 비게 된다.
 * (모르는 값을 채우지 않고 비워두는 쪽 — CLAUDE.md 원칙)
 *
 * <p><b>created_at · updated_at 을 일부러 매핑하지 않는다.</b> 대시보드는 두 값을 한 군데도
 * 읽지 않는데, 매핑해 두면 같은 테이블을 보는 다른 도메인의 엔티티와 논리 컬럼명이 갈려
 * DuplicateMappingException 으로 <b>부팅 자체가 막힌다</b>
 * (Table [part] ... referred to by multiple logical column names: [createdAt], [created_at]).
 * 안 쓰는 컬럼까지 소유권을 주장하지 않는다.
 */
@Entity
@Table(name = "alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 미제출(R1) 경보는 이게 필수, 판정 경보는 submission을 통해서만 있을 수도 있다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_supplier_id")
    private PartSupplier partSupplier;

    /** 판정 경보 대상 제출. 미제출 경보면 비어 있다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(name = "reporting_month", nullable = false)
    private LocalDate reportingMonth;

    /** R1 ~ R7 */
    @Column(name = "rule_id", nullable = false, length = 10)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private SeverityCode severity;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "validated_at", nullable = false)
    private LocalDateTime validatedAt;
}
