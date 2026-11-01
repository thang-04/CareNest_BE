package com.carenest.dto.account;

import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Thay toàn bộ ngoại lệ của một người; quyền không có trong danh sách quay về theo ma trận role. */
public record UpdatePermissionOverridesRequest(
        @NotNull List<@NotNull @Valid OverrideItem> overrides,
        @NotBlank @Size(max = 500) String reason) {

    /** scope chỉ cho GRANT mã sửa theo mảng; bỏ trống ⇒ CAMPUS (AUTH-20). */
    public record OverrideItem(
            @NotNull Permission permission, @NotNull PermissionEffect effect, PermissionScope scope) {}
}
