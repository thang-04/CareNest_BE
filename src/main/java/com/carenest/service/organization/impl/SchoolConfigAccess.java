package com.carenest.service.organization.impl;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
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
 * PHT xem trong điểm trường được phân công (AUTH-02).
 */
@Component
@RequiredArgsConstructor
class SchoolConfigAccess {

    private final StaffAssignmentService staffAssignmentService;
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

    // TODO(Cần): quyền PHT theo từng chức năng (Phân công PHT) chưa làm — hiện PHT chỉ xem điểm trường mình
    Set<Long> vicePrincipalCampusIds(CurrentUser user) {
        if (!user.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            return Set.of();
        }
        return staffAssignmentService.findActiveAssignments(user.userId(), today()).stream()
                .filter(a -> a.staffRole() == StaffRole.VICE_PRINCIPAL && a.campusId() != null)
                .map(StaffAssignmentSummaryResponse::campusId)
                .collect(Collectors.toSet());
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

    /** Điểm trường được xem: null = toàn trường (Hiệu trưởng). */
    Set<Long> viewableCampusIds(CurrentUser user) {
        if (isPrincipal(user)) {
            return null;
        }
        Set<Long> campusIds = vicePrincipalCampusIds(user);
        if (campusIds.isEmpty()) {
            throw GlobalException.of(ApiCode.SCHOOL_CONFIG_ACCESS_DENIED);
        }
        return campusIds;
    }

    void checkCanViewCampus(CurrentUser user, Long campusId) {
        Set<Long> campusIds = viewableCampusIds(user);
        if (campusIds != null && !campusIds.contains(campusId)) {
            throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
        }
    }
}
