package com.carenest.service.attendance.impl;

import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceDay;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceResponse;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceTotals;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentDailyHealthResponse;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentTodayResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceDayUnlock;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.AttendanceRecordHistory;
import com.carenest.entity.attendance.DailyHealthEntry;
import com.carenest.entity.attendance.PickupRecord;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceDayUnlockRepository;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.DailyHealthEntryRepository;
import com.carenest.repository.attendance.PickupRecordRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.ParentAttendanceService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.ParentAttendanceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-04, ATT-15 (Cần chốt 24/11/2026): phụ huynh chỉ xem con đã liên kết; chỉ số đã ghi (ngày đang mở lại trả số trước
 * khi mở lại); mỗi ngày gắn lớp trẻ học ngày đó; ngày trẻ chưa vào lớp / đã nghỉ học là NOT_ENROLLED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentAttendanceServiceImpl implements ParentAttendanceService {

    private static final String LOG_PREFIX = "[ParentAttendanceServiceImpl]";
    private static final int MAX_DAYS = 62;

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordHistoryRepository historyRepository;
    private final AttendanceDayUnlockRepository unlockRepository;
    private final DailyHealthEntryRepository healthEntryRepository;
    private final PickupRecordRepository pickupRecordRepository;
    private final AttendanceDayRules rules;
    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;
    private final AccountService accountService;

    @Override
    public ParentTodayResponse getToday(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|getToday|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            LocalDate today = rules.today();
            ParentAttendanceDay day = new ChildDays(childId, today, today).day(today);
            ParentTodayResponse response = new ParentTodayResponse(
                    today,
                    day.status(),
                    day.absentPart(),
                    day.absentPartUnexcused(),
                    day.arrivedAt(),
                    day.classId(),
                    day.className());
            log.info("{}|status={}|END", logPrefix, response.status());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ParentAttendanceResponse getAttendance(CurrentUser currentUser, Long childId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getAttendance|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            ChildDays child = checkedRange(childId, from, to);
            LocalDate today = rules.today();
            List<ParentAttendanceDay> days = new ArrayList<>();
            int schoolDays = 0;
            double present = 0;
            double excused = 0;
            double absent = 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                ParentAttendanceDay day = child.day(d);
                days.add(day);
                boolean counted = day.status() != ParentAttendanceStatus.NO_SCHOOL
                        && day.status() != ParentAttendanceStatus.NOT_ENROLLED;
                if (!counted || d.isAfter(today)) {
                    continue;
                }
                schoolDays++;
                Snapshot s = child.snapshot(d);
                if (s != null) {
                    // ATT-14: mỗi buổi 0,5 ngày
                    present += s.status() != AttendanceStatus.PRESENT ? 0 : s.absentPart() == null ? 1 : 0.5;
                    double ownExcused = s.status() == AttendanceStatus.EXCUSED ? 1 : 0;
                    excused += s.absentPart() == null
                            ? ownExcused
                            : (s.absentPartUnexcused() ? 0 : 0.5) + ownExcused * 0.5;
                    double ownAbsent = s.status() == AttendanceStatus.UNEXCUSED ? 1 : 0;
                    absent +=
                            s.absentPart() == null ? ownAbsent : (s.absentPartUnexcused() ? 0.5 : 0) + ownAbsent * 0.5;
                }
            }
            ParentAttendanceResponse response = new ParentAttendanceResponse(
                    childId, from, to, days, new ParentAttendanceTotals(schoolDays, present, excused, absent));
            log.info("{}|days={}|END", logPrefix, days.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<ParentDailyHealthResponse> getDailyHealth(
            CurrentUser currentUser, Long childId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getDailyHealth|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            ChildDays child = checkedRange(childId, from, to);
            List<DailyHealthEntry> entries =
                    healthEntryRepository
                            .findByChildIdInAndDateBetweenOrderByRecordedAtDescIdDesc(List.of(childId), from, to)
                            .stream()
                            .filter(e -> child.classOn(e.getDate()) != null)
                            .toList();
            Map<Long, String> names = accountService.findFullNames(entries.stream()
                    .map(DailyHealthEntry::getRecordedBy)
                    .distinct()
                    .toList());
            List<ParentDailyHealthResponse> result = entries.stream()
                    .map(e -> {
                        ClassroomInfoResponse cls = child.classInfo(e.getClassroomId());
                        return new ParentDailyHealthResponse(
                                e.getDate(),
                                e.getTemperature(),
                                e.getObservation() == HealthObservation.NOTED ? e.getSymptoms() : List.of(),
                                e.getNote(),
                                names.get(e.getRecordedBy()),
                                e.getClassroomId(),
                                cls == null ? null : cls.name());
                    })
                    .toList();
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // AUTH-04: chỉ phụ huynh đã liên kết với trẻ
    private void checkParent(CurrentUser user, Long childId) {
        if (!user.hasRole(CareNestRole.PARENT) || !guardianChildLinkService.isGuardianOf(user.userId(), childId)) {
            throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
        }
        // CHD-04 (user chốt 26/11/2026): trẻ đã thôi học hoặc ra trường thì phụ huynh mất quyền xem ngay
        if (!enrollmentService
                .findChildIdsLeftSchool(java.util.Set.of(childId), rules.today())
                .isEmpty()) {
            throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
        }
    }

    // ATT-15: tối đa 62 ngày, nằm trong một năm học (theo lớp trẻ học trong khoảng)
    private ChildDays checkedRange(Long childId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw GlobalException.of(ApiCode.PARENT_RANGE_INVALID, MAX_DAYS);
        }
        ChildDays child = new ChildDays(childId, from, to);
        List<ClassroomInfoResponse> classes = child.stays.stream()
                .map(s -> child.classInfo(s.classroomId()))
                .filter(Objects::nonNull)
                .toList();
        boolean oneYear = classes.stream()
                        .map(ClassroomInfoResponse::schoolYearId)
                        .distinct()
                        .count()
                <= 1;
        boolean insideYear = classes.stream()
                .allMatch(c -> (c.yearStart() == null || !from.isBefore(c.yearStart()))
                        && (c.yearEnd() == null || !to.isAfter(c.yearEnd())));
        if (!oneYear || !insideYear) {
            throw GlobalException.of(ApiCode.PARENT_RANGE_INVALID, MAX_DAYS);
        }
        return child;
    }

    /** Trạng thái đã chốt của bản ghi một ngày (ngày đang mở lại: trạng thái trước khi mở lại). */
    private record Snapshot(
            AttendanceStatus status,
            List<MealSession> meals,
            DayPart absentPart,
            boolean absentPartUnexcused,
            Instant at) {}

    /** Dữ liệu một trẻ trong khoảng ngày, đọc theo lô: các đoạn học lớp, bản ghi, lần mở lại còn hạn. */
    private final class ChildDays {
        private final List<EnrollmentService.ChildClassStay> stays;
        private final Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
        private final Map<LocalDate, AttendanceRecord> records;
        private final Map<String, AttendanceDayUnlock> unlocks;
        private final Map<LocalDate, PickupRecord> pickups;

        ChildDays(Long childId, LocalDate from, LocalDate to) {
            stays = enrollmentService.findChildStays(childId, from, to);
            pickups =
                    pickupRecordRepository
                            .findByChildIdInAndDateBetweenOrderByRecordedAtAscIdAsc(List.of(childId), from, to)
                            .stream()
                            .filter(PickupRecord::isHandedOver)
                            .collect(Collectors.toMap(PickupRecord::getDate, Function.identity(), (a, b) -> b));
            records = recordRepository.findByChildIdInAndDateBetween(List.of(childId), from, to).stream()
                    .collect(Collectors.toMap(AttendanceRecord::getDate, Function.identity(), (a, b) -> a));
            List<Long> classIds = stays.stream()
                    .map(EnrollmentService.ChildClassStay::classroomId)
                    .distinct()
                    .toList();
            unlocks = classIds.isEmpty()
                    ? Map.of()
                    : unlockRepository
                            .findByClassroomIdInAndDateBetweenAndValidUntilAfter(classIds, from, to, rules.now())
                            .stream()
                            .collect(Collectors.toMap(
                                    u -> u.getClassroomId() + ":" + u.getDate(),
                                    Function.identity(),
                                    (a, b) -> a.getId() > b.getId() ? a : b));
        }

        ClassroomInfoResponse classInfo(Long classId) {
            return classes.computeIfAbsent(
                    classId, id -> classroomService.findClassroomInfo(id).orElse(null));
        }

        ClassroomInfoResponse classOn(LocalDate date) {
            return stays.stream()
                    .filter(s -> !date.isBefore(s.from()) && !date.isAfter(s.to()))
                    .findFirst()
                    .map(s -> classInfo(s.classroomId()))
                    .orElse(null);
        }

        Snapshot snapshot(LocalDate date) {
            AttendanceRecord r = records.get(date);
            ClassroomInfoResponse cls = classOn(date);
            if (r == null || cls == null) {
                return null;
            }
            AttendanceDayUnlock unlock = unlocks.get(cls.id() + ":" + date);
            if (unlock == null) {
                return new Snapshot(
                        r.getStatus(), r.getMeals(), r.getAbsentPart(), r.isAbsentPartUnexcused(), r.getRecordedAt());
            }
            // Ngày đang mở lại: GV có thể đang sửa, phụ huynh thấy bản trước lần mở lại này
            List<AttendanceRecordHistory> history = historyRepository.findByRecordIdOrderByIdAsc(r.getId());
            if (history.isEmpty()) {
                return new Snapshot(
                        r.getStatus(), r.getMeals(), r.getAbsentPart(), r.isAbsentPartUnexcused(), r.getRecordedAt());
            }
            return history.stream()
                    .filter(h -> !unlock.getId().equals(h.getUnlockId()))
                    .reduce((a, b) -> b)
                    .map(h -> new Snapshot(
                            h.getStatus(), h.getMeals(), h.getAbsentPart(), h.isAbsentPartUnexcused(), h.getAt()))
                    .orElse(null);
        }

        ParentAttendanceDay day(LocalDate date) {
            ClassroomInfoResponse cls = classOn(date);
            if (cls == null) {
                return new ParentAttendanceDay(
                        date,
                        ParentAttendanceStatus.NOT_ENROLLED,
                        null,
                        false,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null);
            }
            if (!rules.isSchoolDay(date)) {
                return new ParentAttendanceDay(
                        date,
                        ParentAttendanceStatus.NO_SCHOOL,
                        null,
                        false,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        cls.id(),
                        cls.name(),
                        null);
            }
            Snapshot s = snapshot(date);
            if (s == null) {
                return new ParentAttendanceDay(
                        date,
                        ParentAttendanceStatus.NOT_RECORDED,
                        null,
                        false,
                        null,
                        List.of(),
                        List.of(),
                        pickupName(pickups.get(date)),
                        cls.id(),
                        cls.name(),
                        pickups.containsKey(date) ? pickups.get(date).getHandedOverAt() : null);
            }
            List<MealSession> eaten = s.meals().stream().sorted().toList();
            boolean missedPart = s.status() != AttendanceStatus.PRESENT || s.absentPart() != null;
            List<MealSession> cancelled = missedPart
                    ? cls.mealSessions().stream()
                            .filter(m -> !eaten.contains(m))
                            .sorted()
                            .toList()
                    : List.of();
            return new ParentAttendanceDay(
                    date,
                    switch (s.status()) {
                        case PRESENT -> ParentAttendanceStatus.PRESENT;
                        case EXCUSED -> ParentAttendanceStatus.ABSENT_EXCUSED;
                        case UNEXCUSED -> ParentAttendanceStatus.ABSENT;
                    },
                    s.absentPart(),
                    s.absentPartUnexcused(),
                    s.status() == AttendanceStatus.PRESENT ? s.at() : null,
                    eaten,
                    cancelled,
                    pickupName(pickups.get(date)),
                    cls.id(),
                    cls.name(),
                    pickups.containsKey(date) ? pickups.get(date).getHandedOverAt() : null);
        }
    }

    private static String pickupName(PickupRecord pickup) {
        if (pickup == null) return null;
        String name = pickup.getPickupPersonName();
        if (name == null || name.isBlank()) {
            return pickup.getVerification() == com.carenest.utils.enums.PickupVerification.PHONE_CONFIRMED
                    ? "Người đón đã được phụ huynh xác nhận qua điện thoại"
                    : null;
        }
        if (pickup.getPickupPersonRelationship() == null) return name;
        String label =
                switch (pickup.getPickupPersonRelationship()) {
                    case FATHER -> "Bố";
                    case MOTHER -> "Mẹ";
                    case GRANDFATHER -> "Ông";
                    case GRANDMOTHER -> "Bà";
                    case SIBLING -> "Anh/chị";
                    case GUARDIAN -> "Người giám hộ";
                    case OTHER -> "Khác";
                };
        return name + " (" + label + ")";
    }
}
