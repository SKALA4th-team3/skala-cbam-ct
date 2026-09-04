package com.skala.cbam.task.service;

import com.skala.cbam.feedback.domain.Task;
import com.skala.cbam.feedback.domain.TaskType;
import com.skala.cbam.feedback.repository.TaskRepository;
import com.skala.cbam.task.domain.TaskResourceType;
import com.skala.cbam.task.dto.TaskDetailResponse;
import com.skala.cbam.task.error.TaskErrorCode;
import com.skala.cbam.task.error.TaskException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * №19 {@code GET /api/v1/tasks/{taskId}} — 비동기 작업 상태 조회 (CBAM-86).
 *
 * <p>{@code Task} 엔티티는 {@code feedback} 패키지에 있다. 여기로 옮기지 않은 이유는 ADR-0012 —
 * {@code Task} 가 {@code Feedback} 을 참조하고 있어 옮겨도 의존 방향이 그대로 따라온다.
 *
 * <p><b>자원을 찾는 길이 둘이다.</b> 새 작업은 {@code resource_type}·{@code resource_ids} 를 채우고,
 * CBAM-88 이 먼저 만든 발송·재생성 작업은 {@code task} 의 FK 만 갖고 있다. 이 서비스가 그 둘을
 * 한 모양으로 합쳐 내보낸다 — 화면은 어느 쪽으로 저장됐는지 알 필요가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryService {

    private final TaskRepository taskRepository;

    public TaskDetailResponse getDetail(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskException(TaskErrorCode.TASK_NOT_FOUND));

        List<Long> resourceIds = task.getResourceIds() == null || task.getResourceIds().isEmpty()
                ? fallbackResourceIds(task)
                : task.getResourceIds();

        TaskResourceType resourceType = task.getResourceType() != null
                ? task.getResourceType()
                : fallbackResourceType(task);

        // 가리키는 것이 하나도 없으면 종류도 말하지 않는다 — 빈 배열에 종류만 붙어 있으면
        // 화면이 "만들어졌는데 id 를 못 받았다"로 오해한다
        if (resourceIds.isEmpty()) {
            resourceType = null;
        }

        return new TaskDetailResponse(
                task.getId(),
                task.getType(),
                task.getStatus(),
                resourceType,
                resourceIds,
                new TaskDetailResponse.Progress(
                        task.getProgressTotal(), task.getProgressDone(), task.getProgressFailed()),
                task.isFallbackApplied(),
                task.getUnregisteredPartCount(),
                task.getErrorCode(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getCompletedAt());
    }

    /** CBAM-88 이 resource_* 없이 만든 작업 — 타입이 곧 자원 종류다. */
    private TaskResourceType fallbackResourceType(Task task) {
        return switch (task.getType()) {
            case REGENERATE_FEEDBACK_DRAFT -> TaskResourceType.FEEDBACK_DRAFT;
            case GENERATE_FEEDBACK_DRAFT, SEND_FEEDBACK, SEND_REMINDER -> TaskResourceType.FEEDBACK;
            case ANALYZE_MAIL_RECEIPT, REVALIDATE_SUBMISSION -> TaskResourceType.SUBMISSION;
        };
    }

    /**
     * FK 에서 자원 id 를 끌어낸다. 재생성은 만든 초안을, 나머지 피드백 계열은 피드백 건을 가리킨다.
     *
     * <p>일괄 생성(43번)은 여기서 빈 목록이 된다 — 단수 FK 로 N 개를 담을 수 없어서다.
     * 그것이 {@code resource_ids} 가 ERD 에 있는 이유이고, 새로 만드는 작업은 그쪽을 채운다.
     */
    private List<Long> fallbackResourceIds(Task task) {
        if (task.getType() == TaskType.REGENERATE_FEEDBACK_DRAFT) {
            return task.getFeedbackDraft() == null ? List.of() : List.of(task.getFeedbackDraft().getId());
        }
        return task.getFeedback() == null ? List.of() : List.of(task.getFeedback().getId());
    }
}
