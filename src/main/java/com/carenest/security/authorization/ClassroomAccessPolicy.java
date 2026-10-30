package com.carenest.security.authorization;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phạm vi lớp: GV của lớp (phân công còn hiệu lực hôm nay) hoặc BGH có phạm vi campus chứa lớp.
 * {@code canView*} = phạm vi được thao tác; {@code canRead*} theo phạm vi đọc của campus (xem {@link CampusAccessPolicy}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassroomAccessPolicy {

    private final StaffAssignmentService staffAssignmentService;
    private final ClassroomService classroomService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    public boolean canViewClassroom(CurrentUser user, Long classroomId) {
        return isAssignedTeacher(user, classroomId)
                || inCampus(classroomId, campusId -> campusAccessPolicy.canViewCampus(user, campusId));
    }

    public void checkCanViewClassroom(CurrentUser user, Long classroomId) {
        if (!canViewClassroom(user, classroomId)) {
            log.warn("[checkCanViewClassroom]|userId={}|classroomId={}|denied", user.userId(), classroomId);
            throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this class");
        }
    }

    public boolean canReadClassroom(CurrentUser user, Long classroomId) {
        return isAssignedTeacher(user, classroomId)
                || inCampus(classroomId, campusId -> campusAccessPolicy.canReadCampus(user, campusId));
    }

    public void checkCanReadClassroom(CurrentUser user, Long classroomId) {
        if (!canReadClassroom(user, classroomId)) {
            log.warn("[checkCanReadClassroom]|userId={}|classroomId={}|denied", user.userId(), classroomId);
            throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this class");
        }
    }

    // AUTH-03: GV chỉ lớp đang được phân công
    private boolean isAssignedTeacher(CurrentUser user, Long classroomId) {
        return user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasActiveTeacherAssignment(user.userId(), classroomId, LocalDate.now(clock));
    }

    // Lớp không tồn tại cho cùng kết quả với lớp ngoài phạm vi
    private boolean inCampus(Long classroomId, Predicate<Long> campusScope) {
        return classroomService.findCampusId(classroomId).map(campusScope::test).orElse(false);
    }
}
