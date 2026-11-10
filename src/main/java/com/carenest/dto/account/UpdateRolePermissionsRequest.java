package com.carenest.dto.account;

import com.carenest.utils.enums.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Thay toàn bộ quyền đang bật của role; lý do lưu vào lịch sử (AUTH-14). */
public record UpdateRolePermissionsRequest(
        @NotNull List<@NotNull Permission> permissions,
        @NotBlank @Size(max = 500) String reason) {}
