package com.skala.cbam.supplier.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 협력업체 API 의 4xx 응답 본문. 형태는 API 명세 v10 공통 규약 3항의 공통 에러 스키마 그대로다.
 *
 * <p>timestamp 를 OffsetDateTime 이 아니라 String 으로 두는 이유:
 * 명세가 요구하는 표기는 초 단위 오프셋 포함(2026-09-03T15:00:00+09:00)인데,
 * Jackson 기본 직렬화는 나노초를 남겨 형식이 흔들린다. 여기서 초로 잘라 확정한다.
 *
 * <p>details 는 필드 단위 오류·부가 정보만 담는다. 값이 없어도 키를 생략하지 않고 빈 객체를 반환한다.
 */
public record SupplierErrorResponse(
        String timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {

    /** 서버·응답 모두 Asia/Seoul(+09:00) 고정 — 공통 규약 5항. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static SupplierErrorResponse of(
            SupplierErrorCode code, String message, String path, Map<String, Object> details) {
        String now = OffsetDateTime.now(SEOUL)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new SupplierErrorResponse(
                now,
                code.status().value(),
                code.name(),
                message,
                path,
                details == null ? Map.of() : details
        );
    }
}
