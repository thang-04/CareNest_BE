package com.carenest.dto.account;

import java.time.Instant;

/** Ảnh chân dung của tài khoản sau khi tải: còn phải cập nhật hồ sơ hay không (phụ huynh chưa có ảnh). */
public record AccountPhotoResponse(boolean photoUploaded, Instant photoUpdatedAt, boolean profileUpdateRequired) {}
