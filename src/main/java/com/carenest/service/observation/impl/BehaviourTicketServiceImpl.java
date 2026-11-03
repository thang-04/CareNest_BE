package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.TicketDtos.IssueTicketsRequest;
import com.carenest.dto.observation.TicketDtos.IssuedTicket;
import com.carenest.dto.observation.TicketDtos.TicketBoardResponse;
import com.carenest.dto.observation.TicketDtos.TicketBoardRow;
import com.carenest.dto.observation.TicketDtos.TicketChild;
import com.carenest.dto.observation.TicketDtos.TicketClassRef;
import com.carenest.dto.observation.TicketDtos.TicketOverviewResponse;
import com.carenest.dto.observation.TicketDtos.TicketOverviewRow;
import com.carenest.dto.observation.TicketDtos.TicketOverviewTotals;
import com.carenest.dto.observation.TicketDtos.TicketPeriod;
import com.carenest.dto.observation.TicketDtos.TicketSummaryResponse;
import com.carenest.dto.observation.TicketDtos.TicketSummaryRow;
import com.carenest.dto.observation.TicketDtos.TicketSummaryTotals;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.BehaviourTicketService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.TicketType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BehaviourTicketServiceImpl implements BehaviourTicketService {

    private static final String LOG_PREFIX = "[BehaviourTicketServiceImpl]";
    // Công thức phiếu đang có (FE TICKET_RULES), Cần giữ nguyên 30/10/2026
    static final int WEEK_MIN_PRESENT_DAYS = 4;
    static final int WEEK_MIN_FLAGS = 3;
    static final int MONTH_MIN_WEEKLY_TICKETS = 3;
    private static final Map<String, Integer> RULES = Map.of(
            "WEEK_MIN_PRESENT_DAYS", WEEK_MIN_PRESENT_DAYS,
            "WEEK_MIN_FLAGS", WEEK_MIN_FLAGS,
            "MONTH_MIN_WEEKLY_TICKETS", MONTH_MIN_WEEKLY_TICKETS);

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final DailyObservationRepository observationRepository;
    private final BehaviourTicketRepository ticketRepository;

    /** Số ngày có mặt và số cờ (chỉ ngày có mặt) của từng trẻ trong một tuần. */
    private record WeekStats(Map<Long, Integer> present, Map<Long, Integer> flags) {}

    @Override
    public TicketBoardResponse getBoard(CurrentUser currentUser, Long classId, TicketType type, LocalDate periodStart) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canView(currentUser, cls)) {
                throw GlobalException.of(ApiCode.TICKET_VIEW_FORBIDDEN);
            }
            TicketType t = type == null ? TicketType.WEEKLY : type;
            List<TicketPeriod> periods = endedPeriods(cls, t);
            TicketPeriod current = periodStart == null
                    ? (periods.isEmpty() ? null : periods.getFirst())
                    : periods.stream()
                            .filter(p -> p.start().equals(periodStart))
                            .findFirst()
                            .orElseThrow(() -> GlobalException.of(ApiCode.TICKET_PERIOD_INVALID));
            List<TicketBoardRow> rows = current == null ? List.of() : boardRows(cls, t, current);
            log.info("{}|rows={}|END", logPrefix, rows.size());
            return new TicketBoardResponse(new TicketClassRef(cls.id(), cls.name()), t, periods, current, rows, RULES);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public List<IssuedTicket> issue(CurrentUser currentUser, IssueTicketsRequest request) {
        String logPrefix = LOG_PREFIX + "|issue|userId=" + currentUser.userId() + "|classId=" + request.classId()
                + "|type=" + request.type();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(request.classId());
            if (!rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.TICKET_FORBIDDEN);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
            }
            TicketPeriod period = checkPeriod(cls, request.type(), request.periodStart());
            List<Long> childIds = request.childIds();
            if (childIds == null || childIds.isEmpty()) {
                throw GlobalException.of(ApiCode.TICKET_NO_CHILDREN);
            }
            List<ClassChildResponse> children = childrenOf(cls, period);
            if (new HashSet<>(childIds).size() != childIds.size() || childIds.size() > children.size()) {
                throw GlobalException.of(ApiCode.TICKET_TOO_MANY_CHILDREN);
            }
            Map<Long, ClassChildResponse> byId =
                    children.stream().collect(Collectors.toMap(ClassChildResponse::id, c -> c));
            if (!byId.keySet().containsAll(childIds)) {
                throw GlobalException.of(ApiCode.TICKET_CHILD_NOT_IN_CLASS);
            }
            Map<Long, BehaviourTicket> existing = ticketsOf(childIds, request.type(), period.start());
            WeekStats week = request.type() == TicketType.WEEKLY ? weekStats(childIds, period) : null;
            Map<Long, Integer> weekly =
                    request.type() == TicketType.MONTHLY ? weeklyTicketCounts(childIds, period.start()) : Map.of();
            List<BehaviourTicket> toSave = new ArrayList<>();
            for (Long childId : childIds) {
                ClassChildResponse child = byId.get(childId);
                if (existing.containsKey(childId)) {
                    throw GlobalException.of(ApiCode.TICKET_ALREADY_ISSUED, child.fullName());
                }
                Integer present = week == null ? null : week.present().getOrDefault(childId, 0);
                Integer flags = week == null ? null : week.flags().getOrDefault(childId, 0);
                Integer weeklyCount = week == null ? weekly.getOrDefault(childId, 0) : null;
                if (!eligible(request.type(), present, flags, weeklyCount)) {
                    throw GlobalException.of(ApiCode.TICKET_NOT_ELIGIBLE, child.fullName(), label(request.type()));
                }
                toSave.add(BehaviourTicket.issue(
                        childId,
                        cls.id(),
                        cls.campusId(),
                        cls.schoolYearId(),
                        request.type(),
                        period.start(),
                        period.end(),
                        present,
                        flags,
                        weeklyCount,
                        currentUser.userId(),
                        rules.now()));
            }
            List<BehaviourTicket> saved;
            try {
                saved = ticketRepository.saveAllAndFlush(toSave);
            } catch (DataIntegrityViolationException ex) {
                // Hai cô cùng phát một lúc: phiếu đã có
                throw GlobalException.of(ApiCode.TICKET_ALREADY_ISSUED, "Trẻ");
            }
            log.info("{}|issued={}|END", logPrefix, saved.size());
            return saved.stream().map(BehaviourTicketServiceImpl::toIssued).toList();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public TicketSummaryResponse getSummary(CurrentUser currentUser, Long classId, LocalDate month) {
        String logPrefix = LOG_PREFIX + "|getSummary|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canView(currentUser, cls)) {
                throw GlobalException.of(ApiCode.TICKET_VIEW_FORBIDDEN);
            }
            checkMonth(month, cls.yearStart(), cls.yearEnd());
            LocalDate from = month != null ? month : cls.yearStart();
            LocalDate to = month != null ? month.with(TemporalAdjusters.lastDayOfMonth()) : cls.yearEnd();
            LocalDate anchor = clamp(rules.today(), cls.yearStart(), cls.yearEnd());
            List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(cls.id(), anchor);
            List<Long> ids = children.stream().map(ClassChildResponse::id).toList();
            List<BehaviourTicket> tickets = ids.isEmpty()
                    ? List.of()
                    : ticketRepository.findByChildIdInAndPeriodStartBetween(ids, from, to).stream()
                            .filter(t -> t.getClassroomId().equals(cls.id()))
                            .toList();
            Map<Long, Integer> flags = flagCounts(ids, from, minDate(to, rules.today()));
            List<TicketSummaryRow> rows = children.stream()
                    .map(c -> new TicketSummaryRow(
                            new TicketChild(c.id(), c.fullName()),
                            count(tickets, c.id(), TicketType.WEEKLY),
                            count(tickets, c.id(), TicketType.MONTHLY),
                            flags.getOrDefault(c.id(), 0)))
                    .toList();
            TicketSummaryTotals totals = new TicketSummaryTotals(
                    rows.stream().mapToInt(TicketSummaryRow::weekly).sum(),
                    rows.stream().mapToInt(TicketSummaryRow::monthly).sum(),
                    (int) rows.stream()
                            .filter(r -> r.weekly() + r.monthly() > 0)
                            .count(),
                    rows.size());
            log.info("{}|children={}|END", logPrefix, rows.size());
            return new TicketSummaryResponse(
                    new TicketClassRef(cls.id(), cls.name()),
                    month,
                    pastMonths(cls.yearStart(), cls.yearEnd()),
                    rows,
                    totals);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public TicketOverviewResponse getOverview(
            CurrentUser currentUser, Long schoolYearId, LocalDate month, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getOverview|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (!rules.isLeader(currentUser)) {
                throw GlobalException.of(ApiCode.TICKET_VIEW_FORBIDDEN);
            }
            if (campusId != null && !rules.leadsCampus(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            List<ClassroomInfoResponse> scoped = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> rules.leadsCampus(currentUser, c.campusId()))
                    .sorted(Comparator.comparing(ClassroomInfoResponse::campusId)
                            .thenComparing(ClassroomInfoResponse::name))
                    .toList();
            if (scoped.isEmpty()) {
                log.info("{}|classes=0|END", logPrefix);
                return new TicketOverviewResponse(
                        month, List.of(), List.of(), List.of(), new TicketOverviewTotals(0, 0, 0, 0, 0));
            }
            LocalDate yearStart = scoped.getFirst().yearStart();
            LocalDate yearEnd = scoped.getFirst().yearEnd();
            checkMonth(month, yearStart, yearEnd);
            LocalDate from = month != null ? month : yearStart;
            LocalDate to = month != null ? month.with(TemporalAdjusters.lastDayOfMonth()) : yearEnd;
            List<ClassroomInfoResponse> visible = scoped.stream()
                    .filter(c -> campusId == null || campusId.equals(c.campusId()))
                    .toList();
            Map<Long, List<BehaviourTicket>> byClass = visible.isEmpty()
                    ? Map.of()
                    : ticketRepository
                            .findByClassroomIdInAndPeriodStartBetween(
                                    visible.stream()
                                            .map(ClassroomInfoResponse::id)
                                            .toList(),
                                    from,
                                    to)
                            .stream()
                            .collect(Collectors.groupingBy(BehaviourTicket::getClassroomId));
            LocalDate anchor = clamp(rules.today(), yearStart, yearEnd);
            List<TicketOverviewRow> rows = visible.stream()
                    .map(c -> {
                        List<BehaviourTicket> ts = byClass.getOrDefault(c.id(), List.of());
                        return new TicketOverviewRow(
                                c.id(),
                                c.name(),
                                c.campusId(),
                                enrollmentService
                                        .findChildrenInClassroom(c.id(), anchor)
                                        .size(),
                                (int) ts.stream()
                                        .filter(t -> t.getType() == TicketType.WEEKLY)
                                        .count(),
                                (int) ts.stream()
                                        .filter(t -> t.getType() == TicketType.MONTHLY)
                                        .count(),
                                (int) ts.stream()
                                        .map(BehaviourTicket::getChildId)
                                        .distinct()
                                        .count());
                    })
                    .toList();
            TicketOverviewTotals totals = new TicketOverviewTotals(
                    rows.stream().mapToInt(TicketOverviewRow::weekly).sum(),
                    rows.stream().mapToInt(TicketOverviewRow::monthly).sum(),
                    rows.stream().mapToInt(TicketOverviewRow::withTicket).sum(),
                    rows.stream().mapToInt(TicketOverviewRow::children).sum(),
                    rows.size());
            log.info("{}|classes={}|END", logPrefix, rows.size());
            return new TicketOverviewResponse(
                    month,
                    pastMonths(yearStart, yearEnd),
                    scoped.stream()
                            .map(ClassroomInfoResponse::campusId)
                            .distinct()
                            .toList(),
                    rows,
                    totals);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // ---------- kỳ ----------

    // Kỳ đã kết thúc (ngày cuối trước hôm nay) trong năm học của lớp, mới nhất trước.
    // Tuần thứ 2–6 tính từ tuần chứa ngày bắt đầu năm; tháng tính từ tháng bắt đầu năm.
    private List<TicketPeriod> endedPeriods(ClassroomInfoResponse cls, TicketType type) {
        LocalDate today = rules.today();
        List<TicketPeriod> list = new ArrayList<>();
        if (type == TicketType.WEEKLY) {
            LocalDate firstMonday = cls.yearStart().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (LocalDate m = firstMonday; !m.isAfter(cls.yearEnd()); m = m.plusWeeks(1)) {
                if (!m.plusDays(4).isBefore(today)) {
                    break;
                }
                list.add(new TicketPeriod(m, m.plusDays(4), (int) ChronoUnit.WEEKS.between(firstMonday, m) + 1));
            }
        } else {
            for (LocalDate m = cls.yearStart().withDayOfMonth(1); !m.isAfter(cls.yearEnd()); m = m.plusMonths(1)) {
                LocalDate end = m.with(TemporalAdjusters.lastDayOfMonth());
                if (!end.isBefore(today)) {
                    break;
                }
                list.add(new TicketPeriod(m, end, null));
            }
        }
        return list.reversed();
    }

    // Kỳ hợp lệ để phát: đúng đầu kỳ, trong năm học, đã kết thúc
    private TicketPeriod checkPeriod(ClassroomInfoResponse cls, TicketType type, LocalDate start) {
        boolean aligned =
                type == TicketType.WEEKLY ? start.getDayOfWeek() == DayOfWeek.MONDAY : start.getDayOfMonth() == 1;
        LocalDate end = type == TicketType.WEEKLY ? start.plusDays(4) : start.with(TemporalAdjusters.lastDayOfMonth());
        if (!aligned || end.isBefore(cls.yearStart()) || start.isAfter(cls.yearEnd())) {
            throw GlobalException.of(ApiCode.TICKET_PERIOD_INVALID);
        }
        if (!end.isBefore(rules.today())) {
            throw GlobalException.of(ApiCode.TICKET_PERIOD_NOT_ENDED, end);
        }
        return endedPeriods(cls, type).stream()
                .filter(p -> p.start().equals(start))
                .findFirst()
                .orElseThrow(() -> GlobalException.of(ApiCode.TICKET_PERIOD_INVALID));
    }

    // Tháng xem: ngày 1, trong năm học, không ở tương lai
    private void checkMonth(LocalDate month, LocalDate yearStart, LocalDate yearEnd) {
        if (month == null) {
            return;
        }
        if (month.getDayOfMonth() != 1
                || month.isAfter(yearEnd)
                || month.with(TemporalAdjusters.lastDayOfMonth()).isBefore(yearStart)
                || month.isAfter(rules.today())) {
            throw GlobalException.of(ApiCode.TICKET_PERIOD_INVALID);
        }
    }

    private List<LocalDate> pastMonths(LocalDate yearStart, LocalDate yearEnd) {
        LocalDate last = minDate(rules.today(), yearEnd);
        List<LocalDate> months = new ArrayList<>();
        for (LocalDate m = yearStart.withDayOfMonth(1); !m.isAfter(last); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months.reversed();
    }

    // ---------- số liệu ----------

    private List<TicketBoardRow> boardRows(ClassroomInfoResponse cls, TicketType type, TicketPeriod period) {
        List<ClassChildResponse> children = childrenOf(cls, period);
        List<Long> ids = children.stream().map(ClassChildResponse::id).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, BehaviourTicket> issued = ticketsOf(ids, type, period.start());
        WeekStats week = type == TicketType.WEEKLY ? weekStats(ids, period) : null;
        Map<Long, Integer> weekly = type == TicketType.MONTHLY ? weeklyTicketCounts(ids, period.start()) : Map.of();
        return children.stream()
                .map(c -> {
                    Integer present = week == null ? null : week.present().getOrDefault(c.id(), 0);
                    Integer flags = week == null ? null : week.flags().getOrDefault(c.id(), 0);
                    Integer weeklyCount = week == null ? weekly.getOrDefault(c.id(), 0) : null;
                    BehaviourTicket t = issued.get(c.id());
                    return new TicketBoardRow(
                            new TicketChild(c.id(), c.fullName()),
                            present,
                            flags,
                            weeklyCount,
                            eligible(type, present, flags, weeklyCount),
                            t == null ? null : toIssued(t));
                })
                .toList();
    }

    private List<ClassChildResponse> childrenOf(ClassroomInfoResponse cls, TicketPeriod period) {
        return enrollmentService.findChildrenInClassroom(cls.id(), clamp(period.end(), cls.yearStart(), cls.yearEnd()));
    }

    private Map<Long, BehaviourTicket> ticketsOf(Collection<Long> childIds, TicketType type, LocalDate start) {
        return ticketRepository.findByChildIdInAndPeriodStartBetween(childIds, start, start).stream()
                .filter(t -> t.getType() == type)
                .collect(Collectors.toMap(BehaviourTicket::getChildId, t -> t, (a, b) -> a));
    }

    private WeekStats weekStats(Collection<Long> childIds, TicketPeriod period) {
        Set<String> present = attendanceService.findDayStatuses(childIds, period.start(), period.end()).stream()
                .filter(s -> s.status() == AttendanceStatus.PRESENT)
                .map(s -> s.childId() + "|" + s.date())
                .collect(Collectors.toSet());
        Map<Long, Integer> presentDays = new LinkedHashMap<>();
        present.forEach(k -> presentDays.merge(Long.valueOf(k.substring(0, k.indexOf('|'))), 1, Integer::sum));
        Map<Long, Integer> flags = new LinkedHashMap<>();
        observationRepository.findByChildIdInAndDateBetween(childIds, period.start(), period.end()).stream()
                .filter(o -> Boolean.TRUE.equals(o.getFlag()) && present.contains(o.getChildId() + "|" + o.getDate()))
                .forEach(o -> flags.merge(o.getChildId(), 1, Integer::sum));
        return new WeekStats(presentDays, flags);
    }

    // Cờ của các ngày trẻ có mặt trong khoảng
    private Map<Long, Integer> flagCounts(List<Long> childIds, LocalDate from, LocalDate to) {
        if (childIds.isEmpty() || to.isBefore(from)) {
            return Map.of();
        }
        return weekStats(childIds, new TicketPeriod(from, to, null)).flags();
    }

    // Phiếu Bé ngoan đã phát của các tuần có thứ Hai trong tháng
    private Map<Long, Integer> weeklyTicketCounts(Collection<Long> childIds, LocalDate month) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        ticketRepository
                .findByChildIdInAndPeriodStartBetween(childIds, month, month.with(TemporalAdjusters.lastDayOfMonth()))
                .stream()
                .filter(t -> t.getType() == TicketType.WEEKLY)
                .forEach(t -> result.merge(t.getChildId(), 1, Integer::sum));
        return result;
    }

    private static boolean eligible(TicketType type, Integer present, Integer flags, Integer weeklyCount) {
        return type == TicketType.WEEKLY
                ? present != null && flags != null && present >= WEEK_MIN_PRESENT_DAYS && flags >= WEEK_MIN_FLAGS
                : weeklyCount != null && weeklyCount >= MONTH_MIN_WEEKLY_TICKETS;
    }

    private static int count(List<BehaviourTicket> tickets, Long childId, TicketType type) {
        return (int) tickets.stream()
                .filter(t -> t.getChildId().equals(childId) && t.getType() == type)
                .count();
    }

    private static String label(TicketType type) {
        return type == TicketType.WEEKLY ? "Bé ngoan" : "Bé ngoan toàn diện";
    }

    private static IssuedTicket toIssued(BehaviourTicket t) {
        return new IssuedTicket(
                t.getId(),
                t.getType(),
                t.getChildId(),
                t.getPeriodStart(),
                t.getPeriodEnd(),
                t.getPresentDays(),
                t.getFlagCount(),
                t.getWeeklyTickets(),
                t.getIssuedBy(),
                t.getIssuedAt());
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate clamp(LocalDate d, LocalDate from, LocalDate to) {
        return d.isBefore(from) ? from : d.isAfter(to) ? to : d;
    }
}
