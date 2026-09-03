package com.skala.cbam.submission.dto;

import com.skala.cbam.submission.domain.SubmissionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** POST /api/v1/submissions/{id}/confirm 응답 (API 명세 22행, 31번). */
public record SubmissionConfirmResponse(
        Long submissionId,
        SubmissionStatus status,
        String confirmedBy,
        OffsetDateTime confirmedAt,
        CalculatedEmission calculatedEmission
) {
    public record CalculatedEmission(
            BigDecimal directEmission,
            BigDecimal indirectEmission,
            String unit,
            BigDecimal emissionIntensity,
            Integer appliedFactorYear,
            boolean frozen
    ) {
    }
}
