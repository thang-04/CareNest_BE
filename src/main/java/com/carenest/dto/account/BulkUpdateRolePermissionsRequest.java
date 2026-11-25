package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** EDU-110 BR2.4: lưu nhiều vai trò trong một giao dịch; vai trò vắng mặt giữ nguyên. */
public record BulkUpdateRolePermissionsRequest(
        @NotBlank @Size(max = 500) String reason, @NotEmpty List<@Valid @NotNull RoleItem> roles) {

    public record RoleItem(
            @NotNull CareNestRole role, @NotNull List<@NotNull Permission> permissions) {}
}
