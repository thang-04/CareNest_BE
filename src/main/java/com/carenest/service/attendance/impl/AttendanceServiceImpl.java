package com.carenest.service.attendance.impl;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse.ChildTotal;
import com.carenest.dto.attendance.AttendanceSummaryResponse.ClassSummary;
import com.carenest.dto.attendance.AttendanceSummaryResponse.ClassTotal;
import com.carenest.dto.attendance.AttendanceSummaryResponse.DayRow;
import com.carenest.dto.attendance.AttendanceSummaryResponse.MultiSummary;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse.ChildBrief;
import com.carenest.dto.attendance.ClassAttendanceResponse.ChildRow;
import com.carenest.dto.attendance.ClassAttendanceResponse.HealthSummary;
import com.carenest.dto.attendance.ClassAttendanceResponse.RecordView;
import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceResponse;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.AttendanceRecordHistory;
import com.carenest.entity.attendance.DailyHealthEntry;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.DailyHealthEntryRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.TemperatureLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceServiceImpl implements AttendanceService {
    private static final String LOG_PREFIX = "[AttendanceServiceImpl]";
    private static final long MAX_SUMMARY_DAYS = 366;

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordHistoryRepository historyRepository;
    private final DailyHealthEntryRepository healthEntryRepository;
    private final AttendanceDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceClassResponse> getClasses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getClasses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            List<AttendanceClassResponse> classes = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> rules.canView(currentUser, c))
                    .map(c -> new AttendanceClassResponse(
                            c.id(),
                            c.name(),
                            c.campusId(),
                            c.ageGroupId(),
                            c.mealSessions(),
                            rules.canRecord(currentUser, c) || rules.taughtClosedYear(currentUser, c),
                            c.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecord(currentUser, c),
                            c.yearStatus() == SchoolYearStatus.ACTIVE && rules.canUnlock(currentUser, c.campusId())))
                    .toList();
            log.info("{}|END", logPrefix);
            return classes;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DayInfoResponse getDayInfo(CurrentUser currentUser, LocalDate date, Long classId) {
        String logPrefix = LOG_PREFIX + "|getDayInfo|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate day = date == null ? rules.today() : date;
            DayInfoResponse info;
            if (classId != null) {
                ClassroomInfoResponse cls = viewableClass(currentUser, classId);
                info = rules.dayInfo(day, cls, null);
            } else {
                info = rules.dayInfo(
                        day,
                        null,
                        classroomService.findActiveYearClassrooms().stream()
                                .findFirst()
                                .orElse(null));
            }
            log.info("{}|END", logPrefix);
            return info;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ClassAttendanceResponse getClassAttendance(CurrentUser currentUser, Long classId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getClassAttendance|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate day = date == null ? rules.today() : date;
            ClassroomInfoResponse cls = viewableClass(currentUser, classId);
            List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(classId, day);
            Map<Long, AttendanceRecord> records =
                    recordRepository
                            .findByChildIdInAndDate(
                                    children.stream()
                                            .map(ClassChildResponse::id)
                                            .toList(),
                                    day)
                            .stream()
                            .collect(Collectors.toMap(AttendanceRecord::getChildId, Function.identity()));
            Map<Long, String> names = accountService.findFullNames(records.values().stream()
                    .map(AttendanceRecord::getRecordedBy)
                    .collect(Collectors.toSet()));
            // Nhãn sức khỏe ở dòng trẻ theo lần ghi nhận mới nhất của lớp trong ngày
            Map<Long, List<DailyHealthEntry>> health =
                    healthEntryRepository
                            .findByClassroomIdInAndDateOrderByRecordedAtDescIdDesc(List.of(classId), day)
                            .stream()
                            .collect(Collectors.groupingBy(DailyHealthEntry::getChildId));
            List<ChildRow> rows = children.stream()
                    .map(ch -> new ChildRow(
                            toBrief(ch),
                            toView(records.get(ch.id()), cls.mealSessions(), names),
                            toHealth(health.getOrDefault(ch.id(), List.of()))))
                    .toList();
            ClassAttendanceResponse sheet = new ClassAttendanceResponse(
                    rules.dayInfo(day, cls, null),
                    cls.id(),
                    cls.name(),
                    cls.campusId(),
                    cls.mealSessions(),
                    cls.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecord(currentUser, cls),
                    rows);
            log.info("{}|END", logPrefix);
            return sheet;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SaveAttendanceResponse saveClassAttendance(
            CurrentUser currentUser, Long classId, SaveAttendanceRequest request) {
        String logPrefix = LOG_PREFIX + "|saveClassAttendance|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = findClass(classId);
            // ATT-01, AUTH-03: chỉ giáo viên đang dạy lớp
            if (!rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            // CFG-YR-01: lớp năm đã kết thúc (hoặc chưa kích hoạt) chỉ xem
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ATTENDANCE_YEAR_NOT_ACTIVE);
            }
            LocalDate date = request.date();
            if (!AttendanceDayRules.isSchoolDay(date)) {
                throw GlobalException.of(ApiCode.NOT_SCHOOL_DAY);
            }
            DayInfoResponse info = rules.dayInfo(date, cls, null);
            // Ngày khác hôm nay chỉ ghi được khi lớp/ngày đang được mở lại
            if (!date.equals(info.today()) && info.unlock() == null) {
                throw GlobalException.of(ApiCode.ATTENDANCE_TODAY_ONLY);
            }
            if (info.locked()) {
                throw GlobalException.of(ApiCode.ATTENDANCE_LOCKED, info.cutoff());
            }
            Set<Long> classChildIds = enrollmentService.findChildrenInClassroom(classId, date).stream()
                    .map(ClassChildResponse::id)
                    .collect(Collectors.toSet());
            Set<Long> seen = new HashSet<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                if (!classChildIds.contains(entry.childId()) || !seen.add(entry.childId())) {
                    throw GlobalException.of(ApiCode.CHILD_LIST_MISMATCH);
                }
            }
            Map<Long, List<MealSession>> mealsByChild = new LinkedHashMap<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                mealsByChild.put(entry.childId(), mealsOf(entry, cls.mealSessions()));
            }

            Map<Long, AttendanceRecord> existing = recordRepository.findByChildIdInAndDate(seen, date).stream()
                    .collect(Collectors.toMap(AttendanceRecord::getChildId, Function.identity()));
            Instant at = rules.now();
            Long unlockId = info.unlock() == null ? null : info.unlock().id();
            int changed = 0;
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                List<MealSession> meals = mealsByChild.get(entry.childId());
                AttendanceRecord record = existing.get(entry.childId());
                String action;
                if (record == null) {
                    record = recordRepository.save(AttendanceRecord.create(
                            entry.childId(),
                            cls.id(),
                            cls.campusId(),
                            date,
                            entry.status(),
                            meals,
                            currentUser.userId(),
                            at));
                    action = AttendanceRecordHistory.RECORDED;
                } else if (record.differsFrom(entry.status(), meals)) {
                    record.update(entry.status(), meals, currentUser.userId(), at);
                    action = AttendanceRecordHistory.UPDATED;
                } else {
                    continue;
                }
                historyRepository.save(AttendanceRecordHistory.of(record, action, unlockId));
                changed++;
            }
            if (changed > 0) {
                eventPublisher.publishEvent(
                        new AttendanceService.AttendanceChangedEvent(date, cls.campusId(), cls.id()));
            }
            log.info("{}|END", logPrefix);
            return new SaveAttendanceResponse(at, request.entries().size(), changed);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ClassSummary getClassSummary(CurrentUser currentUser, Long classId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getClassSummary|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = viewableClass(currentUser, classId);
            checkRange(from, to);
            ClassSummary summary = summarize(cls, from, to);
            log.info("{}|END", logPrefix);
            return summary;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MultiSummary getSummary(
            CurrentUser currentUser, Long campusId, Long schoolYearId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getSummary|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (!currentUser.hasRole(CareNestRole.PRINCIPAL) && !currentUser.hasRole(CareNestRole.VICE_PRINCIPAL)) {
                throw GlobalException.of(ApiCode.ATTENDANCE_SUMMARY_ACCESS_DENIED);
            }
            checkRange(from, to);
            List<ClassSummary> parts = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> campusId == null || c.campusId().equals(campusId))
                    .filter(c -> rules.canView(currentUser, c))
                    .map(c -> summarize(c, from, to))
                    .toList();
            if (campusId != null && parts.isEmpty() && !rules.canUnlock(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            List<DayRow> days = schoolDays(from, to).stream()
                    .map(date -> {
                        List<DayRow> rows = parts.stream()
                                .flatMap(p -> p.days().stream())
                                .filter(d -> d.date().equals(date))
                                .toList();
                        return new DayRow(
                                date,
                                sum(rows, DayRow::present),
                                sum(rows, DayRow::excused),
                                sum(rows, DayRow::unexcused),
                                sum(rows, DayRow::missing),
                                sum(rows, DayRow::mealTotal),
                                Map.of());
                    })
                    .toList();
            List<ClassTotal> classes = parts.stream()
                    .map(p -> new ClassTotal(
                            p.classId(),
                            p.className(),
                            p.campusId(),
                            p.size(),
                            sum(p.days(), DayRow::present),
                            sum(p.days(), DayRow::excused),
                            sum(p.days(), DayRow::unexcused),
                            sum(p.days(), DayRow::mealTotal)))
                    .toList();
            MultiSummary summary = new MultiSummary(
                    campusId,
                    from,
                    to,
                    parts.stream().mapToInt(ClassSummary::size).sum(),
                    days,
                    classes);
            log.info("{}|END", logPrefix);
            return summary;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassMealTally> tallyMeals(LocalDate date, Long campusId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|tallyMeals|campusId=" + campusId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            List<ClassMealTally> tallies = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> c.campusId().equals(campusId))
                    .map(c -> tally(c, date))
                    .toList();
            log.info("{}|END", logPrefix);
            return tallies;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // NUT-01: suất = trẻ có mặt và có báo ăn bữa đó; trẻ chưa điểm danh không tính suất
    private ClassMealTally tally(ClassroomInfoResponse cls, LocalDate date) {
        List<Long> childIds = enrollmentService.findChildrenInClassroom(cls.id(), date).stream()
                .map(ClassChildResponse::id)
                .toList();
        Map<Long, AttendanceRecord> records = recordRepository.findByChildIdInAndDate(childIds, date).stream()
                .filter(r -> r.getClassroomId().equals(cls.id()))
                .collect(Collectors.toMap(AttendanceRecord::getChildId, Function.identity()));
        int present = 0;
        int absent = 0;
        Map<MealSession, Integer> meals = new EnumMap<>(MealSession.class);
        cls.mealSessions().forEach(s -> meals.put(s, 0));
        for (AttendanceRecord r : records.values()) {
            if (r.getStatus().isAbsent()) {
                absent++;
                continue;
            }
            present++;
            r.getMeals().stream().filter(meals::containsKey).forEach(s -> meals.merge(s, 1, Integer::sum));
        }
        return new ClassMealTally(
                cls.id(),
                cls.name(),
                cls.campusId(),
                cls.mealSessions(),
                present,
                absent,
                childIds.size() - present - absent,
                meals);
    }

    private ClassroomInfoResponse findClass(Long classId) {
        return classroomService
                .findClassroomInfo(classId)
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
    }

    private ClassroomInfoResponse viewableClass(CurrentUser user, Long classId) {
        ClassroomInfoResponse cls = findClass(classId);
        if (!rules.canView(user, cls)) {
            throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
        }
        return cls;
    }

    /** ATT-02: bữa ăn chỉ trong các bữa của nhóm tuổi; vắng thì không ăn. */
    private static List<MealSession> mealsOf(SaveAttendanceRequest.Entry entry, List<MealSession> classSessions) {
        List<MealSession> taken = entry.meals() == null
                ? List.of()
                : entry.meals().entrySet().stream()
                        .filter(e -> e.getKey() != null && Boolean.TRUE.equals(e.getValue()))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
        if (!classSessions.containsAll(taken)) {
            throw GlobalException.of(ApiCode.MEAL_SESSION_NOT_SERVED);
        }
        if (entry.status().isAbsent() && !taken.isEmpty()) {
            throw GlobalException.of(ApiCode.ABSENT_CHILD_NO_MEAL);
        }
        return taken;
    }

    private void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "từ ngày, đến ngày");
        }
        if (from.isAfter(to)) {
            throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
        }
        if (to.isAfter(rules.today())) {
            throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_SUMMARY_DAYS) {
            throw GlobalException.of(ApiCode.DATE_RANGE_TOO_LONG, 366);
        }
    }

    private static List<LocalDate> schoolDays(LocalDate from, LocalDate to) {
        return Stream.iterate(from, d -> !d.isAfter(to), d -> d.plusDays(1))
                .filter(AttendanceDayRules::isSchoolDay)
                .toList();
    }

    // Trẻ của lớp tại ngày cuối khoảng; ngày chưa có bản ghi tính "chưa điểm danh"
    private ClassSummary summarize(ClassroomInfoResponse cls, LocalDate from, LocalDate to) {
        List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(cls.id(), to);
        List<Long> childIds = children.stream().map(ClassChildResponse::id).toList();
        Map<String, AttendanceRecord> records =
                recordRepository.findByChildIdInAndDateBetween(childIds, from, to).stream()
                        .filter(r -> r.getClassroomId().equals(cls.id()))
                        .collect(Collectors.toMap(r -> r.getChildId() + ":" + r.getDate(), Function.identity()));
        Map<Long, int[]> perChild = new LinkedHashMap<>();
        children.forEach(ch -> perChild.put(ch.id(), new int[4]));
        List<DayRow> days = new ArrayList<>();
        for (LocalDate date : schoolDays(from, to)) {
            int[] counts = new int[5]; // present, excused, unexcused, missing, mealTotal
            Map<MealSession, Integer> meals = new EnumMap<>(MealSession.class);
            cls.mealSessions().forEach(s -> meals.put(s, 0));
            for (ClassChildResponse ch : children) {
                AttendanceRecord r = records.get(ch.id() + ":" + date);
                int[] child = perChild.get(ch.id());
                if (r == null) {
                    counts[3]++;
                    continue;
                }
                int slot = r.getStatus() == AttendanceStatus.PRESENT
                        ? 0
                        : r.getStatus() == AttendanceStatus.EXCUSED ? 1 : 2;
                counts[slot]++;
                child[slot]++;
                if (!r.getMeals().isEmpty()) {
                    counts[4]++;
                    child[3]++;
                    r.getMeals().forEach(s -> meals.merge(s, 1, Integer::sum));
                }
            }
            days.add(new DayRow(date, counts[0], counts[1], counts[2], counts[3], counts[4], meals));
        }
        List<ChildTotal> childTotals = children.stream()
                .map(ch -> {
                    int[] c = perChild.get(ch.id());
                    return new ChildTotal(toBrief(ch), c[0], c[1], c[2], c[3]);
                })
                .toList();
        return new ClassSummary(
                cls.id(), cls.name(), cls.campusId(), cls.mealSessions(), from, to, children.size(), days, childTotals);
    }

    private static int sum(List<DayRow> rows, ToIntFunction<DayRow> field) {
        return rows.stream().mapToInt(field).sum();
    }

    private static ChildBrief toBrief(ClassChildResponse ch) {
        return new ChildBrief(ch.id(), ch.fullName(), ch.dateOfBirth());
    }

    private static RecordView toView(AttendanceRecord r, List<MealSession> sessions, Map<Long, String> names) {
        if (r == null) {
            return null;
        }
        Map<MealSession, Boolean> meals = new EnumMap<>(MealSession.class);
        sessions.forEach(s -> meals.put(s, r.getMeals().contains(s)));
        return new RecordView(
                r.getId(), r.getStatus(), meals, r.getRecordedBy(), names.get(r.getRecordedBy()), r.getRecordedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildDayStatus> findDayStatuses(Collection<Long> childIds, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|findDayStatuses|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            List<ChildDayStatus> result = childIds.isEmpty()
                    ? List.of()
                    : recordRepository.findByChildIdInAndDateBetween(childIds, from, to).stream()
                            .map(r -> new ChildDayStatus(r.getChildId(), r.getDate(), r.getStatus()))
                            .toList();
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static HealthSummary toHealth(List<DailyHealthEntry> entries) {
        if (entries.isEmpty()) {
            return new HealthSummary(false, 0, null, null, null, List.of(), "", null);
        }
        DailyHealthEntry latest = entries.getFirst();
        return new HealthSummary(
                true,
                entries.size(),
                latest.getObservation(),
                latest.getTemperature(),
                TemperatureLevel.of(latest.getTemperature()),
                latest.getObservation() == HealthObservation.NOTED ? latest.getSymptoms() : List.of(),
                latest.getNote(),
                latest.getRecordedAt());
    }
}
