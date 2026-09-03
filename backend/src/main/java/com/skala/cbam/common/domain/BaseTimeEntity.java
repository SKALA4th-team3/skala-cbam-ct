package com.skala.cbam.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

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