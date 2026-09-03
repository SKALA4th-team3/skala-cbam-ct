package com.skala.cbam.dashboard.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/dashboard 응답 — API 명세서 v10 24행 그대로 (38 + 40번).
 * counts/ratio 는 "target"(ACTIVE part_supplier) 단위 집계다 — 13행(마감 일정 조회)의
 * targets 숫자와 같은 모수를 쓴다. suppliers[] 는 협력업체 단위 요약이라 모수가 다르다.
 */
public record DashboardResponse(
        String month,
        LocalDate deadlineAt,
        long dDay,
        Counts counts,
        Ratio ratio,
        Map<String, Long> severity,
        List<SupplierSummary> suppliers
) {
    public record Counts(long qualified, long unqualified, long notSubmitted, long total) {}

    public record Ratio(double qualified, double unqualified, double notSubmitted) {}

    public record SupplierSummary(
            Long supplierId,
            String companyName,
            DashboardStatus status,
            long submissionCount,
            long pendingCount
    ) {}
}
