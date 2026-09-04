package com.skala.cbam.feedback.dto;

import com.skala.cbam.common.domain.FeedbackStatus;
import com.skala.cbam.feedback.domain.FeedbackType;
import java.time.OffsetDateTime;

/**
 * 발송 이력 조회 필터 (31행 + ADR-0008). supplierId 는 협력업체별 조회(№31)에서만 채워지고,
 * 전사 조회(ADR-0008, GET /feedback-histories)에서는 null 일 수 있다.
 *
 * <p>이 status 는 검토 상태(DRAFT/REVISED/READY_TO_SEND/DISCARDED) 기준 필터다 —
 * {@link com.skala.cbam.feedback.dto.FeedbackHistoryItem#status()}(발송 결과, DeliveryStatus)와는
 * 다른 값이다. 발송 결과 기준 필터링은 이번 스코프에 없다 — 모르는/안 만든 것은 채우지 않는다.
 */
public record FeedbackHistorySearchCondition(
        Long supplierId,
        FeedbackType type,
        FeedbackStatus status,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
