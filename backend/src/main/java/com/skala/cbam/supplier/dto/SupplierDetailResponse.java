package com.skala.cbam.supplier.dto;

import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 협력업체 상세 조회 응답 (API 명세 №4 · 요구사항 5번).
 *
 * <p>필드 순서와 이름은 명세의 성공 응답 예시를 그대로 따른다.
 * 공통 규약 9항에 따라 값이 없어도 키를 생략하지 않는다 — Jackson 기본 동작(null 포함)에 기대므로
 * 이 클래스에 @JsonInclude(NON_NULL) 을 붙이지 않는다.
 *
 * <p>parts · submissions · alerts · feedbackHistories 의 코드값(status · judgement · severity · type)을
 * enum 이 아니라 String 으로 둔 이유: 이 값들의 enum 은 부품 · 제출 · 경보 · 피드백 도메인 소유다.
 * 아직 없는 남의 enum 을 여기서 선점하지 않는다.
 */
public record SupplierDetailResponse(
        Long id,
        String companyName,
        String businessRegistrationNumber,
        String country,
        String contactName,
        String contactEmail,
        String phone,
        SupplierStatus status,
        List<PartSummary> parts,
        List<SubmissionSummary> submissions,
        List<AlertSummary> alerts,
        List<FeedbackHistorySummary> feedbackHistories
) {

    public static SupplierDetailResponse of(
            Supplier supplier,
            List<PartSummary> parts,
            List<SubmissionSummary> submissions,
            List<AlertSummary> alerts,
            List<FeedbackHistorySummary> feedbackHistories) {
        return new SupplierDetailResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getBusinessRegistrationNumber(),
                supplier.getCountryCode(),
                supplier.getContactName(),
                supplier.getContactEmail(),
                supplier.getContactPhone(),
                supplier.getStatus(),
                parts,
                submissions,
                alerts,
                feedbackHistories
        );
    }

    /** 공급 부품. */
    public record PartSummary(
            Long partId,
            String partCode,
            String partName,
            String cnCode
    ) {
    }

    /** 최근 N개월 제출 이력. reportingMonth 는 YYYY-MM(공통 규약 5항). */
    public record SubmissionSummary(
            Long id,
            Long partId,
            String reportingMonth,
            String status,
            String judgement
    ) {
    }

    /** 수신 경보. ruleId 는 R1~R7(코드·Enum 정의 시트). */
    public record AlertSummary(
            Long alertId,
            String ruleId,
            String severity,
            String message
    ) {
    }

    /** 피드백 발송 이력. sentAt 은 ISO-8601 오프셋 포함(공통 규약 5항). */
    public record FeedbackHistorySummary(
            Long draftId,
            String type,
            OffsetDateTime sentAt,
            String status
    ) {
    }
}
