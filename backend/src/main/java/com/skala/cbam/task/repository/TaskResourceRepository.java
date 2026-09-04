package com.skala.cbam.task.repository;

import com.skala.cbam.task.domain.TaskResource;
import com.skala.cbam.task.domain.TaskResourceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskResourceRepository extends JpaRepository<TaskResource, Long> {

    /** №19 조회 — 작업이 만든 자원을 기록 순서대로. */
    List<TaskResource> findByTaskIdOrderBySequenceNoAsc(String taskId);

    /** 역방향 — 이 자원을 만든 작업이 무엇인가. 재분석 이력 추적에 쓴다. */
    List<TaskResource> findByResourceTypeAndResourceIdOrderByIdDesc(
            TaskResourceType resourceType, Long resourceId);
}
