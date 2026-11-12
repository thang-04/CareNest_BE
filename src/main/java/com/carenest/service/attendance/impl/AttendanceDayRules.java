package com.carenest.service.attendance.impl;

import com.carenest.config.AttendanceProperties;
import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.attendance.DayUnlockResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceDayUnlock;
import com.carenest.repository.attendance.AttendanceDayUnlockRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Quy tắc ngày điểm danh dùng chung: ngày học, giờ chốt, khóa/mở lại, quyền theo lớp. */
@Component
@RequiredArgsConstructor
class AttendanceDayRules {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final Clock clock;
    private final AttendanceProperties properties;
    private final AttendanceCutoffService attendanceCutoffService;
    private final AttendanceDayUnlockRepository unlockRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final CampusAccessPolicy campusAccessPolicy;
    private final AccountService accountService;
    private final SchoolYearService schoolYearService;

    LocalDate today() {
        return LocalDate.now(clock);
    }

    Instant now() {
        return clock.instant();
    }

    // Thứ 2–6 trừ ngày nghỉ của năm học (SCH-05)
    boolean isSchoolDay(LocalDate date) {
        return schoolYearService.isSchoolDay(date);
    }

    LocalTime cutoff(Long schoolYearId, LocalDate date) {
        return attendanceCutoffService.findCutoffTime(schoolYearId, date).orElse(properties.defaultCutoff());
    }

    /** ATT-06: ngày đã qua, hoặc hôm nay từ giờ chốt trở đi. */
    boolean isBaseLocked(LocalDate date, LocalTime cutoff) {
        return isLockedAt(date, cutoff, clock.instant());
    }

    /** ATT-06 tại thời điểm at (giờ trường): đơn nghỉ tạo lúc ngày đã khóa thì không áp cho ngày đó (ATT-05b). */
    boolean isLockedAt(LocalDate date, LocalTime cutoff, Instant at) {
        LocalDateTime when = LocalDateTime.ofInstant(at, clock.getZone());
        LocalDate day = when.toLocalDate();
        return date.isBefore(day) || (date.equals(day) && !when.toLocalTime().isBefore(cutoff));
    }

    Optional<AttendanceDayUnlock> activeUnlock(Long classroomId, LocalDate date) {
        return unlockRepository.findFirstByClassroomIdAndDateAndValidUntilAfterOrderByIdDesc(classroomId, date, now());
    }

    /** cls null ⇒ ngày theo năm của yearRef (lớp bất kỳ của năm đang hoạt động; null = chưa có năm hoạt động). */
    DayInfoResponse dayInfo(LocalDate date, ClassroomInfoResponse cls, ClassroomInfoResponse yearRef) {
        ClassroomInfoResponse year = cls != null ? cls : yearRef;
        LocalTime cutoff = year == null ? properties.defaultCutoff() : cutoff(year.schoolYearId(), date);
        boolean baseLocked = isBaseLocked(date, cutoff);
        // Chỉ lớp năm đang hoạt động được mở lại; năm khác luôn chỉ xem
        Optional<AttendanceDayUnlock> unlock = cls != null && baseLocked && cls.yearStatus() == SchoolYearStatus.ACTIVE
                ? activeUnlock(cls.id(), date)
                : Optional.empty();
        return new DayInfoResponse(
                date,
                today(),
                LocalTime.now(clock).format(HH_MM),
                cutoff.format(HH_MM),
                year != null ? year.yearStart() : null,
                year != null ? year.yearEnd() : null,
                isSchoolDay(date),
                baseLocked,
                now(),
                baseLocked && unlock.isEmpty(),
                unlock.map(u -> toUnlockView(u, cls.name())).orElse(null));
    }

    // Quyền xem của BGH theo đúng scope HT cấp; GV/tổ trưởng vẫn theo lớp/nhóm tuổi được phân công.
    boolean canView(CurrentUser user, ClassroomInfoResponse cls) {
        // GV có quyền ghi điểm danh / sức khỏe luôn xem được lớp đang dạy, kể cả khi HT thu hồi quyền Xem (chỉ bớt xem
        // tổng hợp), để quyền ghi không bị mất theo
        if ((user.hasPermission(Permission.ATTENDANCE_RECORD) || user.hasPermission(Permission.DAILY_HEALTH_RECORD))
                && canRecord(user, cls)) {
            return true;
        }
        if (!user.hasPermission(Permission.ATTENDANCE_VIEW)) {
            return false;
        }
        if (user.hasRole(CareNestRole.PRINCIPAL) || user.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            return campusAccessPolicy.canEditCampus(user, Permission.ATTENDANCE_VIEW, cls.campusId())
                    || ledClosedYear(user, cls, Permission.ATTENDANCE_VIEW);
        }
        return classroomAccessPolicy.canReadClassroom(user, cls.id()) || taughtClosedYear(user, cls);
    }

