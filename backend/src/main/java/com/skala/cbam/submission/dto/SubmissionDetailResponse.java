package com.skala.cbam.submission.dto;

import com.skala.cbam.submission.domain.EligibilityStatus;
import com.skala.cbam.submission.domain.EmissionScope;
import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.domain.ValidationOutcome;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/submissions/{id} 응답 (API 명세 21행, 30번).
 *
 * <p>목록(20행)과 달리 supplierId·partId 가 최상위에 평평하게 있다 — target 으로 묶지 않는다.
 * activityData 의 키(partName·production·electricity·fuel_lng 등)는 extraction_field.field_code
 * 값을 그대로 쓴다. fuel_lng 처럼 스네이크케이스인 것도 있는데 오타 아니고 명세 그대로다.
 */
public record SubmissionDetailResponse(
        Long id,
        Long supplierId,
        Long partId,
        Long mailReceiptId,
        String reportingMonth,
        SubmissionStatus status,
        Judgement judgement,
        String documentType,
        EligibilityStatus eligibilityStatus,
        Map<String, ActivityDataItem> activityData,
        CalculatedEmission calculatedEmission,
        Validation validation,
        List<UnregisteredPartItem> unregisteredParts,
        List<AttachmentItem> attachments
) {

    public record ActivityDataItem(
            Object value,
            String unit,
            String rawValue,
            EmissionScope emissionScope,
            String conversionFailReason,
            Source source
    ) {
    }

    /** 공통 규약 10항의 locator 문법. attachmentId 는 메일 본문에서 추출한 경우 null 이다. */
    public record Source(Long attachmentId, String locator) {
    }

    public record CalculatedEmission(
            BigDecimal directEmission,
            BigDecimal indirectEmission,
            String unit,
            BigDecimal emissionIntensity,
            BigDecimal defaultValueRatio
    ) {
    }

    public record Validation(
            Judgement judgement,
            Severity severity,
            OffsetDateTime validatedAt,
            List<RuleResult> rules
    ) {
    }

    /** alert 테이블을 ruleId 로 묶은 것. checks[] 는 같은 ruleId 안의 check_id 들이다. */
    public record RuleResult(String ruleId, ValidationOutcome result, Severity severity, List<CheckResult> checks) {
    }

    public record CheckResult(String checkId, ValidationOutcome result) {
    }

    public record UnregisteredPartItem(Long id, String rawPartName) {
    }

    /** extraction_field 가 참조하는 첨부 id 로 만든다 — 별도 attachment 조회 없이. */
    public record AttachmentItem(Long id, String viewUrl) {
    }
}
