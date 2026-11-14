package com.carenest.service.observation.impl;

import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationPeriod;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockRequest;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockResponse;
import com.carenest.dto.observation.EvaluationDeadlineDtos.OverdueClassItem;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentClassSummary;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.entity.observation.EvaluationUnlock;
import com.carenest.entity.observation.ThemeEvaluation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.EvaluationUnlockRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.eduplan.EduPlanCalendarQueryService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.observation.EvaluationDeadlineService;
import com.carenest.service.observation.WeeklyCommentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.EvaluationLockKind;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EvaluationDeadlineServiceImpl implements EvaluationDeadlineService {
    private static final String LOG_PREFIX = "[EvaluationDeadlineServiceImpl]";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int NEXT_SCHOOL_DAY_SCAN = 60;
    static final String SEMESTER_1 = "1";
    static final String SEMESTER_2 = "2";
    static final String YEAR_KEY = "Y";

    private final ObservationDayRules rules;
    private final EvaluationUnlockRepository unlockRepository;
    private final ChildEvaluationRepository childEvaluationRepository;
    private final ThemeEvaluationRepository themeEvaluationRepository;
    private final EduPlanCalendarQueryService calendarQueryService;
    private final SchoolYearService schoolYearService;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final StaffAssignmentService staffAssignmentService;
    private final AccountService accountService;
    private final NotificationService notificationService;
    private final WeeklyCommentService weeklyCommentService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationPeriod> getPeriods(CurrentUser currentUser, Long classId) {
        String logPrefix = LOG_PREFIX + "|getPeriods|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canView(currentUser, cls)) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            List<EvaluationPeriod> periods = periodsOf(cls);
            log.info("{}|count={}|END", logPrefix, periods.size());
            return periods;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationPeriod> periodsOf(ClassroomInfoResponse cls) {
        SchoolYearDetailResponse year =
                schoolYearService.findSchoolYearDetail(cls.schoolYearId()).orElse(null);
        if (year == null) {
            return List.of();
        }
        Map<String, Instant> unlocked = new HashMap<>();
        Instant now = rules.now();
        unlockRepository.findAllByOrderByUnlockedAtDescIdDesc().stream()
                .filter(u -> cls.id().equals(u.getClassroomId()) && u.isActiveAt(now))
                .forEach(u -> unlocked.merge(
                        u.getKind() + ":" + u.getPeriodKey(), u.getValidUntil(), (a, b) -> a.isAfter(b) ? a : b));
        List<EvaluationPeriod> out = new ArrayList<>();
        calendarQueryService.findApprovedThemes(cls.id()).stream()
                .filter(t -> t.lineageId() != null && t.endDate() != null)
                .forEach(t -> out.add(period(
                        EvaluationLockKind.THEME,
                        String.valueOf(t.lineageId()),
                        "Đánh giá cuối chủ đề " + t.name(),
                        t.endDate(),
                        AssessmentDeadlines.THEME,
                        null,
                        unlocked)));
        if (year.semester1End() != null) {
            out.add(period(
                    EvaluationLockKind.SEMESTER,
                    SEMESTER_1,
                    "Đánh giá học kỳ I",
                    year.semester1End(),
                    AssessmentDeadlines.SEMESTER,
                    null,
                    unlocked));
        }
        if (year.semester2End() != null) {
            out.add(period(
                    EvaluationLockKind.SEMESTER,
                    SEMESTER_2,
                    "Đánh giá học kỳ II",
                    year.semester2End(),
                    AssessmentDeadlines.SEMESTER,
                    null,
                    unlocked));
        }
        // OBS-28: cuối năm mở từ ngày kết thúc HK II (chưa đặt thì ngày kết thúc năm); hạn có thể sau ngày kết thúc
        // năm,
        // HT đóng năm thì khóa luôn
        LocalDate yearOpen = year.semester2End() == null || year.semester2End().isAfter(year.endDate())
                ? year.endDate()
                : year.semester2End();
        out.add(period(
                EvaluationLockKind.YEAR,
                YEAR_KEY,
                "Đánh giá cuối năm",
                yearOpen,
                AssessmentDeadlines.YEAR_END,
                null,
                unlocked));
        return out;
    }

    // Mở từ ngày kết thúc kỳ; khóa sau 23:59:59 ngày học thứ N sau ngày đó (N = 0 ⇒ chính ngày đó)
    private EvaluationPeriod period(
            EvaluationLockKind kind,
            String key,
            String label,
            LocalDate openFrom,
            int deadlineDays,
            LocalDate cap,
            Map<String, Instant> unlocked) {
        LocalDate deadline = nthSchoolDayAfter(openFrom, deadlineDays);
        if (cap != null && deadline.isAfter(cap)) {
            deadline = cap.isBefore(openFrom) ? openFrom : cap;
        }
        LocalDate today = rules.today();
        return new EvaluationPeriod(
                kind,
                key,
                label,
                openFrom,
                deadline,
                !today.isBefore(openFrom),
                today.isAfter(deadline),
                unlocked.get(kind + ":" + key));
    }

    private LocalDate nthSchoolDayAfter(LocalDate day, int n) {
        LocalDate d = day;
        int found = 0;
        for (int i = 0; found < n && i < NEXT_SCHOOL_DAY_SCAN; i++) {
            d = d.plusDays(1);
            if (rules.isSchoolDay(d)) {
                found++;
            }
        }
        return d;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkWritable(ClassroomInfoResponse cls, EvaluationLockKind kind, String periodKey) {
        if (periodKey == null) {
            return;
        }
        periodsOf(cls).stream()
                .filter(p -> p.kind() == kind && periodKey.equals(p.periodKey()))
                .findFirst()
                .filter(p -> !p.writable())
                .ifPresent(p -> {
                    throw GlobalException.of(
                            ApiCode.EVALUATION_LOCKED,
                            p.label(),
                            DMY.format(p.deadline().plusDays(1)));
                });
    }

    @Override
    @Transactional(readOnly = true)
    public String semesterKey(ClassroomInfoResponse cls, LocalDate periodEnd) {
        LocalDate s1End = schoolYearService
                .findSchoolYearDetail(cls.schoolYearId())
                .map(SchoolYearDetailResponse::semester1End)
                .orElse(null);
        return s1End != null && periodEnd != null && !periodEnd.isAfter(s1End) ? SEMESTER_1 : SEMESTER_2;
    }

    @Override
    public EvaluationUnlockResponse unlock(CurrentUser currentUser, EvaluationUnlockRequest request) {
        String logPrefix = LOG_PREFIX + "|unlock|userId=" + currentUser.userId() + "|classId=" + request.classId()
                + "|kind=" + request.kind();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(request.classId());
            // Như mở lại ngày đánh giá (OBS-19): HT hoặc PHT có quyền mở lại ở điểm trường của lớp, năm đang hoạt động
            if (!currentUser.hasPermission(Permission.ASSESSMENT_UNLOCK)
                    || !rules.leadsCampus(currentUser, Permission.ASSESSMENT_UNLOCK, cls.campusId())) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_YEAR_NOT_ACTIVE);
            }
            String key = request.periodKey().trim();
            EvaluationPeriod period = periodsOf(cls).stream()
                    .filter(p -> p.kind() == request.kind() && key.equals(p.periodKey()))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.EVALUATION_PERIOD_NOT_FOUND));
            if (!period.locked()) {
                throw GlobalException.of(ApiCode.EVALUATION_NOT_LOCKED_YET, period.label());
            }
            Instant now = rules.now();
            // Sau ngày kết thúc năm (HT chưa đóng năm) "Mở đến" không giới hạn theo ngày kết thúc năm
            Instant yearEndAt = cls.yearEnd() == null || rules.today().isAfter(cls.yearEnd())
                    ? null
                    : cls.yearEnd()
                            .atTime(LocalTime.MAX)
                            .atZone(clock.getZone())
                            .toInstant();
            if (!request.validUntil().isAfter(now)
                    || (yearEndAt != null && request.validUntil().isAfter(yearEndAt))) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_DEADLINE_INVALID);
            }
            // Mỗi lớp – kỳ tối đa một lần mở còn hiệu lực; khóa lớp để HT và PHT mở cùng lúc không tạo 2 bản
            rules.lockClassForUpdate(cls.id());
            if (unlockRepository
                    .findFirstByClassroomIdAndKindAndPeriodKeyAndValidUntilAfterOrderByIdDesc(
                            cls.id(), request.kind(), key, now)
                    .isPresent()) {
                throw GlobalException.of(ApiCode.EVALUATION_UNLOCK_ALREADY_ACTIVE, period.label());
            }
            EvaluationUnlock unlock = unlockRepository.save(EvaluationUnlock.create(
                    request.kind(),
                    cls.id(),
                    cls.campusId(),
                    key,
                    request.validUntil(),
                    request.reason().trim(),
                    currentUser.userId(),
                    now));
            notifyTeachers(cls, period, unlock.getValidUntil());
            EvaluationUnlockResponse response = views(List.of(unlock), now).getFirst();
            log.info("{}|unlockId={}|END", logPrefix, unlock.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // OBS-30: GV lớp biết lớp được mở lại tới khi nào
    private void notifyTeachers(ClassroomInfoResponse cls, EvaluationPeriod period, Instant until) {
        List<Long> teachers = staffAssignmentService
                .findCurrentTeacherIds(List.of(cls.id()), rules.teachingDay(cls))
                .getOrDefault(cls.id(), List.of());
        if (teachers.isEmpty()) {
            return;
        }
        String untilText = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
                .withZone(clock.getZone())
                .format(until);
        notificationService.notifyUsers(
                teachers,
                NotificationType.ASSESSMENT_REOPENED,
                period.label() + " được mở lại",
                period.label() + " lớp " + cls.name() + " được mở lại đến " + untilText + ".",
                AssessmentReminderLinks.evaluation(period.kind(), cls.id(), period.periodKey()),
                false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationUnlockResponse> getUnlocks(
            CurrentUser currentUser, Long campusId, Long classId, EvaluationLockKind kind, Boolean active) {
        String logPrefix = LOG_PREFIX + "|getUnlocks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (!currentUser.hasPermission(Permission.ASSESSMENT_UNLOCK) || !rules.isLeader(currentUser)) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
            }
            Instant now = rules.now();
            Map<Long, Boolean> campusAllowed = new HashMap<>();
            List<EvaluationUnlock> unlocks = unlockRepository.findAllByOrderByUnlockedAtDescIdDesc().stream()
                    .filter(u -> campusAllowed.computeIfAbsent(
                            u.getCampusId(),
                            id -> rules.leadsCampus(currentUser, Permission.ASSESSMENT_UNLOCK, id)
                                    || rules.readsCampus(currentUser, id)))
                    .filter(u -> campusId == null || campusId.equals(u.getCampusId()))
                    .filter(u -> classId == null || classId.equals(u.getClassroomId()))
                    .filter(u -> kind == null || kind == u.getKind())
                    .filter(u -> active == null || active == u.isActiveAt(now))
                    .toList();
            List<EvaluationUnlockResponse> result = views(unlocks, now);
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EvaluationUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId) {
        String logPrefix = LOG_PREFIX + "|revokeUnlock|userId=" + currentUser.userId() + "|unlockId=" + unlockId;
        log.info("{}|START", logPrefix);
        try {
            EvaluationUnlock unlock = unlockRepository
                    .findById(unlockId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.EVALUATION_UNLOCK_NOT_FOUND));
            if (!currentUser.hasPermission(Permission.ASSESSMENT_UNLOCK)
                    || !rules.leadsCampus(currentUser, Permission.ASSESSMENT_UNLOCK, unlock.getCampusId())) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
            }
            Instant now = rules.now();
            if (!unlock.isActiveAt(now)) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_ALREADY_ENDED);
            }
            unlock.revoke(currentUser.userId(), now);
            unlockRepository.flush();
            EvaluationUnlockResponse response = views(List.of(unlock), now).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<EvaluationUnlockResponse> views(List<EvaluationUnlock> unlocks, Instant now) {
        if (unlocks.isEmpty()) {
            return List.of();
        }
        Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
        Map<Long, List<EvaluationPeriod>> periods = new HashMap<>();
        Map<Long, String> names = accountService.findFullNames(unlocks.stream()
                .flatMap(u -> Stream.of(u.getUnlockedBy(), u.getRevokedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return unlocks.stream()
                .map(u -> {
                    ClassroomInfoResponse cls = classes.computeIfAbsent(
                            u.getClassroomId(),
                            id -> classroomService.findClassroomInfo(id).orElse(null));
                    String label = cls == null
                            ? null
                            : periods.computeIfAbsent(cls.id(), id -> periodsOf(cls)).stream()
                                    .filter(p -> p.kind() == u.getKind()
                                            && p.periodKey().equals(u.getPeriodKey()))
                                    .map(EvaluationPeriod::label)
                                    .findFirst()
                                    .orElse(null);
                    return new EvaluationUnlockResponse(
                            u.getId(),
                            u.getKind(),
                            u.getClassroomId(),
                            cls == null ? null : cls.name(),
                            u.getCampusId(),
                            u.getPeriodKey(),
                            label,
                            u.getValidUntil(),
                            u.getReason(),
                            u.getUnlockedBy(),
                            names.get(u.getUnlockedBy()),
                            u.getUnlockedAt(),
                            u.getRevokedBy(),
                            u.getRevokedBy() == null ? null : names.get(u.getRevokedBy()),
                            u.getRevokedAt(),
                            u.isActiveAt(now));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdueClassItem> getOverdue(CurrentUser currentUser, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getOverdue|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (!rules.isLeader(currentUser)) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            List<ClassroomInfoResponse> classes = classroomService.findActiveYearClassrooms().stream()
                    .filter(c -> c.yearStatus() == SchoolYearStatus.ACTIVE)
                    .filter(c -> campusId == null || campusId.equals(c.campusId()))
                    .filter(c -> rules.canView(currentUser, c))
                    .toList();
            List<OverdueClassItem> result = new ArrayList<>(overdueOf(classes));
            // Nhận xét tuần: tuần trước đã quá hạn mà chưa gửi đủ
            for (WeeklyCommentClassSummary s : weeklyCommentService.getOverview(
                    currentUser, campusId, rules.today().minusWeeks(1))) {
                if (s.overdue()) {
                    result.add(new OverdueClassItem(
                            "WEEKLY",
                            s.classId(),
                            s.className(),
                            s.campusId(),
                            s.week().weekStart().toString(),
                            "Nhận xét tuần " + DMY.format(s.week().from()) + " – "
                                    + DMY.format(s.week().to()),
                            s.week().deadline(),
                            s.sent(),
                            s.total()));
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
    public List<OverdueClassItem> overdueOf(List<ClassroomInfoResponse> classes) {
        List<OverdueClassItem> result = new ArrayList<>();
        for (ClassroomInfoResponse cls : classes) {
            for (EvaluationPeriod p : periodsOf(cls)) {
                if (!p.locked() || p.unlockedUntil() != null) {
                    continue;
                }
                int[] progress = progress(cls, p);
                if (progress[1] > 0 && progress[0] < progress[1]) {
                    result.add(new OverdueClassItem(
                            p.kind().name(),
                            cls.id(),
                            cls.name(),
                            cls.campusId(),
                            p.periodKey(),
                            p.label(),
                            p.deadline(),
                            progress[0],
                            progress[1]));
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public int[] progress(ClassroomInfoResponse cls, EvaluationPeriod p) {
        if (p.kind() == EvaluationLockKind.THEME) {
            boolean confirmed = themeEvaluationRepository
                    .findByClassroomIdAndThemeId(cls.id(), Long.valueOf(p.periodKey()))
                    .map(ThemeEvaluation::isConfirmed)
                    .orElse(false);
            return new int[] {confirmed ? 1 : 0, 1};
        }
        EvaluationPeriodType type =
                p.kind() == EvaluationLockKind.YEAR ? EvaluationPeriodType.YEAR : EvaluationPeriodType.SEMESTER;
        LocalDate from = cls.yearStart();
        int total = enrollmentService
                .findChildrenInClassroomDuring(cls.id(), from, p.openFrom())
                .size();
        Map<Long, Boolean> confirmedByChild =
                childEvaluationRepository.findByClassroomIdInAndPeriodType(List.of(cls.id()), type).stream()
                        .filter(e -> type == EvaluationPeriodType.YEAR
                                || p.periodKey().equals(semesterKey(cls, e.getPeriodEnd())))
                        .collect(Collectors.toMap(
                                ChildEvaluation::getChildId, ChildEvaluation::isConfirmed, (a, b) -> a || b));
        int done = (int)
                confirmedByChild.values().stream().filter(Boolean::booleanValue).count();
        return new int[] {done, total};
    }
}
