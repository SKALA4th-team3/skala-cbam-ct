package com.skala.cbam.submission.controller;

import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Severity;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.dto.SubmissionConfirmResponse;
import com.skala.cbam.submission.dto.SubmissionDetailResponse;
import com.skala.cbam.submission.dto.SubmissionListItem;
import com.skala.cbam.submission.dto.SubmissionRejectRequest;
import com.skala.cbam.submission.dto.SubmissionRejectResponse;
import com.skala.cbam.submission.dto.SubmissionSearchCondition;
import com.skala.cbam.submission.error.SubmissionErrorCode;
import com.skala.cbam.submission.error.SubmissionException;
import com.skala.cbam.submission.service.SubmissionService;
import com.skala.cbam.supplier.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데이터 검토 API (API 명세 №20~№23 · 요구사항 29~32번, CBAM-90).
 *
 * <p>X-Operator-Id 는 받지 않는다 — {@code SupplierController} 와 같은 이유(주석 참고).
 */
@Tag(name = "데이터 검토", description = "제출 데이터 조회·확정·반려 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/submissions")
@Validated
public class SubmissionController extends SubmissionApiExceptionHandling {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SubmissionService submissionService;

    @Operation(summary = "제출 데이터 목록 조회", description = "협력업체·부품·보고월·처리 상태·판정 결과·"
            + "심각도로 조회한다. 기본 정렬은 심각도 높은 순. 미제출 건은 id 가 null 이고 target 으로 식별한다.")
    @GetMapping
    public ResponseEntity<PageResponse<SubmissionListItem>> listSubmissions(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long partId,
            @RequestParam(required = false) String reportingMonth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String judgement,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String submittedFrom,
            @RequestParam(required = false) String submittedTo,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 는 1 이상이어야 합니다") int size,
            @RequestParam(defaultValue = "severity,desc") String sort) {

        SubmissionSearchCondition condition = new SubmissionSearchCondition(
                supplierId,
                partId,
                parseMonth(reportingMonth),
                parseEnum(status, SubmissionStatus.class, "status"),
                parseEnum(judgement, Judgement.class, "judgement"),
                parseEnum(severity, Severity.class, "severity"),
                parseDayStart(submittedFrom),
                parseDayEnd(submittedTo));

        return ResponseEntity.ok(submissionService.listSubmissions(condition, page, size));
    }

    @Operation(summary = "제출 데이터 상세 조회", description = "항목별 표준화 값·원본 값·추출 근거, "
            + "판정 결과와 사유, 포함된 미등록 부품을 한 화면에서 조회한다.")
    @GetMapping("/{submissionId}")
    public ResponseEntity<SubmissionDetailResponse> getDetail(@PathVariable Long submissionId) {
        return ResponseEntity.ok(submissionService.getDetail(submissionId));
    }

    @Operation(summary = "데이터 확정", description = "판정이 적격이고 미등록 부품이 없는 경우에만 확정할 수 있다.")
    @PostMapping("/{submissionId}/confirm")
    public ResponseEntity<SubmissionConfirmResponse> confirm(@PathVariable Long submissionId) {
        return ResponseEntity.ok(submissionService.confirm(submissionId));
    }

    @Operation(summary = "제출 데이터 반려", description = "부적격 데이터를 반려한다. "
            + "resultStatus 로 REJECTED·NOT_SUBMITTED 중 하나를 지정한다.")
    @PostMapping("/{submissionId}/reject")
    public ResponseEntity<SubmissionRejectResponse> reject(
            @PathVariable Long submissionId, @RequestBody SubmissionRejectRequest request) {
        return ResponseEntity.ok(submissionService.reject(submissionId, request));
    }

    private LocalDate parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value).atDay(1);
        } catch (DateTimeParseException e) {
            throw invalidParameter("reportingMonth", "reportingMonth 는 YYYY-MM 형식이어야 합니다");
        }
    }

    private OffsetDateTime parseDayStart(String value) {
        LocalDate date = parseDate(value, "submittedFrom");
        return date == null ? null : date.atStartOfDay(SEOUL).toOffsetDateTime();
    }

    private OffsetDateTime parseDayEnd(String value) {
        LocalDate date = parseDate(value, "submittedTo");
        return date == null ? null : date.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime().minusSeconds(1);
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalidParameter(field, field + " 는 YYYY-MM-DD 형식이어야 합니다");
        }
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw invalidParameter(field, field + " 값이 올바르지 않습니다");
        }
    }

    private SubmissionException invalidParameter(String field, String message) {
        return new SubmissionException(
                SubmissionErrorCode.INVALID_PARAMETER,
                SubmissionErrorCode.INVALID_PARAMETER.defaultMessage(),
                java.util.Map.of("fieldErrors", java.util.Map.of(field, message)));
    }
}
