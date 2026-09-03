package com.skala.cbam.parts.controller;

import com.skala.cbam.parts.dto.PartErrorResponse;
import com.skala.cbam.parts.exception.PartBusinessException;
import com.skala.cbam.parts.exception.PartErrorCode;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 부품 API 의 예외를 공통 에러 스키마로 바꾼다(공통 규약 3항).
 *
 * <p><b>@RestControllerAdvice 를 쓰지 않는다.</b> 처음에는
 * {@code @RestControllerAdvice(assignableTypes = PartsController.class)} 로 두었는데,
 * 이러면 <b>/v3/api-docs 가 500 을 낸다.</b> springdoc 2.6.0 이 Spring Boot 4.1.1(Framework 7)에서
 * {@code ControllerAdviceBean.<init>(Object)} 를 찾다 NoSuchMethodError 로 죽기 때문이다 —
 * assignableTypes 로 적용 범위를 좁혀도 빈 자체는 만들어지므로 소용이 없고,
 * 부품뿐 아니라 <b>팀 전체의 Swagger 문서가 함께 죽는다.</b>
 * 협력업체 쪽이 같은 이유로 {@code SupplierApiExceptionHandling} 을 상위 클래스로 두었고,
 * 여기도 같은 모양을 따른다. 컨트롤러에 직접 붙은 @ExceptionHandler 는 ControllerAdviceBean 을
 * 만들지 않아 이 문제를 건드리지 않는다.
 *
 * <p>덤으로 남의 도메인에 영향을 주지 않는다 — 여기 붙은 핸들러는 {@link PartsController} 에만 걸린다.
 *
 * <p>catch-all(@ExceptionHandler(Exception.class))은 두지 않는다. Spring MVC 가 스스로 처리하던
 * 404·405 까지 삼켜 전부 500 으로 바뀐다. 명세에 정의된 예외만 좁게 잡는다.
 */
abstract class PartsApiExceptionHandling {

    /** 응답 시각은 Asia/Seoul 고정이다(공통 규약 5항). 배포 장비의 기본 타임존에 기대지 않는다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 업무 규칙 위반. 404 PART_NOT_FOUND · 409 DUPLICATE_* · 400 INVALID_CN_CODE 등. */
    @ExceptionHandler(PartBusinessException.class)
    ResponseEntity<PartErrorResponse> handleBusinessException(
            PartBusinessException ex, HttpServletRequest request) {
        return toResponse(ex.getErrorCode(), ex.getMessage(), request, ex.getDetails());
    }

    /** 요청 본문 검증 실패. 어느 필드가 왜 틀렸는지를 details.fieldErrors 로 돌려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<PartErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        return toResponse(PartErrorCode.INVALID_REQUEST, PartErrorCode.INVALID_REQUEST.getDefaultMessage(),
                request, Map.of("fieldErrors", fieldErrors));
    }

    /**
     * 쿼리 파라미터 타입 불일치. {@code ?supplierId=abc} 같은 요청이 여기로 온다.
     * 막지 않으면 Spring 기본 400 이 나가 명세의 code 필드가 빠진다 — 화면이 분기할 수 없다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<PartErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return toResponse(PartErrorCode.INVALID_PARAMETER, PartErrorCode.INVALID_PARAMETER.getDefaultMessage(),
                request, Map.of("parameter", ex.getName()));
    }

    private ResponseEntity<PartErrorResponse> toResponse(
            PartErrorCode errorCode, String message, HttpServletRequest request, Map<String, Object> details) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new PartErrorResponse(
                        OffsetDateTime.now(SEOUL),
                        errorCode.getStatus().value(),
                        errorCode.name(),
                        message,
                        request.getRequestURI(),
                        details
                ));
    }
}
