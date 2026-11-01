package com.carenest.dto.account;

import com.carenest.utils.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull UserStatus status) {}
