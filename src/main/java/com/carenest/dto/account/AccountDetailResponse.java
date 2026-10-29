package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.List;

/** Chi tiết tài khoản cho màn quản lý nhân sự: mọi phân công (kể cả đã kết thúc), mới nhất trước. */
public record AccountDetailResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        CareNestRole role,
        UserStatus status,
        List<StaffAssignmentResponse> assignments) {}
