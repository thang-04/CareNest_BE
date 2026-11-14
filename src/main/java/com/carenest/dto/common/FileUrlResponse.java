package com.carenest.dto.common;

import java.time.Instant;

/** Liên kết xem/tải tệp có hạn (khoảng 15 phút); hết hạn thì xin lại qua API. */
public record FileUrlResponse(String url, Instant expiresAt) {}
