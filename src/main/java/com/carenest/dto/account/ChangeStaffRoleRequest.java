package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Đổi vai trò nhân sự tại chỗ (AUTH-25). assignment như lúc tạo tài khoản (AUTH-15): PHT/bếp ⇒ phân công campus mới
 * (PHT người đổi bắt buộc gửi để biết campus); GV ⇒ campusId chỉ để kiểm PHT, không tạo phân công lớp.
 */
public record ChangeStaffRoleRequest(
        @NotNull CareNestRole role, @Valid StaffAssignmentScopeRequest assignment) {}
