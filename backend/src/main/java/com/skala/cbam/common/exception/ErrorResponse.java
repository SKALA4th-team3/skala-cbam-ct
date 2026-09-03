package com.skala.cbam.common.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

public record ErrorResponse(

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    OffsetDateTime timestamp,

    int status,
    String code,
    String message,
    String path,
    Map<String, Object> details
) {

    private static final ZoneId SEOUL =
        ZoneId.of("Asia/Seoul");

    // 부가 정보가 없는 기본 오류 응답을 생성한다.
    public static ErrorResponse of(
        ErrorCode errorCode,
        String path
    ) {
        return of(errorCode, path, Map.of());
    }

    // 필드 오류 등의 부가 정보를 포함한 오류 응답을 생성한다.
    public static ErrorResponse of(
        ErrorCode errorCode,
        String path,
        Map<String, Object> details
    ) {
        return new ErrorResponse(
            OffsetDateTime.now(SEOUL),
            errorCode.getHttpStatus().value(),
            errorCode.getCode(),
            errorCode.getMessage(),
            path,
            details
        );
    }

    // 입력값 검증에 실패한 필드명과 오류 메시지를 표현한다.
    public record FieldErrorDetail(
        String field,
        String message
    ) {
    }
}