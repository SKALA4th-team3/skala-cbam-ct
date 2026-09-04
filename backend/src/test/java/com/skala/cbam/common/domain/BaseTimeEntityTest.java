package com.skala.cbam.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("생성일·수정일 공통 엔티티")
class BaseTimeEntityTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("저장·수정 시 서울 기준 시각을 초 단위로 관리한다")
    void managesCreatedAtAndUpdatedAt() throws InterruptedException {
        BaseTimeTestEntity entity = new BaseTimeTestEntity("최초 값");

        entityManager.persist(entity);
        entityManager.flush();
        entityManager.clear();

        BaseTimeTestEntity saved = entityManager.find(BaseTimeTestEntity.class, entity.getId());
        OffsetDateTime createdAt = saved.getCreatedAt();
        OffsetDateTime firstUpdatedAt = saved.getUpdatedAt();

        assertNotNull(createdAt);
        assertEquals(createdAt, firstUpdatedAt);
        assertEquals(ZoneOffset.ofHours(9), createdAt.getOffset());
        assertEquals(0, createdAt.getNano());

        waitUntilNextSecond(firstUpdatedAt);
        saved.changeValue("수정 값");
        entityManager.flush();
        entityManager.clear();

        BaseTimeTestEntity updated = entityManager.find(BaseTimeTestEntity.class, entity.getId());

        assertEquals(createdAt, updated.getCreatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(firstUpdatedAt));
        assertEquals(ZoneOffset.ofHours(9), updated.getUpdatedAt().getOffset());
        assertEquals(0, updated.getUpdatedAt().getNano());
    }

    private void waitUntilNextSecond(OffsetDateTime previous) throws InterruptedException {
        while (!OffsetDateTime.now(previous.getOffset())
                .truncatedTo(ChronoUnit.SECONDS)
                .isAfter(previous)) {
            Thread.sleep(10);
        }
    }
}

@Entity
@Table(name = "base_time_test_entity")
class BaseTimeTestEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    protected BaseTimeTestEntity() {
    }

    BaseTimeTestEntity(String content) {
        this.content = content;
    }

    Long getId() {
        return id;
    }

    void changeValue(String content) {
        this.content = content;
    }
}
