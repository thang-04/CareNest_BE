package com.carenest.service.organization.impl;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
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

    /** Lớp GV đang dạy; upcoming = năm đang chuẩn bị (CFG-YR-05): gồm cả phân công lớp năm mới chưa tới ngày bắt đầu. */
    Set<Long> teacherClassroomIds(CurrentUser user, boolean upcoming) {
        if (!user.hasRole(CareNestRole.TEACHER)) {
            return Set.of();
        }
        if (upcoming) {
            return staffAssignmentService.findCurrentTeacherClassroomIds(user.userId(), today());
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

    /**
     * CFG-YR-05 (Cần chốt 18/11/2026): năm sắp diễn ra chưa tới ngày mở chuẩn bị chỉ HT thấy; từ ngày đó PHT còn phân
     * công điểm trường, GV / Tổ trưởng có phân công năm đó thấy. Năm khác ai cũng thấy.
     */
    boolean canSeeYear(CurrentUser user, SchoolYear year) {
        if (year.getStatus() != SchoolYearStatus.PLANNED || user.hasRole(CareNestRole.PRINCIPAL)) {
            return true;
        }
        LocalDate today = today();
        if (today.isBefore(year.getPrepDate())) {
            return false;
        }
        return isVicePrincipalOnDuty(user, today)
                || staffAssignmentService.hasStaffAssignmentInYear(user.userId(), year.getId(), today);
    }

    /** Năm không được thấy ⇒ trả như không có năm đó (giống GET năm học). */
    void checkYearVisible(CurrentUser user, SchoolYear year) {
        if (!canSeeYear(user, year)) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND);
        }
    }

    private boolean isVicePrincipalOnDuty(CurrentUser user, LocalDate today) {
        return user.hasRole(CareNestRole.VICE_PRINCIPAL)
                && !staffAssignmentService
                        .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                        .isEmpty();
    }
}
