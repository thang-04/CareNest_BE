package com.carenest.dto.child;

import com.carenest.utils.enums.SmsStatus;
import java.time.Instant;

/** CP-21: chỉ trả trạng thái gửi; mã kích hoạt chỉ được gửi qua SMS. */
public record ActivationCodeResponse(Long userId, String username, SmsStatus smsStatus, Instant expiresAt) {}
