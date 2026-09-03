package com.skala.cbam.parts.controller;

import com.skala.cbam.parts.dto.PageResponse;
import com.skala.cbam.parts.dto.PartCreateRequest;
import com.skala.cbam.parts.dto.PartDetailResponse;
import com.skala.cbam.parts.dto.PartResponse;
import com.skala.cbam.parts.dto.PartSummaryResponse;
import com.skala.cbam.parts.dto.PartUpdateRequest;
import com.skala.cbam.parts.service.PartsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "부품", description = "부품 등록·수정·조회 API (요구사항 7~10번)")
@RestController
@RequestMapping("/api/v1/parts")
public class PartsController {

    private final PartsService partsService;

    public PartsController(PartsService partsService) {
        this.partsService = partsService;
    }

    @Operation(summary = "부품 등록", description = "요구사항 7번")
    @PostMapping
    public ResponseEntity<PartResponse> create(@Valid @RequestBody PartCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partsService.create(request));
    }

    @Operation(summary = "부품 수정", description = "요구사항 8번")
    @PatchMapping("/{partId}")
    public ResponseEntity<PartResponse> update(@PathVariable Long partId, @RequestBody PartUpdateRequest request) {
        return ResponseEntity.ok(partsService.update(partId, request));
    }

    @Operation(summary = "부품 리스트 조회", description = "요구사항 9번")
    @GetMapping
    public ResponseEntity<PageResponse<PartSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String cnCode,
            @PageableDefault(size = 20, sort = "partName") Pageable pageable
    ) {
        return ResponseEntity.ok(partsService.list(search, supplierId, cnCode, pageable));
    }

    @Operation(summary = "부품 단일 조회", description = "요구사항 10번")
    @GetMapping("/{partId}")
    public ResponseEntity<PartDetailResponse> getDetail(
            @PathVariable Long partId,
            // TODO(Submission 도메인 구현 후): confirmedData 조회 범위(개월)로 사용
            @RequestParam(required = false, defaultValue = "12") int months
    ) {
        return ResponseEntity.ok(partsService.getDetail(partId));
    }
}
