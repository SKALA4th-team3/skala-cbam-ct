package com.skala.cbam.supplier.controller;

import com.skala.cbam.supplier.error.SupplierErrorCode;
import com.skala.cbam.supplier.error.SupplierErrorResponse;
import com.skala.cbam.supplier.error.SupplierException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 협력업체 API 의 예외를 공통 에러 스키마로 바꾼다(공통 규약 3항).
 *
 * <p><b>@ControllerAdvice 를 쓰지 않고 컨트롤러의 상위 클래스로 둔 이유가 둘 있다.</b>
 *
 * <p>첫째, 남의 도메인에 영향을 주지 않는다. @ControllerAdvice 는 저장소 전체 컨트롤러에 걸려
 * 부품·완제품·메일 담당자가 던지는 예외까지 이 코드가 가로챈다. 여기 붙은 핸들러는
 * {@link SupplierController} 에만 적용된다.
 *
 * <p>둘째, springdoc 2.6.0 이 Spring Boot 4.1.1(Framework 7)에서 깨지는 것을 피한다.
 * Framework 7 에서 ControllerAdviceBean 생성자가 바뀌어, @ControllerAdvice 빈이 <b>하나라도</b>
 * 있으면 /v3/api-docs 가 NoSuchMethodError 로 500 을 낸다. 컨트롤러에 직접 붙은 @ExceptionHandler 는
 * ControllerAdviceBean 을 만들지 않아 이 문제를 건드리지 않는다.
 * (springdoc 3.0.0 으로 올리면 해결되지만 그건 팀 공용 빌드 설정이라 별도 합의 사항이다.)
 *
 * <p>catch-all(@ExceptionHandler(Exception.class))은 두지 않는다. 한 번 넣어 보니
 * Spring MVC 가 스스로 처리하던 예외까지 삼켜 매핑 없는 경로의 404 와 메서드 불일치 405 가
 * 전부 500 으로 바뀌었다. 명세에 정의된 예외만 좁게 잡는다.
 */
abstract class SupplierApiExceptionHandling {

    /** 업무 규칙 위반. 404 SUPPLIER_NOT_FOUND · 409 DUPLICATE_* · 400 INVALID_STATUS 등. */
    @ExceptionHandler(SupplierException.class)
    ResponseEntity<SupplierErrorResponse> handleSupplierException(
            SupplierException e, HttpServletRequest request) {
        SupplierErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status())
                .body(SupplierErrorResponse.of(code, e.getMessage(), request.getRequestURI(), e.details()));
    }

    /**
     * 요청 바디의 Bean Validation 위반.
     * 예) companyName 누락, contactEmail 형식 오류 → 400 INVALID_REQUEST (명세 №1 · №2)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<SupplierErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return respond(SupplierErrorCode.INVALID_REQUEST, request, Map.of("fieldErrors", fieldErrors));
    }

    /**
     * 경로 변수·쿼리 파라미터의 타입이 맞지 않는 경우.
     * 예) months=abc, page=xyz → 400 INVALID_PARAMETER (명세 №3 · №4)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<SupplierErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        Map<String, Object> details = Map.of(
                "fieldErrors", Map.of(e.getName(), "값의 형식이 올바르지 않습니다")
        );
        return respond(SupplierErrorCode.INVALID_PARAMETER, request, details);
    }

    /**
     * @Validated 를 단 컨트롤러의 파라미터 제약(@Min · @Max 등) 위반.
     * 예) months=0, months=25, size=0 → 400 INVALID_PARAMETER (명세 №3 · №4)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<SupplierErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            fieldErrors.put(lastPathNode(violation), violation.getMessage());
        }
        return respond(SupplierErrorCode.INVALID_PARAMETER, request, Map.of("fieldErrors", fieldErrors));
    }

    private ResponseEntity<SupplierErrorResponse> respond(
            SupplierErrorCode code, HttpServletRequest request, Map<String, Object> details) {
        return ResponseEntity.status(code.status())
                .body(SupplierErrorResponse.of(
                        code, code.defaultMessage(), request.getRequestURI(), details));
    }

    /** propertyPath 는 "getSupplierDetail.months" 형태라 마지막 노드만 필드명으로 쓴다. */
    private String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
