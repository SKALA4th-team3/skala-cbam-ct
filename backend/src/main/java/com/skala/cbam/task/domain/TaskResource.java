package com.skala.cbam.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비동기 작업이 만들어 낸 자원을 가리킨다 (API 명세 v10 №19 의 {@code resourceType}·{@code resourceIds}).
 *
 * <p><b>왜 별도 테이블인가</b> — ADR-0011. {@code task} 는 {@code feedback_id} 를 <b>단수</b> FK 로만
 * 갖는데, 43번 일괄 생성은 작업 하나가 초안 <b>N 개</b>를 만든다. 단수 컬럼으로는 못 담는다.
 * 도메인이 붙을 때마다 {@code task} 에 대부분 null 인 컬럼을 늘리지 않으려는 이유도 있다.
 *
 * <p><b>FK 무결성을 DB 가 지키지 않는다.</b> {@code resourceId} 가 어느 테이블을 가리키는지는
 * {@code resourceType} 이 정한다 — 기록하는 쪽이 맞는 id 를 넣어야 한다. ADR-0011 에서
 * 감수하기로 한 값이다.
 *
 * <p>{@code task} 와 달리 {@code created_at} 만 둔다. 이 행은 만들어진 뒤 바뀌지 않는다 —
 * 작업이 무엇을 만들었는지는 사실이고 나중에 정정되지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "task_resource",
        uniqueConstraints = {
                // 같은 작업이 같은 자원을 두 번 가리키지 않는다
                @UniqueConstraint(
                        name = "uk_task_resource_task_type_resource",
                        columnNames = {"task_id", "resource_type", "resource_id"})
        },
        indexes = {
                @Index(name = "ix_task_resource_task_seq", columnList = "task_id, sequence_no"),
                // 「이 제출 건을 만든 작업이 무엇인가」 역방향 조회
                @Index(name = "ix_task_resource_type_resource", columnList = "resource_type, resource_id")
        }
)
public class TaskResource {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * {@code task.id} 를 값으로만 갖는다 — {@code @ManyToOne} 을 쓰지 않는 이유는
     * {@code Task} 가 {@code feedback} 패키지에 있어서다(ADR-0011 ①). 연관을 걸면
     * 두 패키지가 양방향으로 묶여 나중에 {@code Task} 를 옮기기가 더 어려워진다.
     */
    @Column(name = "task_id", nullable = false, length = 50)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private TaskResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /** 명세 №19 의 {@code resourceIds} 순서. 43번 일괄에서 대상 순서를 보존한다. */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private TaskResource(String taskId, TaskResourceType resourceType, Long resourceId, int sequenceNo) {
        this.taskId = taskId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.sequenceNo = sequenceNo;
        this.createdAt = OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.SECONDS);
    }

    /** 작업 하나가 만든 자원 목록을 순서대로 기록한다. 빈 목록이면 빈 결과 — 빈 행을 만들지 않는다. */
    public static List<TaskResource> of(String taskId, TaskResourceType type, List<Long> resourceIds) {
        if (taskId == null || type == null || resourceIds == null) {
            return List.of();
        }
        List<TaskResource> rows = new java.util.ArrayList<>(resourceIds.size());
        int sequence = 0;
        for (Long resourceId : resourceIds) {
            if (resourceId == null) {
                // 아직 저장되지 않아 id 가 없는 것을 0 이나 -1 로 채우지 않는다 (24번과 같은 규칙)
                continue;
            }
            rows.add(new TaskResource(taskId, type, resourceId, sequence++));
        }
        return List.copyOf(rows);
    }
}
