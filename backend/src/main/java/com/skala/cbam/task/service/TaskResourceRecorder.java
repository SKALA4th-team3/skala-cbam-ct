package com.skala.cbam.task.service;

import com.skala.cbam.task.domain.TaskResource;
import com.skala.cbam.task.domain.TaskResourceType;
import com.skala.cbam.task.repository.TaskResourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작업이 만든 자원을 기록한다 (ADR-0011).
 *
 * <p><b>다른 도메인이 부르는 입구다.</b> 202 를 반환하는 작업을 끝낸 쪽에서 「무엇을 만들었는지」를
 * 여기에 남기면, №19 조회가 그것을 {@code resourceIds} 로 내보낸다. 이 호출을 빠뜨리면 화면은
 * 방금 만든 것을 찾지 못한다 — PR #31 리뷰에서 실제로 겪은 문제다.
 *
 * <p>호출하는 쪽의 트랜잭션에 참여한다({@code REQUIRED} 기본값) — 작업이 롤백되면 이 기록도 같이
 * 사라져야 한다. 만들지 않은 자원을 가리키는 행이 남으면 №19 가 없는 id 를 알려 준다.
 */
@Service
@RequiredArgsConstructor
public class TaskResourceRecorder {

    private final TaskResourceRepository taskResourceRepository;

    /** 작업 하나가 만든 자원들을 순서대로 기록한다. 목록이 비면 아무것도 하지 않는다. */
    @Transactional
    public void record(String taskId, TaskResourceType type, List<Long> resourceIds) {
        List<TaskResource> rows = TaskResource.of(taskId, type, resourceIds);
        if (!rows.isEmpty()) {
            taskResourceRepository.saveAll(rows);
        }
    }

    /** 자원 하나를 만든 작업(재생성·발송 등). */
    @Transactional
    public void record(String taskId, TaskResourceType type, Long resourceId) {
        record(taskId, type, resourceId == null ? List.of() : List.of(resourceId));
    }
}
