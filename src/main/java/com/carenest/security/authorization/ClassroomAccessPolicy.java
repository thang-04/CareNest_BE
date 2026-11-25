package com.carenest.security.authorization;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
            throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
        }
    }

    public boolean canReadClassroom(CurrentUser user, Long classroomId) {
        return isAssignedTeacher(user, classroomId)
                || inCampus(classroomId, campusId -> campusAccessPolicy.canReadCampus(user, campusId));
    }

    /**
     * AUTH-03 (Cần chốt 15/11/2026): GV đọc được lớp vào ngày D khi có phân công GV (dạy chính hoặc dạy thay) ở lớp
     * chứa D và D không sau hôm nay; không cần phân công còn hiệu lực.
     */
    public boolean taughtOn(CurrentUser user, Long classroomId, LocalDate date) {
        return user.hasRole(CareNestRole.TEACHER)
                && !date.isAfter(LocalDate.now(clock))
                && staffAssignmentService.existsTeacherInClassBetween(user.userId(), classroomId, date, date);
    }

    /** Khoảng đọc của GV tại lớp (AUTH-03); rỗng nếu chưa từng dạy lớp. */
    public List<StaffAssignmentService.TeacherRange> taughtRanges(CurrentUser user, Long classroomId) {
        if (!user.hasRole(CareNestRole.TEACHER)) {
            return List.of();
        }
        return staffAssignmentService.findTeacherRanges(user.userId(), classroomId, LocalDate.now(clock));
    }

    public void checkCanReadClassroom(CurrentUser user, Long classroomId) {
        if (!canReadClassroom(user, classroomId)) {
            log.warn("[checkCanReadClassroom]|userId={}|classroomId={}|denied", user.userId(), classroomId);
            throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
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
