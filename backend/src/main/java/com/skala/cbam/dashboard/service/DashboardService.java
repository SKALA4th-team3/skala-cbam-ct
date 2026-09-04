package com.skala.cbam.dashboard.service;

import com.skala.cbam.dashboard.dto.DashboardAlertsResponse;
import com.skala.cbam.dashboard.dto.DashboardResponse;
import com.skala.cbam.dashboard.dto.DashboardStatus;
import com.skala.cbam.dashboard.repository.DashboardAlertRepository;
import com.skala.cbam.dashboard.repository.DashboardPartSupplierRepository;
import com.skala.cbam.dashboard.repository.DashboardSubmissionRepository;
import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.parts.entity.PartSupplierStatus;
import com.skala.cbam.submission.domain.Alert;
import com.skala.cbam.submission.domain.AlertStatus;
import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import com.skala.cbam.supplier.repository.SupplierRepository;
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

    /**
     * 판정 전 제출을 어느 칸에 넣을지 팀이 정할 때까지 쓰는 임시값 — {@link #toDashboardStatus} 참고.
     * 이름을 붙여 한 곳에 모아 둔 이유는, 삼항 연산자 안에 숨어 있으면 아무도 다시 안 보기 때문이다.
     */
    private static final DashboardStatus UNJUDGED_FALLBACK = DashboardStatus.UNQUALIFIED;

    private final DashboardPartSupplierRepository partSupplierRepository;
    private final DashboardSubmissionRepository submissionRepository;
    private final DashboardAlertRepository alertRepository;
    /**
     * {@code PartSupplier.supplierId} · {@code Alert.partSupplierId} 가 연관이 아니라 값이라
     * 협력업체는 id 로 따로 읽는다 — 정식 엔티티를 쓰기로 한 대가다(사본을 두면 부팅이 막힌다).
     */
    private final SupplierRepository supplierRepository;

    public DashboardResponse getDashboard(YearMonth month, DashboardStatus statusFilter) {
        LocalDate monthStart = month.atDay(1);
        LocalDate deadlineAt = month.atEndOfMonth();
        long dDay = ChronoUnit.DAYS.between(LocalDate.now(), deadlineAt);

        List<PartSupplier> targets = partSupplierRepository.findActiveTargets(PartSupplierStatus.ACTIVE);
        List<Submission> submissions = submissionRepository.findAllByReportingMonthWithSupplier(monthStart);

        // target(part_supplier) 하나당 이번 달 "현재" 제출 — 여러 건이면 가장 최근 것을 쓴다
        Map<Long, Submission> latestByTarget = submissions.stream()
                .filter(s -> s.getPartSupplierId() != null)
                .collect(Collectors.toMap(
                        Submission::getPartSupplierId,
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
                buildSupplierSummaries(targets, submissions, statusByTarget, statusFilter,
                        suppliersById(targets.stream().map(PartSupplier::getSupplierId).toList()));

        return new DashboardResponse(month.format(MONTH_FORMAT), deadlineAt, dDay, counts, ratio, severity, suppliers);
    }

    public DashboardAlertsResponse getAlerts(YearMonth month, Severity severity, String ruleId, int page, int size) {
        LocalDate monthStart = month.atDay(1);
        LocalDate deadlineAt = month.atEndOfMonth();
        long dDay = ChronoUnit.DAYS.between(LocalDate.now(), deadlineAt);

        Pageable pageable = PageRequest.of(page, size);
        Page<Alert> result = alertRepository.search(monthStart, severity, ruleId, pageable);

        // 경보가 가리키는 part_supplier 와 그 협력업체를 한 번에 읽는다 — 줄마다 조회하면 N+1 이다
        List<Long> partSupplierIds = result.getContent().stream()
                .flatMap(a -> java.util.stream.Stream.of(
                        a.getPartSupplierId(),
                        a.getSubmission() == null ? null : a.getSubmission().getPartSupplierId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, PartSupplier> partSupplierById = partSupplierIds.isEmpty() ? Map.of()
                : partSupplierRepository.findAllById(partSupplierIds).stream()
                        .collect(Collectors.toMap(PartSupplier::getId, Function.identity(), (a, b) -> a));
        Map<Long, Supplier> supplierById = suppliersById(
                partSupplierById.values().stream().map(PartSupplier::getSupplierId).distinct().toList());

        List<DashboardAlertsResponse.AlertItem> items = result.getContent().stream()
                .map(alert -> toAlertItem(alert, dDay, month, partSupplierById, supplierById))
                .toList();

        return new DashboardAlertsResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    // ── 38·40 집계 보조 ─────────────────────────────────────────────

    /**
     * 제출 한 건을 화면 상태 3분류로 바꾼다.
     *
     * <p>⚠️ <b>판정 전(judgement = null) 제출을 부적격으로 세고 있다. 이건 임시값이고 틀렸다.</b>
     * 제출은 했으니 미제출이 아니고, 판정 전이니 적격도 부적격도 아니다 —
     * 그런데 counts 는 qualified·unqualified·notSubmitted <b>3버킷 고정</b>이고
     * {@link Judgement} 에는 PENDING 이 없다.
     * <b>계약에 자리가 없어서</b> 여기서 임의로 고를 수 없다.
     *
     * <p>고치려면 counts 에 4번째 값을 넣거나 모수에서 빼야 하는데 <b>둘 다 API 명세 v10 24행을
     * 먼저 고쳐야 하는 계약 변경</b>이다. 팀이 정하면 이 메서드 하나만 고치면 된다.
     * 배경과 후보는 ADR-0005 「아직 정하지 못한 것」에 적어 두었다.
     */
    private DashboardStatus toDashboardStatus(Submission current) {
        if (current == null) {
            return DashboardStatus.NOT_SUBMITTED;
        }
        if (current.getJudgement() == null) {
            return UNJUDGED_FALLBACK;
        }
        return current.getJudgement() == Judgement.QUALIFIED
                ? DashboardStatus.QUALIFIED
                : DashboardStatus.UNQUALIFIED;
    }

    /** {@code supplierId} 목록으로 협력업체를 한 번에 읽는다 — 줄마다 조회하면 N+1 이 된다. */
    private Map<Long, Supplier> suppliersById(List<Long> supplierIds) {
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity(), (a, b) -> a));
    }

    private long countStatus(Map<Long, DashboardStatus> statusByTarget, DashboardStatus status) {
        return statusByTarget.values().stream().filter(s -> s == status).count();
    }

    private Map<String, Long> severityCounts(LocalDate monthStart) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Severity code : Severity.values()) {
            result.put(code.name(), 0L);
        }
        // 가정: "지금 열려 있는" 경보만 심각도별로 센다 (status=OPEN). 팀 확인 필요.
        List<Alert> openAlerts = alertRepository.findAllByReportingMonthAndStatus(monthStart, AlertStatus.OPEN);
        for (Alert alert : openAlerts) {
            result.merge(alert.getSeverity().name(), 1L, Long::sum);
        }
        return result;
    }

    /**
     * 38번 「업체별 상태와 건수」.
     *
     * <p>모수는 ACTIVE target 이지만, <b>목록은 target 이 없는 업체도 빼지 않는다.</b>
     * targets 만 순회하면 part_supplier 가 없거나 전부 INACTIVE 인 업체는 이번 달 제출이 있어도
     * 화면에서 통째로 사라진다 — 줄이 하나 빠지는 쪽은 숫자가 틀리는 쪽보다 눈에 안 띄어 더 위험하다.
     * 그래서 (ACTIVE target 이 있는 업체) ∪ (이번 달 제출이 있는 업체)를 순회한다.
     *
     * <p>협력 끊김 업체는 양쪽 모두에서 뺀다(요구사항 6번). targets 는 레포지토리에서 이미 걸러져
     * 오고, 제출로만 들어오는 업체는 여기서 status 를 본다.
     */
    private List<DashboardResponse.SupplierSummary> buildSupplierSummaries(
            List<PartSupplier> targets,
            List<Submission> submissions,
            Map<Long, DashboardStatus> statusByTarget,
            DashboardStatus statusFilter,
            Map<Long, Supplier> targetSuppliers) {

        Map<Long, List<PartSupplier>> targetsBySupplier = targets.stream()
                .collect(Collectors.groupingBy(PartSupplier::getSupplierId));
        Map<Long, List<Submission>> submissionsBySupplier = submissions.stream()
                .filter(s -> s.getSupplier().getStatus() == SupplierStatus.ACTIVE)
                .collect(Collectors.groupingBy(s -> s.getSupplier().getId()));

        Map<Long, Supplier> supplierById = new LinkedHashMap<>();
        targets.forEach(ps -> {
            Supplier supplier = targetSuppliers.get(ps.getSupplierId());
            if (supplier != null) {
                supplierById.putIfAbsent(ps.getSupplierId(), supplier);
            }
        });
        submissionsBySupplier.values().forEach(
                list -> supplierById.putIfAbsent(list.get(0).getSupplier().getId(), list.get(0).getSupplier()));

        record Row(Long supplierId, String companyName, DashboardStatus status,
                   long submissionCount, long pendingCount, Severity maxSeverity) {}

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Long, Supplier> entry : supplierById.entrySet()) {
            Long supplierId = entry.getKey();
            List<PartSupplier> supplierTargets = targetsBySupplier.getOrDefault(supplierId, List.of());
            List<Submission> supplierSubmissions = submissionsBySupplier.getOrDefault(supplierId, List.of());

            DashboardStatus worst = worstStatus(supplierTargets, supplierSubmissions, statusByTarget);

            if (statusFilter != null && worst != statusFilter) {
                continue;
            }

            long submissionCount = supplierSubmissions.size();
            long pendingCount = supplierSubmissions.stream()
                    .filter(s -> s.getStatus() == SubmissionStatus.REVIEW_PENDING)
                    .count();
            Severity maxSeverity = supplierSubmissions.stream()
                    .map(Submission::getSeverity)
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingInt(this::severityPriority))
                    .orElse(null);

            rows.add(new Row(supplierId, entry.getValue().getName(), worst,
                    submissionCount, pendingCount, maxSeverity));
        }

        // sort=severity,desc 기본값 — 심각도 높은 업체 먼저, 동률이면 회사명순 (가정, ADR-0004 참고)
        rows.sort(Comparator
                .comparingInt((Row r) -> severityPriority(r.maxSeverity()))
                .thenComparing(Row::companyName));

        return rows.stream()
                .map(r -> new DashboardResponse.SupplierSummary(
                        r.supplierId(), r.companyName(), r.status(), r.submissionCount(), r.pendingCount()))
                .toList();
    }

    /**
     * 업체 한 줄의 대표 상태. ACTIVE target 의 상태 중 가장 나쁜 것을 쓴다.
     *
     * <p>target 이 하나도 없는 업체(제출로만 목록에 들어온 업체)는 제출에서 상태를 뽑는다 —
     * 이때 「미제출」이라고 쓰면 제출한 업체를 미제출로 표시하게 된다.
     */
    private DashboardStatus worstStatus(List<PartSupplier> supplierTargets,
                                        List<Submission> supplierSubmissions,
                                        Map<Long, DashboardStatus> statusByTarget) {
        if (!supplierTargets.isEmpty()) {
            return supplierTargets.stream()
                    .map(ps -> statusByTarget.get(ps.getId()))
                    .max(Comparator.comparingInt(this::statusPriority))
                    .orElse(DashboardStatus.NOT_SUBMITTED);
        }
        return supplierSubmissions.stream()
                .map(this::toDashboardStatus)
                .max(Comparator.comparingInt(this::statusPriority))
                .orElse(DashboardStatus.NOT_SUBMITTED);
    }

    private int statusPriority(DashboardStatus status) {
        // 미제출 > 부적격 > 적격 (숫자가 클수록 우선)
        return switch (status) {
            case NOT_SUBMITTED -> 2;
            case UNQUALIFIED -> 1;
            case QUALIFIED -> 0;
        };
    }

    private int severityPriority(Severity severity) {
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

    private DashboardAlertsResponse.AlertItem toAlertItem(
            Alert alert, long dDay, YearMonth month,
            Map<Long, PartSupplier> partSupplierById, Map<Long, Supplier> supplierById) {

        Long supplierId = null;
        Long partId = null;
        String supplierName = null;
        String partName = null;

        PartSupplier alertTarget = alert.getPartSupplierId() == null ? null
                : partSupplierById.get(alert.getPartSupplierId());

        if (alertTarget != null) {
            supplierId = alertTarget.getSupplierId();
            partId = alertTarget.getPart().getId();
            Supplier supplier = supplierById.get(alertTarget.getSupplierId());
            supplierName = supplier == null ? null : supplier.getName();
            partName = alertTarget.getPart().getPartName();
        } else if (alert.getSubmission() != null) {
            // part_supplier 를 못 찾는 경보(예: 미등록 부품 관련) — supplierId 는 submission 에서 알 수 있지만
            // partId/partName 은 모른다. 모르는 값은 채우지 않고 비워둔다.
            supplierId = alert.getSubmission().getSupplier().getId();
            supplierName = alert.getSubmission().getSupplier().getName();
            PartSupplier fromSubmission = alert.getSubmission().getPartSupplierId() == null ? null
                    : partSupplierById.get(alert.getSubmission().getPartSupplierId());
            if (fromSubmission != null) {
                partId = fromSubmission.getPart().getId();
                partName = fromSubmission.getPart().getPartName();
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
