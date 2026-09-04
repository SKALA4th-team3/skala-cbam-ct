package com.skala.cbam.products.controller;

import com.skala.cbam.products.dto.ProductCreateRequest;
import com.skala.cbam.products.dto.ProductCreateResponse;
import com.skala.cbam.products.dto.ProductDetailResponse;
import com.skala.cbam.products.dto.ProductListResponse;
import com.skala.cbam.products.dto.ProductUpdateRequest;
import com.skala.cbam.products.dto.ProductUpdateResponse;
import com.skala.cbam.products.domain.ProductCalculationStatus;
import com.skala.cbam.products.service.ProductsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@Tag(name = "완제품", description = "완제품 등록·수정·조회 API (요구사항 12~15번)")
@RestController
@RequestMapping("/api/v1/products")
public class ProductsController extends ProductApiExceptionHandling {

    private final ProductsService productsService;

    public ProductsController(ProductsService productsService) {
        this.productsService = productsService;
    }

    @Operation(summary = "완제품 등록", description = "요구사항 12번")
    @PostMapping
    public ResponseEntity<ProductCreateResponse> create(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productsService.create(request));
    }

    @Operation(summary = "완제품 수정", description = "요구사항 13번")
    @PatchMapping("/{productId}")
    public ResponseEntity<ProductUpdateResponse> update(
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productsService.update(productId, request));
    }

    @Operation(summary = "완제품 리스트 조회", description = "요구사항 14번, 41번")
    @GetMapping
    public ResponseEntity<ProductListResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String cnCode,
            @RequestParam(required = false) String reportingMonth,
            @RequestParam(required = false) ProductCalculationStatus calculationStatus,
            @PageableDefault(size = 20, sort = "productName") Pageable pageable) {
        return ResponseEntity.ok(productsService.list(
                search, cnCode, reportingMonth, calculationStatus, pageable));
    }

    @Operation(summary = "완제품 단일 상세 조회", description = "요구사항 15번")
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getDetail(
            @PathVariable Long productId,
            @RequestParam(required = false) String reportingMonth) {
        return ResponseEntity.ok(productsService.getDetail(productId, reportingMonth));
    }
}
