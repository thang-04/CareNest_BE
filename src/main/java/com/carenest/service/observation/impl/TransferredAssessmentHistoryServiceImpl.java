package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.EvaluationDtos.EvaluationDay;
import com.carenest.dto.observation.ThemeEvaluationDtos.Cell;
import com.carenest.dto.observation.ThemeEvaluationDtos.GoalColumn;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.ChildHistory;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.ClassStay;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.HistoryAttendance;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.HistoryDay;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.HistoryGoalResult;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.HistoryHealth;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.HistoryThemeResult;
import com.carenest.dto.observation.TransferredAssessmentHistoryResponse.HistoryWeeklyComment;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.WeeklyComment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.WeeklyCommentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ChildAttendanceDay;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.attendance.DailyHealthService.ChildHealthEntry;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.service.observation.ThemeEvaluationService.ConfirmedChildTheme;
import com.carenest.service.observation.TransferredAssessmentHistoryService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.WeeklyCommentStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransferredAssessmentHistoryServiceImpl implements TransferredAssessmentHistoryService {

    private static final String LOG_PREFIX = "[TransferredAssessmentHistoryServiceImpl]";
    static final int MAX_DAYS = 62;

    private final ObservationDayRules rules;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;
    private final EvaluationSources sources;
    private final WeeklyCommentRepository weeklyCommentRepository;
    private final AccountService accountService;
    private final AttendanceService attendanceService;
    private final DailyHealthService dailyHealthService;
    private final ThemeEvaluationService themeEvaluationService;

    @Override
    public TransferredAssessmentHistoryResponse findHistory(
            CurrentUser currentUser, Long classId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|findHistory|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canViewAsCurrentClass(currentUser, cls)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            if (from == null || to == null || to.isBefore(from)) {
                throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
            }
            if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
                throw GlobalException.of(ApiCode.DATE_RANGE_TOO_LONG, MAX_DAYS);
            }
            // Chỉ trong năm học của lớp, không quá hôm nay
            LocalDate start = from.isBefore(cls.yearStart()) ? cls.yearStart() : from;
            LocalDate end = min(min(to, cls.yearEnd()), rules.today());
            List<ChildHistory> children = end.isBefore(start) ? List.of() : childrenHistory(cls, start, end);
            log.info("{}|children={}|END", logPrefix, children.size());
            return new TransferredAssessmentHistoryResponse(classId, from, to, children);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<ChildHistory> childrenHistory(ClassroomInfoResponse cls, LocalDate from, LocalDate to) {
        // Trẻ đang thuộc lớp: cô lớp mới không xem được trẻ đã rời lớp mình hay trẻ khác của lớp cũ
        LocalDate current = min(rules.today(), cls.yearEnd());
        List<ClassChildResponse> roster = enrollmentService.findChildrenInClassroom(cls.id(), current);
        Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
        Map<Long, List<ClassStay>> staysByChild = new HashMap<>();
        for (ClassChildResponse child : roster) {
            List<ClassStay> stays = new ArrayList<>();
            for (var stay : enrollmentService.findChildStays(child.id(), from, to)) {
                if (cls.id().equals(stay.classroomId())) continue;
                Optional<ClassroomInfoResponse> source = Optional.ofNullable(classes.get(stay.classroomId()))
                        .or(() -> classroomService.findClassroomInfo(stay.classroomId()));
                // Lớp khác năm học không thuộc lịch sử năm này
                if (source.isEmpty() || !Objects.equals(source.get().schoolYearId(), cls.schoolYearId())) continue;
                classes.put(stay.classroomId(), source.get());
                stays.add(new ClassStay(stay.classroomId(), source.get().name(), stay.from(), stay.to()));
            }
            if (!stays.isEmpty()) staysByChild.put(child.id(), stays);
        }
        if (staysByChild.isEmpty()) return List.of();
        // Điểm danh, sức khỏe đọc một lần cho mọi trẻ chuyển đến, rồi lọc đúng lớp cũ và thời gian ở lớp đó
        Map<Long, List<ChildAttendanceDay>> attendance =
                attendanceService.findChildAttendance(staysByChild.keySet(), from, to).stream()
                        .collect(Collectors.groupingBy(ChildAttendanceDay::childId));
        Map<Long, List<ChildHealthEntry>> health =
                dailyHealthService.findDayEntries(staysByChild.keySet(), from, to).stream()
                        .collect(Collectors.groupingBy(ChildHealthEntry::childId));
        Set<Long> userIds = new HashSet<>();
        List<PendingChild> pending = new ArrayList<>();
        for (ClassChildResponse child : roster) {
            List<ClassStay> stays = staysByChild.get(child.id());
            if (stays == null) continue;
            List<ChildAttendanceDay> days = attendance.getOrDefault(child.id(), List.of()).stream()
                    .filter(d -> inStay(stays, d.classroomId(), d.date()))
                    .toList();
            List<ChildHealthEntry> entries = health.getOrDefault(child.id(), List.of()).stream()
                    .filter(e -> inStay(stays, e.classroomId(), e.date()))
                    .toList();
            List<WeeklyComment> comments = sentComments(child.id(), stays);
            days.forEach(d -> userIds.add(d.recordedBy()));
            entries.forEach(e -> userIds.add(e.recordedBy()));
            comments.forEach(c -> userIds.add(c.getSentBy()));
            pending.add(new PendingChild(
                    child,
                    stays,
                    evaluationDays(child.id(), stays, classes),
                    comments,
                    days,
                    entries,
                    themeResults(child.id(), stays)));
        }
        userIds.remove(null);
        Map<Long, String> names = userIds.isEmpty() ? Map.of() : accountService.findFullNames(userIds);
        Function<Long, String> className = id -> classes.get(id).name();
        return pending.stream()
                .map(p -> new ChildHistory(
                        p.child().id(),
                        p.child().fullName(),
                        p.stays(),
                        p.days().stream()
                                .map(d -> new HistoryDay(d, d.recordedById(), d.recordedByName(), d.recordedAt()))
                                .toList(),
                        p.comments().stream()
                                .map(c -> new HistoryWeeklyComment(
                                        c.getWeekStart(),
                                        c.getClassroomId(),
                                        className.apply(c.getClassroomId()),
                                        c.getContent(),
                                        c.getSentAt(),
                                        c.getSentBy(),
                                        names.get(c.getSentBy())))
                                .toList(),
                        p.attendance().stream()
                                .map(d -> new HistoryAttendance(
                                        d.date(),
                                        d.status(),
                                        d.absentPart(),
                                        d.classroomId(),
                                        className.apply(d.classroomId()),
                                        d.recordedBy(),
                                        names.get(d.recordedBy())))
                                .toList(),
                        p.health().stream()
                                .map(e -> new HistoryHealth(
                                        e.date(),
                                        e.temperature(),
                                        e.symptoms(),
                                        e.note(),
                                        e.classroomId(),
                                        className.apply(e.classroomId()),
                                        e.recordedBy(),
                                        names.get(e.recordedBy()),
                                        e.recordedAt()))
                                .toList(),
                        p.themes()))
                .toList();
    }

    private record PendingChild(
            ClassChildResponse child,
            List<ClassStay> stays,
            List<EvaluationDay> days,
            List<WeeklyComment> comments,
            List<ChildAttendanceDay> attendance,
            List<ChildHealthEntry> health,
            List<HistoryThemeResult> themes) {}

    private static boolean inStay(List<ClassStay> stays, Long classId, LocalDate date) {
        return stays.stream()
                .anyMatch(s -> s.classId().equals(classId) && !date.isBefore(s.from()) && !date.isAfter(s.to()));
    }

    /** Đánh giá ngày đã lưu ở đúng lớp cũ trong thời gian trẻ học lớp đó (chỉ ngày có mặt, như hồ sơ phát triển). */
    private List<EvaluationDay> evaluationDays(
            Long childId, List<ClassStay> stays, Map<Long, ClassroomInfoResponse> classes) {
        List<EvaluationDay> days = new ArrayList<>();
        for (ClassStay stay : stays) {
            var data = sources.loadTimeline(classes.get(stay.classId()), childId, stay.from(), stay.to())
                    .get(childId);
            if (data == null) continue;
            data.days().stream().filter(d -> stay.classId().equals(d.classId())).forEach(days::add);
        }
        return days;
    }

    /** Bảng cuối chủ đề đã xác nhận của lớp cũ, chủ đề giao thời gian trẻ học lớp đó; bản nháp không trả về. */
    private List<HistoryThemeResult> themeResults(Long childId, List<ClassStay> stays) {
        List<HistoryThemeResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClassStay stay : stays) {
            for (ConfirmedChildTheme t :
                    themeEvaluationService.confirmedChildThemes(stay.classId(), childId, stay.from(), stay.to())) {
                if (!seen.add(stay.classId() + "|" + t.themeId())) continue;
                Map<Long, Boolean> met = t.row().cells().stream()
                        .filter(c -> c.met() != null)
                        .collect(Collectors.toMap(Cell::goalId, Cell::met, (a, b) -> a));
                List<HistoryGoalResult> goals = new ArrayList<>();
                for (GoalColumn g : t.goals()) {
                    goals.add(new HistoryGoalResult(g.goalId(), g.goalNo(), g.text(), met.get(g.goalId())));
                }
                results.add(new HistoryThemeResult(
                        t.themeId(),
                        t.themeName(),
                        t.startDate(),
                        t.endDate(),
                        stay.classId(),
                        stay.className(),
                        goals,
                        t.row().met(),
                        t.row().notMet(),
                        t.row().rate(),
                        t.row().passed(),
                        t.confirmedById(),
                        t.confirmedByName(),
                        t.confirmedAt()));
            }
        }
        return results;
    }

    /** Nhận xét tuần SENT ở lớp cũ có tuần giao thời gian trẻ học lớp đó; bản nháp không trả về. */
    private List<WeeklyComment> sentComments(Long childId, List<ClassStay> stays) {
        List<WeeklyComment> comments = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ClassStay stay : stays) {
            LocalDate monday = stay.from().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeklyCommentRepository
                    .findByChildIdAndStatusAndWeekStartBetweenOrderByWeekStartAsc(
                            childId, WeeklyCommentStatus.SENT, monday, stay.to())
                    .stream()
                    .filter(c -> stay.classId().equals(c.getClassroomId()) && seen.add(c.getId()))
                    .forEach(comments::add);
        }
        return comments;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
