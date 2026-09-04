package com.skala.cbam.parts.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PartErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {
}
