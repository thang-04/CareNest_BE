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
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveTag;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceResponse;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.AttendanceRecordHistory;
import com.carenest.entity.attendance.DailyHealthEntry;
import com.carenest.entity.attendance.LeaveRequest;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.DailyHealthEntryRepository;
import com.carenest.repository.attendance.LeaveRequestRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.MealSubstituteLookup;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.LeavePart;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.TemperatureLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.springframework.scheduling.annotation.Scheduled;
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
    private final ChildService childService;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;
    private final LeaveRequestRepository leaveRequestRepository;
    private final List<MealSubstituteLookup> mealSubstituteLookups;
    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceClassResponse> getClasses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getClasses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            List<AttendanceClassResponse> classes = classroomService.findYearClassrooms(schoolYearId).stream()
                    // Người dạy thay đã hết đợt vẫn thấy lớp để mở ngày được mở lại trong đợt (xem / ghi xét theo ngày)
                    .filter(c -> rules.canView(currentUser, c) || rules.substitutedInYear(currentUser, c))
                    .map(c -> new AttendanceClassResponse(
                            c.id(),
                            c.name(),
                            c.campusId(),
                            c.ageGroupId(),
                            c.mealSessions(),
                            rules.canRecordAttendance(currentUser, c) || rules.taughtClosedYear(currentUser, c),
                            c.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecordAttendance(currentUser, c),
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
                ClassroomInfoResponse cls = findClass(classId);
                if (!rules.canViewDay(currentUser, cls, day)) {
                    throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
                }
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
    public DayInfoResponse getActiveYearDayInfo(LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getActiveYearDayInfo";
        log.info("{}|START", logPrefix);
        try {
            DayInfoResponse info = rules.dayInfo(
                    date,
                    null,
                    classroomService.findActiveYearClassrooms().stream()
                            .findFirst()
                            .orElse(null));
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
            ClassroomInfoResponse cls = findClass(classId);
            if (!rules.canViewDay(currentUser, cls, day)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
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
            Map<Long, LeaveTag> leaves =
                    leaveTags(children.stream().map(ClassChildResponse::id).collect(Collectors.toSet()), day, cls);
            Set<Long> childIds = children.stream().map(ClassChildResponse::id).collect(Collectors.toSet());
            Set<Long> withoutMeals = childService.findChildIdsWithoutMeals(childIds);
            Set<Long> allergic = childService.findChildIdsWithAllergies(childIds);
            List<ChildRow> rows = children.stream()
                    .map(ch -> new ChildRow(
                            toBrief(ch, withoutMeals, allergic),
                            toView(records.get(ch.id()), cls.mealSessions(), names),
                            toHealth(health.getOrDefault(ch.id(), List.of())),
                            leaves.get(ch.id())))
                    .toList();
            ClassAttendanceResponse sheet = new ClassAttendanceResponse(
                    rules.dayInfo(day, cls, null),
                    cls.id(),
                    cls.name(),
                    cls.campusId(),
                    cls.mealSessions(),
                    cls.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecordAttendanceDay(currentUser, cls, day),
                    rows,
                    rules.latestArrival(cls.schoolYearId(), day));
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
            LocalDate date = request.date();
            // ATT-01, AUTH-03: GV dạy chính đang dạy lớp, hoặc người dạy thay đúng ngày ghi (Cần chốt 14/11/2026)
            if (!rules.canRecordAttendanceDay(currentUser, cls, date)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            // CFG-YR-01: lớp năm đã kết thúc (hoặc chưa kích hoạt) chỉ xem
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ATTENDANCE_YEAR_NOT_ACTIVE);
            }
            classroomService.lockForUpdate(classId);
            if (!rules.isSchoolDay(date)) {
                throw GlobalException.of(ApiCode.NOT_SCHOOL_DAY);
            }
            // Ngày ngoài năm học của lớp không ghi điểm danh (sau ngày kết thúc chỉ ghi ngày trong năm được mở lại)
            if ((cls.yearStart() != null && date.isBefore(cls.yearStart()))
                    || (cls.yearEnd() != null && date.isAfter(cls.yearEnd()))) {
                throw GlobalException.of(ApiCode.ATTENDANCE_TODAY_ONLY);
            }
            DayInfoResponse info = rules.dayInfo(date, cls, null);
            // Ngày khác hôm nay chỉ ghi được khi lớp/ngày đang được mở lại
            if (!date.equals(info.today()) && info.unlock() == null) {
                throw GlobalException.of(ApiCode.ATTENDANCE_TODAY_ONLY);
            }
            if (info.locked()) {
                throw GlobalException.of(ApiCode.ATTENDANCE_LOCKED, info.cutoff());
            }
            List<ClassChildResponse> classChildren = enrollmentService.findChildrenInClassroom(classId, date);
            Set<Long> classChildIds =
                    classChildren.stream().map(ClassChildResponse::id).collect(Collectors.toSet());
            Set<Long> seen = new HashSet<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                if (!classChildIds.contains(entry.childId()) || !seen.add(entry.childId())) {
                    throw GlobalException.of(ApiCode.CHILD_LIST_MISMATCH);
                }
            }
            // ATT-04, ATT-13: trẻ nghỉ cả ngày, nghỉ sáng do đơn ghi; nghỉ chiều GV vẫn điểm danh buổi sáng. Khi
            // lớp/ngày được mở lại, GV sửa được trạng thái của trẻ nghỉ nửa buổi
            Map<Long, LeaveTag> tags = leaveTags(seen, date, cls);
            boolean reopened = info.unlock() != null;
            Map<Long, AttendanceRecord> existing = recordRepository.findByChildIdInAndDate(seen, date).stream()
                    .collect(Collectors.toMap(AttendanceRecord::getChildId, Function.identity()));
            Map<Long, DayPart> absentParts = new HashMap<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                LeaveTag tag = tags.get(entry.childId());
                if (tag == null) {
                    continue;
                }
                AttendanceRecord current = existing.get(entry.childId());
                // Nghỉ sáng trước giờ chốt: GV chỉ đổi công tắc ăn bữa xế (giữ nguyên trạng thái theo đơn)
                boolean mealOnly =
                        tag.part() == LeavePart.MORNING && current != null && current.getStatus() == entry.status();
                if (tag.part() == LeavePart.FULL_DAY || (tag.part() == LeavePart.MORNING && !reopened && !mealOnly)) {
                    throw GlobalException.of(ApiCode.LEAVE_ATTENDANCE_LOCKED);
                }
                absentParts.put(
                        entry.childId(),
                        reopened && entry.absentPart() != null
                                ? entry.absentPart()
                                : tag.part().dayPart());
            }
            // ATT-09: Vắng có phép chỉ do đơn nghỉ đặt; GV giữ nguyên được trạng thái đã có, không tự chọn mới
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                AttendanceRecord record = existing.get(entry.childId());
                if (entry.status() == AttendanceStatus.EXCUSED
                        && (record == null || record.getStatus() != AttendanceStatus.EXCUSED)) {
                    throw GlobalException.of(ApiCode.ATTENDANCE_EXCUSED_NOT_ALLOWED);
                }
            }
            // CHD-13: trẻ không báo ăn không bao giờ có suất, bỏ qua meals gửi lên
            Set<Long> withoutMeals = childService.findChildIdsWithoutMeals(classChildIds);
            Map<Long, List<MealSession>> mealsByChild = new LinkedHashMap<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                LeaveTag tag = tags.get(entry.childId());
                mealsByChild.put(
                        entry.childId(),
                        mealsOf(
                                entry,
                                cls.mealSessions(),
                                !withoutMeals.contains(entry.childId()),
                                absentParts.get(entry.childId()),
                                tag == null || !Boolean.FALSE.equals(tag.eats())));
            }
            Instant at = rules.now();
            Long unlockId = info.unlock() == null ? null : info.unlock().id();
            int changed = 0;
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                List<MealSession> meals = mealsByChild.get(entry.childId());
                DayPart absentPart = absentParts.get(entry.childId());
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
                } else if (record.differsFrom(entry.status(), meals, absentPart)) {
                    record.update(entry.status(), meals, currentUser.userId(), at);
                    action = AttendanceRecordHistory.UPDATED;
                } else {
                    continue;
                }
                record.markAbsentPart(absentPart);
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
    public SaveAttendanceResponse markArrived(CurrentUser currentUser, Long classId, Long childId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|markArrived|userId=" + currentUser.userId() + "|classId=" + classId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = findClass(classId);
            LocalDate day = date == null ? rules.today() : date;
            if (!rules.canRecordAttendanceDay(currentUser, cls, day)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ATTENDANCE_YEAR_NOT_ACTIVE);
            }
            classroomService.lockForUpdate(classId);
            DayInfoResponse info = rules.dayInfo(day, cls, null);
            LocalTime latest = rules.latestArrival(cls.schoolYearId(), day);
            AttendanceRecord record = recordRepository.findByChildIdInAndDate(List.of(childId), day).stream()
                    .filter(r -> r.getClassroomId().equals(classId))
                    .findFirst()
                    .orElse(null);
            // ATT-13: hôm nay từ giờ chốt tới giờ đến muộn nhất; ngoài khung chỉ khi lớp/ngày được mở lại
            boolean inWindow = day.equals(info.today()) && info.baseLocked() && !rules.isBaseLocked(day, latest);
            if (record == null
                    || record.getAbsentPart() != DayPart.MORNING
                    || record.getStatus() == AttendanceStatus.PRESENT
                    || !(inWindow || info.unlock() != null)) {
                throw GlobalException.of(ApiCode.ATTENDANCE_ARRIVAL_NOT_ALLOWED, latest);
            }
            Instant at = rules.now();
            record.update(AttendanceStatus.PRESENT, record.getMeals(), currentUser.userId(), at);
            historyRepository.save(AttendanceRecordHistory.of(
                    record,
                    AttendanceRecordHistory.UPDATED,
                    info.unlock() == null ? null : info.unlock().id()));
            log.info("{}|END", logPrefix);
            return new SaveAttendanceResponse(at, 1, 1);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Scheduled(cron = "0 */5 * * * MON-FRI", zone = "${carenest.time-zone}")
    public int closeMorningLeaves() {
        String logPrefix = LOG_PREFIX + "|closeMorningLeaves";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = rules.today();
            List<AttendanceRecord> pending = rules.isSchoolDay(today)
                    ? recordRepository.findByDateAndAbsentPartAndStatus(
                            today, DayPart.MORNING, AttendanceStatus.EXCUSED)
                    : List.of();
            Instant at = rules.now();
            int closed = 0;
            for (Map.Entry<Long, List<AttendanceRecord>> group : pending.stream()
                    .collect(Collectors.groupingBy(AttendanceRecord::getClassroomId))
                    .entrySet()) {
                ClassroomInfoResponse cls = findClass(group.getKey());
                LocalTime latest = rules.latestArrival(cls.schoolYearId(), today);
                if (!rules.isLockedAt(today, latest, at)) {
                    continue;
                }
                List<Long> teachers = rules.dayTeacherIds(cls, today);
                Map<Long, String> names = childService
                        .findChildBriefs(group.getValue().stream()
                                .map(AttendanceRecord::getChildId)
                                .toList())
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, e -> e.getValue().fullName()));
                for (AttendanceRecord r : group.getValue()) {
                    // Sổ báo ăn giữ nguyên (bữa xế đã tính theo công tắc ăn); sai lệch thì BGH mở lại để sửa
                    r.update(AttendanceStatus.UNEXCUSED, r.getMeals(), r.getRecordedBy(), at);
                    historyRepository.save(AttendanceRecordHistory.of(r, AttendanceRecordHistory.UPDATED, null));
                    String body = "Bé " + names.getOrDefault(r.getChildId(), "") + " nghỉ buổi sáng nhưng chưa đến lớp"
                            + " trước " + latest + ", ghi Vắng cả ngày (sáng có phép, chiều không phép).";
                    Set<Long> receivers = new LinkedHashSet<>(teachers);
                    guardianChildLinkService.findLinksOfChild(r.getChildId()).forEach(l -> receivers.add(l.userId()));
                    if (!receivers.isEmpty()) {
                        notificationService.notifyUsers(
                                receivers,
                                NotificationType.LEAVE_REQUEST_CHANGED,
                                "Bé chưa đến buổi chiều",
                                body,
                                r.getChildId());
                    }
                    closed++;
                }
            }
            log.info("{}|closed={}|END", logPrefix, closed);
            return closed;
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
            ClassroomInfoResponse cls = findClass(classId);
            // Tổ trưởng chuyên môn xem tổng hợp (không xem phiếu điểm danh/sức khỏe từng ngày) các lớp nhóm tuổi mình
            if (!rules.canView(currentUser, cls) && !rules.leadsClass(currentUser, cls)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
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
            CurrentUser currentUser, Long campusId, Long schoolYearId, Long ageGroupId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getSummary|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            boolean leader =
                    currentUser.hasRole(CareNestRole.PRINCIPAL) || currentUser.hasRole(CareNestRole.VICE_PRINCIPAL);
            // Tổ trưởng chuyên môn xem tổng hợp các lớp nhóm tuổi mình (Cần chốt 04/11)
            Set<Long> ledGroups = leader ? Set.of() : rules.teamLeaderAgeGroupIds(currentUser, schoolYearId);
            if (!leader && ledGroups.isEmpty()) {
                throw GlobalException.of(ApiCode.ATTENDANCE_SUMMARY_ACCESS_DENIED);
            }
            checkRange(from, to);
            List<ClassSummary> parts = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> campusId == null || c.campusId().equals(campusId))
                    .filter(c -> ageGroupId == null || ageGroupId.equals(c.ageGroupId()))
                    .filter(c -> leader ? rules.canView(currentUser, c) : ledGroups.contains(c.ageGroupId()))
                    .map(c -> summarize(c, from, to))
                    .toList();
            // Cột bữa của tổng hợp nhiều lớp = các bữa có ở ít nhất một lớp
            List<MealSession> sessions = parts.stream()
                    .flatMap(p -> p.mealSessions().stream())
                    .distinct()
                    .sorted()
                    .toList();
            if (campusId != null && parts.isEmpty() && !rules.canReadCampus(currentUser, campusId)) {
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
                                sum(rows, DayRow::substitute),
                                mealSums(rows, sessions),
                                sum(rows, DayRow::morningOff),
                                sum(rows, DayRow::afternoonOff));
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
                    // OBS-23 (Z1): trẻ chuyển lớp trong khoảng chỉ đếm một lần ở sĩ số chung
                    (int) parts.stream()
                            .flatMap(p -> p.children().stream())
                            .map(c -> c.child().id())
                            .distinct()
                            .count(),
                    days,
                    classes,
                    sessions);
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

    // NUT-01, ATT-02: suất = trẻ có mặt và có bật tham gia bữa đó; trẻ chưa điểm danh không tính suất.
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
        Map<MealSession, Integer> substitutes = new EnumMap<>(MealSession.class);
        cls.mealSessions().forEach(s -> {
            meals.put(s, 0);
            substitutes.put(s, 0);
        });
        // Trẻ có dị ứng trong hồ sơ ăn suất thay thế (Cần chốt 04/11/2026: map theo hồ sơ, cô chỉ điểm danh)
        Set<Long> allergic = childService.findChildIdsWithAllergies(records.keySet());
        for (AttendanceRecord r : records.values()) {
            if (r.getStatus().isAbsent()) {
                absent++;
            } else {
                present++;
            }
            // ATT-13: trẻ nghỉ sáng vẫn có bữa xế theo công tắc ăn dù chưa đến; trẻ vắng cả ngày không có bữa
            boolean substitute = allergic.contains(r.getChildId());
            r.getMeals().stream().filter(meals::containsKey).forEach(s -> {
                meals.merge(s, 1, Integer::sum);
                if (substitute) {
                    substitutes.merge(s, 1, Integer::sum);
                }
            });
        }
        return new ClassMealTally(
                cls.id(),
                cls.name(),
                cls.campusId(),
                cls.mealSessions(),
                present,
                absent,
                childIds.size() - present - absent,
                meals,
                substitutes);
    }

    // ATT-04: đơn nghỉ còn hiệu lực của trẻ trong ngày (đơn đã hủy từ ngày này trở đi thì không tính);
    // ATT-05b: ngày đã khóa lúc tạo đơn bị bỏ qua nên không khóa điểm danh của GV ngày đó
    private Map<Long, LeaveTag> leaveTags(Set<Long> childIds, LocalDate date, ClassroomInfoResponse cls) {
        if (childIds.isEmpty()) {
            return Map.of();
        }
        LocalTime cutoff = rules.cutoff(cls.schoolYearId(), date);
        return leaveRequestRepository.findOverlapping(childIds, date, date).stream()
                .filter(r -> r.coversDate(date))
                .filter(r -> !rules.isLockedAt(date, cutoff, r.getCreatedAt()))
                .collect(Collectors.toMap(
                        LeaveRequest::getChildId,
                        r -> new LeaveTag(
                                r.getId(),
                                r.getFromDate(),
                                r.effectiveTo(),
                                r.getReason(),
                                r.getPart(),
                                r.getEats(),
                                LeaveRequestServiceImpl.eatsMeals(r.getPart(), cls)),
                        (a, b) -> a));
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

    /**
     * ATT-02: Có mặt mặc định ăn đủ bữa, GV được tắt; trẻ vắng không có suất. CHD-13: trẻ không báo ăn luôn không có
     * bữa. ATT-13: trẻ nghỉ nửa buổi chỉ có bữa của buổi còn lại, mặc định theo công tắc ăn của đơn.
     */
    private static List<MealSession> mealsOf(
            SaveAttendanceRequest.Entry entry,
            List<MealSession> classSessions,
            boolean mealRegistered,
            DayPart absentPart,
            boolean leaveEats) {
        if (!mealRegistered) {
            return List.of();
        }
        List<MealSession> served =
                absentPart == null ? classSessions : absentPart.other().mealsOf(classSessions);
        Map<MealSession, Boolean> requested = entry.meals();
        List<MealSession> taken = requested == null || requested.isEmpty()
                ? (entry.status().isAbsent() || (absentPart != null && !leaveEats)
                        ? List.of()
                        : served.stream().sorted().toList())
                : requested.entrySet().stream()
                        .filter(e -> e.getKey() != null && Boolean.TRUE.equals(e.getValue()))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
        if (!served.containsAll(taken)) {
            throw GlobalException.of(ApiCode.MEAL_SESSION_NOT_SERVED);
        }
        if (entry.status().isAbsent() && absentPart != DayPart.MORNING && !taken.isEmpty()) {
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

    private List<LocalDate> schoolDays(LocalDate from, LocalDate to) {
        return Stream.iterate(from, d -> !d.isAfter(to), d -> d.plusDays(1))
                .filter(rules::isSchoolDay)
                .toList();
    }

    // Mọi trẻ từng học lớp trong khoảng; mỗi ngày chỉ tính trẻ thuộc lớp ngày đó (OBS-23)
    private ClassSummary summarize(ClassroomInfoResponse cls, LocalDate from, LocalDate to) {
        List<EnrollmentService.ClassStay> stays = enrollmentService.findStaysInClassroom(cls.id(), from, to);
        List<ClassChildResponse> children = enrollmentService.findChildrenInClassroomDuring(cls.id(), from, to);
        List<Long> childIds = children.stream().map(ClassChildResponse::id).toList();
        Set<Long> withoutMeals = childService.findChildIdsWithoutMeals(childIds);
        Map<String, AttendanceRecord> records =
                recordRepository.findByChildIdInAndDateBetween(childIds, from, to).stream()
                        .filter(r -> r.getClassroomId().equals(cls.id()))
                        .collect(Collectors.toMap(r -> r.getChildId() + ":" + r.getDate(), Function.identity()));
        // present, excused, unexcused, meals, morningOff, afternoonOff, nửa ngày có mặt (đơn vị 0,5)
        Map<Long, int[]> perChild = new LinkedHashMap<>();
        Map<Long, Map<MealSession, Integer>> childMeals = new HashMap<>();
        children.forEach(ch -> {
            perChild.put(ch.id(), new int[7]);
            childMeals.put(ch.id(), new EnumMap<>(MealSession.class));
        });
        // Trẻ có khai dị ứng trong hồ sơ ăn suất thay thế (giống Sổ báo ăn, Cần chốt 04/11/2026); ngày đã có sổ báo ăn
        // thì lấy số thay thế của sổ (sau giờ chốt sổ không tự đổi theo hồ sơ, HT/PHT chỉnh tay — Cần chốt 10/11/2026)
        Set<Long> allergic = childService.findChildIdsWithAllergies(childIds);
        Map<LocalDate, Integer> bookSubstitutes = new HashMap<>();
        mealSubstituteLookups.forEach(l -> bookSubstitutes.putAll(l.substitutes(cls.campusId(), cls.id(), from, to)));
        List<DayRow> days = new ArrayList<>();
        for (LocalDate date : schoolDays(from, to)) {
            // present, excused, unexcused, missing, mealTotal, substitute, morningOff, afternoonOff
            int[] counts = new int[8];
            Map<MealSession, Integer> meals = new EnumMap<>(MealSession.class);
            cls.mealSessions().forEach(s -> meals.put(s, 0));
            for (ClassChildResponse ch : children) {
                AttendanceRecord r = records.get(ch.id() + ":" + date);
                int[] child = perChild.get(ch.id());
                if (r == null) {
                    if (inClass(stays, ch.id(), date)) {
                        counts[3]++;
                    }
                    continue;
                }
                int slot = r.getStatus() == AttendanceStatus.PRESENT
                        ? 0
                        : r.getStatus() == AttendanceStatus.EXCUSED ? 1 : 2;
                counts[slot]++;
                child[slot]++;
                child[6] += (int) Math.round(r.presentDays() * 2);
                if (r.getAbsentPart() != null) {
                    int off = r.getAbsentPart() == DayPart.MORNING ? 6 : 7;
                    counts[off]++;
                    child[off - 2]++;
                }
                List<MealSession> eaten = r.getMeals().stream()
                        .filter(cls.mealSessions()::contains)
                        .toList();
                if (!eaten.isEmpty()) {
                    counts[4]++;
                    if (allergic.contains(ch.id())) {
                        counts[5]++;
                    }
                    child[3]++;
                    eaten.forEach(s -> {
                        meals.merge(s, 1, Integer::sum);
                        childMeals.get(ch.id()).merge(s, 1, Integer::sum);
                    });
                }
            }
            int substitute = bookSubstitutes.getOrDefault(date, counts[5]);
            days.add(new DayRow(
                    date,
                    counts[0],
                    counts[1],
                    counts[2],
                    counts[3],
                    counts[4],
                    substitute,
                    meals,
                    counts[6],
                    counts[7]));
        }
        List<ChildTotal> childTotals = children.stream()
                .map(ch -> {
                    int[] c = perChild.get(ch.id());
                    return new ChildTotal(
                            toBrief(ch, withoutMeals, allergic),
                            c[0],
                            c[1],
                            c[2],
                            c[3],
                            c[6] / 2.0,
                            childMeals.get(ch.id()),
                            c[4],
                            c[5]);
                })
                .toList();
        return new ClassSummary(
                cls.id(), cls.name(), cls.campusId(), cls.mealSessions(), from, to, children.size(), days, childTotals);
    }

    private static boolean inClass(List<EnrollmentService.ClassStay> stays, Long childId, LocalDate date) {
        return stays.stream().anyMatch(s -> s.childId().equals(childId) && s.covers(date));
    }

    private static Map<MealSession, Integer> mealSums(List<DayRow> rows, List<MealSession> sessions) {
        Map<MealSession, Integer> meals = new EnumMap<>(MealSession.class);
        sessions.forEach(s -> meals.put(s, 0));
        rows.forEach(r -> r.meals().forEach((s, n) -> meals.merge(s, n, Integer::sum)));
        return meals;
    }

    private static int sum(List<DayRow> rows, ToIntFunction<DayRow> field) {
        return rows.stream().mapToInt(field).sum();
    }

    private static ChildBrief toBrief(ClassChildResponse ch, Set<Long> withoutMeals, Set<Long> allergic) {
        return new ChildBrief(
                ch.id(), ch.fullName(), ch.dateOfBirth(), !withoutMeals.contains(ch.id()), allergic.contains(ch.id()));
    }

    private static RecordView toView(AttendanceRecord r, List<MealSession> sessions, Map<Long, String> names) {
        if (r == null) {
            return null;
        }
        Map<MealSession, Boolean> meals = new EnumMap<>(MealSession.class);
        sessions.forEach(s -> meals.put(s, r.getMeals().contains(s)));
        return new RecordView(
                r.getId(),
                r.getStatus(),
                meals,
                r.getRecordedBy(),
                names.get(r.getRecordedBy()),
                r.getRecordedAt(),
                r.getAbsentPart());
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
                            .map(r -> new ChildDayStatus(r.getChildId(), r.getDate(), r.getStatus(), r.getAbsentPart()))
                            .toList();
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassChildDay> findClassDays(Collection<Long> classroomIds, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|findClassDays|classes=" + classroomIds.size();
        log.info("{}|START", logPrefix);
        try {
            List<ClassChildDay> result = classroomIds.isEmpty()
                    ? List.of()
                    : recordRepository.findByClassroomIdInAndDateBetween(classroomIds, from, to).stream()
                            .map(r -> new ClassChildDay(
                                    r.getClassroomId(),
                                    r.getChildId(),
                                    r.getDate(),
                                    r.getStatus(),
                                    r.getMeals() == null ? List.of() : List.copyOf(r.getMeals()),
                                    r.getAbsentPart()))
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
