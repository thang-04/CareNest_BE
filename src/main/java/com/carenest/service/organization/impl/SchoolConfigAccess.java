package com.carenest.service.organization.impl;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Quyền trên màn Cấu hình trường: ghi chỉ Hiệu trưởng có phân công còn hiệu lực (AUTH-01);
 * PHT còn phân công xem mọi điểm trường, chỉ xem (AUTH-20).
 */
@Component
@RequiredArgsConstructor
class SchoolConfigAccess {

    private final StaffAssignmentService staffAssignmentService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    LocalDate today() {
        return LocalDate.now(clock);
    }

    ZoneId zone() {
        return clock.getZone();
    }

    boolean isPrincipal(CurrentUser user) {
        return user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today());
    }

    void checkPrincipal(CurrentUser user) {
        if (!isPrincipal(user)) {
            throw GlobalException.of(ApiCode.PRINCIPAL_ONLY, "thay đổi cấu hình trường");
        }
    }

    Set<Long> teacherClassroomIds(CurrentUser user) {
        if (!user.hasRole(CareNestRole.TEACHER)) {
            return Set.of();
        }
        return staffAssignmentService.findActiveAssignments(user.userId(), today()).stream()
                .filter(a -> a.staffRole() == StaffRole.TEACHER && a.classroomId() != null)
                .map(StaffAssignmentSummaryResponse::classroomId)
                .collect(Collectors.toSet());
    }

    /** AUTH-20: HT và PHT còn phân công xem cấu hình trường ở mọi điểm trường. */
    boolean canViewSchool(CurrentUser user) {
        return campusAccessPolicy.canReadAllCampuses(user);
    }

    void checkCanViewSchool(CurrentUser user) {
        if (!canViewSchool(user)) {
            throw GlobalException.of(ApiCode.SCHOOL_CONFIG_ACCESS_DENIED);
        }
    }
}
