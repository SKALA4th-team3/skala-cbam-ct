package com.skala.cbam.feedback.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public record FeedbackErrorResponse(
        String timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static FeedbackErrorResponse of(
            FeedbackErrorCode code, String message, String path, Map<String, Object> details) {
        String now = OffsetDateTime.now(SEOUL)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new FeedbackErrorResponse(now, code.status().value(), code.name(), message, path,
                details == null ? Map.of() : details);
    }
}
