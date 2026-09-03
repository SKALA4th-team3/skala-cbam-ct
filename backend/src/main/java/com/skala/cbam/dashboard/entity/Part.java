package com.skala.cbam.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * ERD part 테이블의 대시보드(경보 조회 partName 표시)용 읽기 전용 투영.
 * 부품 등록/수정(5~8번) 담당이 parts 패키지에 정식 엔티티를 만들면 합쳐야 한다.
 *
 * <p><b>created_at · updated_at 을 일부러 매핑하지 않는다.</b> 대시보드는 두 값을 한 군데도
 * 읽지 않는데, 매핑해 두면 같은 테이블을 보는 다른 도메인의 엔티티와 논리 컬럼명이 갈려
 * DuplicateMappingException 으로 <b>부팅 자체가 막힌다</b>
 * (Table [part] ... referred to by multiple logical column names: [createdAt], [created_at]).
 * 안 쓰는 컬럼까지 소유권을 주장하지 않는다.
 */
// 엔티티 이름을 클래스명(Part)과 다르게 준다. 부품 도메인(parts.entity.Part)이 같은 테이블을
// 자기 엔티티로 매핑하고 있어, 이름이 같으면 "share the entity name 'Part'" 로 부팅이 막힌다.
// 테이블은 같은 part 를 본다 — 소유권이 정해지면 이 투영을 지우고 그쪽 엔티티를 쓴다.
@Entity(name = "DashboardPart")
@Table(name = "part")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;
}
