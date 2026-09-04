package com.skala.cbam.task.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** 공통 오류 봉투. FeedbackErrorResponse 와 같은 모양이다 — 화면이 한 가지 모양만 읽으면 된다. */
public record TaskErrorResponse(
        String timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static TaskErrorResponse of(TaskErrorCode code, String path) {
        String now = OffsetDateTime.now(SEOUL)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new TaskErrorResponse(now, code.status().value(), code.name(), code.defaultMessage(), path, Map.of());
    }
}
