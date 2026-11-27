package com.carenest.service.attendance.impl;

import com.carenest.dto.attendance.CorrectHandoverTimeRequest;
import com.carenest.dto.attendance.PickupBoardResponse;
import com.carenest.dto.attendance.PickupChildResponse;
import com.carenest.dto.attendance.PickupClassResponse;
import com.carenest.dto.attendance.PickupRecordResponse;
import com.carenest.dto.attendance.PickupRequest;
import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildGuardianContact;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.AttendanceRecordHistory;
import com.carenest.entity.attendance.PickupRecord;
import com.carenest.entity.attendance.PickupTimeChange;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.LeaveRequestRepository;
import com.carenest.repository.attendance.PickupRecordRepository;
import com.carenest.repository.attendance.PickupTimeChangeRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountPhotoService;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.PickupService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.LeavePart;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PickupOutcome;
import com.carenest.utils.enums.PickupStatus;
import com.carenest.utils.enums.PickupVerification;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PickupServiceImpl implements PickupService {
    private static final String LOG_PREFIX = "[PickupServiceImpl]";
    private static final int NOTE_MAX = 500;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AttendanceDayRules rules;
    private final PickupRecordRepository pickupRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceRecordHistoryRepository attendanceHistoryRepository;
    private final EarlyPickupRule earlyPickupRule;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final StaffAssignmentService staffAssignmentService;
    private final ChildService childService;
    private final ChildIntakeService childIntakeService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final AccountPhotoService accountPhotoService;
    private final NotificationService notificationService;
    private final AccountService accountService;
    private final Clock clock;
    private final PickupTimeChangeRepository timeChangeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PickupClassResponse> listClasses(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listClasses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            // PCK-01: cùng điều kiện với bảng đón (quyền PICKUP_RECORD, GV đang phân công, năm học đang hoạt động)
            List<PickupClassResponse> classes = currentUser.hasPermission(Permission.PICKUP_RECORD)
                    ? teacherActiveClasses(currentUser, rules.today()).stream()
                            .sorted(Comparator.comparing(ClassroomInfoResponse::campusId)
                                    .thenComparing(ClassroomInfoResponse::name))
                            .map(c -> new PickupClassResponse(c.id(), c.name(), c.campusId(), c.ageGroupId()))
                            .toList()
                    : List.of();
            log.info("{}|END", logPrefix);
            return classes;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PickupBoardResponse getBoard(CurrentUser currentUser, Long classId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = classroomService
                    .findClassroomInfo(classId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
            if (!currentUser.hasPermission(Permission.PICKUP_RECORD) || !rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.PICKUP_ACCESS_DENIED);
            }
            checkActiveYear(cls);
            LocalDate day = date == null ? rules.today() : date;
            checkDay(cls, day);
            List<Long> childIds = enrollmentService.findChildrenInClassroom(cls.id(), day).stream()
                    .map(ClassChildResponse::id)
                    .toList();
            List<PickupBoardResponse.PickupRow> rows = rows(childIds, day, Map.of(cls.id(), cls.name()));
            log.info("{}|END", logPrefix);
            return new PickupBoardResponse(cls.id(), cls.name(), day, rules.today(), rows);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PickupChildResponse getChild(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|getChild|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = classOfChild(currentUser, childId);
            LocalDate today = rules.today();
            PickupBoardResponse.PickupRow row =
                    rows(List.of(childId), today, Map.of(cls.id(), cls.name())).getFirst();
            List<ChildGuardianContact> contacts = childIntakeService.findGuardianContacts(childId);
            Set<Long> withPhoto = accountPhotoService.findUserIdsWithPhoto(contacts.stream()
                    .map(ChildGuardianContact::userId)
                    .filter(Objects::nonNull)
                    .toList());
            List<PickupChildResponse.PickupGuardian> guardians = contacts.stream()
                    .map(g -> new PickupChildResponse.PickupGuardian(
                            g.position(),
                            g.fullName(),
                            g.relationship(),
                            g.phone(),
                            g.photoUrl() != null
                                    ? g.photoUrl()
                                    : g.userId() != null && withPhoto.contains(g.userId())
                                            ? "/children/" + childId + "/guardians/" + g.userId() + "/photo"
                                            : null))
                    .toList();
            log.info("{}|END", logPrefix);
            return new PickupChildResponse(row, today, cls.id(), cls.name(), guardians);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public PickupRecordResponse recordResult(CurrentUser currentUser, PickupRequest request) {
        String logPrefix = LOG_PREFIX + "|recordResult|userId=" + currentUser.userId() + "|childId=" + request.childId()
                + "|verification=" + request.verification();
        log.info("{}|START", logPrefix);
        try {
            if (request.childId() == null) {
                throw GlobalException.of(ApiCode.PICKUP_CHILD_NOT_FOUND);
            }
            ClassroomInfoResponse cls = classOfChild(currentUser, request.childId());
            // Khóa lớp như điểm danh: trả trẻ cùng lúc không ghi hai lần, không đè điểm danh đang lưu
            classroomService.lockForUpdate(cls.id());
            PickupVerification verification = request.verification();
            String note = request.note() == null ? "" : request.note().trim();
            List<ChildGuardianContact> contacts = childIntakeService.findGuardianContacts(request.childId());
            ChildGuardianContact guardian = validate(request, verification, note, contacts);

            LocalDate today = rules.today();
            // Chỉ trẻ điểm danh Có mặt hôm nay mới trả được (user chốt 2026-10-27)
            AttendanceRecord attendance =
                    attendanceRepository.findByChildIdInAndDate(List.of(request.childId()), today).stream()
                            .findFirst()
                            .orElseThrow(() -> GlobalException.of(ApiCode.PICKUP_CHILD_NOT_PRESENT));
            if (attendance.getStatus() != AttendanceStatus.PRESENT) {
                throw GlobalException.of(ApiCode.PICKUP_CHILD_ABSENT);
            }
            if (pickupRepository.existsByChildIdAndDateAndOutcome(
                    request.childId(), today, PickupOutcome.HANDED_OVER)) {
                throw GlobalException.of(ApiCode.PICKUP_ALREADY_HANDED_OVER);
            }
            Instant now = clock.instant();
            // PCK-04: giờ trả là giờ bấm ghi nhận, không cho nhập tay
            LocalTime handoverTime = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
            boolean handedOver = verification.outcome() == PickupOutcome.HANDED_OVER;
            PickupRecord record = pickupRepository.save(PickupRecord.builder()
                    .childId(request.childId())
                    .classroomId(cls.id())
                    .campusId(cls.campusId())
                    .date(today)
                    .verification(verification)
                    .guardianPosition(guardian == null ? null : guardian.position())
                    .pickupPersonName(guardian == null ? "" : guardian.fullName())
                    .pickupPersonRelationship(guardian == null ? null : guardian.relationship())
                    .pickupPersonPhone(guardian == null ? "" : guardian.phone())
                    .handedOverAt(
                            handedOver
                                    ? today.atTime(handoverTime)
                                            .atZone(clock.getZone())
                                            .toInstant()
                                    : null)
                    .note(note)
                    .recordedBy(currentUser.userId())
                    .recordedAt(now)
                    .build());
            if (handedOver) {
                markHalfDayWhenPickedUpBeforeFullDayThreshold(attendance, cls, handoverTime, currentUser, now);
                record.markNotified(notifyParents(record));
            }
            PickupRecordResponse view =
                    views(List.of(record), Map.of(cls.id(), cls.name())).getFirst();
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public PickupRecordResponse correctHandoverTime(
            CurrentUser currentUser, Long pickupId, CorrectHandoverTimeRequest request) {
        String logPrefix = LOG_PREFIX + "|correctHandoverTime|userId=" + currentUser.userId() + "|pickupId=" + pickupId;
        log.info("{}|START", logPrefix);
        try {
            PickupRecord found = pickupRepository
                    .findById(pickupId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.PICKUP_CHILD_NOT_FOUND));
            ClassroomInfoResponse cls = classroomService
                    .findClassroomInfo(found.getClassroomId())
                    .filter(c -> mayCorrect(currentUser, c))
                    .orElseThrow(() -> GlobalException.of(ApiCode.PICKUP_CHILD_NOT_FOUND));
            checkActiveYear(cls);
            classroomService.lockForUpdate(cls.id());
            LocalDate today = rules.today();
            if (!found.getDate().equals(today) || !found.isHandedOver()) {
                throw GlobalException.of(ApiCode.PICKUP_CORRECTION_TODAY_ONLY);
            }
            LocalTime time = request.handoverTime() == null
                    ? null
                    : request.handoverTime().truncatedTo(ChronoUnit.MINUTES);
            String reason = request.reason() == null ? "" : request.reason().trim();
            List<FieldErrorResponse> errors = new ArrayList<>();
            if (time == null) {
                errors.add(error("handoverTime", "Nhập giờ trả trẻ (HH:mm)."));
            } else if (time.isAfter(LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES))) {
                errors.add(error("handoverTime", "Giờ trả trẻ không được sau giờ hiện tại."));
            }
            if (reason.isEmpty()) {
                errors.add(error("reason", "Nhập lý do sửa giờ trả."));
            } else if (reason.length() > NOTE_MAX) {
                errors.add(error("reason", "Lý do tối đa 500 ký tự."));
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
            }
            Instant now = clock.instant();
            Instant oldTime = found.getHandedOverAt();
            Instant newTime = today.atTime(time).atZone(clock.getZone()).toInstant();
            found.correctHandedOverAt(newTime);
            timeChangeRepository.save(
                    PickupTimeChange.of(found.getId(), oldTime, newTime, reason, currentUser.userId(), now));
            recalculateHalfDay(found.getChildId(), cls, oldTime, time, currentUser, now);
            found.markNotified(notifyParents(found));
            PickupRecordResponse view =
                    views(List.of(found), Map.of(cls.id(), cls.name())).getFirst();
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Trả trẻ là việc của GV: chỉ GV đang phụ trách lớp được sửa giờ trả
    private boolean mayCorrect(CurrentUser user, ClassroomInfoResponse cls) {
        return user.hasRole(CareNestRole.TEACHER)
                && user.hasPermission(Permission.PICKUP_RECORD)
                && rules.canRecord(user, cls);
    }

    // Giờ đúng quyết định lại nửa ngày: bỏ dấu nghỉ chiều do giờ cũ gây ra (không đụng đơn nghỉ chiều), suất giữ nguyên
    private void recalculateHalfDay(
            Long childId,
            ClassroomInfoResponse cls,
            Instant oldTime,
            LocalTime newTime,
            CurrentUser currentUser,
            Instant now) {
        LocalDate today = rules.today();
        AttendanceRecord attendance = attendanceRepository.findByChildIdInAndDate(List.of(childId), today).stream()
                .findFirst()
                .orElse(null);
        if (attendance == null || attendance.getStatus() != AttendanceStatus.PRESENT) {
            return;
        }
        boolean wasEarly =
                earlyPickupRule.beforeFullDay(cls.schoolYearId(), today, LocalTime.ofInstant(oldTime, clock.getZone()));
        boolean isEarly = earlyPickupRule.beforeFullDay(cls.schoolYearId(), today, newTime);
        if (isEarly) {
            markHalfDayWhenPickedUpBeforeFullDayThreshold(attendance, cls, newTime, currentUser, now);
        } else if (wasEarly && attendance.getAbsentPart() == DayPart.AFTERNOON && !hasAfternoonLeave(childId, today)) {
            attendance.update(attendance.getStatus(), attendance.getMeals(), currentUser.userId(), now);
            attendance.markAbsentPart(null);
            attendanceRepository.save(attendance);
            attendanceHistoryRepository.save(
                    AttendanceRecordHistory.of(attendance, AttendanceRecordHistory.UPDATED, null));
        }
    }

    private boolean hasAfternoonLeave(Long childId, LocalDate date) {
        return leaveRequestRepository.findOverlapping(List.of(childId), date, date).stream()
                .anyMatch(r -> r.getPart() == LeavePart.AFTERNOON && !r.isNoticeOnly() && r.coversDate(date));
    }

    /** PCK-04: đã có mặt buổi sáng nhưng trả trước mốc giữa chiều chỉ tính 0,5 ngày; không đổi suất ăn đã ghi. */
    private void markHalfDayWhenPickedUpBeforeFullDayThreshold(
            AttendanceRecord attendance,
            ClassroomInfoResponse cls,
            LocalTime handoverTime,
            CurrentUser currentUser,
            Instant now) {
        if (attendance.getAbsentPart() == null
                && earlyPickupRule.beforeFullDay(cls.schoolYearId(), rules.today(), handoverTime)) {
            attendance.update(attendance.getStatus(), attendance.getMeals(), currentUser.userId(), now);
            attendance.markAbsentPart(DayPart.AFTERNOON);
            attendanceRepository.save(attendance);
            attendanceHistoryRepository.save(
                    AttendanceRecordHistory.of(attendance, AttendanceRecordHistory.UPDATED, null));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PickupRecordResponse> listResults(CurrentUser currentUser, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|listResults|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            if (!currentUser.hasRole(CareNestRole.TEACHER) || !currentUser.hasPermission(Permission.PICKUP_RECORD)) {
                throw GlobalException.of(ApiCode.PICKUP_ACCESS_DENIED);
            }
            LocalDate today = rules.today();
            LocalDate day = date == null ? today : date;
            List<ClassroomInfoResponse> classes = teacherActiveClasses(currentUser, today);
            classes.forEach(c -> checkDay(c, day));
            Map<Long, String> classNames =
                    classes.stream().collect(Collectors.toMap(ClassroomInfoResponse::id, ClassroomInfoResponse::name));
            List<PickupRecordResponse> results = classes.isEmpty()
                    ? List.of()
                    : views(
                            pickupRepository.findByClassroomIdInAndDateOrderByRecordedAtDescIdDesc(
                                    classNames.keySet(), day),
                            classNames);
            log.info("{}|END", logPrefix);
            return results;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Lớp năm học đang hoạt động mà GV đang được phân công hôm nay (kể cả dạy thay). */
    private List<ClassroomInfoResponse> teacherActiveClasses(CurrentUser user, LocalDate today) {
        if (!user.hasRole(CareNestRole.TEACHER)) {
            return List.of();
        }
        return staffAssignmentService.findCurrentTeacherClassroomIds(user.userId(), today).stream()
                .map(classroomService::findClassroomInfo)
                .flatMap(Optional::stream)
                .filter(c -> c.yearStatus() == SchoolYearStatus.ACTIVE)
                .toList();
    }

    /** Lớp trẻ đang học hôm nay; chỉ GV đang phụ trách lớp, năm học đang hoạt động. Ngoài phạm vi trả 404. */
    private ClassroomInfoResponse classOfChild(CurrentUser user, Long childId) {
        ClassroomInfoResponse cls = enrollmentService.findActiveClassroomIds(childId, rules.today()).stream()
                .findFirst()
                .flatMap(classroomService::findClassroomInfo)
                .filter(c -> user.hasPermission(Permission.PICKUP_RECORD) && rules.canRecord(user, c))
                .orElseThrow(() -> GlobalException.of(ApiCode.PICKUP_CHILD_NOT_FOUND));
        checkActiveYear(cls);
        return cls;
    }

    private static void checkActiveYear(ClassroomInfoResponse cls) {
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.PICKUP_YEAR_NOT_ACTIVE);
        }
    }

    /** Xem từ đầu năm học đang hoạt động (khi năm đã bắt đầu) tới hôm nay. */
    private void checkDay(ClassroomInfoResponse cls, LocalDate day) {
        LocalDate today = rules.today();
        boolean beforeYear =
                cls.yearStart() != null && !cls.yearStart().isAfter(today) && day.isBefore(cls.yearStart());
        if (day.isAfter(today) || beforeYear) {
            throw GlobalException.of(ApiCode.PICKUP_DATE_INVALID);
        }
    }

    /** Kiểm form theo cách xác minh; trả phụ huynh khớp ảnh (null khi gọi phụ huynh). */
    private ChildGuardianContact validate(
            PickupRequest request, PickupVerification verification, String note, List<ChildGuardianContact> contacts) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (verification == null) {
            errors.add(error("verification", "Chọn kết quả xác minh người đón."));
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
        ChildGuardianContact guardian = null;
        if (verification == PickupVerification.PHOTO_MATCH) {
            guardian = contacts.stream()
                    .filter(g -> request.guardianPosition() != null && g.position() == request.guardianPosition())
                    .findFirst()
                    .orElse(null);
            if (guardian == null) {
                errors.add(
                        error(
                                "guardianPosition",
                                "Người đón không có trong danh sách phụ huynh của hồ sơ trẻ. Hãy gọi điện xác nhận với phụ huynh."));
            }
        }
        boolean byPhone = verification != PickupVerification.PHOTO_MATCH;
        // Gọi phụ huynh (trả hoặc không trả): người đón, lý do ghi trong nội dung cuộc gọi (user chốt 2026-10-23)
        if (byPhone && note.isEmpty()) {
            errors.add(error("note", "Ghi nội dung cuộc gọi với phụ huynh."));
        } else if (note.length() > NOTE_MAX) {
            errors.add(error("note", byPhone ? "Nội dung tối đa 500 ký tự." : "Ghi chú tối đa 500 ký tự."));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
        return guardian;
    }

    /** Báo các tài khoản phụ huynh của trẻ: trẻ đã được đón (PCK-04). Trả số tài khoản được báo. */
    private int notifyParents(PickupRecord record) {
        List<Long> parentIds = guardianChildLinkService.findLinksOfChild(record.getChildId()).stream()
                .map(GuardianLinkResponse::userId)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return 0;
        }
        ChildBriefResponse child =
                childService.findChildBriefs(List.of(record.getChildId())).get(record.getChildId());
        String name = child == null ? "Bé" : "Bé " + child.fullName();
        String time = HH_MM.format(record.getHandedOverAt().atZone(clock.getZone()));
        String by = record.getVerification() == PickupVerification.PHOTO_MATCH
                ? " Người đón: " + record.getPickupPersonName() + " ("
                        + relationLabel(record.getPickupPersonRelationship()) + ")."
                : " Người đón đã được phụ huynh xác nhận qua điện thoại.";
        // Câu chữ theo quy tắc PCK-04 HT đã sửa hoặc mặc định (PAR-09)
        notificationService.notifyEvent(
                parentIds,
                NotificationType.CHILD_PICKED_UP,
                Map.of("tenTre", name, "gio", time, "ngay", DMY.format(record.getDate()), "nguoiDon", by.trim()),
                record.getChildId());
        return parentIds.size();
    }

    private List<PickupBoardResponse.PickupRow> rows(List<Long> childIds, LocalDate day, Map<Long, String> classNames) {
        if (childIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ChildBriefResponse> briefs = childService.findChildBriefs(childIds);
        Map<Long, AttendanceRecord> attendance = attendanceRepository.findByChildIdInAndDate(childIds, day).stream()
                .collect(Collectors.toMap(AttendanceRecord::getChildId, Function.identity()));
        Map<Long, List<PickupRecordResponse>> attempts =
                views(pickupRepository.findByChildIdInAndDateOrderByRecordedAtAscIdAsc(childIds, day), classNames)
                        .stream()
                        .collect(Collectors.groupingBy(PickupRecordResponse::childId));
        return childIds.stream()
                .map(id -> {
                    ChildBriefResponse brief = briefs.get(id);
                    List<PickupRecordResponse> tries = attempts.getOrDefault(id, List.of());
                    PickupRecordResponse done = tries.stream()
                            .filter(p -> p.outcome() == PickupOutcome.HANDED_OVER)
                            .findFirst()
                            .orElse(null);
                    AttendanceRecord att = attendance.get(id);
                    return new PickupBoardResponse.PickupRow(
                            new PickupBoardResponse.PickupChildRef(
                                    id,
                                    brief == null ? null : brief.code(),
                                    brief == null ? "" : brief.fullName(),
                                    brief == null ? null : brief.gender()),
                            att == null ? null : att.getStatus(),
                            statusOf(att, done != null),
                            done,
                            tries);
                })
                .toList();
    }

    // Chờ đón chỉ khi trẻ điểm danh Có mặt; chưa điểm danh không coi là có mặt (user chốt 2026-10-27)
    private static PickupStatus statusOf(AttendanceRecord att, boolean done) {
        if (done) {
            return PickupStatus.PICKED_UP;
        }
        if (att == null) {
            return PickupStatus.UNMARKED;
        }
        return att.getStatus() == AttendanceStatus.PRESENT ? PickupStatus.WAITING : PickupStatus.ABSENT;
    }

    private List<PickupRecordResponse> views(Collection<PickupRecord> records, Map<Long, String> classNames) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, ChildBriefResponse> briefs = childService.findChildBriefs(
                records.stream().map(PickupRecord::getChildId).collect(Collectors.toSet()));
        Map<Long, String> names = accountService.findFullNames(
                records.stream().map(PickupRecord::getRecordedBy).collect(Collectors.toSet()));
        return records.stream()
                .map(r -> new PickupRecordResponse(
                        r.getId(),
                        r.getDate(),
                        r.getChildId(),
                        briefs.get(r.getChildId()) == null
                                ? ""
                                : briefs.get(r.getChildId()).fullName(),
                        r.getClassroomId(),
                        classNames.get(r.getClassroomId()),
                        r.getOutcome(),
                        r.getVerification(),
                        r.getGuardianPosition() == null
                                ? null
                                : r.getGuardianPosition().intValue(),
                        r.getPickupPersonName(),
                        r.getPickupPersonRelationship(),
                        r.getPickupPersonPhone(),
                        r.getHandedOverAt(),
                        r.getNote(),
                        r.getNotifiedParents(),
                        r.getRecordedBy(),
                        names.get(r.getRecordedBy()),
                        r.getRecordedAt()))
                .toList();
    }

    /** Nhãn quan hệ trong thông báo cho phụ huynh. */
    private static String relationLabel(GuardianRelationship relationship) {
        if (relationship == null) {
            return "phụ huynh";
        }
        return switch (relationship) {
            case FATHER -> "Bố";
            case MOTHER -> "Mẹ";
            case GRANDFATHER -> "Ông";
            case GRANDMOTHER -> "Bà";
            case SIBLING -> "Anh/chị";
            case GUARDIAN -> "Người giám hộ";
            default -> "Khác";
        };
    }

    private static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static FieldErrorResponse error(String field, String message) {
        return new FieldErrorResponse(field, message);
    }
}
