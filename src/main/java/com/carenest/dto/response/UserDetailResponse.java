package com.carenest.dto.response;

import java.time.Instant;
import java.util.List;

public record UserDetailResponse(
        UserResponse account,
        Instant createdAt,
        // Null when the user never logged in (the account can then still be deleted).
        Instant lastLoginAt,
        // Children when the user is a parent, otherwise empty.
        List<ChildResponse> children
) {
}
