package com.skala.cbam.mail.controller;

import com.skala.cbam.mail.domain.MailReceiptStatus;
import com.skala.cbam.mail.dto.AttachmentContent;
import com.skala.cbam.mail.dto.MailReceiptDetailResponse;
import com.skala.cbam.mail.dto.MailReceiptListItem;
import com.skala.cbam.mail.dto.MailReceiptMatchRequest;
import com.skala.cbam.mail.dto.MailReceiptMatchResponse;
import com.skala.cbam.mail.dto.MailReceiptSearchCondition;
import com.skala.cbam.mail.error.MailErrorCode;
import com.skala.cbam.mail.error.MailException;
import com.skala.cbam.mail.service.MailService;
import com.skala.cbam.supplier.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 접수 API (API 명세 №15~№18 · 요구사항 18~21번, CBAM-79).
 *
 * <p>수동 매칭(83번)만 X-Operator-Id 를 받는다(linkedBy 기록용) — ADR-0006, CBAM-90 과 같은 이유로
 * 기본값 "demo". 목록·상세·첨부 조회는 이 값을 쓸 데가 없어 안 받는다.
 */
@Tag(name = "이메일 접수", description = "접수 메일 조회·첨부·수동 매칭 API")
@RestController
@RequiredArgsConstructor
@Validated
public class MailController extends MailApiExceptionHandling {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_DISPOSITIONS = Set.of("inline", "attachment");
    private static final Map<String, String> SORTABLE_FIELDS = Map.of("receivedAt", "receivedAt");

    private final MailService mailService;

    @Operation(summary = "접수 이력 조회", description = "협력업체·접수일·상태로 조회한다.")
    @GetMapping("/api/v1/mail-receipts")
    public ResponseEntity<PageResponse<MailReceiptListItem>> listReceipts(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String receivedFrom,
            @RequestParam(required = false) String receivedTo,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 는 1 이상이어야 합니다") int size,
            @RequestParam(defaultValue = "receivedAt,desc") String sort) {

        MailReceiptSearchCondition condition = new MailReceiptSearchCondition(
                supplierId,
                parseStatus(status),
                parseDayStart(receivedFrom, "receivedFrom"),
                parseDayEnd(receivedTo, "receivedTo"));

        return ResponseEntity.ok(mailService.listReceipts(condition, toPageable(page, size, sort)));
    }

    @Operation(summary = "접수 메일 상세 조회", description = "원문 메일 본문과 첨부파일 목록, 분석 실패 사유를 조회한다.")
    @GetMapping("/api/v1/mail-receipts/{receiptId}")
    public ResponseEntity<MailReceiptDetailResponse> getDetail(@PathVariable Long receiptId) {
        return ResponseEntity.ok(mailService.getDetail(receiptId));
    }

    @Operation(summary = "첨부파일 열람·다운로드", description = "disposition=inline 이면 브라우저 뷰어, "
            + "attachment 면 다운로드로 응답한다.")
    @GetMapping("/api/v1/attachments/{attachmentId}")
    public ResponseEntity<Resource> getAttachment(
            @PathVariable Long attachmentId,
            @RequestParam(defaultValue = "inline") String disposition) throws IOException {

        if (!ALLOWED_DISPOSITIONS.contains(disposition)) {
            throw new MailException(
                    MailErrorCode.INVALID_PARAMETER,
                    "disposition 은 inline 또는 attachment 여야 합니다",
                    java.util.Map.of("fieldErrors", java.util.Map.of("disposition", "허용되지 않은 값")));
        }

        AttachmentContent content = mailService.getAttachmentContent(attachmentId);

        ContentDisposition contentDisposition = ContentDisposition.builder(disposition)
                .filename(content.originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }

    @Operation(summary = "발신자-협력업체 수동 매칭", description = "자동 매칭되지 않은 접수 건을 담당자가 직접 연결한다.")
    @PatchMapping("/api/v1/mail-receipts/{receiptId}/supplier")
    public ResponseEntity<MailReceiptMatchResponse> match(
            @PathVariable Long receiptId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "demo") String operatorId,
            @Valid @RequestBody MailReceiptMatchRequest request) {
        return ResponseEntity.ok(mailService.match(receiptId, request.supplierId(), operatorId));
    }

    private Pageable toPageable(int page, int size, String sort) {
        String[] parts = sort.split(",", 2);
        String field = SORTABLE_FIELDS.get(parts[0].trim());
        if (field == null) {
            throw invalidParameter("sort", "정렬 가능한 필드는 " + SORTABLE_FIELDS.keySet() + " 뿐입니다");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String requested = parts[1].trim();
            if (!requested.equalsIgnoreCase("asc") && !requested.equalsIgnoreCase("desc")) {
                throw invalidParameter("sort", "정렬 방향은 asc 또는 desc 여야 합니다");
            }
            direction = Sort.Direction.fromString(requested);
        }
        return PageRequest.of(page, size, Sort.by(direction, field));
    }

    private MailReceiptStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MailReceiptStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalidParameter("status", "status 값이 올바르지 않습니다");
        }
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

    private MailException invalidParameter(String field, String message) {
        return new MailException(
                MailErrorCode.INVALID_PARAMETER,
                MailErrorCode.INVALID_PARAMETER.defaultMessage(),
                java.util.Map.of("fieldErrors", java.util.Map.of(field, message)));
    }
}
