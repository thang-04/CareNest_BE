package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.ClassAssessmentDetailResponse;
import com.carenest.dto.observation.ClassAssessmentDetailResponse.ChildRow;
import com.carenest.dto.observation.ClassAssessmentDetailResponse.Comment;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse.Brief;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse.ClassBrief;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse.ClassRow;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse.MostNotMet;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse.Totals;
import com.carenest.dto.observation.ClassCommentResponse;
import com.carenest.dto.observation.DailySheetResponse.ActivityResult;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.entity.observation.DailyObservation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.repository.observation.ClassDailyCommentRepository;
import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.observation.ClassAssessmentOverviewService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.HealthSymptom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class ClassAssessmentOverviewServiceImpl implements ClassAssessmentOverviewService {

    private static final String LOG_PREFIX = "[ClassAssessmentOverviewServiceImpl]";
    private static final Set<String> PERIODS = Set.of("DAY", "WEEK", "MONTH");

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final DailyHealthService dailyHealthService;
    private final DailyObservationRepository observationRepository;
    private final DailyActivityResultRepository resultRepository;
    private final AssessmentDayUnlockRepository unlockRepository;
    private final ClassDailyCommentRepository classCommentRepository;
    private final EduLessonQueryService lessonQueryService;

    /** Kỳ đã chặn trong năm học và tới hôm nay; days = ngày học trong kỳ. */
    private record Range(String period, LocalDate start, LocalDate end, LocalDate anchor, List<LocalDate> days) {}

    /** Số liệu một trẻ trong kỳ. */
    private static final class ChildStats {
        private final ClassChildResponse child;
        private int presentDays;
        private int assessedDays;
        private int healthDays;
        private final Map<HealthSymptom, Integer> signs = new EnumMap<>(HealthSymptom.class);
        private int dat;
        private int canCoGang;
        private int chuaDat;
        private int flags;
        private final List<Comment> comments = new ArrayList<>();

        private ChildStats(ClassChildResponse child) {
            this.child = child;
        }
    }

    @Override
    public ClassAssessmentOverviewResponse getOverview(
            CurrentUser currentUser, String period, Long schoolYearId, Long campusId, Long ageGroupId, Long classId) {
        String logPrefix = LOG_PREFIX + "|getOverview|userId=" + currentUser.userId() + "|period=" + period;
        log.info("{}|START", logPrefix);
        try {
            if (!rules.isLeader(currentUser)) {
                throw GlobalException.of(ApiCode.ASSESSMENT_OVERVIEW_FORBIDDEN);
            }
            String p = checkPeriod(period);
            // PHT chỉ lớp điểm trường mình, HT toàn trường (AUTH-01, AUTH-02)
            List<ClassroomInfoResponse> scoped = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> rules.leadsCampus(currentUser, c.campusId()))
                    .sorted(Comparator.comparing(ClassroomInfoResponse::campusId)
                            .thenComparing(ClassroomInfoResponse::name))
                    .toList();
            Range range = scoped.isEmpty() ? rangeOf(p, rules.today(), null, null) : rangeOf(p, scoped.getFirst());
            List<ClassroomInfoResponse> visible = scoped.stream()
                    .filter(c -> (campusId == null || campusId.equals(c.campusId()))
                            && (ageGroupId == null || ageGroupId.equals(c.ageGroupId()))
                            && (classId == null || classId.equals(c.id())))
                    .toList();
            Map<Long, List<ClassCommentResponse>> comments = classComments(visible, range);
            List<ClassRow> rows = visible.stream()
                    .map(c -> toRow(c, range, statsOf(c, range), comments.getOrDefault(c.id(), List.of())))
                    .toList();
            ClassAssessmentOverviewResponse response = new ClassAssessmentOverviewResponse(
                    p,
                    range.start(),
                    range.end(),
                    range.days().size(),
                    scoped.stream()
                            .map(ClassroomInfoResponse::campusId)
                            .distinct()
                            .map(id -> new Brief(id, null))
                            .toList(),
                    scoped.stream()
                            .map(ClassroomInfoResponse::ageGroupId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .map(id -> new Brief(id, null))
                            .toList(),
                    scoped.stream()
                            .map(c -> new ClassBrief(c.id(), c.name(), c.campusId(), c.ageGroupId()))
                            .toList(),
                    rows,
                    totalsOf(rows));
            log.info("{}|classes={}|END", logPrefix, rows.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ClassAssessmentDetailResponse getClassDetail(CurrentUser currentUser, Long classId, String period) {
        String logPrefix = LOG_PREFIX + "|getClassDetail|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.leadsCampus(currentUser, cls.campusId())) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            Range range = rangeOf(checkPeriod(period), cls);
            List<ChildRow> children = statsOf(cls, range).stream()
                    .map(s -> new ChildRow(
                            s.child.id(),
                            s.child.fullName(),
                            s.presentDays,
                            s.assessedDays,
                            s.healthDays,
                            s.signs,
                            s.dat,
                            s.canCoGang,
                            s.chuaDat,
                            s.flags,
                            s.comments))
                    .toList();
            ClassAssessmentDetailResponse response = new ClassAssessmentDetailResponse(
                    cls.id(),
                    cls.name(),
                    cls.campusId(),
                    range.period(),
                    range.start(),
                    range.end(),
                    range.days().size(),
                    children,
                    classComments(List.of(cls), range).getOrDefault(cls.id(), List.of()));
            log.info("{}|children={}|END", logPrefix, children.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static String checkPeriod(String period) {
        String p = period == null ? "DAY" : period.toUpperCase();
        if (!PERIODS.contains(p)) {
            throw GlobalException.of(ApiCode.INVALID_VALUE, "kỳ tổng hợp");
        }
        return p;
    }

    private Range rangeOf(String period, ClassroomInfoResponse cls) {
        return rangeOf(period, rules.today(), cls.yearStart(), cls.yearEnd());
    }

    // Năm đang chạy neo hôm nay; năm đã qua neo ngày cuối năm, năm chưa tới neo ngày đầu năm
    private static Range rangeOf(String period, LocalDate today, LocalDate yearStart, LocalDate yearEnd) {
        LocalDate anchor = yearEnd != null && today.isAfter(yearEnd)
                ? yearEnd
                : yearStart != null && today.isBefore(yearStart) ? yearStart : today;
        LocalDate start;
        LocalDate end;
        switch (period) {
            case "WEEK" -> {
                start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = start.plusDays(4);
            }
            case "MONTH" -> {
                start = anchor.withDayOfMonth(1);
                end = anchor.with(TemporalAdjusters.lastDayOfMonth());
            }
            default -> {
                start = anchor;
                end = anchor;
            }
        }
        LocalDate last = end.isAfter(anchor) ? anchor : end;
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(last); d = d.plusDays(1)) {
            if (ObservationDayRules.isSchoolDay(d)
                    && (yearStart == null || !d.isBefore(yearStart))
                    && (yearEnd == null || !d.isAfter(yearEnd))) {
                days.add(d);
            }
        }
        return new Range(period, start, end, anchor, days);
    }

    /** Từng trẻ của lớp trong các ngày học của kỳ: có mặt, đã đánh giá, sức khỏe, MT, cờ, nhận xét. */
    private List<ChildStats> statsOf(ClassroomInfoResponse cls, Range range) {
        List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(cls.id(), range.anchor());
        List<ChildStats> stats = children.stream().map(ChildStats::new).toList();
        if (children.isEmpty() || range.days().isEmpty()) {
            return stats;
        }
        List<Long> ids = children.stream().map(ClassChildResponse::id).toList();
        LocalDate from = range.days().getFirst();
        LocalDate to = range.days().getLast();
        Set<String> present = attendanceService.findDayStatuses(ids, from, to).stream()
                .filter(s -> s.status() == AttendanceStatus.PRESENT)
                .map(s -> s.childId() + "|" + s.date())
                .collect(Collectors.toSet());
        Map<String, DailyObservation> observations =
                observationRepository.findByChildIdInAndDateBetween(ids, from, to).stream()
                        .collect(Collectors.toMap(o -> o.getChildId() + "|" + o.getDate(), Function.identity()));
        List<DailyActivityResult> storedResults = resultRepository.findByChildIdInAndDateBetween(ids, from, to);
        Map<String, List<DailyActivityResult>> results =
                storedResults.stream().collect(Collectors.groupingBy(r -> r.getChildId() + "|" + r.getDate()));
        Map<DailyActivityResult, ActivityResult> projected = AssessmentResultProjection.project(
                lessonQueryService,
                storedResults.isEmpty() ? Map.of() : lessonQueryService.findApprovedDayLessons(cls.id(), from, to),
                storedResults);
        Map<String, List<HealthSymptom>> prefill = new HashMap<>();
        dailyHealthService
                .findLatestDaySigns(ids, from, to)
                .forEach(h -> prefill.put(h.childId() + "|" + h.date(), h.signs()));
        Set<LocalDate> daySet = new HashSet<>(range.days());
        for (ChildStats s : stats) {
            for (LocalDate d : range.days()) {
                String key = s.child.id() + "|" + d;
                if (!present.contains(key) || !daySet.contains(d)) {
                    continue;
                }
                s.presentDays++;
                DailyObservation o = observations.get(key);
                List<DailyActivityResult> rs = results.getOrDefault(key, List.of());
                if (o != null || !rs.isEmpty()) {
                    s.assessedDays++;
                }
                List<HealthSymptom> signs =
                        o != null && o.isHealthOverridden() ? o.getHealthSigns() : prefill.getOrDefault(key, List.of());
                if (!signs.isEmpty()) {
                    s.healthDays++;
                    signs.forEach(x -> s.signs.merge(x, 1, Integer::sum));
                }
                for (DailyActivityResult r : rs) {
                    ActivityResult view = projected.get(r);
                    s.dat += view.goalsMet().size();
                    s.canCoGang += view.goalsEffort().size();
                    s.chuaDat += view.goalsNotMet().size();
                }
                if (o != null && Boolean.TRUE.equals(o.getFlag())) {
                    s.flags++;
                }
                if (o != null && !o.getComment().isBlank()) {
                    s.comments.add(new Comment(d, o.getComment()));
                }
            }
        }
        return stats;
    }

    private Map<Long, List<ClassCommentResponse>> classComments(List<ClassroomInfoResponse> classes, Range range) {
        if (classes.isEmpty() || range.days().isEmpty()) return Map.of();
        var comments = classCommentRepository
                .findByClassroomIdInAndDateBetweenOrderByDateDesc(
                        classes.stream().map(ClassroomInfoResponse::id).toList(),
                        range.days().getFirst(),
                        range.days().getLast())
                .stream()
                .filter(c -> !c.getText().isBlank() && range.days().contains(c.getDate()))
                .toList();
        return rules.commentViews(comments).stream().collect(Collectors.groupingBy(ClassCommentResponse::classId));
    }

    private ClassRow toRow(
            ClassroomInfoResponse cls, Range range, List<ChildStats> stats, List<ClassCommentResponse> comments) {
        Set<LocalDate> assessed = new HashSet<>();
        if (!stats.isEmpty() && !range.days().isEmpty()) {
            List<Long> ids = stats.stream().map(s -> s.child.id()).toList();
            LocalDate from = range.days().getFirst();
            LocalDate to = range.days().getLast();
            observationRepository.findByChildIdInAndDateBetween(ids, from, to).forEach(o -> assessed.add(o.getDate()));
            resultRepository.findByChildIdInAndDateBetween(ids, from, to).forEach(r -> assessed.add(r.getDate()));
        }
        Set<LocalDate> reopened = range.days().isEmpty()
                ? Set.of()
                : unlockRepository
                        .findByClassroomIdInAndDateBetweenAndValidUntilAfter(
                                List.of(cls.id()),
                                range.days().getFirst(),
                                range.days().getLast(),
                                rules.now())
                        .stream()
                        .map(AssessmentDayUnlock::getDate)
                        .collect(Collectors.toSet());
        int locked = (int) range.days().stream()
                .filter(d -> rules.isBaseLocked(d) && !reopened.contains(d))
                .count();
        return new ClassRow(
                cls.id(),
                cls.name(),
                cls.campusId(),
                cls.ageGroupId(),
                stats.size(),
                stats.stream().mapToInt(s -> s.presentDays).sum(),
                stats.stream().mapToInt(s -> s.assessedDays).sum(),
                (int) stats.stream().filter(s -> s.healthDays > 0).count(),
                stats.stream().mapToInt(s -> s.dat).sum(),
                stats.stream().mapToInt(s -> s.canCoGang).sum(),
                stats.stream().mapToInt(s -> s.chuaDat).sum(),
                stats.stream().mapToInt(s -> s.flags).sum(),
                range.days().size(),
                (int) range.days().stream().filter(assessed::contains).count(),
                locked,
                (int) range.days().stream().filter(reopened::contains).count(),
                comments);
    }

    private static Totals totalsOf(List<ClassRow> rows) {
        int dat = rows.stream().mapToInt(ClassRow::dat).sum();
        int graded =
                dat + rows.stream().mapToInt(r -> r.canCoGang() + r.chuaDat()).sum();
        ClassRow most =
                rows.stream().max(Comparator.comparingInt(ClassRow::chuaDat)).orElse(null);
        return new Totals(
                rows.size(),
                (int) rows.stream()
                        .filter(r -> r.present() > 0 && r.assessed() >= r.present())
                        .count(),
                rows.stream().mapToInt(ClassRow::present).sum(),
                rows.stream().mapToInt(ClassRow::assessed).sum(),
                rows.stream().mapToInt(ClassRow::healthChildren).sum(),
                rows.stream().mapToInt(ClassRow::flags).sum(),
                graded == 0 ? null : Math.round(dat * 100f / graded),
                most == null || most.chuaDat() == 0 ? null : new MostNotMet(most.className(), most.chuaDat()));
    }
}
