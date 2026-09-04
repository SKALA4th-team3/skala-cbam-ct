package com.skala.cbam.products.controller;

import com.skala.cbam.products.dto.ProductCreateRequest;
import com.skala.cbam.products.dto.ProductCreateResponse;
import com.skala.cbam.products.service.ProductsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "완제품", description = "완제품 등록 API (요구사항 12번)")
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
}
