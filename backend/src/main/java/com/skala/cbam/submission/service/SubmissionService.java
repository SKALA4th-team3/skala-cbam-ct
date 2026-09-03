package com.skala.cbam.submission.service;

import com.skala.cbam.submission.domain.Alert;
import com.skala.cbam.submission.domain.ExtractionField;
import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.domain.UnregisteredPart;
import com.skala.cbam.submission.domain.UnregisteredPartStatus;
import com.skala.cbam.submission.domain.ValidationOutcome;
import com.skala.cbam.submission.dto.SubmissionConfirmResponse;
import com.skala.cbam.submission.dto.SubmissionDetailResponse;
import com.skala.cbam.submission.dto.SubmissionListItem;
import com.skala.cbam.submission.dto.SubmissionRejectRequest;
import com.skala.cbam.submission.dto.SubmissionRejectResponse;
import com.skala.cbam.submission.dto.SubmissionSearchCondition;
import com.skala.cbam.submission.error.SubmissionErrorCode;
import com.skala.cbam.submission.error.SubmissionException;
import com.skala.cbam.submission.repository.AlertRepository;
import com.skala.cbam.submission.repository.ExtractionFieldRepository;
import com.skala.cbam.submission.repository.SubmissionRepository;
import com.skala.cbam.submission.repository.UnregisteredPartRepository;
import com.skala.cbam.submission.service.port.PartRelatedDataProvider;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.dto.PageResponse;
import com.skala.cbam.supplier.repository.SupplierRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터 검토 API 서비스 (29~32번, CBAM-90).
 *
 * <p>X-Operator-Id 는 읽지 않는다 — {@code SupplierController} 가 이미 정한 대로 인증·인가 방식이
 * 명세에서 [미정]이라서다. confirmedBy·rejectedBy 는 응답 계약상 값이 있어야 해서
 * {@link #OPERATOR_PLACEHOLDER} 로 채우고, 인증 방식이 정해지면 실제 담당자로 교체한다.
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String OPERATOR_PLACEHOLDER = "UNKNOWN";
    private static final String EMISSION_UNIT = "tCO2e";

    private final SubmissionRepository submissionRepository;
    private final UnregisteredPartRepository unregisteredPartRepository;
    private final ExtractionFieldRepository extractionFieldRepository;
    private final AlertRepository alertRepository;
    private final PartRelatedDataProvider partRelatedDataProvider;
    private final SupplierRepository supplierRepository;

    // ── 29번: 목록 조회 ────────────────────────────────────────────

    public PageResponse<SubmissionListItem> listSubmissions(
            SubmissionSearchCondition condition, int page, int size) {

        List<Submission> filtered = submissionRepository.search(
                condition.supplierId(), condition.reportingMonth(), condition.status(),
                condition.judgement(), condition.severity(), condition.submittedFrom(), condition.submittedTo());

        if (condition.partId() != null) {
            Set<Long> matchedPartSupplierIds = partRelatedDataProvider
                    .findActiveTargets(condition.supplierId(), condition.partId()).stream()
                    .map(PartRelatedDataProvider.PartSupplierTarget::partSupplierId)
                    .collect(Collectors.toSet());
            filtered = filtered.stream()
                    .filter(s -> s.getPartSupplierId() != null && matchedPartSupplierIds.contains(s.getPartSupplierId()))
                    .toList();
        }

        List<SubmissionListItem> realItems = filtered.stream().map(this::toListItem).toList();
        List<SubmissionListItem> virtualItems = buildVirtualNotSubmittedItems(condition);

        List<SubmissionListItem> combined = new ArrayList<>(realItems.size() + virtualItems.size());
        combined.addAll(realItems);
        combined.addAll(virtualItems);

        // 기본 정렬: 심각도 높은 순(공통 규약대로 HIGH 를 앞에 두려면 알파벳순이 아니라 우선순위로 정렬해야 한다)
        combined.sort(Comparator
                .comparingInt((SubmissionListItem item) -> severityPriority(item.severity()))
                .thenComparing(item -> item.submittedAt() == null ? OffsetDateTime.MIN : item.submittedAt(),
                        Comparator.reverseOrder()));

        return paginate(combined, page, size);
    }

    private SubmissionListItem toListItem(Submission s) {
        Long partId = null;
        String partName = null;
        if (s.getPartSupplierId() != null) {
            var info = partRelatedDataProvider.findPartInfo(s.getPartSupplierId()).orElse(null);
            if (info != null) {
                partId = info.partId();
                partName = info.partName();
            }
        }
        // 제출 건마다 미등록 부품을 따로 조회한다 — 지금 규모에서는 괜찮지만 건수가 많아지면
        // 배치 조회로 바꿔야 한다(N+1).
        long unregisteredCount = unregisteredPartRepository.findBySubmissionId(s.getId()).size();

        return new SubmissionListItem(
                s.getId(),
                new SubmissionListItem.Target(s.getSupplier().getId(), partId, toMonthString(s.getReportingMonth())),
                s.getSupplier().getName(),
                partName,
                s.getSubmittedAt(),
                s.getStatus(),
                s.getJudgement(),
                s.getSeverity(),
                unregisteredCount
        );
    }

    /**
     * ACTIVE part_supplier 중 이번 달 제출이 하나도 없는 조합을 가상 행으로 만든다.
     *
     * <p>reportingMonth 필터가 없으면 가상 행을 만들지 않는다 — 이건 임의 판단이 아니라 ERD 정의를
     * 그대로 따른 것이다: "미제출 target = ACTIVE part_supplier 중 <b>대상 월</b>의 유효 submission이
     * 없는 조합". "대상 월"이 없으면 이 정의 자체가 성립하지 않는다.
     */
    private List<SubmissionListItem> buildVirtualNotSubmittedItems(SubmissionSearchCondition condition) {
        if (condition.reportingMonth() == null) {
            return List.of();
        }
        if (condition.status() != null && condition.status() != SubmissionStatus.NOT_SUBMITTED) {
            return List.of();
        }
        if (condition.judgement() != null || condition.submittedFrom() != null || condition.submittedTo() != null) {
            return List.of(); // 가상 행은 judgement·submittedAt 이 없어 이 필터들과 함께면 결과가 없다
        }
        // 미제출 판정 R1 의 심각도는 HIGH 로 확정돼 있다(코드·Enum 정의 시트) — severity 필터가
        // HIGH 가 아니면 가상 행은 제외한다.
        if (condition.severity() != null && condition.severity() != Severity.HIGH) {
            return List.of();
        }

        // "이번 달에 이미 제출된 조합"은 상태와 무관하게 전부 걸러야 한다 — status 필터 때문에
        // 위에서 걸러진 실제 목록만 보면 진짜 미제출이 아닌데 미제출로 잘못 표시될 수 있다.
        List<Submission> allThisMonth = submissionRepository.search(
                condition.supplierId(), condition.reportingMonth(), null, null, null, null, null);
        Set<Long> covered = allThisMonth.stream()
                .map(Submission::getPartSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<PartRelatedDataProvider.PartSupplierTarget> targets =
                partRelatedDataProvider.findActiveTargets(condition.supplierId(), condition.partId());

        List<SubmissionListItem> virtual = new ArrayList<>();
        for (var target : targets) {
            if (covered.contains(target.partSupplierId())) {
                continue;
            }
            Supplier supplier = supplierRepository.findById(target.supplierId()).orElse(null);
            if (supplier == null) {
                continue;
            }
            virtual.add(new SubmissionListItem(
                    null,
                    new SubmissionListItem.Target(target.supplierId(), target.partId(), toMonthString(condition.reportingMonth())),
                    supplier.getName(),
                    target.partName(),
                    null,
                    SubmissionStatus.NOT_SUBMITTED,
                    null,
                    Severity.HIGH,
                    null
            ));
        }
        return virtual;
    }

    private PageResponse<SubmissionListItem> paginate(List<SubmissionListItem> all, int page, int size) {
        int totalElements = all.size();
        int totalPages = size == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        int from = Math.min(page * size, totalElements);
        int to = Math.min(from + size, totalElements);
        return new PageResponse<>(all.subList(from, to), page, size, totalElements, totalPages);
    }

    // ── 30번: 상세 조회 ────────────────────────────────────────────

    public SubmissionDetailResponse getDetail(Long submissionId) {
        Submission s = submissionRepository.findByIdWithSupplier(submissionId)
                .orElseThrow(() -> new SubmissionException(SubmissionErrorCode.SUBMISSION_NOT_FOUND));

        Long partId = s.getPartSupplierId() == null ? null
                : partRelatedDataProvider.findPartInfo(s.getPartSupplierId())
                        .map(PartRelatedDataProvider.PartInfo::partId).orElse(null);

        List<ExtractionField> fields =
                extractionFieldRepository.findBySubmissionIdOrderByFieldCodeAscSequenceNumberAsc(submissionId);

        Map<String, SubmissionDetailResponse.ActivityDataItem> activityData = new LinkedHashMap<>();
        Set<Long> attachmentIds = new LinkedHashSet<>();
        for (ExtractionField field : fields) {
            // 같은 field_code 가 여러 번(sequence_number > 1) 오면 뒤엣것이 앞엣것을 덮어쓴다.
            // ERD 는 반복을 허용하지만(sequence_number 존재 이유), 요구사항 23번 원문에는 항목이
            // 반복된다는 말이 없다 — 팀 확인 결과 "요구사항 기준으로 하면 된다"고 해서 필드당
            // 한 번만 온다고 보고 이대로 간다.
            activityData.put(field.getFieldCode(), new SubmissionDetailResponse.ActivityDataItem(
                    field.normalizedValue(),
                    field.getUnit(),
                    field.getRawValue(),
                    field.getEmissionScope(),
                    field.getConversionFailureReason(),
                    new SubmissionDetailResponse.Source(field.getSourceAttachmentId(), field.getSourceLocator())
            ));
            if (field.getSourceAttachmentId() != null) {
                attachmentIds.add(field.getSourceAttachmentId());
            }
        }

        SubmissionDetailResponse.CalculatedEmission calculatedEmission = new SubmissionDetailResponse.CalculatedEmission(
                s.getDirectEmissionTco2e(), s.getIndirectEmissionTco2e(), EMISSION_UNIT,
                s.calculateEmissionIntensity(), s.getDefaultValueRatio());

        List<Alert> alerts = alertRepository.findBySubmissionIdOrderByRuleIdAscCheckIdAsc(submissionId);
        OffsetDateTime validatedAt = alerts.stream().map(Alert::getValidatedAt)
                .max(Comparator.naturalOrder()).orElse(null);
        SubmissionDetailResponse.Validation validation = new SubmissionDetailResponse.Validation(
                s.getJudgement(), s.getSeverity(), validatedAt, groupAlertsByRule(alerts));

        List<SubmissionDetailResponse.UnregisteredPartItem> unregisteredParts =
                unregisteredPartRepository.findBySubmissionId(submissionId).stream()
                        .map(u -> new SubmissionDetailResponse.UnregisteredPartItem(u.getId(), u.getRawPartName()))
                        .toList();

        // ERD 상 진짜 관계는 attachment.mail_receipt_id = submission.mailReceiptId 다 — 즉 이
        // 메일에 첨부된 파일 "전체"가 맞는 목록이다. 그런데 attachment 도메인(이메일 접수 쪽)이
        // 아직 없어서 그 조회를 못 한다. 지금은 이 제출의 activityData 가 실제로 근거로 삼은
        // extraction_field.source_attachment_id 들로만 목록을 만든다 — 같은 메일의 다른 첨부(예:
        // 이 제출과 무관한 파일)는 빠진다. attachment 도메인이 생기면 mailReceiptId 기준으로 바꿔야 한다.
        List<SubmissionDetailResponse.AttachmentItem> attachments = attachmentIds.stream()
                .map(id -> new SubmissionDetailResponse.AttachmentItem(id, "/api/v1/attachments/" + id))
                .toList();

        return new SubmissionDetailResponse(
                s.getId(), s.getSupplier().getId(), partId, s.getMailReceiptId(),
                toMonthString(s.getReportingMonth()), s.getStatus(), s.getJudgement(),
                s.getDocumentType(), s.getEligibilityStatus(),
                activityData, calculatedEmission, validation, unregisteredParts, attachments);
    }

    private List<SubmissionDetailResponse.RuleResult> groupAlertsByRule(List<Alert> alerts) {
        Map<String, List<Alert>> byRule = alerts.stream()
                .collect(Collectors.groupingBy(Alert::getRuleId, LinkedHashMap::new, Collectors.toList()));

        List<SubmissionDetailResponse.RuleResult> rules = new ArrayList<>();
        for (Map.Entry<String, List<Alert>> entry : byRule.entrySet()) {
            List<Alert> group = entry.getValue();
            ValidationOutcome ruleOutcome = group.stream().anyMatch(a -> a.getOutcome() == ValidationOutcome.FAIL)
                    ? ValidationOutcome.FAIL
                    : group.stream().anyMatch(a -> a.getOutcome() == ValidationOutcome.SKIPPED)
                            ? ValidationOutcome.SKIPPED : ValidationOutcome.PASS;
            Severity severity = group.get(0).getSeverity();
            List<SubmissionDetailResponse.CheckResult> checks = group.stream()
                    .map(a -> new SubmissionDetailResponse.CheckResult(a.getCheckId(), a.getOutcome()))
                    .toList();
            rules.add(new SubmissionDetailResponse.RuleResult(entry.getKey(), ruleOutcome, severity, checks));
        }
        return rules;
    }

    // ── 31번: 확정 ─────────────────────────────────────────────────

    @Transactional
    public SubmissionConfirmResponse confirm(Long submissionId) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionException(SubmissionErrorCode.SUBMISSION_NOT_FOUND));

        if (s.getStatus() == SubmissionStatus.CONFIRMED) {
            throw new SubmissionException(SubmissionErrorCode.ALREADY_CONFIRMED);
        }
        if (s.getJudgement() != Judgement.QUALIFIED) {
            throw new SubmissionException(SubmissionErrorCode.NOT_QUALIFIED);
        }
        List<UnregisteredPart> open =
                unregisteredPartRepository.findBySubmissionIdAndStatus(submissionId, UnregisteredPartStatus.OPEN);
        if (!open.isEmpty()) {
            throw new SubmissionException(
                    SubmissionErrorCode.UNREGISTERED_PART_EXISTS,
                    SubmissionErrorCode.UNREGISTERED_PART_EXISTS.defaultMessage(),
                    Map.of("unregisteredPartIds", open.stream().map(UnregisteredPart::getId).toList()));
        }

        // ERD: part.benchmark_factor_year = "현재 기준 배출원단위 적용 연도". 확정 시점엔 그 값을
        // 스냅샷으로 찍어야 한다("확정한 날짜의 연도"가 아니다) — Port 로 부품 도메인에 물어본다.
        // 부품 도메인이 아직 dev 에 없어 못 받으면(Optional 비어있으면), 부팅은 되게 현재 연도로
        // 잠깐 대체한다 — 이건 질문이 아니라 그냥 데이터가 없어서 나는 임시 기본값이다.
        Integer benchmarkFactorYear = s.getPartSupplierId() == null ? null
                : partRelatedDataProvider.findPartInfo(s.getPartSupplierId())
                        .map(PartRelatedDataProvider.PartInfo::benchmarkFactorYear)
                        .orElse(null);
        int appliedFactorYear = benchmarkFactorYear != null
                ? benchmarkFactorYear
                : OffsetDateTime.now(SEOUL).getYear();
        s.confirm(OPERATOR_PLACEHOLDER, appliedFactorYear);

        return new SubmissionConfirmResponse(
                s.getId(), s.getStatus(), s.getConfirmedBy(), s.getConfirmedAt(),
                new SubmissionConfirmResponse.CalculatedEmission(
                        s.getDirectEmissionTco2e(), s.getIndirectEmissionTco2e(), EMISSION_UNIT,
                        s.calculateEmissionIntensity(), s.getAppliedFactorYear(), s.isFrozen()));
    }

    // ── 32번: 반려 ─────────────────────────────────────────────────

    @Transactional
    public SubmissionRejectResponse reject(Long submissionId, SubmissionRejectRequest request) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionException(SubmissionErrorCode.SUBMISSION_NOT_FOUND));

        if (s.getStatus() == SubmissionStatus.CONFIRMED) {
            throw new SubmissionException(SubmissionErrorCode.ALREADY_CONFIRMED);
        }
        // 가정(팀 확인 완료, 엣지케이스로 정리함): 검토 대기 상태만 반려할 수 있다.
        // 이미 반려된 건을 또 반려하면 400 NOT_REJECTABLE.
        if (s.getStatus() != SubmissionStatus.REVIEW_PENDING) {
            throw new SubmissionException(SubmissionErrorCode.NOT_REJECTABLE);
        }

        SubmissionStatus resultStatus = parseResultStatus(request.resultStatus());
        s.reject(resultStatus, request.reasonCode(), request.reason(), OPERATOR_PLACEHOLDER);

        // createFeedbackDraft 요청 여부와 관계없이 feedbackDraftTaskId 는 항상 null —
        // 피드백 초안 도메인(42~46번)이 아직 없다. 가짜 taskId 는 만들지 않는다.
        return new SubmissionRejectResponse(
                s.getId(), s.getStatus(), s.getJudgement(), s.getRejectionReasonCode(), s.getRejectionReason(),
                s.getRejectedBy(), s.getRejectedAt(), null);
    }

    private SubmissionStatus parseResultStatus(String raw) {
        if ("REJECTED".equals(raw)) {
            return SubmissionStatus.REJECTED;
        }
        if ("NOT_SUBMITTED".equals(raw)) {
            return SubmissionStatus.NOT_SUBMITTED;
        }
        throw new SubmissionException(SubmissionErrorCode.INVALID_RESULT_STATUS);
    }

    // ── 공통 ──────────────────────────────────────────────────────

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

    private String toMonthString(java.time.LocalDate date) {
        return date == null ? null : date.toString().substring(0, 7);
    }
}
