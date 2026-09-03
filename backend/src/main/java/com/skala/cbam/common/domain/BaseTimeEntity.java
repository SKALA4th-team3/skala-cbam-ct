package com.skala.cbam.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

/**
 * 엔티티의 생성일과 수정일을 공통으로 관리하는 기반 클래스.
 *
 * <p>참고: {@code Supplier}는 자체 생성일·수정일 필드와 생명주기 콜백을 사용하고 있어
 * 현재 이 클래스를 상속하지 않는다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onPrePersist() {
        createdAt = now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = now();
    }

    protected static OffsetDateTime now() {
        return OffsetDateTime.now(SEOUL)
                .truncatedTo(ChronoUnit.SECONDS);
    }
}
