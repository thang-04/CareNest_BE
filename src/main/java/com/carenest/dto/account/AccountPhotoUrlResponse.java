package com.carenest.dto.account;

import java.time.Instant;

/** Link xem có hạn ảnh đại diện của một tài khoản. */
public record AccountPhotoUrlResponse(Long userId, String url, Instant expiresAt) {}
