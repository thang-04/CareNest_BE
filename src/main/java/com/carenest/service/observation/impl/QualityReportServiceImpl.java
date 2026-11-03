package com.carenest.service.observation.impl;

import com.carenest.dto.observation.QualityReportResponse;
import com.carenest.dto.observation.QualityReportResponse.QualityRow;
import com.carenest.dto.observation.QualityReportResponse.QualityTotal;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassChildDay;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.QualityReportService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.TicketType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    // OBS-23: Bé chăm = đi học từ 90% số ngày phải đi của trẻ (ngày học của lớp trong thời gian trẻ thuộc lớp)
    private static final int DILIGENT_PERCENT = 90;
    private static final String NHA_TRE = "NHA_TRE";
    private static final String MAU_GIAO = "MAU_GIAO";
    private static final String TOAN_TRUONG = "TOAN_TRUONG";

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final AgeGroupService ageGroupService;
    private final BehaviourTicketRepository ticketRepository;

    @Override
    public QualityReportResponse getReport(CurrentUser currentUser, Long schoolYearId, YearMonth month, Long campusId) {
        String logPrefix =
                LOG_PREFIX + "|getReport|userId=" + currentUser.userId() + "|month=" + month + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            boolean leader = rules.isLeader(currentUser);
            if (!leader && !currentUser.hasRole(CareNestRole.TEACHER)) {
                throw GlobalException.of(ApiCode.QUALITY_REPORT_FORBIDDEN);
            }
            if (campusId != null && !rules.readsCampus(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            LocalDate today = rules.today();
            // HT, PHT mọi điểm trường (AUTH-20); GV lớp mình dạy, tổ trưởng thêm các lớp nhóm tuổi mình (Cần chốt
            // 03/11)
            Set<Long> ledGroups = leader ? Set.of() : rules.teamLeaderAgeGroupIds(currentUser);
            List<ClassroomInfoResponse> scoped = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> leader
                            ? rules.readsCampus(currentUser, c.campusId())
                            : rules.canRecord(currentUser, c)
                                    || rules.taughtClosedYear(currentUser, c)
                                    || ledGroups.contains(c.ageGroupId()))
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
            // Mặc định tháng đã kết thúc gần nhất (tháng đang dở còn thiếu số liệu); đầu năm học thì tháng đầu
            boolean lastOpen = !dataEnd.isBefore(yearStart) && dataEnd.isBefore(lastMonth.atEndOfMonth());
            YearMonth m = month != null
                    ? month
                    : lastOpen && lastMonth.isAfter(firstMonth) ? lastMonth.minusMonths(1) : lastMonth;
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
            Map<Long, List<BehaviourTicket>> ticketsByClass = visible.isEmpty()
                    ? Map.of()
                    : ticketRepository
                            .findByClassroomIdInAndPeriodStartBetween(
                                    visible.stream()
                                            .map(ClassroomInfoResponse::id)
                                            .toList(),
                                    m.atDay(1),
                                    m.atEndOfMonth())
                            .stream()
                            .collect(Collectors.groupingBy(BehaviourTicket::getClassroomId));
            List<ClassResult> results = visible.stream()
                    .map(c -> rowOf(
                            c,
                            m,
                            months,
                            days.getOrDefault(c.id(), List.of()),
                            ticketsByClass.getOrDefault(c.id(), List.of()),
                            today,
                            ageFrom))
                    .toList();
            List<QualityRow> rows = results.stream().map(ClassResult::row).toList();
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
                    // Dòng tổng khối / toàn trường chỉ cho BGH
                    leader ? totalsOf(results, months) : List.of());
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

    /** Dòng của lớp kèm id trẻ theo từng chỉ số, để dòng tổng đếm mỗi trẻ một lần (trẻ chuyển lớp trong tháng). */
    private record ClassResult(
            QualityRow row,
            Set<Long> children,
            Set<Long> meal,
            Set<Long> diligent,
            Set<Long> good,
            Set<Long> allRound) {}

    // Ngày học của lớp = thứ 2–6 có điểm danh; trẻ ra lớp bình quân = tổng lượt có mặt / số ngày học (làm tròn).
    // OBS-23: trẻ vào/nghỉ trong tháng vẫn thuộc danh sách tháng đó; ngày phải đi chỉ tính lúc trẻ thuộc lớp
    private ClassResult rowOf(
            ClassroomInfoResponse cls,
            YearMonth month,
            List<YearMonth> months,
            List<ClassChildDay> classDays,
            List<BehaviourTicket> tickets,
            LocalDate today,
            Map<Long, Integer> ageFrom) {
        Map<YearMonth, Set<LocalDate>> schoolDays = new HashMap<>();
        Map<YearMonth, Integer> presentByMonth = new HashMap<>();
        Map<Long, Integer> childPresent = new HashMap<>();
        Set<Long> lunch = new HashSet<>();
        for (ClassChildDay d : classDays) {
            YearMonth ym = YearMonth.from(d.date());
            schoolDays.computeIfAbsent(ym, k -> new TreeSet<>()).add(d.date());
            if (d.status() != AttendanceStatus.PRESENT) {
                continue;
            }
            presentByMonth.merge(ym, 1, Integer::sum);
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
        LocalDate rosterFrom = month.atDay(1).isBefore(cls.yearStart()) ? cls.yearStart() : month.atDay(1);
        if (anchor.isAfter(cls.yearEnd())) {
            anchor = cls.yearEnd();
        }
        List<EnrollmentService.ClassStay> stays = anchor.isBefore(rosterFrom)
                ? List.of()
                : enrollmentService.findStaysInClassroom(cls.id(), rosterFrom, anchor);
        Set<Long> children = stays.stream()
                .map(EnrollmentService.ClassStay::childId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<LocalDate> monthDays = schoolDays.getOrDefault(month, Set.of());
        int sd = monthDays.size();
        // Ngày phải đi của từng trẻ = ngày học của lớp trong tháng mà trẻ đang thuộc lớp
        Map<Long, Integer> expected = new HashMap<>();
        for (LocalDate day : monthDays) {
            stays.stream()
                    .filter(st -> st.covers(day))
                    .map(EnrollmentService.ClassStay::childId)
                    .distinct()
                    .forEach(id -> expected.merge(id, 1, Integer::sum));
        }
        int presentDays = children.stream()
                .mapToInt(id -> childPresent.getOrDefault(id, 0))
                .sum();
        int possibleDays =
                children.stream().mapToInt(id -> expected.getOrDefault(id, 0)).sum();
        Set<Long> meal = children.stream().filter(lunch::contains).collect(Collectors.toSet());
        Set<Long> diligent = children.stream()
                .filter(id -> expected.getOrDefault(id, 0) > 0
                        && childPresent.getOrDefault(id, 0) * 100 >= DILIGENT_PERCENT * expected.get(id))
                .collect(Collectors.toSet());
        // OBS-23: Bé ngoan = trẻ được GV phát phiếu tuần trong tháng; Bé ngoan toàn diện = trẻ được phát phiếu tháng
        Set<Long> good = issuedTo(tickets, TicketType.WEEKLY, children);
        Set<Long> allRound = issuedTo(tickets, TicketType.MONTHLY, children);
        Integer from = cls.ageGroupId() == null ? null : ageFrom.get(cls.ageGroupId());
        int n = children.size();
        QualityRow row = new QualityRow(
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
                meal.size(),
                percent(meal.size(), n),
                diligent.size(),
                percent(diligent.size(), n),
                good.size(),
                percent(good.size(), n),
                allRound.size(),
                percent(allRound.size(), n),
                avgByMonth);
        return new ClassResult(row, children, meal, diligent, good, allRound);
    }

    private static Set<Long> issuedTo(List<BehaviourTicket> tickets, TicketType type, Set<Long> children) {
        return tickets.stream()
                .filter(t -> t.getType() == type && children.contains(t.getChildId()))
                .map(BehaviourTicket::getChildId)
                .collect(Collectors.toSet());
    }

    private static List<QualityTotal> totalsOf(List<ClassResult> results, List<YearMonth> months) {
        if (results.isEmpty()) {
            return List.of();
        }
        List<QualityTotal> totals = new ArrayList<>();
        for (String level : List.of(NHA_TRE, MAU_GIAO)) {
            List<ClassResult> group =
                    results.stream().filter(r -> level.equals(r.row().level())).toList();
            if (!group.isEmpty()) {
                totals.add(totalOf(level, group, months));
            }
        }
        totals.add(totalOf(TOAN_TRUONG, results, months));
        return totals;
    }

    // Số trẻ của dòng tổng đếm mỗi trẻ một lần dù trẻ chuyển lớp trong tháng; lượt có mặt vẫn cộng theo lớp
    private static QualityTotal totalOf(String level, List<ClassResult> results, List<YearMonth> months) {
        List<QualityRow> rows = results.stream().map(ClassResult::row).toList();
        int children = distinct(results, ClassResult::children);
        int meal = distinct(results, ClassResult::meal);
        int diligent = distinct(results, ClassResult::diligent);
        int good = distinct(results, ClassResult::good);
        int allRound = distinct(results, ClassResult::allRound);
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

    private static int distinct(List<ClassResult> results, Function<ClassResult, Set<Long>> ids) {
        return (int)
                results.stream().flatMap(r -> ids.apply(r).stream()).distinct().count();
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
