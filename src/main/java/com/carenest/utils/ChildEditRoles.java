package com.carenest.utils;

import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CareNestRole;

/** Ai sửa hồ sơ trẻ trực tiếp, ai phải gửi yêu cầu duyệt (AUTH-10, CHD-11). */
public final class ChildEditRoles {

    private ChildEditRoles() {}

    /** GV không kiêm BGH: sửa hồ sơ, khai báo sức khỏe phải gửi yêu cầu để HT/PHT duyệt (CHD-11). */
    public static boolean teacherOnly(CurrentUser actor) {
        return actor.hasRole(CareNestRole.TEACHER)
                && !actor.hasRole(CareNestRole.VICE_PRINCIPAL)
                && !actor.hasRole(CareNestRole.PRINCIPAL);
    }
}
