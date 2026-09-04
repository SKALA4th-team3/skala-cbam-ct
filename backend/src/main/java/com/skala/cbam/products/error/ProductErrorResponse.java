package com.skala.cbam.products.error;

import java.time.OffsetDateTime;
import java.util.Map;

public record ProductErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {
}
