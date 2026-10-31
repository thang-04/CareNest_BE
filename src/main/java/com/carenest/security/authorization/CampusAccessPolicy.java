package com.carenest.security.authorization;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phạm vi campus của BGH: cần đồng thời role trong token VÀ phân công còn hiệu lực hôm nay (ADR-0003).
 * Bếp có phân công campus nhưng chỉ cho dữ liệu bếp (AUTH-05) nên không được xem dữ liệu campus qua đây.
 * {@code canView*} = phạm vi được thao tác (dùng cho ghi); {@code canRead*} rộng hơn, chỉ cho đường đọc.
 * Quản lý tài khoản/phân công/phụ huynh dùng {@link StaffAccessPolicy} theo campus phụ trách.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampusAccessPolicy {

    private final StaffAssignmentService staffAssignmentService;
    private final Clock clock;

    public boolean canViewCampus(CurrentUser user, Long campusId) {
        LocalDate today = LocalDate.now(clock);
        // AUTH-01: Hiệu trưởng toàn trường
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            return true;
        }
        // AUTH-02: Hiệu phó chỉ campus được phân công
        return user.hasRole(CareNestRole.VICE_PRINCIPAL)
                && staffAssignmentService.hasActiveCampusAssignment(
                        user.userId(), StaffRole.VICE_PRINCIPAL, campusId, today);
    }

    public void checkCanViewCampus(CurrentUser user, Long campusId) {
        if (!canViewCampus(user, campusId)) {
            log.warn("[checkCanViewCampus]|userId={}|campusId={}|denied", user.userId(), campusId);
            throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
        }
    }

    /** AUTH-20 (chốt 31/10): HT và PHT còn phân công đọc dữ liệu trường ở mọi campus, chỉ xem. */
    public boolean canReadAllCampuses(CurrentUser user) {
        LocalDate today = LocalDate.now(clock);
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            return true;
        }
        return user.hasRole(CareNestRole.VICE_PRINCIPAL)
                && !staffAssignmentService
                        .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                        .isEmpty();
    }

    // Đọc không phụ thuộc campus nào: ai đọc được thì đọc mọi campus (AUTH-20)
    public boolean canReadCampus(CurrentUser user, Long campusId) {
        return canReadAllCampuses(user);
    }

    /**
     * AUTH-20: sửa việc của một mảng = có mã quyền (ma trận/GRANT) và campus nằm trong phạm vi: HT mọi campus; PHT
     * được GRANT phạm vi cả trường thì mọi campus (vẫn cần phân công PHT), còn lại campus đang phân công.
     */
    public boolean canEditCampus(CurrentUser user, Permission permission, Long campusId) {
        if (!user.hasPermission(permission)) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            return true;
        }
        if (!user.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            return false;
        }
        if (user.hasSchoolWidePermission(permission)) {
            return !staffAssignmentService
                    .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                    .isEmpty();
        }
        return staffAssignmentService.hasActiveCampusAssignment(
                user.userId(), StaffRole.VICE_PRINCIPAL, campusId, today);
    }

    public void checkCanReadCampus(CurrentUser user, Long campusId) {
        if (!canReadCampus(user, campusId)) {
            log.warn("[checkCanReadCampus]|userId={}|campusId={}|denied", user.userId(), campusId);
            throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
        }
    }
}
