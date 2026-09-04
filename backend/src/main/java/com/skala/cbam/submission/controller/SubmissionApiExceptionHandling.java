package com.skala.cbam.submission.controller;

import com.skala.cbam.submission.error.SubmissionErrorCode;
import com.skala.cbam.submission.error.SubmissionErrorResponse;
import com.skala.cbam.submission.error.SubmissionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 제출 데이터 API 의 예외를 공통 에러 스키마로 바꾼다. {@code SupplierApiExceptionHandling} 과 같은
 * 이유로 {@code @ControllerAdvice} 대신 컨트롤러의 상위 클래스로 둔다 — 남의 도메인에 안 번지고,
 * springdoc 2.6.0 + Spring Boot 4.1.1 조합에서 {@code @ControllerAdvice} 빈이 있으면
 * {@code /v3/api-docs} 가 500 나는 문제를 피한다.
 */
abstract class SubmissionApiExceptionHandling {

    /** 업무 규칙 위반. 404 SUBMISSION_NOT_FOUND · 409 ALREADY_CONFIRMED · 400 NOT_QUALIFIED 등. */
    @ExceptionHandler(SubmissionException.class)
    ResponseEntity<SubmissionErrorResponse> handleSubmissionException(
            SubmissionException e, HttpServletRequest request) {
        SubmissionErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status())
                .body(SubmissionErrorResponse.of(code, e.getMessage(), request.getRequestURI(), e.details()));
    }

    /** 쿼리 파라미터·경로 변수 타입이 안 맞는 경우. 예) reportingMonth=abc, page=xyz. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<SubmissionErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        Map<String, Object> details = Map.of(
                "fieldErrors", Map.of(e.getName(), "값의 형식이 올바르지 않습니다")
        );
        return respond(SubmissionErrorCode.INVALID_PARAMETER, request, details);
    }

    /** @Validated 파라미터 제약(@Min·@Max 등) 위반. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<SubmissionErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            fieldErrors.put(lastPathNode(violation), violation.getMessage());
        }
        return respond(SubmissionErrorCode.INVALID_PARAMETER, request, Map.of("fieldErrors", fieldErrors));
    }

    private ResponseEntity<SubmissionErrorResponse> respond(
            SubmissionErrorCode code, HttpServletRequest request, Map<String, Object> details) {
        return ResponseEntity.status(code.status())
                .body(SubmissionErrorResponse.of(
                        code, code.defaultMessage(), request.getRequestURI(), details));
    }

    private String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
