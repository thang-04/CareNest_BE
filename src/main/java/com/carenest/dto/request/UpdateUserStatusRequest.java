package com.carenest.dto.request;

import com.carenest.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        UserStatus status
) {
}
