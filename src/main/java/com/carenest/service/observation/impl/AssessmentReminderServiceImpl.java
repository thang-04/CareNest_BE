package com.carenest.service.observation.impl;

import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationPeriod;
import com.carenest.dto.observation.EvaluationDeadlineDtos.OverdueClassItem;
import com.carenest.dto.observation.WeeklyCommentDtos.WeekOption;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentClassSummary;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.entity.observation.EvaluationUnlock;
import com.carenest.entity.observation.WeeklyCommentUnlock;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.repository.observation.EvaluationUnlockRepository;
import com.carenest.repository.observation.WeeklyCommentUnlockRepository;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassChildDay;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.observation.AssessmentReminderService;
import com.carenest.service.observation.EvaluationDeadlineService;
import com.carenest.service.observation.WeeklyCommentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AssessmentReminderServiceImpl implements AssessmentReminderService {
    private static final String LOG_PREFIX = "[AssessmentReminderServiceImpl]";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Duration CUTOFF_LEAD = Duration.ofMinutes(30);
    private static final Duration UNLOCK_ENDING_LEAD = Duration.ofHours(1);
    private static final int NEXT_SCHOOL_DAY_SCAN = 30;

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final AttendanceService attendanceService;
    private final DailyObservationRepository observationRepository;
    private final NotificationService notificationService;
    private final EvaluationDeadlineService evaluationDeadlineService;
    private final WeeklyCommentService weeklyCommentService;
    private final EvaluationUnlockRepository evaluationUnlockRepository;
    private final WeeklyCommentUnlockRepository weeklyUnlockRepository;
    private final AssessmentDayUnlockRepository dayUnlockRepository;
    private final Clock clock;

    @Override
    @Scheduled(cron = "0 */5 6-12 * * MON-FRI", zone = "${carenest.time-zone}")
    public int remindAttendanceCutoff() {
        String logPrefix = LOG_PREFIX + "|remindAttendanceCutoff";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = rules.today();
            DayInfoResponse info = attendanceService.getActiveYearDayInfo(today);
            int sent = 0;
            if (info != null && info.schoolDay() && !info.baseLocked() && info.cutoff() != null) {
                LocalDateTime cutoffAt = today.atTime(LocalTime.parse(info.cutoff()));
                Duration left = Duration.between(LocalDateTime.now(clock), cutoffAt);
                if (!left.isNegative() && left.compareTo(CUTOFF_LEAD) <= 0) {
                    List<ClassroomInfoResponse> classes = activeClasses(today);
                    Set<Long> taken = attendanceService.findClassDays(ids(classes), today, today).stream()
                            .map(ClassChildDay::classroomId)
                            .collect(Collectors.toSet());
                    Map<Long, List<Long>> teachers = teachersOf(classes, today);
                    for (ClassroomInfoResponse cls : classes) {
                        if (taken.contains(cls.id())) {
                            continue;
                        }
                        sent += notifyOnce(
                                teachers.getOrDefault(cls.id(), List.of()),
                                NotificationType.ATTENDANCE_REMINDER,
                                "Sắp tới giờ chốt điểm danh",
                                "Lớp " + cls.name() + " chưa điểm danh hôm nay. Giờ chốt " + info.cutoff() + ".",
                                "/attendance?classId=" + cls.id() + "&date=" + today,
                                "cutoff-" + today);
                    }
                }
            }
            log.info("{}|sent={}|END", logPrefix, sent);
            return sent;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Scheduled(cron = "0 0 20 * * MON-FRI", zone = "${carenest.time-zone}")
    public int remindDailyAssessment() {
        String logPrefix = LOG_PREFIX + "|remindDailyAssessment";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = rules.today();
            int sent = 0;
            if (rules.isSchoolDay(today)) {
                List<ClassroomInfoResponse> classes = activeClasses(today);
                Map<Long, List<Long>> present = attendanceService.findClassDays(ids(classes), today, today).stream()
                        .filter(d -> d.status() == AttendanceStatus.PRESENT)
                        .collect(Collectors.groupingBy(
                                ClassChildDay::classroomId,
                                Collectors.mapping(ClassChildDay::childId, Collectors.toList())));
                Map<Long, List<Long>> teachers = teachersOf(classes, today);
                for (ClassroomInfoResponse cls : classes) {
                    List<Long> children = present.getOrDefault(cls.id(), List.of());
                    if (children.isEmpty()) {
                        continue;
                    }
                    Set<Long> done = new HashSet<>();
                    observationRepository
                            .findByChildIdInAndDate(children, today)
                            .forEach(o -> done.add(o.getChildId()));
                    long missing =
                            children.stream().filter(c -> !done.contains(c)).count();
                    if (missing == 0) {
                        continue;
                    }
                    sent += notifyOnce(
                            teachers.getOrDefault(cls.id(), List.of()),
                            NotificationType.ASSESSMENT_REMINDER,
                            "Chưa xong đánh giá hôm nay",
                            "Lớp " + cls.name() + " còn " + missing
                                    + " trẻ có mặt chưa được đánh giá. Ngày đánh giá khóa lúc 23:59.",
                            AssessmentReminderLinks.daily(cls.id(), today),
                            "daily-" + today);
                }
            }
            log.info("{}|sent={}|END", logPrefix, sent);
            return sent;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "${carenest.time-zone}")
    public int remindEvaluationDeadlines() {
        String logPrefix = LOG_PREFIX + "|remindEvaluationDeadlines";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = rules.today();
            int sent = 0;
            if (rules.isSchoolDay(today)) {
                LocalDate nextSchoolDay = nextSchoolDay(today);
                List<ClassroomInfoResponse> classes = activeClasses(today);
                Map<Long, List<Long>> teachers = teachersOf(classes, today);
                for (ClassroomInfoResponse cls : classes) {
                    List<Long> to = teachers.getOrDefault(cls.id(), List.of());
                    if (to.isEmpty()) {
                        continue;
                    }
                    for (EvaluationPeriod p : evaluationDeadlineService.periodsOf(cls)) {
                        if (!p.open()) {
                            continue;
                        }
                        int[] progress = evaluationDeadlineService.progress(cls, p);
                        boolean unfinished = progress[1] > 0 && progress[0] < progress[1];
                        String link = AssessmentReminderLinks.evaluation(p.kind(), cls.id(), p.periodKey());
                        sent += remindStep(
                                to,
                                cls,
                                p.label(),
                                p.deadline(),
                                p.locked(),
                                p.unlockedUntil() != null,
                                unfinished,
                                progress[1] - progress[0],
                                nextSchoolDay,
                                link,
                                p.kind() + "-" + p.periodKey());
                    }
                    LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    for (LocalDate monday : List.of(thisMonday.minusWeeks(1), thisMonday)) {
                        WeeklyCommentClassSummary s =
                                weeklyCommentService.summarize(cls, monday).orElse(null);
                        if (s == null || !s.week().open()) {
                            continue;
                        }
                        WeekOption w = s.week();
                        sent += remindStep(
                                to,
                                cls,
                                "Nhận xét tuần " + DM.format(w.from()) + " – " + DMY.format(w.to()),
                                w.deadline(),
                                w.locked(),
                                w.unlockedUntil() != null,
                                s.total() > 0 && s.sent() < s.total(),
                                s.total() - s.sent(),
                                nextSchoolDay,
                                AssessmentReminderLinks.weekly(cls.id(), w.weekStart()),
                                "WEEKLY-" + w.weekStart());
                    }
                }
            }
            log.info("{}|sent={}|END", logPrefix, sent);
            return sent;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Đã mở (một lần), còn 1 ngày học tới hạn mà chưa xong (một lần), đã khóa mà chưa xong (một lần)
    private int remindStep(
            List<Long> to,
            ClassroomInfoResponse cls,
            String label,
            LocalDate deadline,
            boolean locked,
            boolean unlocked,
            boolean unfinished,
            int remaining,
            LocalDate nextSchoolDay,
            String link,
            String key) {
        String due = "23:59 " + DMY.format(deadline);
        if (locked) {
            if (!unlocked && unfinished) {
                return notifyOnce(
                        to,
                        NotificationType.ASSESSMENT_REMINDER,
                        label + " đã khóa",
                        label + " lớp " + cls.name() + " đã quá hạn " + due
                                + ". Liên hệ PHT hoặc Hiệu trưởng để mở lại.",
                        link,
                        key + "-locked");
            }
            return 0;
        }
        int sent = notifyOnce(
                to,
                NotificationType.ASSESSMENT_REMINDER,
                label + " đã mở",
                label + " lớp " + cls.name() + " đã mở. Hạn " + due + ".",
                link,
                key + "-open");
        if (unfinished && deadline.equals(nextSchoolDay)) {
            sent += notifyOnce(
                    to,
                    NotificationType.ASSESSMENT_REMINDER,
                    label + " sắp hết hạn",
                    label + " lớp " + cls.name() + " còn " + remaining + " mục chưa xong. Hạn " + due + ".",
                    link,
                    key + "-near");
        }
        return sent;
    }

    @Override
    @Scheduled(cron = "0 */10 * * * *", zone = "${carenest.time-zone}")
    public int remindUnlocksEnding() {
        String logPrefix = LOG_PREFIX + "|remindUnlocksEnding";
        log.info("{}|START", logPrefix);
        try {
            Instant now = rules.now();
            Instant until = now.plus(UNLOCK_ENDING_LEAD);
            LocalDate today = rules.today();
            int sent = 0;
            for (AssessmentDayUnlock u : dayUnlockRepository.findByValidUntilBetween(now, until)) {
                sent += remindEnding(
                        u.getClassroomId(),
                        "Đánh giá ngày " + DMY.format(u.getDate()),
                        u.getValidUntil(),
                        AssessmentReminderLinks.daily(u.getClassroomId(), u.getDate()),
                        "ending-D" + u.getId(),
                        today);
            }
            for (WeeklyCommentUnlock u : weeklyUnlockRepository.findByValidUntilBetween(now, until)) {
                sent += remindEnding(
                        u.getClassroomId(),
                        "Nhận xét tuần " + DMY.format(u.getWeekStart()),
                        u.getValidUntil(),
                        AssessmentReminderLinks.weekly(u.getClassroomId(), u.getWeekStart()),
                        "ending-W" + u.getId(),
                        today);
            }
            for (EvaluationUnlock u : evaluationUnlockRepository.findByValidUntilBetween(now, until)) {
                sent += remindEnding(
                        u.getClassroomId(),
                        "Đánh giá đã mở lại",
                        u.getValidUntil(),
                        AssessmentReminderLinks.evaluation(u.getKind(), u.getClassroomId(), u.getPeriodKey()),
                        "ending-E" + u.getId(),
                        today);
            }
            log.info("{}|sent={}|END", logPrefix, sent);
            return sent;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private int remindEnding(Long classId, String label, Instant validUntil, String link, String key, LocalDate today) {
        ClassroomInfoResponse cls = classroomService.findClassroomInfo(classId).orElse(null);
        if (cls == null || cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            return 0;
        }
        List<Long> to = teachersOf(List.of(cls), rules.teachingDay(cls)).getOrDefault(cls.id(), List.of());
        String at = HM.withZone(clock.getZone()).format(validUntil);
        return notifyOnce(
                to,
                NotificationType.ASSESSMENT_REMINDER,
                "Sắp hết thời gian mở lại",
                label + " lớp " + cls.name() + " chỉ còn mở đến " + at + " hôm nay.",
                link,
                key);
    }

    @Override
    @Scheduled(cron = "0 30 7 * * MON-FRI", zone = "${carenest.time-zone}")
    public int remindLeadersOverdue() {
        String logPrefix = LOG_PREFIX + "|remindLeadersOverdue";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = rules.today();
            int sent = 0;
            if (rules.isSchoolDay(today)) {
                List<ClassroomInfoResponse> classes = activeClasses(today);
                List<OverdueClassItem> overdue = new ArrayList<>(evaluationDeadlineService.overdueOf(classes));
                LocalDate lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .minusWeeks(1);
                for (ClassroomInfoResponse cls : classes) {
                    weeklyCommentService
                            .summarize(cls, lastMonday)
                            .filter(WeeklyCommentClassSummary::overdue)
                            .ifPresent(s -> overdue.add(new OverdueClassItem(
                                    "WEEKLY",
                                    cls.id(),
                                    cls.name(),
                                    cls.campusId(),
                                    s.week().weekStart().toString(),
                                    "Nhận xét tuần",
                                    s.week().deadline(),
                                    s.sent(),
                                    s.total())));
                }
                Map<Long, List<OverdueClassItem>> byCampus = overdue.stream()
                        .collect(Collectors.groupingBy(
                                OverdueClassItem::campusId, LinkedHashMap::new, Collectors.toList()));
                // PHT: điểm trường mình; HT: một bản tóm tắt cả trường
                for (Map.Entry<Long, List<OverdueClassItem>> e : byCampus.entrySet()) {
                    sent += notifyOnce(
                            staffAssignmentService.findCampusVicePrincipalIds(e.getKey(), today),
                            NotificationType.LEADER_REMINDER,
                            "Lớp quá hạn đánh giá",
                            summary(e.getValue()),
                            "/assessment/day-unlocks?campusId=" + e.getKey(),
                            "overdue-" + e.getKey() + "-" + today);
                }
                if (!overdue.isEmpty()) {
                    sent += notifyOnce(
                            staffAssignmentService.findPrincipalIds(today),
                            NotificationType.LEADER_REMINDER,
                            "Lớp quá hạn đánh giá",
                            summary(overdue),
                            "/assessment/day-unlocks",
                            "overdue-all-" + today);
                }
            }
            log.info("{}|sent={}|END", logPrefix, sent);
            return sent;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static String summary(List<OverdueClassItem> items) {
        long classes = items.stream().map(OverdueClassItem::classId).distinct().count();
        return classes + " lớp còn " + items.size() + " mục đánh giá / nhận xét tuần quá hạn chưa xong.";
    }

    // Lớp của năm đang hoạt động, hôm nay nằm trong năm học
    private List<ClassroomInfoResponse> activeClasses(LocalDate today) {
        return classroomService.findActiveYearClassrooms().stream()
                .filter(c -> c.yearStatus() == SchoolYearStatus.ACTIVE)
                .filter(c -> c.yearStart() == null || !today.isBefore(c.yearStart()))
                .filter(c -> c.yearEnd() == null || !today.isAfter(c.yearEnd()))
                .toList();
    }

    private Map<Long, List<Long>> teachersOf(List<ClassroomInfoResponse> classes, LocalDate today) {
        return classes.isEmpty() ? Map.of() : staffAssignmentService.findCurrentTeacherIds(ids(classes), today);
    }

    private static List<Long> ids(List<ClassroomInfoResponse> classes) {
        return classes.stream().map(ClassroomInfoResponse::id).toList();
    }

    private LocalDate nextSchoolDay(LocalDate day) {
        LocalDate d = day;
        for (int i = 0; i < NEXT_SCHOOL_DAY_SCAN; i++) {
            d = d.plusDays(1);
            if (rules.isSchoolDay(d)) {
                return d;
            }
        }
        return d;
    }

    // Một lần cho mỗi người + loại + đường dẫn (khóa lời nhắc nằm trong đường dẫn)
    private int notifyOnce(
            Collection<Long> to, NotificationType type, String title, String body, String link, String key) {
        if (to.isEmpty()) {
            return 0;
        }
        notificationService.notifyUsers(to, type, title, body, AssessmentReminderLinks.once(link, key), true);
        return to.size();
    }
}
