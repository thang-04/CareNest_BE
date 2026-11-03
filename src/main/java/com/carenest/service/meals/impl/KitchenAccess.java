package com.carenest.service.meals.impl;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Điểm trường của dữ liệu bếp (AUTH-21, AUTH-22): bếp theo phân công KITCHEN_STAFF còn hiệu lực, HT/PHT đọc mọi điểm
 * trường. Không tin campusId client gửi cho bếp: ghi luôn lấy từ phân công.
 */
@Component
@RequiredArgsConstructor
class KitchenAccess {

    private final StaffAssignmentService staffAssignmentService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    /** Điểm trường được đọc: bếp ⇒ điểm trường mình (campusId chỉ để chọn khi có nhiều); HT/PHT ⇒ campusId bắt buộc. */
    Long resolveReadCampus(CurrentUser user, Long campusId) {
        if (user.hasRole(CareNestRole.KITCHEN_STAFF)) {
            Set<Long> own = kitchenCampuses(user);
            if (campusId != null) {
                if (!own.contains(campusId)) {
                    throw GlobalException.of(ApiCode.KITCHEN_CAMPUS_DENIED);
                }
                return campusId;
            }
            if (own.size() != 1) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "campusId");
            }
            return own.iterator().next();
        }
        if (campusId == null) {
            throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "campusId");
        }
        campusAccessPolicy.checkCanReadCampus(user, campusId);
        return campusId;
    }

    /** Đọc dữ liệu không gắn điểm trường (khung giờ ảnh): bếp có phân công hoặc HT/PHT còn phân công. */
    void checkCanRead(CurrentUser user) {
        if (user.hasRole(CareNestRole.KITCHEN_STAFF)) {
            if (kitchenCampuses(user).isEmpty()) {
                throw GlobalException.of(ApiCode.KITCHEN_CAMPUS_DENIED);
            }
            return;
        }
        if (!campusAccessPolicy.canReadAllCampuses(user)) {
            throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
        }
    }

    /** Điểm trường bếp ghi được: cần mã KITCHEN_RECORD và đúng một phân công bếp còn hiệu lực. */
    Long resolveWriteCampus(CurrentUser user) {
        if (!user.hasRole(CareNestRole.KITCHEN_STAFF) || !user.hasPermission(Permission.KITCHEN_RECORD)) {
            throw GlobalException.of(ApiCode.KITCHEN_CAMPUS_DENIED);
        }
        Set<Long> own = kitchenCampuses(user);
        if (own.size() != 1) {
            throw GlobalException.of(ApiCode.KITCHEN_CAMPUS_DENIED);
        }
        return own.iterator().next();
    }

    private Set<Long> kitchenCampuses(CurrentUser user) {
        return staffAssignmentService.findActiveCampusIds(user.userId(), StaffRole.KITCHEN_STAFF, LocalDate.now(clock));
    }
}
