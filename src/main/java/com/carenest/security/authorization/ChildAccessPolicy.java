package com.carenest.security.authorization;

import com.carenest.exception.GlobalException;
import com.carenest.security.CareNestRole;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phạm vi trẻ: phụ huynh có liên kết, hoặc nhân sự có phạm vi lớp trẻ đang học hôm nay.
 * Id không tồn tại và id ngoài phạm vi cho cùng kết quả (không lộ id để dò) — gọi trước khi load dữ liệu trẻ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChildAccessPolicy {

    // Bếp (AUTH-05) và Admin (AUTH-06, P-16) không có quyền xem hồ sơ trẻ; Admin không bypass phạm vi
    private static final Set<CareNestRole> STAFF_ROLES_WITH_CHILD_SCOPE =
            Set.of(CareNestRole.TEACHER, CareNestRole.VICE_PRINCIPAL, CareNestRole.PRINCIPAL);

    private final EnrollmentService enrollmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final Clock clock;

    public boolean canViewChild(CurrentUser user, Long childId) {
        // AUTH-04: phụ huynh chỉ trẻ có liên kết với chính tài khoản đang đăng nhập
        if (user.hasRole(CareNestRole.PARENT) && guardianChildLinkService.isGuardianOf(user.userId(), childId)) {
            return true;
        }
        if (STAFF_ROLES_WITH_CHILD_SCOPE.stream().noneMatch(user::hasRole)) {
            return false;
        }
        return enrollmentService.findActiveClassroomIds(childId, LocalDate.now(clock)).stream()
                .anyMatch(classroomId -> classroomAccessPolicy.canViewClassroom(user, classroomId));
    }

    public void checkCanViewChild(CurrentUser user, Long childId) {
        if (!canViewChild(user, childId)) {
            log.warn("[checkCanViewChild]|userId={}|childId={}|denied", user.userId(), childId);
            throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this child");
        }
    }
}
