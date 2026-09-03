package com.skala.cbam.mail.dto;

import org.springframework.core.io.Resource;

/** GET /api/v1/attachments/{id} 스트리밍 응답을 만들 때 필요한 값 (17번). */
public record AttachmentContent(Resource resource, String mimeType, String originalFilename, long sizeBytes) {
}
