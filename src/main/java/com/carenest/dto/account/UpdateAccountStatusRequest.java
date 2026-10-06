package com.carenest.dto.account;

import com.carenest.entity.account.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull UserStatus status) {
}
