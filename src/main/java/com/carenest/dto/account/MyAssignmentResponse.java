package com.carenest.dto.account;

import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;

/**
 * Phân công đang hiệu lực của người đăng nhập. {@code campusName}: tên điểm trường để App ghi nhãn nơi đang xem (bếp
 * dùng chung một tài khoản mỗi điểm trường — AUTH-22); null khi phân công không gắn điểm trường.
 */
public record MyAssignmentResponse(
        StaffRole staffRole,
        Long campusId,
        String campusName,
        Long classroomId,
        Long ageGroupId,
        LocalDate validFrom,
        LocalDate validTo) {}
