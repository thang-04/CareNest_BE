package com.carenest.dto.common;

import java.time.OffsetDateTime;

/** Giờ thật và giờ server đang dùng cho nghiệp vụ; {@code simulated=true} khi request có header giả lập hợp lệ. */
public record DevClockResponse(OffsetDateTime realNow, OffsetDateTime effectiveNow, boolean simulated) {}
