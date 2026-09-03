package com.skala.cbam.dashboard.entity;

import com.skala.cbam.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD supplier 테이블의 대시보드 조회용 읽기 전용 투영(projection).
 *
 * 주의: 협력업체 등록/수정(1~4번)을 맡은 사람이 supplier 패키지에 "진짜" Supplier 엔티티를
 * 따로 만들 수 있다. 그러면 같은 테이블을 가리키는 엔티티가 두 개가 되어 합칠 때 정리가 필요하다.
 * (CLAUDE.md 대로 팀에 슬랙으로 미리 알렸음 — 확인 필요)
 */
@Entity
@Table(name = "supplier")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Supplier extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LifecycleStatus status;
}
