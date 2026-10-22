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
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
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

    LocalDate today() {
        return LocalDate.now(clock);
    }

    Instant now() {
        return clock.instant();
    }

    // Tạm thứ 2–6 (chưa có lịch nghỉ lễ của trường)
    static boolean isSchoolDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    LocalTime cutoff(Long schoolYearId, LocalDate date) {
        return attendanceCutoffService.findCutoffTime(schoolYearId, date).orElse(properties.defaultCutoff());
    }

    /** ATT-06: ngày đã qua, hoặc hôm nay từ giờ chốt trở đi. */
    boolean isBaseLocked(LocalDate date, LocalTime cutoff) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        return date.isBefore(today) || (date.equals(today) && !now.toLocalTime().isBefore(cutoff));
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

    boolean canView(CurrentUser user, ClassroomInfoResponse cls) {
        return classroomAccessPolicy.canViewClassroom(user, cls.id()) || taughtClosedYear(user, cls);
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

    /** Cần chốt 21/10/2026: HT mở lại mọi lớp, PHT chỉ lớp điểm trường mình (AUTH-01, AUTH-02). */
    boolean canUnlock(CurrentUser user, Long campusId) {
        return (user.hasRole(CareNestRole.PRINCIPAL) || user.hasRole(CareNestRole.VICE_PRINCIPAL))
                && campusAccessPolicy.canViewCampus(user, campusId);
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
