package com.skala.cbam.task.controller;

import com.skala.cbam.task.dto.TaskDetailResponse;
import com.skala.cbam.task.service.TaskQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비동기 작업 상태 조회 API (API 명세 v10 №19, CBAM-86).
 *
 * <p>명세 31건 중 <b>유일한 설계 파생 API</b> 다. 202 를 반환하는 모든 API 가 준 {@code taskId} 를
 * 여기로 물어본다 — 분석(№18)·초안 생성(№26)·재생성(№28)·발송(№30).
 *
 * <p>조회라서 {@code X-Operator-Id} 를 읽지 않는다 — 응답에 「누가 했는지」 필드가 없다(ADR-0006).
 */
@Tag(name = "비동기 작업", description = "202 로 시작한 작업의 진행 상태와 결과를 조회한다")
@RestController
@RequiredArgsConstructor
public class TaskController extends TaskApiExceptionHandling {

    private final TaskQueryService taskQueryService;

    @Operation(
            summary = "비동기 작업 상태 조회",
            description = "작업의 진행 상태와 만들어진 자원 id 를 반환한다. "
                    + "작업이 실패해도 200 이고 status=FAILED 로 알린다 — 404 는 그런 작업이 없을 때만이다.")
    @GetMapping("/api/v1/tasks/{taskId}")
    public ResponseEntity<TaskDetailResponse> getDetail(@PathVariable String taskId) {
        return ResponseEntity.ok(taskQueryService.getDetail(taskId));
    }
}
