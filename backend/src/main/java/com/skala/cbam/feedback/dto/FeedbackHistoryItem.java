package com.skala.cbam.feedback.dto;

import com.skala.cbam.common.domain.DeliveryStatus;
import com.skala.cbam.feedback.domain.FeedbackType;
import com.skala.cbam.feedback.domain.ResendReason;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET .../feedback-histories 의 content[] 항목 (31행 + ADR-0008, 51·53번).
 *
 * <p>status 는 이 건의 검토 상태(공용 FeedbackStatus)가 아니라 <b>발송 결과</b>다 — 공용
 * DeliveryStatus 설계(발송 결과는 별도 관리)를 따라 최신 발송 시도의 결과를 담는다. 아직 한 번도
 * 발송을 시도하지 않았으면 PENDING이다.
 *
 * <p>replyStatus·replyDetectedAt·replyMailReceiptId 는 항상 NO_REPLY/null 이다 — 회신 감지가
 * 메일 수신 스케줄러(18번, 아무도 안 만듦) 소관인데 아직 없다. 모르는 값은 채우지 않는다.
 */
public record FeedbackHistoryItem(
        Long draftId,
        Long submissionId,
        FeedbackType type,
        String subject,
        DeliveryStatus status,
        String replyStatus,
        OffsetDateTime sentAt,
        OffsetDateTime replyDetectedAt,
        Long replyMailReceiptId,
        OffsetDateTime resendableFrom,
        List<DeliveryItem> deliveries
) {
    public record DeliveryItem(
            int attempt,
            OffsetDateTime sentAt,
            DeliveryStatus status,
            String failureCode,
            ResendReason resendReason
    ) {
    }
}
