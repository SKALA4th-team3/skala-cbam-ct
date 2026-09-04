package com.skala.cbam.feedback.controller;

import com.skala.cbam.feedback.error.FeedbackErrorCode;
import com.skala.cbam.feedback.error.FeedbackErrorResponse;
import com.skala.cbam.feedback.error.FeedbackException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** {@code SupplierApiExceptionHandling} 과 같은 이유로 {@code @ControllerAdvice} 대신 상속으로 처리한다. */
abstract class FeedbackApiExceptionHandling {

    @ExceptionHandler(FeedbackException.class)
    ResponseEntity<FeedbackErrorResponse> handleFeedbackException(
            FeedbackException e, HttpServletRequest request) {
        FeedbackErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status())
                .body(FeedbackErrorResponse.of(code, e.getMessage(), request.getRequestURI(), e.details()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<FeedbackErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        Map<String, Object> details = Map.of("fieldErrors", Map.of(e.getName(), "값의 형식이 올바르지 않습니다"));
        return respond(FeedbackErrorCode.INVALID_PARAMETER, request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<FeedbackErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            fieldErrors.put(lastPathNode(violation), violation.getMessage());
        }
        return respond(FeedbackErrorCode.INVALID_PARAMETER, request, Map.of("fieldErrors", fieldErrors));
    }

    private ResponseEntity<FeedbackErrorResponse> respond(
            FeedbackErrorCode code, HttpServletRequest request, Map<String, Object> details) {
        return ResponseEntity.status(code.status())
                .body(FeedbackErrorResponse.of(code, code.defaultMessage(), request.getRequestURI(), details));
    }

    private String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
