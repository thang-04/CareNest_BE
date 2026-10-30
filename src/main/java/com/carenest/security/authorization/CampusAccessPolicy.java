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
            throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this campus");
        }
    }

    public boolean canReadCampus(CurrentUser user, Long campusId) {
        if (canViewCampus(user, campusId)) {
            return true;
        }
        // AUTH-20: xem liên cơ sở chỉ đọc, vẫn phải đang là PHT của ít nhất một campus
        return user.hasRole(CareNestRole.VICE_PRINCIPAL)
                && user.hasPermission(Permission.ALL_CAMPUS_VIEW)
                && !staffAssignmentService
                        .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, LocalDate.now(clock))
                        .isEmpty();
    }

    public void checkCanReadCampus(CurrentUser user, Long campusId) {
        if (!canReadCampus(user, campusId)) {
            log.warn("[checkCanReadCampus]|userId={}|campusId={}|denied", user.userId(), campusId);
            throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this campus");
        }
    }
}
