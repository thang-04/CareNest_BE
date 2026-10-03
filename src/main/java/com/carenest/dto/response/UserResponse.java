package com.carenest.dto.response;

import com.carenest.entity.Role;
import com.carenest.entity.User;
import com.carenest.entity.UserStatus;

import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        String phoneNumber,
        String fullName,
        String position,
        // Presigned URL of the avatar, or null.
        String avatarUrl,
        Set<Role> roles,
        UserStatus status,
        boolean mustChangePassword
) {
    public static UserResponse from(User user, String avatarUrl) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getPosition(),
                avatarUrl,
                Set.copyOf(user.getRoles()),
                user.getStatus(),
                user.isMustChangePassword()
        );
    }
}
