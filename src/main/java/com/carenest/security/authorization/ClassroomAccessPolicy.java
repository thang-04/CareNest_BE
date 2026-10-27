package com.carenest.security.authorization;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Phạm vi lớp: GV của lớp (phân công còn hiệu lực hôm nay) hoặc BGH có phạm vi campus chứa lớp. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassroomAccessPolicy {

    private final StaffAssignmentService staffAssignmentService;
    private final ClassroomService classroomService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    public boolean canViewClassroom(CurrentUser user, Long classroomId) {
        // AUTH-03: GV chỉ lớp được phân công; phân công năm/kỳ trước không còn quyền (chưa có rule xem lịch sử)
        if (user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasActiveTeacherAssignment(
                        user.userId(), classroomId, LocalDate.now(clock))) {
            return true;
        }
        return classroomService
                .findCampusId(classroomId)
                .map(campusId -> campusAccessPolicy.canViewCampus(user, campusId))
                .orElse(false);
    }

    public void checkCanViewClassroom(CurrentUser user, Long classroomId) {
        if (!canViewClassroom(user, classroomId)) {
            log.warn("[checkCanViewClassroom]|userId={}|classroomId={}|denied", user.userId(), classroomId);
            throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
        }
    }
}
