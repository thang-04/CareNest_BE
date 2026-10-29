package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.UserStatus;
import java.util.List;

/**
 * Tài khoản phụ huynh theo CCCD (AUTH-18): {@code children} chỉ trẻ chưa nghỉ trong phạm vi người hỏi, trẻ ngoài phạm
 * vi chỉ đếm; {@code relation} = quan hệ ghi ở hồ sơ anh/chị (gợi ý). {@code phone} = tên đăng nhập.
 */
public record ParentAccountLookupResponse(
        Long id,
        String fullName,
        String phone,
        String idNumber,
        String email,
        UserStatus status,
        List<LinkedChildResponse> children,
        int otherChildrenCount,
        GuardianRelationship relation) {}
