package com.carenest.dto.account;

import java.time.Instant;

/** Một chữ ký ảnh của tài khoản; ảnh tải riêng qua {@code GET /signatures/me/{id}/content}. */
public record UserSignatureResponse(Long id, boolean isDefault, String contentType, int sizeBytes, Instant createdAt) {}
