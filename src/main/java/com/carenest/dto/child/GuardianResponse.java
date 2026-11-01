package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.UserStatus;
import java.time.Instant;

/** Phụ huynh của một trẻ; username là SĐT đăng nhập (AUTH-18). */
public record GuardianResponse(
        Long userId,
        String username,
        String fullName,
        GuardianRelationship relationship,
        UserStatus status,
        Instant linkedAt) {}
