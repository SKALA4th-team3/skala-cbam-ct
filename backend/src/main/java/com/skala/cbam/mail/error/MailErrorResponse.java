package com.skala.cbam.mail.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** 이메일 접수 API 의 4xx·409 응답 본문. 공통 규약 3항의 공통 에러 스키마 그대로다. */
public record MailErrorResponse(
        String timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MailErrorResponse of(MailErrorCode code, String message, String path, Map<String, Object> details) {
        String now = OffsetDateTime.now(SEOUL)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new MailErrorResponse(now, code.status().value(), code.name(), message, path,
                details == null ? Map.of() : details);
    }
}
