package com.skala.cbam.feedback.controller;

import com.skala.cbam.common.domain.FeedbackStatus;
import com.skala.cbam.feedback.domain.FeedbackType;
import com.skala.cbam.feedback.dto.FeedbackConfirmRequest;
import com.skala.cbam.feedback.dto.FeedbackConfirmResponse;
import com.skala.cbam.feedback.dto.FeedbackDraftCreateRequest;
import com.skala.cbam.feedback.dto.FeedbackDraftCreateResponse;
import com.skala.cbam.feedback.dto.FeedbackDraftDetailResponse;
import com.skala.cbam.feedback.dto.FeedbackDraftRegenerateRequest;
import com.skala.cbam.feedback.dto.FeedbackDraftRegenerateResponse;
import com.skala.cbam.feedback.dto.FeedbackHistoryItem;
import com.skala.cbam.feedback.dto.FeedbackHistorySearchCondition;
import com.skala.cbam.feedback.dto.FeedbackSendRequest;
import com.skala.cbam.feedback.dto.FeedbackSendResponse;
import com.skala.cbam.feedback.error.FeedbackErrorCode;
import com.skala.cbam.feedback.error.FeedbackException;
import com.skala.cbam.feedback.service.FeedbackService;
import com.skala.cbam.supplier.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 피드백 API (API 명세 №26~№31 · 요구사항 42~53번 중 47·49번 제외, CBAM-88).
 *
 * <p>X-Operator-Id 는 응답에 "누가 했는지" 필드(createdBy·confirmedBy 등)가 있는 생성·확정·발송에서만
 * 읽는다 — ADR-0006, CBAM-90/CBAM-79와 같은 이유로 기본값 "demo".
 */
@Tag(name = "피드백", description = "피드백 초안 생성·조회·재생성·확정·발송·이력 API")
@RestController
@RequiredArgsConstructor
@Validated
public class FeedbackController extends FeedbackApiExceptionHandling {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FeedbackService feedbackService;

    @Operation(summary = "초안 생성 요청 (개별·일괄)", description = "부적격·미제출 건에서 초안 생성을 요청한다.")
    @PostMapping("/api/v1/feedback-drafts")
    public ResponseEntity<FeedbackDraftCreateResponse> createDrafts(
            @RequestHeader(value = "X-Operator-Id", defaultValue = "demo") String operatorId,
            @RequestBody FeedbackDraftCreateRequest request) {
        return ResponseEntity.accepted().body(feedbackService.createDrafts(request, operatorId));
    }

    @Operation(summary = "초안 조회", description = "생성된 초안을 판정 근거와 나란히 조회한다.")
    @GetMapping("/api/v1/feedback-drafts/{draftId}")
    public ResponseEntity<FeedbackDraftDetailResponse> getDetail(
            @PathVariable Long draftId,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(feedbackService.getDetail(draftId, version));
    }

    @Operation(summary = "초안 재생성", description = "추가 지시와 문체를 입력해 다시 생성한다. 이전 초안은 버전으로 보관한다.")
    @PostMapping("/api/v1/feedback-drafts/{draftId}/regenerate")
    public ResponseEntity<FeedbackDraftRegenerateResponse> regenerate(
            @PathVariable Long draftId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "demo") String operatorId,
            @RequestBody FeedbackDraftRegenerateRequest request) {
        return ResponseEntity.accepted().body(feedbackService.regenerate(draftId, request, operatorId));
    }

    @Operation(summary = "피드백 확정", description = "검토를 마친 피드백을 확정한다. "
            + "이 엔드포인트는 확정만 지원한다 — 문안 수정·폐기는 CBAM-93 스코프 밖이다.")
    @PatchMapping("/api/v1/feedback-drafts/{draftId}")
    public ResponseEntity<FeedbackConfirmResponse> confirm(
            @PathVariable Long draftId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "demo") String operatorId,
            @RequestBody FeedbackConfirmRequest request) {
        return ResponseEntity.ok(feedbackService.confirm(draftId, request, operatorId));
    }

    @Operation(summary = "피드백 발송·재발송", description = "확정된 피드백을 발송한다. "
            + "이미 발송한 건에 다시 호출하면 재발송으로 처리된다.")
    @PostMapping("/api/v1/feedback-drafts/{draftId}/send")
    public ResponseEntity<FeedbackSendResponse> send(
            @PathVariable Long draftId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "demo") String operatorId,
            @RequestBody(required = false) FeedbackSendRequest request) {
        FeedbackSendRequest body = request == null ? new FeedbackSendRequest(null) : request;
        return ResponseEntity.accepted().body(feedbackService.send(draftId, body, operatorId));
    }

    @Operation(summary = "발송 이력 조회 (협력업체별)", description = "협력업체별 발송 이력을 조회한다.")
    @GetMapping("/api/v1/suppliers/{supplierId}/feedback-histories")
    public ResponseEntity<PageResponse<FeedbackHistoryItem>> listHistoriesBySupplier(
            @PathVariable Long supplierId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 는 1 이상이어야 합니다") int size) {
        return ResponseEntity.ok(listHistories(supplierId, type, status, from, to, page, size));
    }

    @Operation(summary = "발송 이력 조회 (전사)", description = "ADR-0008 — supplierId 없이 전체 발송 이력을 조회한다.")
    @GetMapping("/api/v1/feedback-histories")
    public ResponseEntity<PageResponse<FeedbackHistoryItem>> listHistoriesAll(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 는 1 이상이어야 합니다") int size) {
        return ResponseEntity.ok(listHistories(supplierId, type, status, from, to, page, size));
    }

    private PageResponse<FeedbackHistoryItem> listHistories(
            Long supplierId, String type, String status, String from, String to, int page, int size) {
        FeedbackHistorySearchCondition condition = new FeedbackHistorySearchCondition(
                supplierId,
                parseEnum(type, FeedbackType.class, "type"),
                parseEnum(status, FeedbackStatus.class, "status"),
                parseDayStart(from, "from"),
                parseDayEnd(to, "to"));
        Pageable pageable = PageRequest.of(page, size);
        return feedbackService.listHistories(condition, pageable);
    }

    private OffsetDateTime parseDayStart(String value, String field) {
        LocalDate date = parseDate(value, field);
        return date == null ? null : date.atStartOfDay(SEOUL).toOffsetDateTime();
    }

    private OffsetDateTime parseDayEnd(String value, String field) {
        LocalDate date = parseDate(value, field);
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

    private FeedbackException invalidParameter(String field, String message) {
        return new FeedbackException(
                FeedbackErrorCode.INVALID_PARAMETER,
                FeedbackErrorCode.INVALID_PARAMETER.defaultMessage(),
                java.util.Map.of("fieldErrors", java.util.Map.of(field, message)));
    }
}
