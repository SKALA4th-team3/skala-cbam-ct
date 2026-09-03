package com.skala.cbam.supplier.controller;

import com.skala.cbam.supplier.error.SupplierErrorCode;
import com.skala.cbam.supplier.error.SupplierException;
import com.skala.cbam.supplier.domain.SupplierStatus;
import com.skala.cbam.supplier.dto.PageResponse;
import com.skala.cbam.supplier.dto.SupplierCreateRequest;
import com.skala.cbam.supplier.dto.SupplierCreateResponse;
import com.skala.cbam.supplier.dto.SupplierDetailResponse;
import com.skala.cbam.supplier.dto.SupplierSearchCondition;
import com.skala.cbam.supplier.dto.SupplierSummaryResponse;
import com.skala.cbam.supplier.dto.SupplierUpdateRequest;
import com.skala.cbam.supplier.dto.SupplierUpdateResponse;
import com.skala.cbam.supplier.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 협력업체 API (API 명세 №1~№4 · 요구사항 1~6번). Base URL 은 /api/v1(공통 규약 1항).
 *
 * <p>X-Operator-Id 헤더를 받지 않는다. 명세 2항이 이 헤더를 "감사 기록용 담당자 식별자이며
 * 인증 수단이 아니다"로 규정했고, 인증·인가 방식 자체가 명세에서 [미정]이다.
 * 감사 기록을 남길 자리(누가 끊었는지)는 인증 방식이 정해질 때 함께 붙인다.
 */
@Tag(name = "협력업체", description = "협력업체 기준정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/suppliers")
@Validated
public class SupplierController extends SupplierApiExceptionHandling {

    /** 조회 기간 기본값. 요구사항 3번 · 5번의 「최근 12개월」. */
    private static final String DEFAULT_MONTHS = "12";

    /**
     * 지원하는 정렬 키.
     *
     * <p>명세는 companyName · lastSubmittedAt 둘로 한정하지만 lastSubmittedAt 은 제출 도메인 값이라
     * 지금은 정렬할 수 없다. 조용히 무시하면 화면은 정렬된 줄로 오해하므로 400 으로 막는다.
     * <b>요구사항 4번의 정렬 기준 자체가 미결정</b>이므로 팀이 정하면 여기부터 고친다.
     */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of("companyName", "name");

    private static final Set<String> SUBMISSION_STATUSES =
            Set.of("QUALIFIED", "UNQUALIFIED", "NOT_SUBMITTED");

    private final SupplierService supplierService;

    @Operation(summary = "협력업체 등록",
            description = "협력업체명·사업자등록번호·국가·담당자 정보를 입력해 등록한다. "
                    + "사업자등록번호와 담당자 이메일은 중복 등록할 수 없다.")
    @PostMapping
    public ResponseEntity<SupplierCreateResponse> createSupplier(
            @Valid @RequestBody SupplierCreateRequest request) {
        SupplierCreateResponse response = supplierService.createSupplier(request);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + response.id())).body(response);
    }

    @Operation(summary = "협력업체 수정 · 협력 끊김 처리",
            description = "담당자명·이메일·전화번호를 수정하고 협력 상태를 전환한다. "
                    + "status 를 보내면 상태 전이가, 보내지 않으면 정보 수정만 일어난다.")
    @PatchMapping("/{supplierId}")
    public ResponseEntity<SupplierUpdateResponse> updateSupplier(
            @PathVariable Long supplierId,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return ResponseEntity.ok(supplierService.updateSupplier(supplierId, request));
    }

    @Operation(summary = "협력업체 리스트 조회",
            description = "업체명 검색과 국가·협력상태·적격상태 필터, 정렬을 지원하며 "
                    + "최근 N개월 월별 제출 상태를 함께 반환한다.")
    @GetMapping
    public ResponseEntity<PageResponse<SupplierSummaryResponse>> searchSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String submissionStatus,
            @RequestParam(defaultValue = DEFAULT_MONTHS)
            @Min(value = 1, message = "months 는 1 이상이어야 합니다")
            @Max(value = 24, message = "months 는 24 이하여야 합니다") int months,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size,
            @RequestParam(defaultValue = "companyName,asc") String sort) {

        SupplierSearchCondition condition = new SupplierSearchCondition(
                blankToNull(search),
                blankToNull(country),
                parseStatus(status),
                parseSubmissionStatus(submissionStatus),
                months);

        return ResponseEntity.ok(supplierService.searchSuppliers(condition, toPageable(page, size, sort)));
    }

    @Operation(summary = "협력업체 상세 조회",
            description = "협력업체 기본정보와 공급 부품 목록, 최근 N개월 제출 이력·수신 경보·"
                    + "피드백 발송 이력을 조회한다.")
    @GetMapping("/{supplierId}")
    public ResponseEntity<SupplierDetailResponse> getSupplierDetail(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = DEFAULT_MONTHS)
            @Min(value = 1, message = "months 는 1 이상이어야 합니다")
            @Max(value = 24, message = "months 는 24 이하여야 합니다") int months) {
        return ResponseEntity.ok(supplierService.getSupplierDetail(supplierId, months));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SupplierStatus parseStatus(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return null;
        }
        SupplierStatus parsed = SupplierStatus.from(value);
        if (parsed == null) {
            throw invalidParameter("status", "status 는 ACTIVE 또는 INACTIVE 여야 합니다");
        }
        return parsed;
    }

    private String parseSubmissionStatus(String submissionStatus) {
        String value = blankToNull(submissionStatus);
        if (value != null && !SUBMISSION_STATUSES.contains(value)) {
            throw invalidParameter("submissionStatus",
                    "submissionStatus 는 QUALIFIED · UNQUALIFIED · NOT_SUBMITTED 중 하나여야 합니다");
        }
        return value;
    }

    /** sort 는 "필드,asc|desc" 형식이다(공통 규약 4항). */
    private Pageable toPageable(int page, int size, String sort) {
        String[] parts = sort.split(",", 2);
        String field = SORTABLE_FIELDS.get(parts[0].trim());
        if (field == null) {
            throw invalidParameter("sort",
                    "정렬 가능한 필드는 " + SORTABLE_FIELDS.keySet() + " 뿐입니다");
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

    private SupplierException invalidParameter(String field, String message) {
        return new SupplierException(
                SupplierErrorCode.INVALID_PARAMETER,
                SupplierErrorCode.INVALID_PARAMETER.defaultMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }
}
