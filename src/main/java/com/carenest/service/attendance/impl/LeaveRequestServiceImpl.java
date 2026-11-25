package com.carenest.service.attendance.impl;

import com.carenest.config.AttendanceProperties;
import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveDay;
import com.carenest.dto.attendance.LeaveRequestDtos.LeavePreviewResponse;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveRequestResponse;
import com.carenest.dto.attendance.LeaveRequestDtos.PreviewLeaveRequest;
import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.AttendanceRecordHistory;
import com.carenest.entity.attendance.LeaveAttendanceEffect;
import com.carenest.entity.attendance.LeaveRequest;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.LeaveAttendanceEffectRepository;
import com.carenest.repository.attendance.LeaveRequestRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.ChildClassHistoryService;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.LeavePart;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ATT-04, ATT-05, ATT-05b: đơn nghỉ là thông báo, không duyệt. Ngày học chưa qua giờ chốt được ghi Vắng có phép, không
 * suất (sổ báo ăn tính theo điểm danh); ngày đã qua giờ chốt giữ nguyên. Trẻ đến lớp phải được phụ huynh hủy đơn trước giờ chốt rồi giáo viên mới điểm danh lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private static final String LOG_PREFIX = "[LeaveRequestServiceImpl]";
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveAttendanceEffectRepository leaveAttendanceEffectRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordHistoryRepository historyRepository;
    private final AttendanceDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceProperties attendanceProperties;
    private final ChildService childService;
    private final StaffAssignmentService staffAssignmentService;
    private final NotificationService notificationService;
    private final ChildClassHistoryService childClassHistoryService;

    @Override
    public LeaveRequestResponse createLeaveRequest(CurrentUser currentUser, CreateLeaveRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createLeaveRequest|userId=" + currentUser.userId() + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        try {
            LeavePlan plan = planLeave(
                    currentUser,
                    request.childId(),
                    request.fromDate(),
                    request.toDate(),
                    request.part(),
                    request.eats(),
                    true);
            LocalDate from = request.fromDate();
            LocalDate to = request.toDate();
            LeavePart part = plan.part();
            Boolean eats = plan.eats();
            List<LocalDate> applied = plan.applied();
            List<LocalDate> skipped = plan.skipped();
            Map<LocalDate, ClassroomInfoResponse> classOf = plan.classOf();
            List<LeaveDay> days = plan.days();
            Instant at = rules.now();
            LeaveRequest saved = LeaveRequest.create(
                            request.childId(), from, to, request.reason().trim(), currentUser.userId(), at)
                    .withPart(part, eats);
            if (plan.noticeOnly()) {
                saved.asNoticeOnly();
            }
            saved = leaveRequestRepository.save(saved);
            boolean mealRegistered = !childService
                    .findChildIdsWithoutMeals(List.of(request.childId()))
                    .contains(request.childId());
            for (LocalDate d : applied) {
                if (part == LeavePart.FULL_DAY) {
                    markExcused(saved, classOf.get(d), d, at);
                } else {
                    markHalfDay(saved, classOf.get(d), d, Boolean.TRUE.equals(eats) && mealRegistered, at);
                }
            }
            Map<LocalDate, ClassroomInfoResponse> notifiedClasses = new HashMap<>(classOf);
            if (plan.noticeOnly()) {
                notifiedClasses.put(from, plan.startingClass());
            }
            notifyTeachers(
                    request.childId(), notifiedClasses, plan.noticeOnly() ? List.of(from) : applied, from, to, part);
            ClassroomInfoResponse responseClass =
                    applied.isEmpty() ? plan.startingClass() : classOf.get(applied.getFirst());
            LeaveRequestResponse response = toResponse(
                    saved,
                    responseClass,
                    applied,
                    skipped,
                    childNames(saved, responseClass),
                    days,
                    accountService.findFullNames(Set.of(saved.getCreatedBy())),
                    null);
            log.info(
                    "{}|leaveRequestId={}|days={}|noticeOnly={}|END",
                    logPrefix,
                    saved.getId(),
                    applied.size(),
                    plan.noticeOnly());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Kiểm tra đơn nghỉ dùng chung cho gửi và xem trước: cùng thứ tự kiểm tra, cùng mã lỗi (ATT-04, ATT-05, ATT-12). */
    private record LeavePlan(
            LeavePart part,
            Boolean eats,
            List<LocalDate> applied,
            List<LocalDate> skipped,
            Map<LocalDate, ClassroomInfoResponse> classOf,
            List<LeaveDay> days,
            ClassroomInfoResponse startingClass,
            boolean noticeOnly) {}

    private LeavePlan planLeave(
            CurrentUser user,
            Long childId,
            LocalDate from,
            LocalDate to,
            LeavePart requestedPart,
            Boolean requestedEats,
            boolean lock) {
        checkGuardian(user, childId);
        if (to.isBefore(from)) {
            throw GlobalException.of(ApiCode.LEAVE_DATE_RANGE_INVALID);
        }
        if (from.isBefore(rules.today())) {
            throw GlobalException.of(ApiCode.LEAVE_DATE_IN_PAST);
        }
        LeavePart part = requestedPart == null ? LeavePart.FULL_DAY : requestedPart;

        // ATT-12: nghỉ một buổi chỉ cho đơn 1 ngày
        if (part != LeavePart.FULL_DAY && !from.equals(to)) {
            throw GlobalException.of(ApiCode.LEAVE_HALF_DAY_SINGLE_DAY);
        }
        ClassroomInfoResponse startingClass = activeClassOf(childId, from);
        // ATT-12 (Cần chốt 18/11/2026): công tắc ăn chỉ áp dụng khi buổi còn học có bữa của nhóm tuổi
        boolean hasMeals = part != LeavePart.FULL_DAY
                && !part.dayPart().other().mealsOf(startingClass.mealSessions()).isEmpty();
        Boolean eats = hasMeals ? (Boolean) (requestedEats == null || requestedEats) : null;
        if (from.isBefore(startingClass.yearStart()) || to.isAfter(startingClass.yearEnd())) {
            throw GlobalException.of(ApiCode.LEAVE_DATE_RANGE_INVALID);
        }
        // ATT-05: một đơn tối đa leaveMaxDays ngày lịch, tính cả ngày đầu và ngày cuối
        if (ChronoUnit.DAYS.between(from, to) + 1 > attendanceProperties.leaveMaxDays()) {
            throw GlobalException.of(ApiCode.LEAVE_TOO_LONG, attendanceProperties.leaveMaxDays());
        }
        if (lock) {
            lockClasses(childId, from, to);
        }
        List<LocalDate> applied = new ArrayList<>();
        List<LocalDate> skipped = new ArrayList<>();
        Map<LocalDate, ClassroomInfoResponse> classOf = new HashMap<>();
        List<LeaveDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!rules.isSchoolDay(d)) {
                continue;
            }
            ClassroomInfoResponse cls = activeClassOf(childId, d);
            days.add(new LeaveDay(d, cls.id(), cls.name()));
            // ATT-05b: ngày đã qua giờ chốt giữ nguyên điểm danh và số suất
            if (rules.isBaseLocked(d, rules.cutoff(cls.schoolYearId(), d))) {
                skipped.add(d);
            } else {
                applied.add(d);
                classOf.put(d, cls);
            }
        }
        // User chốt 25/11/2026: sau giờ chốt chỉ nhận báo nghỉ chiều của chính hôm nay; không sửa điểm danh/suất.
        boolean noticeOnly = applied.isEmpty()
                && part == LeavePart.AFTERNOON
                && from.equals(rules.today())
                && to.equals(rules.today())
                && skipped.equals(List.of(from));
        if (applied.isEmpty() && !noticeOnly) {
            throw GlobalException.of(ApiCode.LEAVE_NO_SCHOOL_DAY);
        }
        List<LocalDate> effectiveDays = noticeOnly ? List.of(from) : applied;
        for (LeaveRequest other : leaveRequestRepository.findOverlapping(List.of(childId), from, to)) {
            for (LocalDate day : effectiveDays) {
                if (!day.isBefore(other.getFromDate()) && !day.isAfter(other.effectiveTo())) {
                    throw GlobalException.of(ApiCode.LEAVE_OVERLAP, day.format(DD_MM_YYYY));
                }
            }
        }
        return new LeavePlan(
                part, noticeOnly ? null : eats, applied, skipped, classOf, days, startingClass, noticeOnly);
    }

    @Override
    @Transactional(readOnly = true)
    public LeavePreviewResponse previewLeaveRequest(CurrentUser currentUser, PreviewLeaveRequest request) {
        String logPrefix =
                LOG_PREFIX + "|previewLeaveRequest|userId=" + currentUser.userId() + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        try {
            // Không lưu gì: chỉ kiểm tra như khi gửi và trả bữa bị hủy / còn ăn theo nhóm tuổi của lớp ngày đó
            LeavePlan plan = planLeave(
                    currentUser,
                    request.childId(),
                    request.fromDate(),
                    request.toDate(),
                    request.part(),
                    request.eats(),
                    false);
            ClassroomInfoResponse cls = plan.applied().isEmpty()
                    ? plan.startingClass()
                    : plan.classOf().get(plan.applied().getFirst());
            List<MealSession> kept = eatsMeals(plan.part(), cls);
            LeavePreviewResponse preview = new LeavePreviewResponse(
                    plan.part(),
                    offMeals(plan.part(), cls),
                    kept,
                    plan.part() != LeavePart.FULL_DAY && !kept.isEmpty(),
                    plan.applied(),
                    plan.skipped(),
                    plan.noticeOnly());
            log.info("{}|days={}|END", logPrefix, plan.applied().size());
            return preview;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** ATT-05: đơn nghỉ mới ⇒ báo đúng GV dạy từng ngày, gồm người dạy thay đang hiệu lực. */
    private void notifyTeachers(
            Long childId,
            Map<LocalDate, ClassroomInfoResponse> classOf,
            List<LocalDate> days,
            LocalDate from,
            LocalDate to,
            LeavePart part) {
        Set<Long> teachers = new LinkedHashSet<>();
        for (LocalDate day : days) {
            ClassroomInfoResponse cls = classOf.get(day);
            if (cls != null) {
                teachers.addAll(teacherIds(cls, day));
            }
        }
        if (teachers.isEmpty()) {
            return;
        }
        ChildBriefResponse child =
                childService.findChildBriefs(List.of(childId)).get(childId);
        notificationService.notifyEvent(
                teachers,
                NotificationType.LEAVE_REQUEST_CREATED,
                Map.of(
                        "tenTre",
                        child == null ? "Bé" : "Bé " + child.fullName(),
                        "tuNgay",
                        from.format(DD_MM_YYYY),
                        "denNgay",
                        to.format(DD_MM_YYYY) + partSuffix(part)),
                childId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> findMyLeaveRequests(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|findMyLeaveRequests|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            List<Long> childIds = guardianChildLinkService.findChildIdsOfGuardian(currentUser.userId());
            List<LeaveRequest> leaves = childIds.isEmpty()
                    ? List.of()
                    : leaveRequestRepository.findByChildIdInOrderByFromDateDescIdDesc(childIds);
            // Đọc theo lô cho mọi đơn: tên trẻ, người gửi, lớp theo ngày (tránh truy vấn theo từng đơn)
            LeaveViews views = new LeaveViews(leaves);
            Map<Long, String> names = new HashMap<>();
            childService.findChildBriefs(childIds).forEach((id, brief) -> names.put(id, brief.fullName()));
            Map<Long, ClassroomInfoResponse> todayClass = new HashMap<>();
            List<LeaveRequestResponse> result = leaves.stream()
                    .map(r -> {
                        ClassroomInfoResponse cls = views.classOn(r.getChildId(), r.getFromDate());
                        if (cls == null) {
                            cls = todayClass.computeIfAbsent(r.getChildId(), id -> classOnOrNull(id, rules.today()));
                        }
                        return toResponse(r, cls, null, null, names, views);
                    })
                    .toList();
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // OBS-31: báo GV lớp khi phụ huynh gửi hoặc hủy đơn nghỉ
    private void notifyTeachers(ClassroomInfoResponse cls, LocalDate date, Long childId, String title, String body) {
        if (cls == null) {
            return;
        }
        Set<Long> teachers = teacherIds(cls, date);
        if (!teachers.isEmpty()) {
            notificationService.notifyUsers(teachers, NotificationType.LEAVE_REQUEST_CHANGED, title, body, childId);
        }
    }

    private Set<Long> teacherIds(ClassroomInfoResponse cls, LocalDate date) {
        Set<Long> teachers = new LinkedHashSet<>();
        List<Long> classIds = List.of(cls.id());
        teachers.addAll(
                staffAssignmentService.findCurrentMainTeacherIds(classIds, date).getOrDefault(cls.id(), List.of()));
        teachers.addAll(staffAssignmentService
                .findSubstituteTeacherIdsOn(classIds, date)
                .getOrDefault(cls.id(), List.of()));
        return teachers;
    }

    @Override
    public LeaveRequestResponse cancelLeaveRequest(CurrentUser currentUser, Long leaveRequestId) {
        String logPrefix =
                LOG_PREFIX + "|cancelLeaveRequest|userId=" + currentUser.userId() + "|leaveRequestId=" + leaveRequestId;
        log.info("{}|START", logPrefix);
        try {
            LeaveRequest leave = leaveRequestRepository
                    .findById(leaveRequestId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.LEAVE_NOT_FOUND));
            checkGuardian(currentUser, leave.getChildId());
            Instant at = rules.now();
            // Báo nghỉ chiều sau giờ chốt chỉ là thông báo nên phụ huynh hủy được mà không mở/sửa điểm danh.
            if (leave.isNoticeOnly()) {
                if (leave.getCancelledFrom() != null) {
                    throw GlobalException.of(ApiCode.LEAVE_CANNOT_CANCEL);
                }
                leave.cancelFrom(leave.getFromDate(), currentUser.userId(), at);
                leaveRequestRepository.flush();
                notifyTeachers(
                        classOnOrNull(leave.getChildId(), leave.getFromDate()),
                        leave.getFromDate(),
                        leave.getChildId(),
                        "Phụ huynh hủy báo nghỉ",
                        "Phụ huynh hủy báo nghỉ chiều ngày "
                                + leave.getFromDate().format(DD_MM_YYYY) + ".");
                return toResponse(leave, classOnOrNull(leave.getChildId(), leave.getFromDate()), null, null);
            }
            lockClasses(leave.getChildId(), rules.today(), leave.effectiveTo());
            LocalDate today = rules.today();
            LocalDate end = leave.effectiveTo();
            LocalDate first = null;
            for (LocalDate d = leave.getFromDate().isBefore(today) ? today : leave.getFromDate();
                    !d.isAfter(end);
                    d = d.plusDays(1)) {
                if (!isLocked(leave.getChildId(), d)) {
                    first = d;
                    break;
                }
            }
            if (first == null) {
                throw GlobalException.of(ApiCode.LEAVE_CANNOT_CANCEL);
            }
            leave.cancelFrom(first, currentUser.userId(), at);
            Map<Long, LeaveAttendanceEffect> effects =
                    leaveAttendanceEffectRepository.findByLeaveRequestId(leave.getId()).stream()
                            .collect(Collectors.toMap(LeaveAttendanceEffect::getAttendanceRecordId, effect -> effect));
            for (LocalDate d = first; !d.isAfter(end); d = d.plusDays(1)) {
                LocalDate day = d;
                // Đơn gửi trước V196 không có dòng leave_attendance_effects: hoàn tác như cách cũ
                if (effects.isEmpty()) {
                    revertLegacy(leave, day, currentUser.userId(), at);
                    continue;
                }
                recordRepository
                        .findByChildIdInAndDate(List.of(leave.getChildId()), day)
                        .forEach(record -> {
                            LeaveAttendanceEffect effect = effects.get(record.getId());
                            if (effect != null) {
                                revertEffect(effect, record, currentUser.userId(), at);
                            } else if (leave.getPart() == LeavePart.AFTERNOON
                                    && record.getAbsentPart() == DayPart.AFTERNOON) {
                                // Bản ghi được GV tạo sau khi đơn đã gửi: bỏ đúng dấu do đơn áp, trả lại dữ liệu để GV
                                // chỉnh tiếp.
                                restoreTeacherRecordedAfternoon(record, leave.getChildId(), currentUser.userId(), at);
                            }
                        });
            }
            leaveRequestRepository.flush();
            notifyTeachers(
                    classOnOrNull(leave.getChildId(), first),
                    first,
                    leave.getChildId(),
                    "Phụ huynh hủy đơn nghỉ",
                    "Phụ huynh hủy đơn nghỉ từ ngày " + first.format(DD_MM_YYYY) + ", cô điểm danh lại các ngày này.");
            LeaveRequestResponse response =
                    toResponse(leave, classOnOrNull(leave.getChildId(), leave.getFromDate()), null, null);
            log.info("{}|cancelledFrom={}|END", logPrefix, first);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<LeaveRequestResponse> searchLeaveRequests(
            CurrentUser currentUser, Long classId, Long campusId, LocalDate from, LocalDate to, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|searchLeaveRequests|userId=" + currentUser.userId() + "|classId=" + classId
                + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate start = from != null ? from : rules.today().withDayOfMonth(1);
            LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
            if (end.isBefore(start)) {
                throw GlobalException.of(ApiCode.LEAVE_DATE_RANGE_INVALID);
            }
            List<ClassroomInfoResponse> classes = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> classId == null || c.id().equals(classId))
                    .filter(c -> campusId == null || c.campusId().equals(campusId))
                    .toList();
            // GV lọc điểm trường có lớp mình vẫn xem được; điểm trường không có lớp nào xem được thì chặn
            if (campusId != null
                    && !campusAccessPolicy.canReadCampus(currentUser, campusId)
                    && classes.stream().noneMatch(c -> rules.canView(currentUser, c))) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            if (classId != null && (classes.isEmpty() || !rules.canView(currentUser, classes.getFirst()))) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            // GV: lớp mình; PHT: lớp điểm trường mình; HT: mọi lớp (AUTH-01..03). Trẻ từng học lớp trong khoảng xem
            // vẫn thấy đơn (OBS-23), nhưng chỉ đơn trùng thời gian trẻ ở lớp đó và gắn đúng lớp
            Map<Long, List<StayInClass>> staysOfChild = new HashMap<>();
            Map<Long, String> names = new HashMap<>();
            for (ClassroomInfoResponse c : classes) {
                if (!rules.canView(currentUser, c)) {
                    continue;
                }
                enrollmentService
                        .findChildrenInClassroomDuring(c.id(), start, end)
                        .forEach(ch -> names.putIfAbsent(ch.id(), ch.fullName()));
                enrollmentService
                        .findStaysInClassroom(c.id(), start, end)
                        .forEach(st -> staysOfChild
                                .computeIfAbsent(st.childId(), k -> new ArrayList<>())
                                .add(new StayInClass(c, st)));
            }
            List<LeaveRequestResponse> result = new ArrayList<>();
            if (!staysOfChild.isEmpty()) {
                List<LeaveRequest> leaves = leaveRequestRepository.findOverlapping(staysOfChild.keySet(), start, end);
                LeaveViews views = new LeaveViews(leaves);
                List<StaffAssignmentResponse> teaching = rules.teacherAssignments(currentUser);
                Set<Long> seen = new java.util.HashSet<>();
                for (LeaveRequest r : leaves) {
                    // Đơn đã hủy hết vẫn hiện (giữ lịch sử đơn), xét theo khoảng ngày phụ huynh gửi
                    LocalDate leaveEnd = r.effectiveTo().isBefore(r.getFromDate()) ? r.getToDate() : r.effectiveTo();
                    staysOfChild.get(r.getChildId()).stream()
                            .filter(x -> !x.stay().from().isAfter(leaveEnd)
                                    && !x.stay().to().isBefore(r.getFromDate()))
                            .min(Comparator.comparing(x -> x.stay().from()))
                            .ifPresent(x -> {
                                result.add(toResponse(r, x.cls(), null, null, names, views));
                                // Cần chốt 24/11/2026: GV của lớp (dạy chính / dạy thay ngày của đơn) mở danh sách ⇒ đã
                                // xem
                                if (AttendanceDayRules.teachesDuring(
                                        teaching, x.cls().id(), r.getFromDate(), r.getToDate())) {
                                    seen.add(r.getId());
                                }
                            });
                }
                if (!seen.isEmpty()) {
                    leaveRequestRepository.markTeacherSeen(seen, currentUser.userId(), rules.now());
                }
            }
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private record StayInClass(ClassroomInfoResponse cls, EnrollmentService.ClassStay stay) {}

    private void lockClasses(Long childId, LocalDate from, LocalDate to) {
        Set<Long> ids = new java.util.TreeSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            classOn(childId, d).ifPresent(c -> ids.add(c.id()));
        }
        ids.forEach(classroomService::lockForUpdate);
    }

    private void checkGuardian(CurrentUser user, Long childId) {
        if (!guardianChildLinkService.isGuardianOf(user.userId(), childId)) {
            throw GlobalException.of(ApiCode.LEAVE_NOT_GUARDIAN);
        }
    }

    private boolean isLocked(Long childId, LocalDate date) {
        return classOn(childId, date)
                .map(c -> rules.isBaseLocked(date, rules.cutoff(c.schoolYearId(), date)))
                .orElse(date.isBefore(rules.today()));
    }

    private ClassroomInfoResponse activeClassOf(Long childId, LocalDate date) {
        return classOn(childId, date)
                .filter(c -> c.yearStatus() == SchoolYearStatus.ACTIVE)
                .orElseThrow(() -> GlobalException.of(ApiCode.LEAVE_CHILD_NO_CLASS, date.format(DD_MM_YYYY)));
    }

    private Optional<ClassroomInfoResponse> classOn(Long childId, LocalDate date) {
        return enrollmentService.findActiveClassroomIds(childId, date).stream()
                .findFirst()
                .flatMap(classroomService::findClassroomInfo);
    }

    private ClassroomInfoResponse classOnOrNull(Long childId, LocalDate date) {
        return classOn(childId, date).or(() -> classOn(childId, rules.today())).orElse(null);
    }

    private void markExcused(LeaveRequest leave, ClassroomInfoResponse cls, LocalDate date, Instant at) {
        Long childId = leave.getChildId();
        AttendanceRecord record = recordRepository.findByChildIdInAndDate(List.of(childId), date).stream()
                .findFirst()
                .orElse(null);
        AttendanceStatus beforeStatus = record == null ? null : record.getStatus();
        List<MealSession> beforeMeals = record == null ? null : List.copyOf(record.getMeals());
        DayPart beforeAbsentPart = record == null ? null : record.getAbsentPart();
        Long beforeRecordedBy = record == null ? null : record.getRecordedBy();
        Instant beforeRecordedAt = record == null ? null : record.getRecordedAt();
        boolean created = record == null;
        String action;
        if (record == null) {
            record = recordRepository.save(AttendanceRecord.create(
                    childId,
                    cls.id(),
                    cls.campusId(),
                    date,
                    AttendanceStatus.EXCUSED,
                    List.of(),
                    leave.getCreatedBy(),
                    at));
            action = AttendanceRecordHistory.RECORDED;
        } else if (record.differsFrom(AttendanceStatus.EXCUSED, List.of())
                || !record.getRecordedBy().equals(leave.getCreatedBy())) {
            record.update(AttendanceStatus.EXCUSED, List.of(), leave.getCreatedBy(), at);
            action = AttendanceRecordHistory.UPDATED;
        } else {
            return;
        }
        historyRepository.save(AttendanceRecordHistory.of(record, action, null));
        saveEffect(
                leave,
                record,
                created,
                beforeStatus,
                beforeMeals,
                beforeAbsentPart,
                beforeRecordedBy,
                beforeRecordedAt);
        eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(date, cls.campusId(), cls.id()));
    }

    private static String partSuffix(LeavePart part) {
        return switch (part) {
            case MORNING -> " (nghỉ buổi sáng)";
            case AFTERNOON -> " (nghỉ buổi chiều)";
            case FULL_DAY -> "";
        };
    }

    /**
     * ATT-12, ATT-13: nghỉ sáng ⇒ bản ghi "Nghỉ sáng (có phép)" chờ trẻ đến buổi chiều, bữa xế theo công tắc ăn; nghỉ
     * chiều ⇒ GV điểm danh buổi sáng như thường, bản ghi đã có thì bỏ bữa xế (và bữa sáng nếu tắt ăn).
     */
    private void markHalfDay(LeaveRequest leave, ClassroomInfoResponse cls, LocalDate date, boolean eats, Instant at) {
        Long childId = leave.getChildId();
        DayPart off = leave.getPart().dayPart();
        AttendanceRecord record = recordRepository.findByChildIdInAndDate(List.of(childId), date).stream()
                .findFirst()
                .orElse(null);
        AttendanceStatus beforeStatus = record == null ? null : record.getStatus();
        List<MealSession> beforeMeals = record == null ? null : List.copyOf(record.getMeals());
        DayPart beforeAbsentPart = record == null ? null : record.getAbsentPart();
        Long beforeRecordedBy = record == null ? null : record.getRecordedBy();
        Instant beforeRecordedAt = record == null ? null : record.getRecordedAt();
        boolean created = record == null;
        List<MealSession> otherMeals = eats ? off.other().mealsOf(cls.mealSessions()) : List.of();
        String action;
        if (off == DayPart.MORNING) {
            if (record == null) {
                record = recordRepository.save(AttendanceRecord.create(
                        childId,
                        cls.id(),
                        cls.campusId(),
                        date,
                        AttendanceStatus.EXCUSED,
                        otherMeals,
                        leave.getCreatedBy(),
                        at));
                action = AttendanceRecordHistory.RECORDED;
            } else {
                record.update(AttendanceStatus.EXCUSED, otherMeals, leave.getCreatedBy(), at);
                action = AttendanceRecordHistory.UPDATED;
            }
            record.markAbsentPart(DayPart.MORNING);
        } else {
            if (record == null) {
                return;
            }
            List<MealSession> kept = record.getStatus().isAbsent()
                    ? List.of()
                    : record.getMeals().stream().filter(otherMeals::contains).toList();
            record.update(record.getStatus(), kept, leave.getCreatedBy(), at);
            record.markAbsentPart(DayPart.AFTERNOON);
            action = AttendanceRecordHistory.UPDATED;
        }
        historyRepository.save(AttendanceRecordHistory.of(record, action, null));
        saveEffect(
                leave,
                record,
                created,
                beforeStatus,
                beforeMeals,
                beforeAbsentPart,
                beforeRecordedBy,
                beforeRecordedAt);
        eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(date, cls.campusId(), cls.id()));
    }

    private void saveEffect(
            LeaveRequest leave,
            AttendanceRecord record,
            boolean created,
            AttendanceStatus beforeStatus,
            List<MealSession> beforeMeals,
            DayPart beforeAbsentPart,
            Long beforeRecordedBy,
            Instant beforeRecordedAt) {
        LeaveAttendanceEffect effect = created
                ? LeaveAttendanceEffect.created(leave.getId(), record)
                : LeaveAttendanceEffect.changed(
                        leave.getId(),
                        record,
                        beforeStatus,
                        beforeMeals,
                        beforeAbsentPart,
                        beforeRecordedBy,
                        beforeRecordedAt);
        leaveAttendanceEffectRepository.save(effect);
    }

    private void revertEffect(LeaveAttendanceEffect effect, AttendanceRecord record, Long userId, Instant at) {
        // Nếu giáo viên/PHT đã cập nhật sau hiệu ứng của đơn, giữ trạng thái/bữa họ ghi, chỉ bỏ dấu nửa buổi của đơn
        if (!record.getRecordedAt().equals(effect.getAfterRecordedAt())) {
            if (record.getAbsentPart() != effect.getBeforeAbsentPart()) {
                record.markAbsentPart(effect.getBeforeAbsentPart());
                historyRepository.save(AttendanceRecordHistory.of(record, AttendanceRecordHistory.UPDATED, null));
                eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(
                        record.getDate(), record.getCampusId(), record.getClassroomId()));
            }
            leaveAttendanceEffectRepository.delete(effect);
            return;
        }
        if (effect.isRecordCreated()) {
            historyRepository.save(AttendanceRecordHistory.cancelled(record, userId, at));
            leaveAttendanceEffectRepository.delete(effect);
            recordRepository.delete(record);
        } else {
            record.update(
                    effect.getBeforeStatus(),
                    effect.getBeforeMeals(),
                    effect.getBeforeRecordedBy(),
                    effect.getBeforeRecordedAt());
            record.markAbsentPart(effect.getBeforeAbsentPart());
            historyRepository.save(AttendanceRecordHistory.cancelled(record, userId, at));
            leaveAttendanceEffectRepository.delete(effect);
        }
        eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(
                record.getDate(), record.getCampusId(), record.getClassroomId()));
    }

    /** Hoàn tác đơn cũ chưa có snapshot hiệu ứng (trước V196), giữ nguyên cách làm trước đó. */
    private void revertLegacy(LeaveRequest leave, LocalDate day, Long userId, Instant at) {
        // Nghỉ chiều: buổi sáng do GV ghi nên chỉ bỏ dấu nghỉ chiều (ATT-12)
        recordRepository.findByChildIdInAndDate(List.of(leave.getChildId()), day).stream()
                .filter(r -> r.getAbsentPart() == DayPart.AFTERNOON)
                .findFirst()
                .ifPresent(r -> {
                    r.markAbsentPart(null);
                    historyRepository.save(AttendanceRecordHistory.of(r, AttendanceRecordHistory.UPDATED, null));
                    eventPublisher.publishEvent(
                            new AttendanceService.AttendanceChangedEvent(day, r.getCampusId(), r.getClassroomId()));
                });
        recordRepository.findByChildIdInAndDate(List.of(leave.getChildId()), day).stream()
                .filter(r -> r.getStatus() == AttendanceStatus.EXCUSED
                        && r.getAbsentPart() != DayPart.AFTERNOON
                        // nghỉ sáng: GV có thể đã đổi công tắc ăn xế trước giờ chốt
                        && (r.getAbsentPart() == DayPart.MORNING
                                || r.getRecordedBy().equals(leave.getCreatedBy())))
                .findFirst()
                .ifPresent(r -> {
                    historyRepository.save(AttendanceRecordHistory.cancelled(r, userId, at));
                    historyRepository.flush();
                    recordRepository.delete(r);
                    eventPublisher.publishEvent(
                            new AttendanceService.AttendanceChangedEvent(day, r.getCampusId(), r.getClassroomId()));
                });
    }

    private void restoreTeacherRecordedAfternoon(AttendanceRecord record, Long childId, Long userId, Instant at) {
        ClassroomInfoResponse cls =
                classroomService.findClassroomInfo(record.getClassroomId()).orElse(null);
        if (cls == null || record.getStatus() != AttendanceStatus.PRESENT) {
            return;
        }
        boolean eats = !childService.findChildIdsWithoutMeals(List.of(childId)).contains(childId);
        record.update(AttendanceStatus.PRESENT, eats ? cls.mealSessions() : List.of(), userId, at);
        record.markAbsentPart(null);
        historyRepository.save(AttendanceRecordHistory.of(record, AttendanceRecordHistory.UPDATED, null));
        eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(
                record.getDate(), record.getCampusId(), record.getClassroomId()));
    }

    private LeaveRequestResponse toResponse(
            LeaveRequest r, ClassroomInfoResponse cls, List<LocalDate> applied, List<LocalDate> skipped) {
        return toResponse(r, cls, applied, skipped, childNames(r, cls), new LeaveViews(List.of(r)));
    }

    private Map<Long, String> childNames(LeaveRequest r, ClassroomInfoResponse cls) {
        Map<Long, String> names = new HashMap<>();
        if (cls != null) {
            LocalDate day = r.getFromDate().isBefore(rules.today()) ? rules.today() : r.getFromDate();
            enrollmentService.findChildrenInClassroom(cls.id(), day).forEach(ch -> names.put(ch.id(), ch.fullName()));
        }
        return names;
    }

    /** Dữ liệu dùng chung khi dựng nhiều đơn (tên người gửi, lớp, các đoạn học của trẻ): đọc theo lô, không theo đơn. */
    private final class LeaveViews {
        private final Map<Long, String> userNames;
        private final Map<Long, List<EnrollmentService.ChildClassStay>> stays = new HashMap<>();
        private final Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
        private final Collection<LeaveRequest> leaves;
        // Nhãn chuyển lớp / nghỉ học: mỗi lớp đọc một lần cho mọi trẻ của danh sách
        private final Map<Long, Map<Long, String>> historyByClass = new HashMap<>();

        LeaveViews(Collection<LeaveRequest> leaves) {
            this.leaves = leaves;
            userNames = accountService.findFullNames(leaves.stream()
                    .flatMap(r -> java.util.stream.Stream.of(r.getCreatedBy(), r.getTeacherSeenBy()))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet()));
            leaves.stream()
                    .collect(Collectors.groupingBy(LeaveRequest::getChildId))
                    .forEach((childId, own) -> stays.put(
                            childId,
                            enrollmentService.findChildStays(
                                    childId,
                                    own.stream()
                                            .map(LeaveRequest::getFromDate)
                                            .min(Comparator.naturalOrder())
                                            .orElseThrow(),
                                    own.stream()
                                            .map(LeaveRequest::getToDate)
                                            .max(Comparator.naturalOrder())
                                            .orElseThrow())));
        }

        String historyStatus(LeaveRequest r, ClassroomInfoResponse cls) {
            if (cls == null) {
                return null;
            }
            return historyByClass
                    .computeIfAbsent(
                            cls.id(),
                            classId -> childClassHistoryService.statuses(
                                    classId,
                                    leaves.stream()
                                            .map(LeaveRequest::getChildId)
                                            .distinct()
                                            .toList(),
                                    rules.today()))
                    .get(r.getChildId());
        }

        ClassroomInfoResponse classOn(Long childId, LocalDate date) {
            return stays.getOrDefault(childId, List.of()).stream()
                    .filter(st -> !date.isBefore(st.from()) && !date.isAfter(st.to()))
                    .findFirst()
                    .map(st -> classes.computeIfAbsent(
                            st.classroomId(),
                            id -> classroomService.findClassroomInfo(id).orElse(null)))
                    .orElse(null);
        }

        /** Ngày học của đơn gắn lớp trẻ học ngày đó (trẻ chuyển lớp sau khi gửi đơn vẫn gắn đúng lớp mới). */
        List<LeaveDay> days(LeaveRequest r) {
            List<LeaveDay> days = new ArrayList<>();
            for (LocalDate d = r.getFromDate(); !d.isAfter(r.getToDate()); d = d.plusDays(1)) {
                if (rules.isSchoolDay(d)) {
                    ClassroomInfoResponse cls = classOn(r.getChildId(), d);
                    days.add(new LeaveDay(d, cls == null ? null : cls.id(), cls == null ? null : cls.name()));
                }
            }
            return days;
        }
    }

    private LeaveRequestResponse toResponse(
            LeaveRequest r,
            ClassroomInfoResponse cls,
            List<LocalDate> applied,
            List<LocalDate> skipped,
            Map<Long, String> childNames,
            LeaveViews views) {
        return toResponse(
                r, cls, applied, skipped, childNames, views.days(r), views.userNames, views.historyStatus(r, cls));
    }

    private LeaveRequestResponse toResponse(
            LeaveRequest r,
            ClassroomInfoResponse cls,
            List<LocalDate> applied,
            List<LocalDate> skipped,
            Map<Long, String> childNames,
            List<LeaveDay> days,
            Map<Long, String> userNames,
            String historyStatus) {
        String creator = userNames.get(r.getCreatedBy());
        boolean cancelledAll =
                r.getCancelledFrom() != null && !r.getCancelledFrom().isAfter(r.getFromDate());
        return new LeaveRequestResponse(
                r.getId(),
                r.getChildId(),
                childNames.get(r.getChildId()),
                historyStatus,
                cls == null ? null : cls.id(),
                cls == null ? null : cls.name(),
                cls == null ? null : cls.campusId(),
                r.getFromDate(),
                r.getToDate(),
                cancelledAll ? null : r.effectiveTo(),
                r.getReason(),
                cancelledAll ? "CANCELLED" : "ACTIVE",
                r.getCreatedBy(),
                creator,
                r.getCreatedAt(),
                r.getCancelledAt(),
                applied == null ? List.of() : applied,
                skipped == null ? List.of() : skipped,
                r.getPart(),
                r.getEats(),
                cls == null ? null : cls.ageGroupId(),
                eatsMeals(r.getPart(), cls),
                days,
                offMeals(r.getPart(), cls),
                r.getTeacherSeenAt(),
                r.getTeacherSeenBy() == null ? null : userNames.get(r.getTeacherSeenBy()),
                r.isNoticeOnly());
    }

    /** Bữa của buổi nghỉ theo nhóm tuổi của lớp: cả ngày = mọi bữa (ATT-12). */
    static List<MealSession> offMeals(LeavePart part, ClassroomInfoResponse cls) {
        if (cls == null) {
            return List.of();
        }
        return part == null || part == LeavePart.FULL_DAY
                ? cls.mealSessions().stream().sorted().toList()
                : part.dayPart().mealsOf(cls.mealSessions());
    }

    /** Các bữa của buổi còn học theo nhóm tuổi của lớp (ghi rõ tên bữa ở ô "Bé tham gia ăn"). */
    static List<MealSession> eatsMeals(LeavePart part, ClassroomInfoResponse cls) {
        return part == null || part == LeavePart.FULL_DAY || cls == null
                ? List.of()
                : part.dayPart().other().mealsOf(cls.mealSessions());
    }
}
