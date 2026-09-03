package com.skala.cbam.parts.exception;

import com.skala.cbam.parts.controller.PartsController;
import com.skala.cbam.parts.dto.PartErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PartsController에만 적용되는 임시 예외 처리기.
 * 공통(global) 예외 처리는 다른 팀원이 올릴 예정이라, assignableTypes로 범위를 좁혀
 * 나중에 global 쪽 @RestControllerAdvice와 충돌하지 않게 한다.
 * TODO(global 공통 인프라 머지 후): 이 클래스는 지우고 global의 공통 핸들러를 쓴다.
 */
@RestControllerAdvice(assignableTypes = PartsController.class)
public class PartExceptionHandler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @ExceptionHandler(PartBusinessException.class)
    public ResponseEntity<PartErrorResponse> handleBusinessException(PartBusinessException ex, HttpServletRequest request) {
        PartErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(new PartErrorResponse(
                        OffsetDateTime.now(KST),
                        errorCode.getStatus().value(),
                        errorCode.name(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        ex.getDetails()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PartErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new PartErrorResponse(
                        OffsetDateTime.now(KST),
                        HttpStatus.BAD_REQUEST.value(),
                        PartErrorCode.INVALID_REQUEST.name(),
                        PartErrorCode.INVALID_REQUEST.getDefaultMessage(),
                        request.getRequestURI(),
                        Map.of("fieldErrors", fieldErrors)
                ));
    }
}
