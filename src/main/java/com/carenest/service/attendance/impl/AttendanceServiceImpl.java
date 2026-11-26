package com.carenest.service.attendance.impl;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse.ChildTotal;
import com.carenest.dto.attendance.AttendanceSummaryResponse.ClassSummary;
import com.carenest.dto.attendance.AttendanceSummaryResponse.ClassTotal;
import com.carenest.dto.attendance.AttendanceSummaryResponse.DayRow;
import com.carenest.dto.attendance.AttendanceSummaryResponse.MultiSummary;
import com.carenest.dto.attendance.AttendanceSummaryResponse.PreviousClassTotal;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse.ChildBrief;
import com.carenest.dto.attendance.ClassAttendanceResponse.ChildRow;
import com.carenest.dto.attendance.ClassAttendanceResponse.HealthSummary;
import com.carenest.dto.attendance.ClassAttendanceResponse.RecordView;
import com.carenest.dto.attendance.CorrectMealParticipationRequest;
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
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.TemperatureLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private final com.carenest.service.attendance.ChildClassHistoryService childClassHistoryService;
    private final EnrollmentService enrollmentService;
    private final ChildService childService;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;
    private final LeaveRequestRepository leaveRequestRepository;
    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;
    private final com.carenest.repository.attendance.PickupRecordRepository pickupRecordRepository;
    private final EarlyPickupRule earlyPickupRule;
    private final com.carenest.service.attendance.MealChangeGuard mealChangeGuard;

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceClassResponse> getClasses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getClasses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            // Phân công hôm nay đọc một lần cho mọi lớp; mỗi lớp chỉ xét quyền một lần
            Set<Long> active = rules.activeTeacherClassIds(currentUser);
            List<AttendanceClassResponse> classes = new ArrayList<>();
            for (ClassroomInfoResponse c : classroomService.findYearClassrooms(schoolYearId)) {
                LocalDate last = rules.lastReadableDay(currentUser, c);
                // Người dạy thay đã hết đợt vẫn thấy lớp để mở ngày được mở lại trong đợt (xem / ghi xét theo ngày)
                if (last == null && !rules.canView(currentUser, c, active)) {
                    continue;
                }
                boolean teaches = c.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecord(currentUser, c, active);
                boolean records = teaches && currentUser.hasPermission(Permission.ATTENDANCE_RECORD);
                classes.add(new AttendanceClassResponse(
                        c.id(),
                        c.name(),
                        c.campusId(),
                        c.ageGroupId(),
                        c.mealSessions(),
                        teaches,
                        records,
                        c.yearStatus() == SchoolYearStatus.ACTIVE && rules.canUnlock(currentUser, c.campusId()),
                        !teaches && last != null,
                        last));
            }
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
    public DayInfoResponse getYearDayInfo(LocalDate date, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getYearDayInfo|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            var reference = classroomService.findYearClassrooms(schoolYearId).stream()
                    .findFirst()
                    .orElse(null);
            var response = rules.dayInfo(date, null, reference);
            log.info("{}|END", logPrefix);
            return response;
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
            // Cần chốt 24/11/2026: GV của lớp ngày đó (dạy chính / dạy thay) mở màn điểm danh ⇒ đã xem đơn; BGH không
            // tính
            if (!leaves.isEmpty()
                    && AttendanceDayRules.teachesDuring(rules.teacherAssignments(currentUser), cls.id(), day, day)) {
                leaveRequestRepository.markTeacherSeen(
                        leaves.values().stream().map(LeaveTag::requestId).toList(), currentUser.userId(), rules.now());
            }
            Set<Long> childIds = children.stream().map(ClassChildResponse::id).collect(Collectors.toSet());
            Set<Long> withoutMeals = childService.findChildIdsWithoutMeals(childIds);
            Set<Long> allergic = childService.findChildIdsWithAllergies(childIds);
            var history = childClassHistoryService.statuses(
                    cls.id(), childIds, rules.today().isAfter(cls.yearEnd()) ? cls.yearEnd() : rules.today());
            List<ChildRow> rows = children.stream()
                    .map(ch -> new ChildRow(
                            new ChildBrief(
                                    ch.id(),
                                    ch.fullName(),
                                    ch.dateOfBirth(),
                                    !withoutMeals.contains(ch.id()),
                                    allergic.contains(ch.id()),
                                    history.get(ch.id())),
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
                    rules.afternoonStart(cls.schoolYearId(), day),
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
            // ATT-08 (user chốt 26/11/2026): Cả ngày / Chỉ sáng / Chỉ chiều chỉ chọn khi ngày được mở lại, trẻ Có mặt
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                if (entry.presentPart() != null && (!reopened || entry.status() != AttendanceStatus.PRESENT)) {
                    throw GlobalException.of(ApiCode.ATTENDANCE_PART_REQUIRES_UNLOCK);
                }
            }
            Map<Long, DayPart> absentParts = new HashMap<>();
            Set<Long> leaveCoversMorning = new HashSet<>();
            Set<Long> keptFullDayExcused = new HashSet<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                LeaveTag tag = tags.get(entry.childId());
                if (tag == null || tag.noticeOnly()) {
                    continue;
                }
                if (tag.part() != LeavePart.AFTERNOON) {
                    leaveCoversMorning.add(entry.childId());
                }
                AttendanceRecord current = existing.get(entry.childId());
                // Nghỉ sáng trước giờ chốt: GV chỉ đổi công tắc ăn bữa xế (giữ nguyên trạng thái theo đơn)
                boolean mealOnly =
                        tag.part() == LeavePart.MORNING && current != null && current.getStatus() == entry.status();
                // Đơn cả ngày (user chốt 26/11/2026): trẻ vẫn đến lớp thì GV ghi Có mặt ngay, kể cả trước giờ chốt;
                // vẫn không đổi thành vắng không phép
                boolean fullDayLocked =
                        tag.part() == LeavePart.FULL_DAY && entry.status() == AttendanceStatus.UNEXCUSED;
                if (fullDayLocked || (tag.part() == LeavePart.MORNING && !reopened && !mealOnly)) {
                    throw GlobalException.of(ApiCode.LEAVE_ATTENDANCE_LOCKED);
                }
                // Nghỉ sáng đã ghép thêm báo nghỉ chiều: cô lưu lại không đổi thì giữ nguyên cả ngày và suất
                if (tag.part() == LeavePart.MORNING
                        && current != null
                        && current.getStatus() == AttendanceStatus.EXCUSED
                        && current.getAbsentPart() == null
                        && entry.status() == AttendanceStatus.EXCUSED
                        && entry.presentPart() == null
                        && entry.absentPart() == null) {
                    keptFullDayExcused.add(entry.childId());
                    continue;
                }
                absentParts.put(
                        entry.childId(),
                        entry.presentPart() != null
                                ? entry.absentPartFromPresent()
                                : tag.part() == LeavePart.FULL_DAY && entry.status() == AttendanceStatus.PRESENT
                                        ? null
                                        : reopened && entry.absentPart() != null
                                                ? entry.absentPart()
                                                : tag.part().dayPart());
            }
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                if (entry.presentPart() != null && !absentParts.containsKey(entry.childId())) {
                    absentParts.put(entry.childId(), entry.absentPartFromPresent());
                }
            }
            // ATT-09: Vắng có phép chỉ do đơn nghỉ đặt; GV giữ nguyên được trạng thái đã có, không tự chọn mới
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                AttendanceRecord record = existing.get(entry.childId());
                LeaveTag excusingTag = tags.get(entry.childId());
                boolean fullDayLeave =
                        excusingTag != null && !excusingTag.noticeOnly() && excusingTag.part() == LeavePart.FULL_DAY;
                // Đơn nghỉ cả ngày còn hiệu lực thì GV đổi lại Vắng có phép được (trẻ ghi Có mặt nhầm)
                if (entry.status() == AttendanceStatus.EXCUSED
                        && !fullDayLeave
                        && (record == null || record.getStatus() != AttendanceStatus.EXCUSED)) {
                    throw GlobalException.of(ApiCode.ATTENDANCE_EXCUSED_NOT_ALLOWED);
                }
            }
            // CHD-13: trẻ không báo ăn không bao giờ có suất, bỏ qua meals gửi lên
            Set<Long> withoutMeals = childService.findChildIdsWithoutMeals(classChildIds);
            Map<Long, List<MealSession>> mealsByChild = new LinkedHashMap<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                LeaveTag tag = tags.get(entry.childId());
                if (tag != null && tag.noticeOnly()) {
                    tag = null;
                }
                AttendanceRecord current = existing.get(entry.childId());
                // ATT-08: sau giờ chốt, lựa chọn Có mặt cả ngày / chỉ sáng / chỉ chiều
                // là sửa chuyên cần. Không được suy lại hay cắt suất đã chốt theo buổi;
                // bản ghi chưa có trước đó cũng không được tự sinh suất mới.
                if (keptFullDayExcused.contains(entry.childId()) || (reopened && entry.presentPart() != null)) {
                    mealsByChild.put(entry.childId(), current == null ? List.of() : current.getMeals());
                    continue;
                }
                mealsByChild.put(
                        entry.childId(),
                        mealsOf(
                                entry,
                                cls.mealSessions(),
                                !withoutMeals.contains(entry.childId()),
                                absentParts.get(entry.childId()),
                                tag == null || !Boolean.FALSE.equals(tag.eats())));
            }
            // Một lần mở lại vẫn có thể sửa hồ sơ chuyên cần, nhưng tuyệt đối không
            // được làm tăng/giảm suất của bữa đã xác nhận hoặc đã khóa ở bếp.
            // So cả bữa cũ lẫn bữa mới để chặn đúng trường hợp bỏ một suất đã chốt.
            Set<MealSession> changedMealSessions = new LinkedHashSet<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                List<MealSession> before = existing.containsKey(entry.childId())
                        ? existing.get(entry.childId()).getMeals()
                        : List.of();
                List<MealSession> after = mealsByChild.get(entry.childId());
                if (!before.equals(after)) {
                    changedMealSessions.addAll(before);
                    changedMealSessions.addAll(after);
                }
            }
            ensureMealChangeAllowed(cls, date, changedMealSessions);
            Instant at = rules.now();
            Long unlockId = info.unlock() == null ? null : info.unlock().id();
            // PCK-04: trẻ không có đơn, đã giao trước mốc tính đủ ngày ⇒ giữ 0,5 ngày khi lưu lại (bữa không đổi)
            Set<Long> pickedUpEarly = pickedUpBeforeFullDay(cls, date, seen);
            int changed = 0;
            List<Long> presentDespiteLeave = new ArrayList<>();
            for (SaveAttendanceRequest.Entry entry : request.entries()) {
                List<MealSession> meals = mealsByChild.get(entry.childId());
                AttendanceRecord record = existing.get(entry.childId());
                LeaveTag fullDayTag = tags.get(entry.childId());
                if (entry.status() == AttendanceStatus.PRESENT
                        && record != null
                        && record.getStatus() == AttendanceStatus.EXCUSED
                        && fullDayTag != null
                        && !fullDayTag.noticeOnly()
                        && fullDayTag.part() == LeavePart.FULL_DAY) {
                    presentDespiteLeave.add(entry.childId());
                }
                DayPart absentPart = absentParts.containsKey(entry.childId())
                        ? absentParts.get(entry.childId())
                        : entry.status() == AttendanceStatus.PRESENT
                                        && record != null
                                        && record.getAbsentPart() == DayPart.MORNING
                                        && record.isAbsentPartUnexcused()
                                ? DayPart.MORNING
                                : entry.status() == AttendanceStatus.PRESENT && pickedUpEarly.contains(entry.childId())
                                        ? DayPart.AFTERNOON
                                        : null;
                // Vắng sáng không phép khi buổi sáng không có đơn nghỉ che (GV chọn "Chỉ chiều" lúc mở lại)
                boolean unexcusedMorning = absentPart == DayPart.MORNING
                        && (entry.presentPart() != null
                                ? !leaveCoversMorning.contains(entry.childId())
                                : record != null && record.isAbsentPartUnexcused());
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
                } else if (record.differsFrom(entry.status(), meals, absentPart)
                        || record.isAbsentPartUnexcused() != unexcusedMorning
                        || !record.getClassroomId().equals(cls.id())) {
                    record.moveToClass(cls.id(), cls.campusId());
                    record.update(entry.status(), meals, currentUser.userId(), at);
                    action = AttendanceRecordHistory.UPDATED;
                } else {
                    continue;
                }
                record.markAbsentPart(absentPart, unexcusedMorning);
                historyRepository.save(AttendanceRecordHistory.of(record, action, unlockId));
                changed++;
            }
            // Trẻ có đơn nghỉ cả ngày nhưng GV ghi Có mặt: báo phụ huynh, đơn giữ trong lịch sử
            if (!presentDespiteLeave.isEmpty()) {
                notifyGuardiansPresent(presentDespiteLeave, date);
            }
            // Mọi thay đổi điểm danh đều cập nhật số của sổ chưa xác nhận; sổ đã xác nhận khóa cứng nên bị bỏ qua
            // (ATT-07)
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

    private void notifyGuardiansPresent(List<Long> childIds, LocalDate date) {
        for (Long childId : childIds) {
            List<Long> parents = guardianChildLinkService.findLinksOfChild(childId).stream()
                    .map(com.carenest.dto.child.GuardianLinkResponse::userId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!parents.isEmpty()) {
                notificationService.notifyUsers(
                        parents,
                        com.carenest.utils.enums.NotificationType.LEAVE_REQUEST_CHANGED,
                        "Cô đã ghi con có mặt",
                        "Con có đơn nghỉ ngày "
                                + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                + " nhưng đến lớp, cô đã ghi con có mặt. Đơn nghỉ vẫn được lưu trong lịch sử.",
                        childId);
            }
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
            LocalTime afternoonStart = rules.afternoonStart(cls.schoolYearId(), day);
            LocalTime latest = rules.latestArrival(cls.schoolYearId(), day);
            // Trẻ phải thuộc lớp ngày đó; bản ghi do đơn nghỉ gửi trước khi chuyển lớp vẫn mang lớp cũ.
            boolean inClassToday = enrollmentService.findChildrenInClassroom(classId, day).stream()
                    .anyMatch(c -> c.id().equals(childId));
            AttendanceRecord record = !inClassToday
                    ? null
                    : recordRepository.findByChildIdInAndDate(List.of(childId), day).stream()
                            .findFirst()
                            .orElse(null);
            // ATT-03/B2: trường chỉ nhận trẻ trong khung buổi chiều, không phải ngay sau giờ chốt sáng.
            boolean inWindow = day.equals(info.today())
                    && info.baseLocked()
                    && rules.reachedToday(afternoonStart)
                    && !rules.isBaseLocked(day, latest);
            boolean morningLeave = record != null && record.getAbsentPart() == DayPart.MORNING;
            boolean morningUnexcused = record != null
                    && record.getAbsentPart() == null
                    && record.getStatus() == AttendanceStatus.UNEXCUSED;
            if (record == null
                    || (!morningLeave && !morningUnexcused)
                    || record.getStatus() == AttendanceStatus.PRESENT
                    || !(inWindow || info.unlock() != null)) {
                throw GlobalException.of(ApiCode.ATTENDANCE_ARRIVAL_NOT_ALLOWED, afternoonStart, latest);
            }
            Instant at = rules.now();
            record.moveToClass(cls.id(), cls.campusId());
            record.update(AttendanceStatus.PRESENT, record.getMeals(), currentUser.userId(), at);
            // B2: vắng sáng không phép nhưng đến chiều giữ suất đã chốt, chỉ đổi chuyên cần thành 0,5 + 0,5.
            record.markAbsentPart(DayPart.MORNING, morningUnexcused || record.isAbsentPartUnexcused());
            historyRepository.save(AttendanceRecordHistory.of(
                    record,
                    AttendanceRecordHistory.UPDATED,
                    info.unlock() == null ? null : info.unlock().id()));
            // ATT-03/B2: bữa đã chốt giữ nguyên; sổ chưa xác nhận cập nhật số có mặt, sổ đã xác nhận bị bỏ qua (ATT-07)
            eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(day, cls.campusId(), cls.id()));
            log.info("{}|END", logPrefix);
            return new SaveAttendanceResponse(at, 1, 1);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SaveAttendanceResponse correctFullDayPresent(
            CurrentUser currentUser, Long classId, Long childId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|correctFullDayPresent|userId=" + currentUser.userId() + "|classId=" + classId
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
            boolean inClassToday = enrollmentService.findChildrenInClassroom(classId, day).stream()
                    .anyMatch(c -> c.id().equals(childId));
            AttendanceRecord record = !inClassToday
                    ? null
                    : recordRepository.findByChildIdInAndDate(List.of(childId), day).stream()
                            .findFirst()
                            .orElse(null);
            // ATT-03/B2: chỉ sửa lỗi GV ghi nhầm "vắng sáng không phép" khi BGH đã mở lại ngày; đơn nghỉ sáng có phép
            // của phụ huynh không sửa ở đây
            if (info.unlock() == null
                    || record == null
                    || record.getStatus() != AttendanceStatus.PRESENT
                    || record.getAbsentPart() != DayPart.MORNING
                    || !record.isAbsentPartUnexcused()) {
                throw GlobalException.of(ApiCode.ATTENDANCE_FULL_DAY_FIX_NOT_ALLOWED);
            }
            Instant at = rules.now();
            record.update(AttendanceStatus.PRESENT, record.getMeals(), currentUser.userId(), at);
            record.markAbsentPart(null);
            historyRepository.save(AttendanceRecordHistory.of(
                    record, AttendanceRecordHistory.UPDATED, info.unlock().id()));
            // Bữa giữ nguyên; sổ chưa xác nhận cập nhật số, sổ đã xác nhận bị bỏ qua (ATT-07)
            eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(day, cls.campusId(), cls.id()));
            log.info("{}|END", logPrefix);
            return new SaveAttendanceResponse(at, 1, 1);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SaveAttendanceResponse correctMealParticipation(
            CurrentUser currentUser, Long classId, Long childId, CorrectMealParticipationRequest request) {
        String logPrefix = LOG_PREFIX + "|correctMealParticipation|userId=" + currentUser.userId() + "|classId="
                + classId + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = findClass(classId);
            if (!request.date().equals(rules.today())
                    || !rules.canRecordAttendanceDay(currentUser, cls, request.date())) {
                throw GlobalException.of(ApiCode.ATTENDANCE_TODAY_ONLY);
            }
            DayInfoResponse info = rules.dayInfo(request.date(), cls, null);
            // ATT-08, ATT-02a: sau giờ chốt, chỉ lớp/ngày PHT đang mở lại mới được sửa suất chưa xác nhận.
            if (!info.baseLocked() || info.unlock() == null) {
                throw GlobalException.of(ApiCode.ATTENDANCE_LOCKED, info.cutoff());
            }
            classroomService.lockForUpdate(classId);
            AttendanceRecord record = recordRepository.findByChildIdInAndDate(List.of(childId), request.date()).stream()
                    .filter(r -> r.getClassroomId().equals(classId))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_LIST_MISMATCH));
            if (record.getStatus() != AttendanceStatus.PRESENT
                    || childService.findChildIdsWithoutMeals(Set.of(childId)).contains(childId)) {
                throw GlobalException.of(ApiCode.ABSENT_CHILD_NO_MEAL);
            }
            List<MealSession> served = record.getAbsentPart() == null
                    ? cls.mealSessions()
                    : record.getAbsentPart().other().mealsOf(cls.mealSessions());
            ensureMealChangeAllowed(cls, request.date(), new LinkedHashSet<>(served));
            List<MealSession> meals = request.participates() ? served : List.of();
            if (record.getMeals().equals(meals)) return new SaveAttendanceResponse(rules.now(), 1, 0);
            Instant at = rules.now();
            record.update(AttendanceStatus.PRESENT, meals, currentUser.userId(), at);
            historyRepository.save(AttendanceRecordHistory.of(record, AttendanceRecordHistory.UPDATED, null));
            eventPublisher.publishEvent(
                    new AttendanceService.AttendanceChangedEvent(request.date(), cls.campusId(), cls.id()));
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
            LocalDate now = rules.today();
            List<LocalDate> dates = new ArrayList<>(recordRepository.findPastDatesWithOpenMorningLeave(now));
            dates.add(now);
            Instant at = rules.now();
            int closed = 0;
            for (LocalDate today : dates) {
                List<Long> classIds = rules.isSchoolDay(today)
                        ? recordRepository.findClassroomIdsWithOpenMorningLeave(today)
                        : List.of();
                for (Long classId : classIds) {
                    ClassroomInfoResponse cls =
                            classroomService.findClassroomInfo(classId).orElse(null);
                    if (cls != null && cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                        continue;
                    }
                    if (cls == null) {
                        // Lớp đã xóa hoặc không còn: bỏ qua, không làm hỏng việc đóng buổi của các lớp khác
                        log.warn("{}|classId={}|SKIP|class not found", logPrefix, classId);
                        continue;
                    }
                    LocalTime latest = rules.latestArrival(cls.schoolYearId(), today);
                    if (!rules.isLockedAt(today, latest, at)) {
                        continue;
                    }
                    // Khóa lớp như "Đã đến" rồi mới đọc lại: trẻ vừa được bấm đến không bị ghi vắng cả ngày
                    classroomService.lockForUpdate(classId);
                    List<AttendanceRecord> pending =
                            recordRepository.findByClassroomIdInAndDate(List.of(classId), today).stream()
                                    .filter(r -> r.getAbsentPart() == DayPart.MORNING
                                            && r.getStatus() == AttendanceStatus.EXCUSED)
                                    .toList();
                    if (pending.isEmpty()) {
                        continue;
                    }
                    List<Long> teachers = rules.dayTeacherIds(cls, today);
                    Map<Long, String> names = childService
                            .findChildBriefs(pending.stream()
                                    .map(AttendanceRecord::getChildId)
                                    .toList())
                            .entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey, e -> e.getValue().fullName()));
                    for (AttendanceRecord r : pending) {
                        // Sổ báo ăn giữ nguyên (bữa xế đã tính theo công tắc ăn); sai lệch thì BGH mở lại để sửa
                        r.update(AttendanceStatus.UNEXCUSED, r.getMeals(), r.getRecordedBy(), at);
                        historyRepository.save(AttendanceRecordHistory.of(r, AttendanceRecordHistory.UPDATED, null));
                        String body =
                                "Bé " + names.getOrDefault(r.getChildId(), "") + " nghỉ buổi sáng nhưng chưa đến lớp"
                                        + " trước " + latest + ", ghi Vắng cả ngày (sáng có phép, chiều không phép).";
                        Set<Long> receivers = new LinkedHashSet<>(teachers);
                        guardianChildLinkService
                                .findLinksOfChild(r.getChildId())
                                .forEach(l -> receivers.add(l.userId()));
                        if (!receivers.isEmpty()) {
                            try {
                                notificationService.notifyUsers(
                                        receivers,
                                        NotificationType.LEAVE_REQUEST_CHANGED,
                                        "Bé chưa đến buổi chiều",
                                        body,
                                        r.getChildId());
                            } catch (RuntimeException notifyEx) {
                                // Lỗi thông báo không được làm mất việc đóng buổi của cả lớp (job chạy lại mỗi 5 phút)
                                log.warn(
                                        "{}|childId={}|NOTIFY_ERROR|type={}",
                                        logPrefix,
                                        r.getChildId(),
                                        notifyEx.getClass().getSimpleName());
                            }
                        }
                        closed++;
                    }
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
    public ClassSummary getClassSummary(
            CurrentUser currentUser, Long classId, LocalDate from, LocalDate to, Integer semester) {
        String logPrefix = LOG_PREFIX + "|getClassSummary|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = findClass(classId);
            LocalDate[] range = summaryRange(cls.schoolYearId(), from, to, semester);
            // Tổ trưởng chuyên môn xem tổng hợp (không xem phiếu điểm danh/sức khỏe từng ngày) các lớp nhóm tuổi mình
            if (!rules.canViewRange(currentUser, cls, range[0], range[1]) && !rules.leadsClass(currentUser, cls)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            checkRange(range[0], range[1]);
            ClassSummary summary = summarize(cls, range[0], range[1]);
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
            CurrentUser currentUser,
            Long campusId,
            Long schoolYearId,
            Long ageGroupId,
            LocalDate from,
            LocalDate to,
            Integer semester) {
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
            List<ClassroomInfoResponse> yearClasses = classroomService.findYearClassrooms(schoolYearId);
            ClassroomInfoResponse yearRef = yearClasses.isEmpty() ? null : yearClasses.getFirst();
            Long yearId = yearRef != null ? yearRef.schoolYearId() : schoolYearId;
            if (semester != null && yearId == null) {
                throw GlobalException.of(ApiCode.SEMESTER_DATE_REQUIRED, "năm học có học kỳ");
            }
            LocalDate[] range = summaryRange(yearId, from, to, semester);
            checkRange(range[0], range[1]);
            // CFG-YR-01: chỉ tính ngày trong năm học, khoảng chọn vượt năm thì cắt về ngày bắt đầu / kết thúc năm
            LocalDate start = yearRef == null ? range[0] : clampStart(range[0], yearRef);
            LocalDate end = yearRef == null ? range[1] : clampEnd(range[1], yearRef);
            // Phân công hôm nay đọc một lần cho mọi lớp (như getClasses), không tra lại phân công từng lớp
            Set<Long> activeClassIds = leader ? rules.activeTeacherClassIds(currentUser) : Set.of();
            List<ClassSummary> parts = yearClasses.stream()
                    .filter(c -> campusId == null || c.campusId().equals(campusId))
                    .filter(c -> ageGroupId == null || ageGroupId.equals(c.ageGroupId()))
                    .filter(c ->
                            leader ? rules.canView(currentUser, c, activeClassIds) : ledGroups.contains(c.ageGroupId()))
                    .map(c -> summarize(c, start, end))
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
            List<DayRow> days = schoolDays(start, end).stream()
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
                    start,
                    end,
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
            List<ClassroomInfoResponse> classes = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> c.campusId().equals(campusId))
                    .toList();
            Map<Long, Set<Long>> childIdsByClass = enrollmentService.findChildIdsInClassrooms(
                    classes.stream().map(ClassroomInfoResponse::id).toList(), date);
            Set<Long> childIds =
                    childIdsByClass.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
            Map<Long, AttendanceRecord> records = childIds.isEmpty()
                    ? Map.of()
                    : recordRepository.findByChildIdInAndDate(childIds, date).stream()
                            .collect(Collectors.toMap(AttendanceRecord::getChildId, Function.identity()));
            List<ClassMealTally> tallies = classes.stream()
                    .map(c -> tally(c, childIdsByClass.getOrDefault(c.id(), Set.of()), records))
                    .toList();
            log.info("{}|END", logPrefix);
            return tallies;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // NUT-01, ATT-02: suất = trẻ có mặt và có bật tham gia bữa đó; trẻ chưa điểm danh không tính suất.
    private ClassMealTally tally(
            ClassroomInfoResponse cls, Set<Long> childIds, Map<Long, AttendanceRecord> recordsByChild) {
        // Trẻ của lớp ngày đó: bản ghi đơn nghỉ gửi trước khi chuyển lớp tính cho lớp mới (một trẻ một bản ghi / ngày)
        Map<Long, AttendanceRecord> records = recordsByChild.entrySet().stream()
                .filter(entry -> childIds.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        int present = 0;
        int absent = 0;
        Map<MealSession, Integer> meals = new EnumMap<>(MealSession.class);
        cls.mealSessions().forEach(s -> meals.put(s, 0));
        // Bữa đã ghi là lịch sử thực tế, không loại khi cấu hình nhóm tuổi thay đổi.
        records.values().forEach(r -> r.getMeals().forEach(s -> meals.putIfAbsent(s, 0)));
        for (AttendanceRecord r : records.values()) {
            if (r.getStatus().isAbsent()) {
                absent++;
            } else {
                present++;
            }
            // ATT-13: trẻ nghỉ sáng vẫn có bữa xế theo công tắc ăn dù chưa đến; trẻ vắng cả ngày không có bữa
            r.getMeals().stream().filter(meals::containsKey).forEach(s -> meals.merge(s, 1, Integer::sum));
        }
        return new ClassMealTally(
                cls.id(),
                cls.name(),
                cls.campusId(),
                List.copyOf(meals.keySet()),
                present,
                absent,
                childIds.size() - present - absent,
                meals);
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
                .filter(r -> r.isNoticeOnly() || !rules.isLockedAt(date, cutoff, r.getCreatedAt()))
                .collect(Collectors.toMap(
                        LeaveRequest::getChildId,
                        r -> new LeaveTag(
                                r.getId(),
                                r.getFromDate(),
                                r.effectiveTo(),
                                r.getReason(),
                                r.getPart(),
                                r.getEats(),
                                LeaveRequestServiceImpl.eatsMeals(r.getPart(), cls),
                                r.isNoticeOnly()),
                        // Trùng ngày (nghỉ sáng + báo nghỉ chiều): nhãn của đơn có hiệu lực điểm danh được ưu tiên
                        (a, b) -> a.noticeOnly() ? b : a));
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
    private Set<Long> pickedUpBeforeFullDay(ClassroomInfoResponse cls, LocalDate date, Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return Set.of();
        }
        java.util.function.Predicate<java.time.LocalTime> early =
                earlyPickupRule.beforeFullDayOn(cls.schoolYearId(), date);
        return pickupRecordRepository.findByChildIdInAndDateOrderByRecordedAtAscIdAsc(childIds, date).stream()
                .filter(p -> p.getOutcome() == com.carenest.utils.enums.PickupOutcome.HANDED_OVER
                        && p.getHandedOverAt() != null
                        && early.test(java.time.LocalTime.ofInstant(p.getHandedOverAt(), rules.zone())))
                .map(com.carenest.entity.attendance.PickupRecord::getChildId)
                .collect(Collectors.toSet());
    }

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
        // Cần chốt 19/11/2026: bữa của buổi trẻ nghỉ báo câu riêng, không nhầm với bữa ngoài nhóm tuổi
        if (absentPart != null) {
            List<MealSession> leaveMeals = taken.stream()
                    .filter(m -> classSessions.contains(m) && absentPart.includes(m))
                    .toList();
            if (!leaveMeals.isEmpty()) {
                throw GlobalException.of(
                        ApiCode.HALF_DAY_LEAVE_MEAL,
                        absentPart == DayPart.AFTERNOON ? "chiều" : "sáng",
                        leaveMeals.stream()
                                .map(AttendanceServiceImpl::mealLabel)
                                .collect(Collectors.joining(", ")));
            }
        }
        if (!served.containsAll(taken)) {
            throw GlobalException.of(ApiCode.MEAL_SESSION_NOT_SERVED);
        }
        if (entry.status().isAbsent() && absentPart != DayPart.MORNING && !taken.isEmpty()) {
            throw GlobalException.of(ApiCode.ABSENT_CHILD_NO_MEAL);
        }
        return taken;
    }

    /** ATT-02a, ATT-07: bữa đã xác nhận hoặc phiếu bếp đã ký thì khóa cứng, không đổi suất. */
    private void ensureMealChangeAllowed(ClassroomInfoResponse cls, LocalDate date, Set<MealSession> sessions) {
        mealChangeGuard.ensureChangeAllowed(cls.campusId(), cls.schoolYearId(), date, sessions);
    }

    private static String mealLabel(MealSession session) {
        return switch (session) {
            case MORNING -> "Phụ sáng";
            case LUNCH -> "Trưa";
            case AFTERNOON -> "Xế";
        };
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

    // Chọn học kỳ ⇒ ngày học kỳ của năm học, chưa hết học kỳ thì tới hôm nay; không chọn ⇒ from..to người dùng gửi
    private LocalDate[] summaryRange(Long schoolYearId, LocalDate from, LocalDate to, Integer semester) {
        if (semester == null) {
            return new LocalDate[] {from, to};
        }
        LocalDate[] dates = rules.semesterRange(schoolYearId, semester);
        LocalDate today = rules.today();
        return new LocalDate[] {dates[0], dates[1].isAfter(today) ? today : dates[1]};
    }

    private static LocalDate clampStart(LocalDate from, ClassroomInfoResponse cls) {
        return cls.yearStart() != null && from.isBefore(cls.yearStart()) ? cls.yearStart() : from;
    }

    private static LocalDate clampEnd(LocalDate to, ClassroomInfoResponse cls) {
        return cls.yearEnd() != null && to.isAfter(cls.yearEnd()) ? cls.yearEnd() : to;
    }

    private List<LocalDate> schoolDays(LocalDate from, LocalDate to) {
        return Stream.iterate(from, d -> !d.isAfter(to), d -> d.plusDays(1))
                .filter(rules::isSchoolDay)
                .toList();
    }

    // Mọi trẻ từng học lớp trong khoảng; mỗi ngày chỉ tính trẻ thuộc lớp ngày đó (OBS-23)
    private ClassSummary summarize(ClassroomInfoResponse cls, LocalDate requestedFrom, LocalDate requestedTo) {
        // CFG-YR-01: chỉ ngày học trong năm học của lớp
        LocalDate from = clampStart(requestedFrom, cls);
        LocalDate to = clampEnd(requestedTo, cls);
        List<EnrollmentService.ClassStay> stays = enrollmentService.findStaysInClassroom(cls.id(), from, to);
        List<ClassChildResponse> children = enrollmentService.findChildrenInClassroomDuring(cls.id(), from, to);
        List<Long> childIds = children.stream().map(ClassChildResponse::id).toList();
        Set<Long> withoutMeals = childService.findChildIdsWithoutMeals(childIds);
        List<AttendanceRecord> childRecords = recordRepository.findByChildIdInAndDateBetween(childIds, from, to);
        // OBS-23 (Cần chốt 24/11/2026): bản ghi thuộc lớp theo thời gian trẻ ở lớp, không theo lớp lưu trên bản ghi —
        // bỏ bản ghi sót sau khi trẻ nghỉ / chuyển đi; đơn nghỉ gửi trước khi chuyển lớp tính cho lớp mới
        Map<String, AttendanceRecord> records = childRecords.stream()
                .filter(r -> inClass(stays, r.getChildId(), r.getDate()))
                .collect(Collectors.toMap(r -> r.getChildId() + ":" + r.getDate(), Function.identity()));
        // present, excused, unexcused, meals, morningOff, afternoonOff, nửa ngày có mặt (đơn vị 0,5)
        Map<Long, int[]> perChild = new LinkedHashMap<>();
        Map<Long, Map<MealSession, Integer>> childMeals = new HashMap<>();
        children.forEach(ch -> {
            perChild.put(ch.id(), new int[7]);
            childMeals.put(ch.id(), new EnumMap<>(MealSession.class));
        });
        Set<Long> allergic = childService.findChildIdsWithAllergies(childIds);
        List<DayRow> days = new ArrayList<>();
        Set<MealSession> summarySessions = new java.util.TreeSet<>(cls.mealSessions());
        records.values().forEach(r -> summarySessions.addAll(r.getMeals()));
        for (LocalDate date : schoolDays(from, to)) {
            // present, excused, unexcused, missing, mealTotal, substitute, morningOff, afternoonOff
            int[] counts = new int[8];
            Map<MealSession, Integer> meals = new EnumMap<>(MealSession.class);
            summarySessions.forEach(s -> meals.put(s, 0));
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
                List<MealSession> eaten = r.getMeals();
                if (!eaten.isEmpty()) {
                    counts[4]++;
                    child[3]++;
                    eaten.forEach(s -> {
                        meals.merge(s, 1, Integer::sum);
                        childMeals.get(ch.id()).merge(s, 1, Integer::sum);
                    });
                }
            }
            days.add(new DayRow(
                    date, counts[0], counts[1], counts[2], counts[3], counts[4], 0, meals, counts[6], counts[7]));
        }
        var history = childClassHistoryService.statuses(
                cls.id(), childIds, rules.today().isAfter(cls.yearEnd()) ? cls.yearEnd() : rules.today());
        Map<Long, ClassroomInfoResponse> classCache = new HashMap<>();
        List<ChildTotal> childTotals = children.stream()
                .map(ch -> {
                    int[] c = perChild.get(ch.id());
                    LocalDate movedIn = transferredInOn(cls, ch.id(), stays, from, classCache);
                    return new ChildTotal(
                            toBrief(ch, withoutMeals, allergic),
                            c[0],
                            c[1],
                            c[2],
                            c[3],
                            c[6] / 2.0,
                            childMeals.get(ch.id()),
                            c[4],
                            c[5],
                            history.get(ch.id()),
                            movedIn,
                            movedIn == null
                                    ? List.of()
                                    : previousClasses(cls, ch.id(), movedIn, from, childRecords, classCache));
                })
                .toList();
        return new ClassSummary(
                cls.id(),
                cls.name(),
                cls.campusId(),
                List.copyOf(summarySessions),
                from,
                to,
                children.size(),
                days,
                childTotals);
    }

    /** Ngày trẻ chuyển đến lớp trong khoảng: đoạn ở lớp bắt đầu sau from và trước đó trẻ học lớp khác cùng năm. */
    private LocalDate transferredInOn(
            ClassroomInfoResponse cls,
            Long childId,
            List<EnrollmentService.ClassStay> stays,
            LocalDate from,
            Map<Long, ClassroomInfoResponse> classCache) {
        LocalDate first = stays.stream()
                .filter(s -> s.childId().equals(childId))
                .map(EnrollmentService.ClassStay::from)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (first == null || !first.isAfter(from) || cls.yearStart() == null || !first.isAfter(cls.yearStart())) {
            return null;
        }
        boolean fromOtherClass = enrollmentService.findChildStays(childId, cls.yearStart(), first.minusDays(1)).stream()
                .anyMatch(s -> !s.classroomId().equals(cls.id()) && sameYear(s.classroomId(), cls, classCache));
        return fromOtherClass ? first : null;
    }

    // Cần chốt 24/11/2026: số liệu ở lớp cũ chỉ để xem, đếm trong phần khoảng tổng hợp trẻ còn ở lớp cũ
    private List<PreviousClassTotal> previousClasses(
            ClassroomInfoResponse cls,
            Long childId,
            LocalDate movedIn,
            LocalDate from,
            List<AttendanceRecord> childRecords,
            Map<Long, ClassroomInfoResponse> classCache) {
        List<PreviousClassTotal> result = new ArrayList<>();
        for (EnrollmentService.ChildClassStay stay :
                enrollmentService.findChildStays(childId, from, movedIn.minusDays(1))) {
            if (stay.classroomId().equals(cls.id()) || !sameYear(stay.classroomId(), cls, classCache)) {
                continue;
            }
            int[] counts = new int[3];
            childRecords.stream()
                    .filter(r -> r.getChildId().equals(childId)
                            && !r.getDate().isBefore(stay.from())
                            && !r.getDate().isAfter(stay.to()))
                    .forEach(r -> counts[
                            r.getStatus() == AttendanceStatus.PRESENT
                                    ? 0
                                    : r.getStatus() == AttendanceStatus.EXCUSED ? 1 : 2]++);
            result.add(new PreviousClassTotal(
                    stay.classroomId(),
                    classCache.get(stay.classroomId()).name(),
                    stay.from(),
                    stay.to(),
                    counts[0],
                    counts[1],
                    counts[2]));
        }
        return result;
    }

    private boolean sameYear(Long classroomId, ClassroomInfoResponse cls, Map<Long, ClassroomInfoResponse> cache) {
        ClassroomInfoResponse other = cache.computeIfAbsent(
                classroomId, id -> classroomService.findClassroomInfo(id).orElse(null));
        return other != null && cls.schoolYearId().equals(other.schoolYearId());
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
                r.getAbsentPart(),
                r.isAbsentPartUnexcused());
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
            // Bản ghi thuộc lớp theo thời gian trẻ ở lớp, không theo lớp lưu sẵn trên bản ghi (đơn nghỉ tạo bản ghi từ
            // trước): bỏ ngày sau khi trẻ nghỉ / chuyển đi, tính cho lớp mới ngày trẻ đã chuyển đến
            List<ClassChildDay> result = new ArrayList<>();
            Map<Long, List<EnrollmentService.ClassStay>> staysByClass = new LinkedHashMap<>();
            Set<Long> childIds = new HashSet<>();
            for (Long classroomId : classroomIds) {
                List<EnrollmentService.ClassStay> stays = enrollmentService.findStaysInClassroom(classroomId, from, to);
                staysByClass.put(classroomId, stays);
                stays.forEach(stay -> childIds.add(stay.childId()));
            }
            if (!childIds.isEmpty()) {
                for (AttendanceRecord r : recordRepository.findByChildIdInAndDateBetween(childIds, from, to)) {
                    staysByClass.forEach((classroomId, stays) -> {
                        if (inClass(stays, r.getChildId(), r.getDate())) {
                            result.add(new ClassChildDay(
                                    classroomId,
                                    r.getChildId(),
                                    r.getDate(),
                                    r.getStatus(),
                                    r.getMeals() == null ? List.of() : List.copyOf(r.getMeals()),
                                    r.getAbsentPart()));
                        }
                    });
                }
            }
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildAttendanceDay> findChildAttendance(Collection<Long> childIds, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|findChildAttendance|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            List<ChildAttendanceDay> result = childIds.isEmpty()
                    ? List.of()
                    : recordRepository.findByChildIdInAndDateBetween(childIds, from, to).stream()
                            .map(r -> new ChildAttendanceDay(
                                    r.getChildId(),
                                    r.getClassroomId(),
                                    r.getDate(),
                                    r.getStatus(),
                                    r.getAbsentPart(),
                                    r.getRecordedBy()))
                            .sorted(Comparator.comparing(ChildAttendanceDay::date))
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
