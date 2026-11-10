package com.carenest.dto.meals;

import java.time.Instant;

/** Ghi hủy mẫu (NUT-21); {@code disposedAt} bỏ trống ⇒ lúc ghi, phải cách lúc lấy mẫu ít nhất 24 giờ. */
public record FoodSampleDisposeRequest(Instant disposedAt) {}
