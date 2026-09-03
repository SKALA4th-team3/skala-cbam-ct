package com.skala.cbam.mail.controller;

import com.skala.cbam.mail.error.MailErrorCode;
import com.skala.cbam.mail.error.MailErrorResponse;
import com.skala.cbam.mail.error.MailException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 이메일 접수 API 의 예외를 공통 에러 스키마로 바꾼다. {@code SupplierApiExceptionHandling} 과 같은
 * 이유로 {@code @ControllerAdvice} 대신 컨트롤러의 상위 클래스로 둔다.
 */
abstract class MailApiExceptionHandling {

    @ExceptionHandler(MailException.class)
    ResponseEntity<MailErrorResponse> handleMailException(MailException e, HttpServletRequest request) {
        MailErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status())
                .body(MailErrorResponse.of(code, e.getMessage(), request.getRequestURI(), e.details()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<MailErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        Map<String, Object> details = Map.of("fieldErrors", Map.of(e.getName(), "값의 형식이 올바르지 않습니다"));
        return respond(MailErrorCode.INVALID_PARAMETER, request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<MailErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            fieldErrors.put(lastPathNode(violation), violation.getMessage());
        }
        return respond(MailErrorCode.INVALID_PARAMETER, request, Map.of("fieldErrors", fieldErrors));
    }

    private ResponseEntity<MailErrorResponse> respond(
            MailErrorCode code, HttpServletRequest request, Map<String, Object> details) {
        return ResponseEntity.status(code.status())
                .body(MailErrorResponse.of(code, code.defaultMessage(), request.getRequestURI(), details));
    }

    private String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
