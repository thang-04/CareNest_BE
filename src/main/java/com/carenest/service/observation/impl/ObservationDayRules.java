package com.carenest.service.observation.impl;

import com.carenest.dto.observation.AssessmentDayUnlockResponse;
import com.carenest.dto.observation.ClassCommentResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.entity.observation.ClassDailyComment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Quy tắc ngày đánh giá dùng chung: ngày học, khóa / mở lại, phạm vi giáo viên / PHT / HT (EDU-19, AUTH-02, AUTH-03). */
@Component
@RequiredArgsConstructor
class ObservationDayRules {

    private final Clock clock;
    private final AssessmentDayUnlockRepository unlockRepository;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final CampusAccessPolicy campusAccessPolicy;
    private final AccountService accountService;
    private final SchoolYearService schoolYearService;

    /** Ngày theo giờ trường (Clock của ứng dụng đặt Asia/Ho_Chi_Minh). */
    void lockClassForUpdate(Long id) {
        classroomService.lockForUpdate(id);
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    // OBS-06 / OBS-14 (Cần chốt 10/11/2026): việc cuối năm mở từ ngày kết thúc HK II; chưa đặt HK II thì ngày kết
    // thúc năm học
    LocalDate yearEndOpensOn(CurrentUser user, ClassroomInfoResponse cls) {
        var year = schoolYearService.getSchoolYear(user, cls.schoolYearId());
        LocalDate semester2End = year == null ? null : year.semester2End();
        return semester2End == null || semester2End.isAfter(cls.yearEnd()) ? cls.yearEnd() : semester2End;
    }

    Instant now() {
        return clock.instant();
    }

    // Thứ 2–6 trừ ngày nghỉ của năm học, như điểm danh (SCH-05)
    boolean isSchoolDay(LocalDate date) {
        return schoolYearService.isSchoolDay(date);
    }

    /** Ngày D khóa hết 23:59:59 ngày D: từ ngày sau trở đi là đã khóa. */
    boolean isBaseLocked(LocalDate date) {
        return date.isBefore(today());
    }

    Optional<AssessmentDayUnlock> activeUnlock(Long classroomId, LocalDate date) {
        return unlockRepository.findFirstByClassroomIdAndDateAndValidUntilAfterOrderByIdDesc(classroomId, date, now());
    }

    ClassroomInfoResponse findClass(Long classId) {
        return classroomService
                .findClassroomInfo(classId)
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
    }

    // BGH xem theo scope quyền HT cấp; GV/tổ trưởng vẫn theo lớp/nhóm tuổi được phân công.
    boolean canView(CurrentUser user, ClassroomInfoResponse cls) {
        if (!user.hasPermission(Permission.ASSESSMENT_VIEW)) {
            return false;
        }
        if (isLeader(user)) {
            return campusAccessPolicy.canEditCampus(user, Permission.ASSESSMENT_VIEW, cls.campusId());
        }
        return classroomAccessPolicy.canReadClassroom(user, cls.id()) || taughtClosedYear(user, cls);
    }

    /** Giáo viên xem lại (chỉ xem) lớp mình từng dạy ở năm đã kết thúc. */
    /** Nhóm tuổi người dùng đang làm tổ trưởng (AUTH-23); rỗng nếu không phải tổ trưởng. */
    Set<Long> teamLeaderAgeGroupIds(CurrentUser user, Long schoolYearId) {
        // Tổ trưởng chỉ có quyền ở nhóm tuổi mình TRONG năm học được phân công (null = năm đang hoạt động)
        return user.hasRole(CareNestRole.TEACHER)
                ? staffAssignmentService.findScopedTeamLeaderAgeGroupIds(user.userId(), schoolYearId, today())
                : Set.of();
    }

    /** Người dùng là Tổ trưởng nhóm tuổi của lớp trong đúng năm học của lớp. */
    boolean leadsClass(CurrentUser user, ClassroomInfoResponse cls) {
        return cls.ageGroupId() != null
                && teamLeaderAgeGroupIds(user, cls.schoolYearId()).contains(cls.ageGroupId());
    }

    boolean taughtClosedYear(CurrentUser user, ClassroomInfoResponse cls) {
        return cls.yearStatus() == SchoolYearStatus.CLOSED
                && user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasTeacherAssignmentHistory(user.userId(), cls.id(), today());
    }

    /** AUTH-03: ghi đánh giá = giáo viên đang được phân công lớp. */
    boolean canRecord(CurrentUser user, ClassroomInfoResponse cls) {
        return user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasActiveTeacherAssignment(user.userId(), cls.id(), today());
    }

    boolean isLeader(CurrentUser user) {
        return user.hasRole(CareNestRole.PRINCIPAL) || user.hasRole(CareNestRole.VICE_PRINCIPAL);
    }

    /** Ghi (xét duyệt, mở lại ngày): HT mọi điểm trường, PHT điểm trường mình (AUTH-01, AUTH-02). */
    boolean leadsCampus(CurrentUser user, Long campusId) {
        return isLeader(user) && campusAccessPolicy.canViewCampus(user, campusId);
    }

    /** Thao tác được HT cấp phạm vi campus hoặc toàn trường cho đúng mã quyền. */
    boolean leadsCampus(CurrentUser user, Permission permission, Long campusId) {
        return isLeader(user) && campusAccessPolicy.canEditCampus(user, permission, campusId);
    }

    /** Xem: HT và PHT xem mọi điểm trường (AUTH-20, Cần chốt 03/11/2026 cho cả Đánh giá trẻ). */
    boolean readsCampus(CurrentUser user, Long campusId) {
        return isLeader(user) && campusAccessPolicy.canEditCampus(user, Permission.ASSESSMENT_VIEW, campusId);
    }

    List<ClassCommentResponse> commentViews(List<ClassDailyComment> comments) {
        if (comments.isEmpty()) return List.of();
        Map<Long, String> names = accountService.findFullNames(comments.stream()
                .map(ClassDailyComment::getRecordedBy)
                .distinct()
                .toList());
        return comments.stream()
                .map(c -> new ClassCommentResponse(
                        c.getClassroomId(),
                        c.getDate(),
                        c.getText(),
                        c.getRecordedBy(),
                        names.get(c.getRecordedBy()),
                        c.getUpdatedAt()))
                .toList();
    }

    AssessmentDayUnlockResponse toView(AssessmentDayUnlock u, String className) {
        Map<Long, String> names = accountService.findFullNames(Stream.of(u.getUnlockedBy(), u.getRevokedBy())
                .filter(Objects::nonNull)
                .toList());
        return toView(u, className, names, now());
    }

    static AssessmentDayUnlockResponse toView(
            AssessmentDayUnlock u, String className, Map<Long, String> names, Instant now) {
        return new AssessmentDayUnlockResponse(
                u.getId(),
                u.getClassroomId(),
                className,
                u.getCampusId(),
                u.getDate(),
                u.getValidUntil(),
                u.getReason(),
                u.getUnlockedBy(),
                names.get(u.getUnlockedBy()),
                u.getUnlockedAt(),
                u.getRevokedBy(),
                u.getRevokedBy() == null ? null : names.get(u.getRevokedBy()),
                u.getRevokedAt(),
                u.isActiveAt(now));
    }
}
