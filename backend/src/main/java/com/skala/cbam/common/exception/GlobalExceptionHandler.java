package com.skala.cbam.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
        LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 업무 규칙 위반으로 발생한 BusinessException을 처리한다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException exception,
        HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        log.warn(
            "업무 예외 발생: code={}, path={}",
            errorCode.getCode(),
            request.getRequestURI()
        );

        ErrorResponse response = ErrorResponse.of(
            errorCode,
            request.getRequestURI(),
            exception.getDetails()
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    // @Valid를 사용한 요청 본문의 유효성 검증 실패를 처리한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        var fieldErrors = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fieldError ->
                new ErrorResponse.FieldErrorDetail(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
                )
            )
            .toList();

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        ErrorResponse response = ErrorResponse.of(
            errorCode,
            request.getRequestURI(),
            Map.of("fieldErrors", fieldErrors)
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    // 요청 파라미터 또는 경로 변수의 타입 변환 실패를 처리한다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;

        ErrorResponse response = ErrorResponse.of(
            errorCode,
            request.getRequestURI(),
            Map.of(
                "parameter", exception.getName(),
                "rejectedValue", String.valueOf(exception.getValue())
            )
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    // 요청 본문의 JSON 형식이 잘못되었거나 읽을 수 없는 경우를 처리한다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessageException(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ErrorResponse.of(
                    errorCode,
                    request.getRequestURI()
                )
            );
    }

    // 앞에서 처리하지 못한 예상 밖의 모든 서버 예외를 처리한다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error(
            "처리되지 않은 예외 발생: path={}",
            request.getRequestURI(),
            exception
        );

        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ErrorResponse.of(
                    errorCode,
                    request.getRequestURI()
                )
            );
    }
}