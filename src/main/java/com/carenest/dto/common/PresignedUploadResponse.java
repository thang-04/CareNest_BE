package com.carenest.dto.common;

import java.time.Instant;
import java.util.Map;

/** Liên kết tải lên trực tiếp: client PUT tệp lên {@code url} kèm {@code headers}, rồi gọi API xác nhận với {@code uploadId}. */
public record PresignedUploadResponse(
        String uploadId, String url, String method, Map<String, String> headers, Instant expiresAt) {}
