package com.carenest.dto.response;

import java.time.Instant;

public record ImageResponse(
        Long id,
        String title,
        // Presigned URL, valid for app.storage.presigned-url-expiration.
        String url,
        Instant createdAt
) {
}
