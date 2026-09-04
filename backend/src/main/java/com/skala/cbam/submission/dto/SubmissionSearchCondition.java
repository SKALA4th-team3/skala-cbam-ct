package com.skala.cbam.submission.dto;

import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** GET /api/v1/submissions 의 필터 조건 (29번). */
public record SubmissionSearchCondition(
        Long supplierId,
        Long partId,
        LocalDate reportingMonth,
        SubmissionStatus status,
        Judgement judgement,
        Severity severity,
        OffsetDateTime submittedFrom,
        OffsetDateTime submittedTo
) {
}
