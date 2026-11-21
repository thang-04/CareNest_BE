package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.TicketDtos.IssueTicketsRequest;
import com.carenest.dto.observation.TicketDtos.IssuedTicket;
import com.carenest.dto.observation.TicketDtos.TicketBoardResponse;
import com.carenest.dto.observation.TicketDtos.TicketBoardRow;
import com.carenest.dto.observation.TicketDtos.TicketChild;
import com.carenest.dto.observation.TicketDtos.TicketClassRef;
import com.carenest.dto.observation.TicketDtos.TicketEvidence;
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
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.TicketType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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
    // OBS-10 (Cần chốt 11/11/2026): phiếu tuần chỉ cần ≥3 cờ trong tuần (mỗi ngày tối đa 1 cờ ⇒ có mặt ≥3 ngày);
    // phiếu tháng cần ≥3 phiếu tuần
    static final int WEEK_MIN_FLAGS = 3;
    static final int MONTH_MIN_WEEKLY_TICKETS = 3;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, Integer> RULES = Map.of(
            "WEEK_MIN_FLAGS", WEEK_MIN_FLAGS,
            "MONTH_MIN_WEEKLY_TICKETS", MONTH_MIN_WEEKLY_TICKETS);

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final DailyObservationRepository observationRepository;
    private final BehaviourTicketRepository ticketRepository;
    private final TicketParentNotifier ticketParentNotifier;
    private final com.carenest.repository.child.ChildRepository childRepository;

    /** Số ngày có mặt và số cờ (chỉ ngày có mặt) của từng trẻ trong một tuần. */
    private record WeekStats(Map<Long, Integer> present, Map<Long, Integer> flags) {}

    @Override
    public TicketBoardResponse getBoard(CurrentUser currentUser, Long classId, TicketType type, LocalDate periodStart) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            boolean views = rules.canView(currentUser, cls);
            TicketType t = type == null ? TicketType.WEEKLY : type;
            List<TicketPeriod> periods = endedPeriods(cls, t);
            // Người dạy thay đã hết đợt: chỉ các kỳ có ngày cuối nằm trong đợt dạy thay (như khi phát phiếu)
            if (!views) {
                periods = periods.stream()
                        .filter(p -> rules.canViewDay(currentUser, cls, p.end()))
                        .toList();
                if (periods.isEmpty()) {
                    throw GlobalException.of(ApiCode.TICKET_VIEW_FORBIDDEN);
                }
            }
            TicketPeriod current = periodStart == null
                    ? (periods.isEmpty() ? null : periods.getFirst())
                    : periods.stream()
                            .filter(p -> p.start().equals(periodStart))
                            .findFirst()
                            .orElseThrow(() -> GlobalException.of(
                                    views ? ApiCode.TICKET_PERIOD_INVALID : ApiCode.TICKET_VIEW_FORBIDDEN));
            List<TicketBoardRow> rows = current == null ? List.of() : boardRows(currentUser, cls, t, current);
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
            if (!currentUser.hasPermission(Permission.ASSESSMENT_RECORD)
                    || !currentUser.hasRole(CareNestRole.TEACHER)) {
                throw GlobalException.of(ApiCode.TICKET_FORBIDDEN);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
            }
            TicketPeriod period = checkPeriod(cls, request.type(), request.periodStart());
            // Cần chốt 14/11/2026: xét theo ngày cuối kỳ phát phiếu — GV dạy chính, hoặc người dạy thay ngày đó
            if (!rules.canRecordDay(currentUser, cls, period.end())) {
                throw GlobalException.of(ApiCode.TICKET_FORBIDDEN);
            }
            List<Long> childIds = request.childIds();
            if (childIds == null || childIds.isEmpty()) {
                throw GlobalException.of(ApiCode.TICKET_NO_CHILDREN);
            }
            List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(
                    cls.id(), clamp(period.end(), cls.yearStart(), cls.yearEnd()));
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
            ticketParentNotifier.ticketsIssued(
                    childIds.stream()
                            .collect(Collectors.toMap(
                                    id -> id, id -> byId.get(id).fullName())),
                    request.type(),
                    period.start(),
                    period.end());
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
            // Tổ trưởng chuyên môn xem tổng hợp phiếu các lớp nhóm tuổi mình (AUTH-23)
            if (!rules.canView(currentUser, cls) && !rules.leadsClass(currentUser, cls)) {
                throw GlobalException.of(ApiCode.TICKET_VIEW_FORBIDDEN);
            }
            checkMonth(month, cls.yearStart(), cls.yearEnd());
            LocalDate from = month != null ? month : cls.yearStart();
            LocalDate to = month != null ? month.with(TemporalAdjusters.lastDayOfMonth()) : cls.yearEnd();
            List<ClassChildResponse> children = rosterDuring(cls.id(), from, to, cls.yearStart(), cls.yearEnd());
            List<Long> ids = children.stream().map(ClassChildResponse::id).toList();
            Map<Long, String> childCodes = childRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(com.carenest.entity.child.Child::getId, com.carenest.entity.child.Child::getCode));
            List<BehaviourTicket> allTickets = ids.isEmpty()
                    ? List.of()
                    : ticketRepository
                            .findByChildIdInAndPeriodStartBetween(ids, month != null ? weekMondayFrom(month) : from, to)
                            .stream()
                            .filter(t -> month == null || inMonth(t, month))
                            .toList();
            List<BehaviourTicket> classTickets = allTickets.stream()
                    .filter(t -> t.getClassroomId().equals(cls.id()))
                    .toList();
            Map<Long, Integer> flags = flagCounts(cls.id(), ids, from, minDate(to, rules.today()));
            Set<Long> endMembers = enrollmentService
                    .findChildrenInClassroom(cls.id(), minDate(to, rules.today()))
                    .stream()
                    .map(ClassChildResponse::id)
                    .collect(Collectors.toSet());
            List<TicketSummaryRow> rows = children.stream()
                    .map(c -> {
                        boolean historical = !endMembers.contains(c.id());
                        List<TicketEvidence> evidence =
                                evidenceOf(c.id(), from, minDate(to, rules.today()), month,
                                        historical ? cls.id() : null);
                        // Trẻ đang ở lớp hiện tại được xem tổng phiếu/cờ đã có trong kỳ,
                        // kể cả phần đã tích lũy ở lớp cũ. Lớp cũ chỉ xem số liệu lịch sử của mình.
                        List<BehaviourTicket> ticketScope = historical ? classTickets : allTickets;
                        int flagScope = historical
                                ? flags.getOrDefault(c.id(), 0)
                                : evidence.stream().mapToInt(TicketEvidence::flagCount).sum();
                        return new TicketSummaryRow(
                                new TicketChild(c.id(), c.fullName(), childCodes.get(c.id())),
                                count(ticketScope, c.id(), TicketType.WEEKLY),
                                count(ticketScope, c.id(), TicketType.MONTHLY),
                                flagScope,
                                evidence,
                                historical);
                    })
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
            boolean leader = rules.isLeader(currentUser);
            // Tổ trưởng chuyên môn xem phiếu các lớp nhóm tuổi mình (Cần chốt 04/11)
            Set<Long> ledGroups = leader ? Set.of() : rules.teamLeaderAgeGroupIds(currentUser, schoolYearId);
            if (!leader && ledGroups.isEmpty()) {
                throw GlobalException.of(ApiCode.TICKET_VIEW_FORBIDDEN);
            }
            if (campusId != null && leader && !rules.readsCampus(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            List<ClassroomInfoResponse> scoped = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c ->
                            leader ? rules.readsCampus(currentUser, c.campusId()) : ledGroups.contains(c.ageGroupId()))
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
            Map<Long, List<ClassChildResponse>> rosters = new HashMap<>();
            visible.forEach(c -> rosters.put(c.id(), rosterDuring(c.id(), from, to, yearStart, yearEnd)));
            List<TicketOverviewRow> rows = visible.stream()
                    .map(c -> {
                        List<BehaviourTicket> ts = byClass.getOrDefault(c.id(), List.of());
                        return new TicketOverviewRow(
                                c.id(),
                                c.name(),
                                c.campusId(),
                                rosters.get(c.id()).size(),
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
                    // OBS-23: dòng tổng đếm mỗi trẻ một lần dù trẻ chuyển lớp trong kỳ
                    (int) byClass.values().stream()
                            .flatMap(List::stream)
                            .map(BehaviourTicket::getChildId)
                            .distinct()
                            .count(),
                    (int) rosters.values().stream()
                            .flatMap(List::stream)
                            .map(ClassChildResponse::id)
                            .distinct()
                            .count(),
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

    // Kỳ đã mở phát (hôm nay từ ngày mở phát) trong năm học của lớp, mới nhất trước.
    // Tuần thứ 2–6 tính từ tuần chứa ngày bắt đầu năm; tháng tính từ tháng bắt đầu năm.
    private List<TicketPeriod> endedPeriods(ClassroomInfoResponse cls, TicketType type) {
        LocalDate today = rules.today();
        List<TicketPeriod> list = new ArrayList<>();
        if (type == TicketType.WEEKLY) {
            LocalDate firstMonday = cls.yearStart().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (LocalDate m = firstMonday; !m.isAfter(cls.yearEnd()); m = m.plusWeeks(1)) {
                if (today.isBefore(issueFrom(TicketType.WEEKLY, m, rules::isSchoolDay))) {
                    break;
                }
                list.add(new TicketPeriod(m, m.plusDays(4), (int) ChronoUnit.WEEKS.between(firstMonday, m) + 1));
            }
        } else {
            for (LocalDate m = cls.yearStart().withDayOfMonth(1); !m.isAfter(cls.yearEnd()); m = m.plusMonths(1)) {
                LocalDate end = m.with(TemporalAdjusters.lastDayOfMonth());
                if (today.isBefore(issueFrom(TicketType.MONTHLY, m, rules::isSchoolDay))) {
                    break;
                }
                list.add(new TicketPeriod(m, end, null));
            }
        }
        return list.reversed();
    }

    /**
     * OBS-10 (Cần chốt 18/11/2026): phiếu tuần phát từ thứ Sáu của tuần, phiếu toàn diện từ thứ Sáu cuối cùng của tháng;
     * thứ Sáu là ngày nghỉ thì lùi về ngày học cuối của tuần đó (cờ, phiếu tuần của chính tuần đó vẫn tính).
     */
    static LocalDate issueFrom(TicketType type, LocalDate start, Predicate<LocalDate> isSchoolDay) {
        LocalDate friday = type == TicketType.WEEKLY
                ? start.plusDays(4)
                : start.with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
        LocalDate monday = friday.minusDays(4);
        for (LocalDate d = friday; !d.isBefore(monday); d = d.minusDays(1)) {
            if (isSchoolDay.test(d)) {
                return d;
            }
        }
        return friday;
    }

    // Kỳ hợp lệ để phát: đúng đầu kỳ, trong năm học, đã tới ngày mở phát
    private TicketPeriod checkPeriod(ClassroomInfoResponse cls, TicketType type, LocalDate start) {
        boolean aligned =
                type == TicketType.WEEKLY ? start.getDayOfWeek() == DayOfWeek.MONDAY : start.getDayOfMonth() == 1;
        LocalDate end = type == TicketType.WEEKLY ? start.plusDays(4) : start.with(TemporalAdjusters.lastDayOfMonth());
        if (!aligned || end.isBefore(cls.yearStart()) || start.isAfter(cls.yearEnd())) {
            throw GlobalException.of(ApiCode.TICKET_PERIOD_INVALID);
        }
        LocalDate issueFrom = issueFrom(type, start, rules::isSchoolDay);
        if (rules.today().isBefore(issueFrom)) {
            throw GlobalException.of(ApiCode.TICKET_PERIOD_NOT_ENDED, DMY.format(issueFrom));
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

    private List<TicketBoardRow> boardRows(
            CurrentUser currentUser, ClassroomInfoResponse cls, TicketType type, TicketPeriod period) {
        List<ClassChildResponse> children = childrenOf(cls, period);
        Set<Long> endMembers =
                enrollmentService
                        .findChildrenInClassroom(cls.id(), clamp(period.end(), cls.yearStart(), cls.yearEnd()))
                        .stream()
                        .map(ClassChildResponse::id)
                        .collect(Collectors.toSet());
        List<Long> ids = children.stream().map(ClassChildResponse::id).toList();
        Map<Long, String> childCodes = childRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(com.carenest.entity.child.Child::getId, com.carenest.entity.child.Child::getCode));
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
                            new TicketChild(c.id(), c.fullName(), childCodes.get(c.id())),
                            present,
                            flags,
                            weeklyCount,
                            t == null && eligible(type, present, flags, weeklyCount),
                            t == null ? null : toIssued(t),
                            t != null && !t.getClassroomId().equals(cls.id()),
                            evidenceOf(c.id(), period.start(), period.end(),
                                    type == TicketType.MONTHLY ? period.start() : null,
                                    endMembers.contains(c.id()) ? null : cls.id()),
                            !endMembers.contains(c.id()),
                            currentUser.hasPermission(Permission.ASSESSMENT_RECORD)
                                    && rules.canRecordDay(currentUser, cls, period.end())
                                    && endMembers.contains(c.id()));
                })
                .toList();
    }

    private List<ClassChildResponse> childrenOf(ClassroomInfoResponse cls, TicketPeriod period) {
        return rosterDuring(cls.id(), period.start(), period.end(), cls.yearStart(), cls.yearEnd());
    }

    private Map<Long, BehaviourTicket> ticketsOf(Collection<Long> childIds, TicketType type, LocalDate start) {
        return ticketRepository.findByChildIdInAndPeriodStartBetween(childIds, start, start).stream()
                .filter(t -> t.getType() == type)
                .collect(Collectors.toMap(BehaviourTicket::getChildId, t -> t, (a, b) -> a));
    }

    // Bằng chứng phiếu tuần đi theo trẻ (cả ngày ở lớp cũ khi chuyển lớp giữa tuần, test continuity); số cờ tổng hợp
    // theo lớp thì ở flagCounts
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

    // Trẻ từng học lớp trong tháng/năm đã qua (tới hôm nay): trẻ đã chuyển/nghỉ vẫn còn trong số liệu kỳ cũ
    private List<ClassChildResponse> rosterDuring(
            Long classId, LocalDate from, LocalDate to, LocalDate yearStart, LocalDate yearEnd) {
        LocalDate start = clamp(from, yearStart, yearEnd);
        LocalDate end = clamp(minDate(to, rules.today()), yearStart, yearEnd);
        return end.isBefore(start)
                ? enrollmentService.findChildrenInClassroom(classId, start)
                : enrollmentService.findChildrenInClassroomDuring(classId, start, end);
    }

    /** OBS-10: cờ và phiếu thuộc trẻ, nhưng bảng phải nêu lớp đã ghi nhận khi trẻ chuyển lớp. */
    private List<TicketEvidence> evidenceOf(Long childId, LocalDate from, LocalDate to, LocalDate month, Long historyClassId) {
        if (to.isBefore(from)) {
            return List.of();
        }
        Map<Long, Integer> present = new HashMap<>();
        Map<Long, Integer> flags = new HashMap<>();
        Map<Long, Integer> weeklyTickets = new HashMap<>();
        Set<Long> classroomIds = enrollmentService.findChildStays(childId, from, to).stream()
                .map(EnrollmentService.ChildClassStay::classroomId)
                .filter(id -> historyClassId == null || historyClassId.equals(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!classroomIds.isEmpty()) {
            Set<String> presentDays = new HashSet<>();
            attendanceService.findClassDays(classroomIds, from, to).stream()
                    .filter(d -> d.childId().equals(childId) && d.status() == AttendanceStatus.PRESENT)
                    .forEach(d -> {
                        present.merge(d.classroomId(), 1, Integer::sum);
                        presentDays.add(d.classroomId() + "|" + d.date());
                    });
            observationRepository.findByChildIdInAndDateBetween(List.of(childId), from, to).stream()
                    .filter(o -> classroomIds.contains(o.getClassroomId())
                            && Boolean.TRUE.equals(o.getFlag())
                            && presentDays.contains(o.getClassroomId() + "|" + o.getDate()))
                    .forEach(o -> flags.merge(o.getClassroomId(), 1, Integer::sum));
        }
        LocalDate ticketFrom = month != null ? weekMondayFrom(month) : from;
        LocalDate ticketTo = month != null ? weekMondayTo(month) : to;
        ticketRepository.findByChildIdInAndPeriodStartBetween(List.of(childId), ticketFrom, ticketTo).stream()
                .filter(t -> t.getType() == TicketType.WEEKLY)
                .filter(t -> month == null || inMonth(t, month))
                .filter(t -> historyClassId == null || historyClassId.equals(t.getClassroomId()))
                .forEach(t -> {
                    classroomIds.add(t.getClassroomId());
                    weeklyTickets.merge(t.getClassroomId(), 1, Integer::sum);
                });
        return classroomIds.stream()
                .map(classId -> new TicketEvidence(
                        classId,
                        classroomService.findClassroomInfo(classId).map(ClassroomInfoResponse::name).orElse("Lớp đã lưu"),
                        present.getOrDefault(classId, 0),
                        flags.getOrDefault(classId, 0),
                        weeklyTickets.getOrDefault(classId, 0)))
                .sorted(Comparator.comparing(TicketEvidence::className))
                .toList();
    }

    // Cờ của các ngày trẻ có mặt trong khoảng
    private Map<Long, Integer> flagCounts(Long classId, List<Long> childIds, LocalDate from, LocalDate to) {
        if (childIds.isEmpty() || to.isBefore(from)) {
            return Map.of();
        }
        Set<String> present = attendanceService.findClassDays(List.of(classId), from, to).stream()
                .filter(d -> childIds.contains(d.childId()) && d.status() == AttendanceStatus.PRESENT)
                .map(d -> d.childId() + "|" + d.date())
                .collect(Collectors.toSet());
        Map<Long, Integer> flags = new LinkedHashMap<>();
        observationRepository.findByChildIdInAndDateBetween(childIds, from, to).stream()
                .filter(o -> classId.equals(o.getClassroomId())
                        && Boolean.TRUE.equals(o.getFlag())
                        && present.contains(o.getChildId() + "|" + o.getDate()))
                .forEach(o -> flags.merge(o.getChildId(), 1, Integer::sum));
        return flags;
    }

    // Phiếu Bé ngoan đã phát của các tuần có thứ Sáu trong tháng
    private Map<Long, Integer> weeklyTicketCounts(Collection<Long> childIds, LocalDate month) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        ticketRepository
                .findByChildIdInAndPeriodStartBetween(childIds, weekMondayFrom(month), weekMondayTo(month))
                .stream()
                .filter(t -> t.getType() == TicketType.WEEKLY)
                .forEach(t -> result.merge(t.getChildId(), 1, Integer::sum));
        return result;
    }

    // OBS-10 (Cần chốt 18/11/2026): tuần thuộc tháng chứa thứ Sáu của tuần ⇒ thứ Hai từ ngày 1 − 4 tới ngày cuối − 4
    static LocalDate weekMondayFrom(LocalDate month) {
        return month.withDayOfMonth(1).minusDays(4);
    }

    static LocalDate weekMondayTo(LocalDate month) {
        return month.with(TemporalAdjusters.lastDayOfMonth()).minusDays(4);
    }

    static boolean inMonth(BehaviourTicket ticket, LocalDate month) {
        LocalDate start = ticket.getPeriodStart();
        return ticket.getType() == TicketType.WEEKLY
                ? !start.isBefore(weekMondayFrom(month)) && !start.isAfter(weekMondayTo(month))
                : start.equals(month.withDayOfMonth(1));
    }

    static boolean eligible(TicketType type, Integer present, Integer flags, Integer weeklyCount) {
        return type == TicketType.WEEKLY
                ? flags != null && flags >= WEEK_MIN_FLAGS
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
