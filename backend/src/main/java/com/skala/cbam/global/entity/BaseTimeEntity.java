package com.skala.cbam.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * ERD의 모든 테이블이 공통으로 갖는 created_at · updated_at 을 자동 관리한다.
 * (ADR 대상: 이 클래스는 dashboard 작업 중 처음 생기는 공통 엔티티라 global 패키지에 둔다.
 *  다른 도메인 엔티티도 이걸 상속해서 쓰면 된다.)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
