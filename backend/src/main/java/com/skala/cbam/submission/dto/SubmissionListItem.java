package com.skala.cbam.submission.dto;

import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.SubmissionStatus;
import java.time.OffsetDateTime;

/**
 * GET /api/v1/submissions 의 content[] 항목 (API 명세 20행, 29번).
 *
 * <p>id 가 null 이면 미제출 가상 행이다 — 제출 데이터 행 자체가 없다는 뜻(명세 20행 비고).
 * unregisteredPartCount 는 미제출 행엔 의미가 없어 null 이 된다 — 공통 규약 9항대로 키는 생략하지 않는다.
 */
public record SubmissionListItem(
        Long id,
        Target target,
        String supplierName,
        String partName,
        OffsetDateTime submittedAt,
        SubmissionStatus status,
        Judgement judgement,
        Severity severity,
        Long unregisteredPartCount
) {
    public record Target(Long supplierId, Long partId, String reportingMonth) {
    }
}
