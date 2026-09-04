package com.skala.cbam.products.controller;

import com.skala.cbam.products.error.ProductErrorCode;
import com.skala.cbam.products.error.ProductErrorResponse;
import com.skala.cbam.products.error.ProductException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** 제품 API의 예외를 공통 형식의 오류 응답으로 변환한다. */
abstract class ProductApiExceptionHandling {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @ExceptionHandler(ProductException.class)
    ResponseEntity<ProductErrorResponse> handleProductException(
            ProductException exception, HttpServletRequest request) {
        return toResponse(exception.getErrorCode(), exception.getMessage(),
                exception.getDetails(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProductErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        return toResponse(ProductErrorCode.INVALID_REQUEST,
                ProductErrorCode.INVALID_REQUEST.getDefaultMessage(),
                Map.of("fieldErrors", fieldErrors), request);
    }

    private ResponseEntity<ProductErrorResponse> toResponse(
            ProductErrorCode errorCode,
            String message,
            Map<String, Object> details,
            HttpServletRequest request) {
        return ResponseEntity.status(errorCode.getStatus()).body(new ProductErrorResponse(
                OffsetDateTime.now(SEOUL),
                errorCode.getStatus().value(),
                errorCode.name(),
                message,
                request.getRequestURI(),
                details));
    }
}
