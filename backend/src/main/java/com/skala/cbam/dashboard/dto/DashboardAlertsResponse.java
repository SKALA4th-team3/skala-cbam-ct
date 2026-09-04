package com.skala.cbam.dashboard.dto;

import com.skala.cbam.submission.domain.Severity;

import java.util.List;

/**
 * GET /api/v1/dashboard/alerts 응답 — API 명세서 v10 25행 그대로 (39번).
 */
public record DashboardAlertsResponse(
        List<AlertItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record AlertItem(
            Long alertId,
            Long submissionId,
            Target target,
            String supplierName,
            String partName,
            String ruleId,
            Severity severity,
            String message,
            long dDay
    ) {}

    /** partId 는 미등록 부품 관련 경보 등 part_supplier 를 못 찾는 경우 비어 있을 수 있다 */
    public record Target(Long supplierId, Long partId, String reportingMonth) {}
}
