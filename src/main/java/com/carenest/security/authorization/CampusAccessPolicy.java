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
 * Chỉ dùng cho đường đọc; quản lý tài khoản/phân công/phụ huynh dùng {@link StaffAccessPolicy} theo campus phụ trách.
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
        if (!user.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            return false;
        }
        // AUTH-02: Hiệu phó chỉ campus được phân công
        if (staffAssignmentService.hasActiveCampusAssignment(
                user.userId(), StaffRole.VICE_PRINCIPAL, campusId, today)) {
            return true;
        }
        // AUTH-20: được cấp xem liên cơ sở thì xem mọi campus, nhưng vẫn phải đang là PHT của ít nhất một campus
        return user.hasPermission(Permission.ALL_CAMPUS_VIEW)
                && !staffAssignmentService
                        .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                        .isEmpty();
    }

    public void checkCanViewCampus(CurrentUser user, Long campusId) {
        if (!canViewCampus(user, campusId)) {
            log.warn("[checkCanViewCampus]|userId={}|campusId={}|denied", user.userId(), campusId);
            throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this campus");
        }
    }
}
