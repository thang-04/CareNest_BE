package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.QualityReportResponse;
import com.carenest.dto.observation.QualityReportResponse.QualityRow;
import com.carenest.dto.observation.QualityReportResponse.QualityTotal;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassChildDay;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.QualityReportService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.MealSession;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QualityReportServiceImpl implements QualityReportService {

    private static final String LOG_PREFIX = "[QualityReportServiceImpl]";
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MONTH_TEXT = DateTimeFormatter.ofPattern("MM/yyyy");
    // Nhóm tuổi bắt đầu dưới 36 tháng là Nhà trẻ, còn lại Mẫu giáo
    private static final int NURSERY_BELOW_MONTHS = 36;
    // Bé chăm: đi học từ 90% số ngày học của lớp trong tháng (Cần chốt 30/10/2026)
    private static final int DILIGENT_PERCENT = 90;
    // Công thức phiếu đang có (TICKET_RULES của FE): tuần có ≥ 4 ngày có mặt và ≥ 3 cờ ⇒ phiếu Bé ngoan;
    // tháng có ≥ 3 phiếu Bé ngoan ⇒ Bé ngoan toàn diện. Tuần thuộc tháng chứa thứ Hai của tuần.
    private static final int WEEK_MIN_PRESENT_DAYS = 4;
    private static final int WEEK_MIN_FLAGS = 3;
    private static final int MONTH_MIN_WEEKLY_TICKETS = 3;
    private static final String NHA_TRE = "NHA_TRE";
    private static final String MAU_GIAO = "MAU_GIAO";
    private static final String TOAN_TRUONG = "TOAN_TRUONG";

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final AgeGroupService ageGroupService;
    private final DailyObservationRepository observationRepository;

    @Override
    public QualityReportResponse getReport(CurrentUser currentUser, Long schoolYearId, YearMonth month, Long campusId) {
        String logPrefix =
                LOG_PREFIX + "|getReport|userId=" + currentUser.userId() + "|month=" + month + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (!rules.isLeader(currentUser)) {
                throw GlobalException.of(ApiCode.QUALITY_REPORT_FORBIDDEN);
            }
            if (campusId != null && !rules.leadsCampus(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            LocalDate today = rules.today();
            // PHT chỉ lớp điểm trường mình, HT toàn trường (AUTH-01, AUTH-02)
            List<ClassroomInfoResponse> scoped = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> rules.leadsCampus(currentUser, c.campusId()))
                    .sorted(Comparator.comparing(ClassroomInfoResponse::campusId)
                            .thenComparing(ClassroomInfoResponse::name))
                    .toList();
            if (scoped.isEmpty()) {
                YearMonth m = month != null ? month : YearMonth.from(today);
                checkNotFuture(m, today);
                log.info("{}|classes=0|END", logPrefix);
                return new QualityReportResponse(
                        m.format(MONTH_KEY), m.atDay(1), m.atEndOfMonth(), List.of(), List.of(), List.of(), List.of());
            }
            LocalDate yearStart = scoped.getFirst().yearStart();
            LocalDate yearEnd = scoped.getFirst().yearEnd();
            LocalDate dataEnd = today.isBefore(yearEnd) ? today : yearEnd;
            YearMonth firstMonth = YearMonth.from(yearStart);
            YearMonth lastMonth = dataEnd.isBefore(yearStart) ? firstMonth : YearMonth.from(dataEnd);
            YearMonth m = month != null ? month : lastMonth;
            checkNotFuture(m, today);
            if (m.isBefore(firstMonth) || m.isAfter(YearMonth.from(yearEnd))) {
                throw GlobalException.of(
                        ApiCode.QUALITY_REPORT_MONTH_OUT_OF_YEAR,
                        firstMonth.format(MONTH_TEXT),
                        YearMonth.from(yearEnd).format(MONTH_TEXT));
            }
            List<YearMonth> months = new ArrayList<>();
            for (YearMonth x = firstMonth; !x.isAfter(lastMonth); x = x.plusMonths(1)) {
                months.add(x);
            }
            List<ClassroomInfoResponse> visible = scoped.stream()
                    .filter(c -> campusId == null || campusId.equals(c.campusId()))
                    .toList();
            Map<Long, Integer> ageFrom = new HashMap<>();
            for (AgeGroupDetailResponse g : ageGroupService.getAgeGroups()) {
                if (g.ageFromMonths() != null) {
                    ageFrom.put(g.id(), g.ageFromMonths());
                }
            }
            Map<Long, List<ClassChildDay>> days = visible.isEmpty() || dataEnd.isBefore(yearStart)
                    ? Map.of()
                    : attendanceService
                            .findClassDays(
                                    visible.stream()
                                            .map(ClassroomInfoResponse::id)
                                            .toList(),
                                    yearStart,
                                    dataEnd)
                            .stream()
                            .filter(d -> ObservationDayRules.isSchoolDay(d.date()))
                            .collect(Collectors.groupingBy(ClassChildDay::classroomId));
            List<QualityRow> rows = visible.stream()
                    .map(c -> rowOf(c, m, months, days.getOrDefault(c.id(), List.of()), today, ageFrom))
                    .toList();
            QualityReportResponse response = new QualityReportResponse(
                    m.format(MONTH_KEY),
                    m.atDay(1),
                    m.atEndOfMonth(),
                    months.stream().map(x -> x.format(MONTH_KEY)).toList(),
                    scoped.stream()
                            .map(ClassroomInfoResponse::campusId)
                            .distinct()
                            .toList(),
                    rows,
                    totalsOf(rows, months));
            log.info("{}|classes={}|END", logPrefix, rows.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static void checkNotFuture(YearMonth month, LocalDate today) {
        if (month.isAfter(YearMonth.from(today))) {
            throw GlobalException.of(ApiCode.QUALITY_REPORT_FUTURE_MONTH);
        }
    }

    // Ngày học của lớp = thứ 2–6 có điểm danh; trẻ ra lớp bình quân = tổng lượt có mặt / số ngày học (làm tròn)
    private QualityRow rowOf(
            ClassroomInfoResponse cls,
            YearMonth month,
            List<YearMonth> months,
            List<ClassChildDay> classDays,
            LocalDate today,
            Map<Long, Integer> ageFrom) {
        Map<YearMonth, Set<LocalDate>> schoolDays = new HashMap<>();
        Map<YearMonth, Integer> presentByMonth = new HashMap<>();
        Set<String> present = new HashSet<>();
        Map<Long, Integer> childPresent = new HashMap<>();
        Set<Long> lunch = new HashSet<>();
        for (ClassChildDay d : classDays) {
            YearMonth ym = YearMonth.from(d.date());
            schoolDays.computeIfAbsent(ym, k -> new TreeSet<>()).add(d.date());
            if (d.status() != AttendanceStatus.PRESENT) {
                continue;
            }
            presentByMonth.merge(ym, 1, Integer::sum);
            present.add(d.childId() + "|" + d.date());
            if (ym.equals(month)) {
                childPresent.merge(d.childId(), 1, Integer::sum);
                // Bán trú: có ăn bữa trưa ở trường ít nhất 1 ngày trong tháng
                if (d.meals().contains(MealSession.LUNCH)) {
                    lunch.add(d.childId());
                }
            }
        }
        Map<String, Integer> avgByMonth = new LinkedHashMap<>();
        for (YearMonth ym : months) {
            avgByMonth.put(
                    ym.format(MONTH_KEY),
                    average(
                            presentByMonth.getOrDefault(ym, 0),
                            schoolDays.getOrDefault(ym, Set.of()).size()));
        }

        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate anchor = monthEnd.isAfter(today) ? today : monthEnd;
        if (anchor.isBefore(cls.yearStart())) {
            anchor = cls.yearStart();
        }
        List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(cls.id(), anchor);
        int sd = schoolDays.getOrDefault(month, Set.of()).size();
        int presentDays = children.stream()
                .mapToInt(c -> childPresent.getOrDefault(c.id(), 0))
                .sum();
        int possibleDays = children.size() * sd;
        int meal = (int) children.stream().filter(c -> lunch.contains(c.id())).count();
        int diligent = sd == 0
                ? 0
                : (int) children.stream()
                        .filter(c -> childPresent.getOrDefault(c.id(), 0) * 100 >= DILIGENT_PERCENT * sd)
                        .count();
        Map<Long, Integer> weeklyTickets = weeklyTickets(children, month, today, present);
        int good = (int) weeklyTickets.values().stream().filter(n -> n >= 1).count();
        int allRound = (int) weeklyTickets.values().stream()
                .filter(n -> n >= MONTH_MIN_WEEKLY_TICKETS)
                .count();
        Integer from = cls.ageGroupId() == null ? null : ageFrom.get(cls.ageGroupId());
        int n = children.size();
        return new QualityRow(
                cls.id(),
                cls.name(),
                cls.campusId(),
                cls.ageGroupId(),
                from != null && from < NURSERY_BELOW_MONTHS ? NHA_TRE : MAU_GIAO,
                n,
                sd,
                average(presentByMonth.getOrDefault(month, 0), sd),
                presentDays,
                possibleDays,
                percent(presentDays, possibleDays),
                meal,
                percent(meal, n),
                diligent,
                percent(diligent, n),
                good,
                percent(good, n),
                allRound,
                percent(allRound, n),
                avgByMonth);
    }

    /** Số tuần đủ phiếu Bé ngoan của từng trẻ, các tuần có thứ Hai nằm trong tháng. */
    private Map<Long, Integer> weeklyTickets(
            List<ClassChildResponse> children, YearMonth month, LocalDate today, Set<String> present) {
        Map<Long, Integer> result = new HashMap<>();
        children.forEach(c -> result.put(c.id(), 0));
        LocalDate firstMonday = month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate monthEnd = month.atEndOfMonth();
        if (children.isEmpty() || firstMonday.isAfter(monthEnd) || firstMonday.isAfter(today)) {
            return result;
        }
        LocalDate lastFriday = monthEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusDays(4);
        Set<String> flags = observationRepository
                .findByChildIdInAndDateBetween(
                        children.stream().map(ClassChildResponse::id).toList(), firstMonday, lastFriday)
                .stream()
                .filter(o -> Boolean.TRUE.equals(o.getFlag()))
                .map(o -> o.getChildId() + "|" + o.getDate())
                .collect(Collectors.toSet());
        for (ClassChildResponse c : children) {
            int tickets = 0;
            for (LocalDate monday = firstMonday; !monday.isAfter(monthEnd); monday = monday.plusDays(7)) {
                int p = 0;
                int f = 0;
                for (int i = 0; i < 5; i++) {
                    String key = c.id() + "|" + monday.plusDays(i);
                    if (present.contains(key)) {
                        p++;
                        if (flags.contains(key)) {
                            f++;
                        }
                    }
                }
                if (p >= WEEK_MIN_PRESENT_DAYS && f >= WEEK_MIN_FLAGS) {
                    tickets++;
                }
            }
            result.put(c.id(), tickets);
        }
        return result;
    }

    private static List<QualityTotal> totalsOf(List<QualityRow> rows, List<YearMonth> months) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<QualityTotal> totals = new ArrayList<>();
        for (String level : List.of(NHA_TRE, MAU_GIAO)) {
            List<QualityRow> group =
                    rows.stream().filter(r -> level.equals(r.level())).toList();
            if (!group.isEmpty()) {
                totals.add(totalOf(level, group, months));
            }
        }
        totals.add(totalOf(TOAN_TRUONG, rows, months));
        return totals;
    }

    private static QualityTotal totalOf(String level, List<QualityRow> rows, List<YearMonth> months) {
        int children = sum(rows, QualityRow::children);
        int meal = sum(rows, QualityRow::mealChildren);
        int diligent = sum(rows, QualityRow::diligentChildren);
        int good = sum(rows, QualityRow::goodChildren);
        int allRound = sum(rows, QualityRow::allRoundChildren);
        Map<String, Integer> byMonth = new LinkedHashMap<>();
        for (YearMonth ym : months) {
            String key = ym.format(MONTH_KEY);
            byMonth.put(key, sumOrNull(rows, r -> r.avgPresentByMonth().get(key)));
        }
        return new QualityTotal(
                level,
                rows.size(),
                children,
                sumOrNull(rows, QualityRow::avgPresent),
                percent(sum(rows, QualityRow::presentDays), sum(rows, QualityRow::possibleDays)),
                meal,
                percent(meal, children),
                diligent,
                percent(diligent, children),
                good,
                percent(good, children),
                allRound,
                percent(allRound, children),
                byMonth);
    }

    private static int sum(List<QualityRow> rows, ToIntFunction<QualityRow> value) {
        return rows.stream().mapToInt(value).sum();
    }

    // Cộng các lớp có số liệu; không lớp nào có thì null
    private static Integer sumOrNull(List<QualityRow> rows, Function<QualityRow, Integer> value) {
        List<Integer> values = rows.stream().map(value).filter(Objects::nonNull).toList();
        return values.isEmpty()
                ? null
                : values.stream().mapToInt(Integer::intValue).sum();
    }

    private static Integer average(int total, int days) {
        return days == 0 ? null : Math.round((float) total / days);
    }

    private static Integer percent(int part, int whole) {
        return whole == 0 ? null : Math.round(part * 100f / whole);
    }
}
