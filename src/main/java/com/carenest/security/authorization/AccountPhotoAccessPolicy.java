package com.carenest.security.authorization;

import com.carenest.security.CurrentUser;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AUTH-27: ai xem được ảnh đại diện nhân sự. Admin/HT xem mọi người; PHT/GV/bếp xem nhân sự cùng điểm trường theo phân
 * công hôm nay và mọi HT; phụ huynh xem GV đang dạy lớp con. Ai cũng xem ảnh của mình. Chỉ lọc id, không biết ai có ảnh.
 */
@Component
@RequiredArgsConstructor
public class AccountPhotoAccessPolicy {
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final ClassroomService classroomService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final Clock clock;

    /** Tập con của {@code userIds} mà {@code viewer} được xem ảnh (chưa xét tài khoản có ảnh hay là nhân sự). */
    public Set<Long> visibleUserIds(CurrentUser viewer, Collection<Long> userIds) {
        Set<Long> requested = new HashSet<>(userIds);
        if (staffAccessPolicy.seesAllCampuses(viewer)) {
            return requested;
        }
        LocalDate today = LocalDate.now(clock);
        Set<Long> allowed = new HashSet<>();
        allowed.add(viewer.userId());
        Set<Long> campusIds = staffCampusIds(viewer, today);
        if (!campusIds.isEmpty()) {
            allowed.addAll(staffAssignmentService.findActiveStaffUserIds(campusIds, today));
            allowed.addAll(staffAssignmentService.findPrincipalIds(today));
        }
        if (viewer.hasRole(CareNestRole.PARENT)) {
            allowed.addAll(teachersOfChildren(viewer.userId(), today));
        }
        requested.retainAll(allowed);
        return requested;
    }

    // Campus của người xem theo phân công hôm nay; GV tính qua lớp vì phân công GV gắn lớp, không gắn campus
    private Set<Long> staffCampusIds(CurrentUser viewer, LocalDate today) {
        Set<Long> campusIds = new HashSet<>();
        if (viewer.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            campusIds.addAll(
                    staffAssignmentService.findActiveCampusIds(viewer.userId(), StaffRole.VICE_PRINCIPAL, today));
        }
        if (viewer.hasRole(CareNestRole.KITCHEN_STAFF)) {
            campusIds.addAll(
                    staffAssignmentService.findActiveCampusIds(viewer.userId(), StaffRole.KITCHEN_STAFF, today));
        }
        if (viewer.hasRole(CareNestRole.TEACHER)) {
            Set<Long> classroomIds = staffAssignmentService.findCurrentTeacherClassroomIds(viewer.userId(), today);
            if (!classroomIds.isEmpty()) {
                campusIds.addAll(classroomService.findCampusIds(classroomIds).values());
            }
        }
        return campusIds;
    }

    private Set<Long> teachersOfChildren(Long guardianUserId, LocalDate today) {
        List<Long> childIds = guardianChildLinkService.findChildIdsOfGuardian(guardianUserId);
        if (childIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> classroomIds = new HashSet<>(enrollmentService
                .findActiveClassroomIdByChildIds(childIds, today)
                .values());
        if (classroomIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> teacherIds = new HashSet<>();
        staffAssignmentService
                .findCurrentTeacherIds(classroomIds, today)
                .values()
                .forEach(teacherIds::addAll);
        return teacherIds;
    }
}
