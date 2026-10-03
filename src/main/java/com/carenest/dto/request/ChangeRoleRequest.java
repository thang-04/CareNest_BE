package com.carenest.dto.request;

import com.carenest.entity.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        // VICE_PRINCIPAL, TEACHER or STAFF.
        @NotNull(message = "Vai trò không được để trống")
        Role role
) {
}
