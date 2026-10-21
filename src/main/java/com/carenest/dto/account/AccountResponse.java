package com.carenest.dto.account;

import com.carenest.utils.enums.UserStatus;

public record AccountResponse(Long userId, String username, String fullName, String email, UserStatus status) {}