    /** Xem một ngày của lớp: như canView, thêm người dạy thay xem lại đúng ngày mình dạy thay khi năm đã kết thúc. */
    boolean canViewDay(CurrentUser user, ClassroomInfoResponse cls, LocalDate date) {
        return canView(user, cls)
                || (cls.yearStatus() == SchoolYearStatus.CLOSED
                        && user.hasRole(CareNestRole.TEACHER)
                        && staffAssignmentService.hasSubstituteAssignmentOn(user.userId(), cls.id(), date));
    }

    /**
     * Năm đã kết thúc (Cần chốt 12/11/2026): HT / PHT xem lại (chỉ xem) theo phân công của chính năm đó, kể cả khi năm
     * nay không còn phân công; PHT theo điểm trường của năm đó, hoặc mọi điểm trường nếu quyền xem là toàn trường.
     */
    boolean ledClosedYear(CurrentUser user, ClassroomInfoResponse cls, Permission permission) {
        if (cls.yearStatus() != SchoolYearStatus.CLOSED || !user.hasPermission(permission)) {
            return false;
        }
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hadRoleInYear(user.userId(), StaffRole.PRINCIPAL, null, cls.schoolYearId())) {
            return true;
        }
        return user.hasRole(CareNestRole.VICE_PRINCIPAL)
                && staffAssignmentService.hadRoleInYear(
                        user.userId(),
                        StaffRole.VICE_PRINCIPAL,
                        user.hasSchoolWidePermission(permission) ? null : cls.campusId(),
                        cls.schoolYearId());
    }

    /** Nhóm tuổi người dùng đang làm tổ trưởng chuyên môn; rỗng nếu không phải tổ trưởng. */
    Set<Long> teamLeaderAgeGroupIds(CurrentUser user, Long schoolYearId) {
        // Tổ trưởng chỉ có quyền ở nhóm tuổi mình TRONG năm học được phân công (null = năm đang hoạt động)
        return user.hasRole(CareNestRole.TEACHER)
                ? staffAssignmentService.findScopedTeamLeaderAgeGroupIds(
                        user.userId(), schoolYearId, LocalDate.now(clock))
                : Set.of();
    }

    /** Người dùng là Tổ trưởng nhóm tuổi của lớp trong đúng năm học của lớp. */
    boolean leadsClass(CurrentUser user, ClassroomInfoResponse cls) {
        return cls.ageGroupId() != null
                && teamLeaderAgeGroupIds(user, cls.schoolYearId()).contains(cls.ageGroupId());
    }

    boolean canReadCampus(CurrentUser user, Long campusId) {
        return campusAccessPolicy.canEditCampus(user, Permission.ATTENDANCE_VIEW, campusId);
    }

    /**
     * Cần chốt 23/10/2026: giáo viên xem lại (chỉ xem) lớp mình từng dạy ở năm đã kết thúc; năm đang hoạt động vẫn
     * chỉ lớp đang được phân công (AUTH-03).
     */
    boolean taughtClosedYear(CurrentUser user, ClassroomInfoResponse cls) {
        return cls.yearStatus() == SchoolYearStatus.CLOSED
                && user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasTeacherAssignmentHistory(user.userId(), cls.id(), today());
    }

    /** AUTH-03: ghi điểm danh = giáo viên đang được phân công lớp. */
    boolean canRecord(CurrentUser user, ClassroomInfoResponse cls) {
        return user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasActiveTeacherAssignment(user.userId(), cls.id(), today());
    }

    boolean canRecordAttendance(CurrentUser user, ClassroomInfoResponse cls) {
        return user.hasPermission(Permission.ATTENDANCE_RECORD) && canRecord(user, cls);
    }

    /** Cần chốt 21/10/2026: HT mở lại mọi lớp, PHT chỉ lớp điểm trường mình (AUTH-01, AUTH-02). */
    boolean canUnlock(CurrentUser user, Long campusId) {
        return (user.hasRole(CareNestRole.PRINCIPAL) || user.hasRole(CareNestRole.VICE_PRINCIPAL))
                && campusAccessPolicy.canEditCampus(user, Permission.ATTENDANCE_UNLOCK, campusId);
    }

    DayUnlockResponse toUnlockView(AttendanceDayUnlock u, String className) {
        Map<Long, String> names = accountService.findFullNames(Stream.of(u.getUnlockedBy(), u.getRevokedBy())
                .filter(id -> id != null)
                .toList());
        return toUnlockView(u, className, names);
    }

    static DayUnlockResponse toUnlockView(AttendanceDayUnlock u, String className, Map<Long, String> names) {
        return new DayUnlockResponse(
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
                u.getRevokedAt());
    }
}
