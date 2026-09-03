package com.skala.cbam.dashboard.service;

import com.skala.cbam.dashboard.dto.DashboardAlertsResponse;
import com.skala.cbam.dashboard.dto.DashboardResponse;
import com.skala.cbam.dashboard.dto.DashboardStatus;
import com.skala.cbam.dashboard.entity.Alert;
import com.skala.cbam.dashboard.entity.AlertStatus;
import com.skala.cbam.dashboard.entity.LifecycleStatus;
import com.skala.cbam.dashboard.entity.PartSupplier;
import com.skala.cbam.dashboard.entity.SeverityCode;
import com.skala.cbam.dashboard.entity.Submission;
import com.skala.cbam.dashboard.entity.SubmissionStatus;
import com.skala.cbam.dashboard.repository.AlertRepository;
import com.skala.cbam.dashboard.repository.PartSupplierRepository;
import com.skala.cbam.dashboard.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 38·39·40번 (대시보드) 집계 로직.
 *
 * counts/ratio 는 "target"(ACTIVE part_supplier) 단위 모수를 쓴다 — API 명세 24행 응답 예시 숫자가
 * 13행(마감 일정 조회) 의 targets 예시 숫자와 정확히 같아서, suppliers(협력업체 수)가 아니라
 * targets(부품 × 협력업체 조합 수) 기준이라고 판단했다. 이 판단은 팀 확인 필요.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PartSupplierRepository partSupplierRepository;
    private final SubmissionRepository submissionRepository;
    private final AlertRepository alertRepository;

    public DashboardResponse getDashboard(YearMonth month, DashboardStatus statusFilter) {
        LocalDate monthStart = month.atDay(1);
        LocalDate deadlineAt = month.atEndOfMonth();
        long dDay = ChronoUnit.DAYS.between(LocalDate.now(), deadlineAt);

        List<PartSupplier> targets = partSupplierRepository.findAllWithPartAndSupplierByStatus(LifecycleStatus.ACTIVE);
        List<Submission> submissions = submissionRepository.findAllByReportingMonthWithSupplier(monthStart);

        // target(part_supplier) 하나당 이번 달 "현재" 제출 — 여러 건이면 가장 최근 것을 쓴다
        Map<Long, Submission> latestByTarget = submissions.stream()
                .filter(s -> s.getPartSupplier() != null)
                .collect(Collectors.toMap(
                        s -> s.getPartSupplier().getId(),
                        Function.identity(),
                        (a, b) -> a.getSubmittedAt().isAfter(b.getSubmittedAt()) ? a : b));

        Map<Long, DashboardStatus> statusByTarget = new HashMap<>();
        for (PartSupplier target : targets) {
            statusByTarget.put(target.getId(), toDashboardStatus(latestByTarget.get(target.getId())));
        }

        long qualified = countStatus(statusByTarget, DashboardStatus.QUALIFIED);
        long unqualified = countStatus(statusByTarget, DashboardStatus.UNQUALIFIED);
        long notSubmitted = countStatus(statusByTarget, DashboardStatus.NOT_SUBMITTED);
        long total = targets.size();

        DashboardResponse.Counts counts = new DashboardResponse.Counts(qualified, unqualified, notSubmitted, total);
        DashboardResponse.Ratio ratio = total == 0
                ? new DashboardResponse.Ratio(0, 0, 0)
                : new DashboardResponse.Ratio(
                        round(qualified / (double) total),
                        round(unqualified / (double) total),
                        round(notSubmitted / (double) total));

        Map<String, Long> severity = severityCounts(monthStart);
        List<DashboardResponse.SupplierSummary> suppliers =
                buildSupplierSummaries(targets, submissions, statusByTarget, statusFilter);

        return new DashboardResponse(month.format(MONTH_FORMAT), deadlineAt, dDay, counts, ratio, severity, suppliers);
    }

    public DashboardAlertsResponse getAlerts(YearMonth month, SeverityCode severity, String ruleId, int page, int size) {
        LocalDate monthStart = month.atDay(1);
        LocalDate deadlineAt = month.atEndOfMonth();
        long dDay = ChronoUnit.DAYS.between(LocalDate.now(), deadlineAt);

        Pageable pageable = PageRequest.of(page, size);
        Page<Alert> result = alertRepository.search(monthStart, severity, ruleId, pageable);

        List<DashboardAlertsResponse.AlertItem> items = result.getContent().stream()
                .map(alert -> toAlertItem(alert, dDay, month))
                .toList();

        return new DashboardAlertsResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    // ── 38·40 집계 보조 ─────────────────────────────────────────────

    private DashboardStatus toDashboardStatus(Submission current) {
        if (current == null) {
            return DashboardStatus.NOT_SUBMITTED;
        }
        // judgement 가 아직 안 채워진 제출(분석 직후·판정 전)은 일단 부적격 쪽으로 본다 — 확인 필요
        return current.getJudgement() == com.skala.cbam.dashboard.entity.JudgementStatus.QUALIFIED
                ? DashboardStatus.QUALIFIED
                : DashboardStatus.UNQUALIFIED;
    }

    private long countStatus(Map<Long, DashboardStatus> statusByTarget, DashboardStatus status) {
        return statusByTarget.values().stream().filter(s -> s == status).count();
    }

    private Map<String, Long> severityCounts(LocalDate monthStart) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (SeverityCode code : SeverityCode.values()) {
            result.put(code.name(), 0L);
        }
        // 가정: "지금 열려 있는" 경보만 심각도별로 센다 (status=OPEN). 팀 확인 필요.
        List<Alert> openAlerts = alertRepository.findAllByReportingMonthAndStatus(monthStart, AlertStatus.OPEN);
        for (Alert alert : openAlerts) {
            result.merge(alert.getSeverity().name(), 1L, Long::sum);
        }
        return result;
    }

    private List<DashboardResponse.SupplierSummary> buildSupplierSummaries(
            List<PartSupplier> targets,
            List<Submission> submissions,
            Map<Long, DashboardStatus> statusByTarget,
            DashboardStatus statusFilter) {

        Map<Long, List<PartSupplier>> targetsBySupplier = targets.stream()
                .collect(Collectors.groupingBy(ps -> ps.getSupplier().getId()));
        Map<Long, List<Submission>> submissionsBySupplier = submissions.stream()
                .collect(Collectors.groupingBy(s -> s.getSupplier().getId()));

        record Row(Long supplierId, String companyName, DashboardStatus status,
                   long submissionCount, long pendingCount, SeverityCode maxSeverity) {}

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Long, List<PartSupplier>> entry : targetsBySupplier.entrySet()) {
            Long supplierId = entry.getKey();
            List<PartSupplier> supplierTargets = entry.getValue();

            DashboardStatus worst = supplierTargets.stream()
                    .map(ps -> statusByTarget.get(ps.getId()))
                    .max(Comparator.comparingInt(this::statusPriority))
                    .orElse(DashboardStatus.NOT_SUBMITTED);

            if (statusFilter != null && worst != statusFilter) {
                continue;
            }

            List<Submission> supplierSubmissions = submissionsBySupplier.getOrDefault(supplierId, List.of());
            long submissionCount = supplierSubmissions.size();
            long pendingCount = supplierSubmissions.stream()
                    .filter(s -> s.getStatus() == SubmissionStatus.REVIEW_PENDING)
                    .count();
            SeverityCode maxSeverity = supplierSubmissions.stream()
                    .map(Submission::getSeverity)
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingInt(this::severityPriority))
                    .orElse(null);

            String companyName = supplierTargets.get(0).getSupplier().getName();
            rows.add(new Row(supplierId, companyName, worst, submissionCount, pendingCount, maxSeverity));
        }

        // sort=severity,desc 기본값 — 심각도 높은 업체 먼저, 동률이면 회사명순 (가정, 팀 확인 필요)
        rows.sort(Comparator
                .comparingInt((Row r) -> severityPriority(r.maxSeverity()))
                .thenComparing(Row::companyName));

        return rows.stream()
                .map(r -> new DashboardResponse.SupplierSummary(
                        r.supplierId(), r.companyName(), r.status(), r.submissionCount(), r.pendingCount()))
                .toList();
    }

    private int statusPriority(DashboardStatus status) {
        // 미제출 > 부적격 > 적격 (숫자가 클수록 우선)
        return switch (status) {
            case NOT_SUBMITTED -> 2;
            case UNQUALIFIED -> 1;
            case QUALIFIED -> 0;
        };
    }

    private int severityPriority(SeverityCode severity) {
        if (severity == null) {
            return 3;
        }
        return switch (severity) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── 39 조회 보조 ────────────────────────────────────────────────

    private DashboardAlertsResponse.AlertItem toAlertItem(Alert alert, long dDay, YearMonth month) {
        Long supplierId = null;
        Long partId = null;
        String supplierName = null;
        String partName = null;

        if (alert.getPartSupplier() != null) {
            supplierId = alert.getPartSupplier().getSupplier().getId();
            partId = alert.getPartSupplier().getPart().getId();
            supplierName = alert.getPartSupplier().getSupplier().getName();
            partName = alert.getPartSupplier().getPart().getName();
        } else if (alert.getSubmission() != null) {
            // part_supplier 를 못 찾는 경보(예: 미등록 부품 관련) — supplierId 는 submission 에서 알 수 있지만
            // partId/partName 은 모른다. 모르는 값은 채우지 않고 비워둔다.
            supplierId = alert.getSubmission().getSupplier().getId();
            supplierName = alert.getSubmission().getSupplier().getName();
            if (alert.getSubmission().getPartSupplier() != null) {
                partId = alert.getSubmission().getPartSupplier().getPart().getId();
                partName = alert.getSubmission().getPartSupplier().getPart().getName();
            }
        }

        DashboardAlertsResponse.Target target =
                new DashboardAlertsResponse.Target(supplierId, partId, month.format(MONTH_FORMAT));

        return new DashboardAlertsResponse.AlertItem(
                alert.getId(),
                alert.getSubmission() != null ? alert.getSubmission().getId() : null,
                target,
                supplierName,
                partName,
                alert.getRuleId(),
                alert.getSeverity(),
                alert.getMessage(),
                dDay);
    }
}
