package com.skala.cbam.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.skala.cbam.common.domain.TaskStatus;
import com.skala.cbam.feedback.domain.TaskType;
import com.skala.cbam.task.domain.TaskResourceType;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * API 명세 v10 №19 {@code GET /api/v1/tasks/{taskId}} 응답.
 *
 * <p>명세 31건 중 <b>유일한 설계 파생 API</b> 이고, 202 를 반환하는 모든 API(№18 자동 분석 ·
 * №26 초안 생성 · №28 재생성 · №30 발송)의 결과를 확인하는 단 하나의 창구다.
 *
 * <p><b>작업이 실패해도 200 이다</b> — 명세 그대로. 「조회 자체는 성공했고 그 작업이 실패했다」를
 * {@code status: FAILED} 와 {@code errorCode} 로 말한다. 404 는 그런 작업이 없을 때만이다.
 */
public record TaskDetailResponse(

        String taskId,
        TaskType taskType,
        TaskStatus status,

        /**
         * 이 작업이 만든 자원의 종류. 아직 아무것도 만들지 못했으면 null —
         * 지어내지 않는다(요구사항 24번과 같은 규칙).
         */
        TaskResourceType resourceType,

        /** 만들어진 자원 id. 진행 중이거나 실패면 빈 배열이다. */
        List<Long> resourceIds,

        Progress progress,

        /** 46번 — AI 초안 대신 기본 템플릿으로 대체됐는가. */
        boolean fallbackApplied,

        /**
         * 25번 — 이 작업이 만든 제출 건에 남은 미등록 부품 수.
         * {@code ANALYZE_MAIL_RECEIPT} 에서만 0 이 아니다 (ADR-0012 ③ — 저장하지 않고 조회 때 센다).
         */
        int unregisteredPartCount,

        /** 실패 사유. 명세가 정한 값을 쓴다 — {@code AI_TIMEOUT} · {@code AI_ERROR} · {@code MAIL_GATEWAY_ERROR} 등. */
        String errorCode,
        String errorMessage,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime startedAt,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime completedAt
) {

    /** 일괄 작업(43번)의 진행률. 단건 작업은 total 이 1 이다. */
    public record Progress(int total, int done, int failed) {
    }
}
