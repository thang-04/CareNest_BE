package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.EvaluationDtos.ChildRef;
import com.carenest.dto.observation.EvaluationDtos.ClassRef;
import com.carenest.dto.observation.EvaluationDtos.DateRange;
import com.carenest.dto.observation.EvaluationDtos.DevelopmentChildRow;
import com.carenest.dto.observation.EvaluationDtos.DevelopmentResponse;
import com.carenest.dto.observation.EvaluationDtos.DevelopmentWeek;
import com.carenest.dto.observation.EvaluationDtos.Draft;
import com.carenest.dto.observation.EvaluationDtos.EvaluationDay;
import com.carenest.dto.observation.EvaluationDtos.EvaluationDetailResponse;
import com.carenest.dto.observation.EvaluationDtos.EvaluationRow;
import com.carenest.dto.observation.EvaluationDtos.EventRow;
import com.carenest.dto.observation.EvaluationDtos.ProgramCompletionResponse;
import com.carenest.dto.observation.EvaluationDtos.ProgramRow;
import com.carenest.dto.observation.EvaluationDtos.ProgramTotals;
import com.carenest.dto.observation.EvaluationDtos.SummaryInfo;
import com.carenest.dto.observation.EvaluationDtos.TicketRef;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.entity.observation.ChildEvaluationEvent;
import com.carenest.entity.observation.DailyObservation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.repository.observation.ChildEvaluationEventRepository;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.ChildEvaluationService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EvaluationAction;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.EvaluationStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import java.text.Collator;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChildEvaluationServiceImpl implements ChildEvaluationService {

    private static final String LOG_PREFIX = "[ChildEvaluationServiceImpl]";
    // Như FE validateEvaluationContent
    static final int CONTENT_MIN = 30;
    static final int CONTENT_MAX = 3000;
    // Lớp 5–6 tuổi: nhóm tuổi từ 60 tháng (sổ chỉ đạo chuyên môn mục 8)
    static final int PROGRAM_AGE_FROM_MONTHS = 60;
    // Hồ sơ phát triển: tối đa hơn một năm học
    static final int DEVELOPMENT_MAX_DAYS = 400;
    private static final DateTimeFormatter MONTH_TEXT = DateTimeFormatter.ofPattern("MM/yyyy");

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final AgeGroupService ageGroupService;
    private final EnrollmentService enrollmentService;
    private final AccountService accountService;
    private final EvaluationSources sources;
    private final ChildEvaluationRepository evaluationRepository;
    private final ChildEvaluationEventRepository eventRepository;
    private final DailyObservationRepository observationRepository;
    private final BehaviourTicketRepository ticketRepository;

    @Override
    @Transactional
    public List<EvaluationRow> list(
            CurrentUser currentUser,
            EvaluationPeriodType periodType,
            Long schoolYearId,
            Long classId,
            LocalDate month) {
        EvaluationPeriodType type = periodType == null ? EvaluationPeriodType.MONTH : periodType;
        String logPrefix =
                LOG_PREFIX + "|list|userId=" + currentUser.userId() + "|type=" + type + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            List<ClassroomInfoResponse> years = classroomService.findYearClassrooms(schoolYearId);
            List<ClassroomInfoResponse> visible = years.stream()
                    .filter(c -> teaches(currentUser, c) || rules.leadsCampus(currentUser, c.campusId()))
                    .filter(c -> classId == null || classId.equals(c.id()))
                    .toList();
            if (classId != null && visible.isEmpty()) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            if (month != null && !years.isEmpty()) {
                checkMonth(month, years.getFirst());
            }
            for (ClassroomInfoResponse cls : visible) {
                if (cls.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecord(currentUser, cls)) {
                    ensureDrafts(cls, type);
                }
            }
            Map<Long, ClassroomInfoResponse> byId =
                    visible.stream().collect(Collectors.toMap(ClassroomInfoResponse::id, c -> c));
            List<ChildEvaluation> evaluations = byId.isEmpty()
                    ? List.of()
                    : evaluationRepository.findByClassroomIdInAndPeriodType(byId.keySet(), type).stream()
                            .filter(e -> teaches(currentUser, byId.get(e.getClassroomId())) || e.isConfirmed())
                            .filter(e -> month == null
                                    || YearMonth.from(e.getPeriodStart()).equals(YearMonth.from(month)))
                            .toList();
            List<EvaluationRow> rows = toRows(currentUser, evaluations, byId, false);
            log.info("{}|rows={}|END", logPrefix, rows.size());
            return rows;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EvaluationDetailResponse get(CurrentUser currentUser, Long id) {
        String logPrefix = LOG_PREFIX + "|get|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        try {
            ChildEvaluation ev = find(id);
            ClassroomInfoResponse cls = rules.findClass(ev.getClassroomId());
            boolean teacher = teaches(currentUser, cls);
            if (!teacher && !(ev.isConfirmed() && rules.leadsCampus(currentUser, cls.campusId()))) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            boolean year = ev.getPeriodType() == EvaluationPeriodType.YEAR;
            LocalDate to = minDate(ev.getPeriodEnd(), rules.today());
            EvaluationSources.ChildData data = sources.load(
                            cls, List.of(ev.getChildId()), ev.getPeriodStart(), to, !year)
                    .getOrDefault(ev.getChildId(), null);
            ClassChildResponse child = childOf(ev);
            List<EvaluationRow> monthly = year
                    ? toRows(
                            currentUser,
                            evaluationRepository
                                    .findByChildIdAndPeriodTypeAndStatusAndPeriodStartBetweenOrderByPeriodStartAsc(
                                            ev.getChildId(),
                                            EvaluationPeriodType.MONTH,
                                            EvaluationStatus.CONFIRMED,
                                            ev.getPeriodStart(),
                                            ev.getPeriodEnd()),
                            Map.of(cls.id(), cls),
                            false)
                    : List.of();
            EvaluationRow row = toRows(currentUser, List.of(ev), Map.of(cls.id(), cls), true)
                    .getFirst();
            int observed = data == null ? 0 : data.structured().observedDays();
            log.info("{}|END", logPrefix);
            return new EvaluationDetailResponse(
                    row,
                    new ChildRef(
                            ev.getChildId(),
                            child == null ? null : child.fullName(),
                            child == null ? null : child.dateOfBirth()),
                    new ClassRef(
                            cls.id(),
                            cls.name(),
                            cls.campusId(),
                            teacher,
                            cls.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecord(currentUser, cls)),
                    data == null ? List.of() : data.days(),
                    cls.mealSessions(),
                    monthly,
                    new SummaryInfo(
                            data == null ? null : data.structured(),
                            data == null ? 0 : data.presentDays(),
                            observed,
                            observed));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EvaluationRow saveContent(CurrentUser currentUser, Long id, String content) {
        String logPrefix = LOG_PREFIX + "|saveContent|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        try {
            ChildEvaluation ev = find(id);
            ClassroomInfoResponse cls = checkEditable(currentUser, ev);
            String text = checkContent(content);
            ev.edit(text);
            evaluationRepository.saveAndFlush(ev);
            eventRepository.save(
                    ChildEvaluationEvent.of(ev.getId(), EvaluationAction.EDITED, currentUser.userId(), rules.now()));
            log.info("{}|END", logPrefix);
            return toRows(currentUser, List.of(ev), Map.of(cls.id(), cls), true).getFirst();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EvaluationRow confirm(CurrentUser currentUser, Long id, String content) {
        String logPrefix = LOG_PREFIX + "|confirm|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        try {
            ChildEvaluation ev = find(id);
            ClassroomInfoResponse cls = checkEditable(currentUser, ev);
            String text = checkContent(content);
            if (programApplicable(ev, cls, ageFromMonths()) && ev.getProgramCompleted() == null) {
                throw GlobalException.of(ApiCode.EVALUATION_PROGRAM_REQUIRED);
            }
            Instant at = rules.now();
            ev.confirm(text, currentUser.userId(), at);
            evaluationRepository.saveAndFlush(ev);
            eventRepository.save(
                    ChildEvaluationEvent.of(ev.getId(), EvaluationAction.CONFIRMED, currentUser.userId(), at));
            log.info("{}|END", logPrefix);
            return toRows(currentUser, List.of(ev), Map.of(cls.id(), cls), true).getFirst();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EvaluationRow redraft(CurrentUser currentUser, Long id) {
        String logPrefix = LOG_PREFIX + "|redraft|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        try {
            ChildEvaluation ev = find(id);
            ClassroomInfoResponse cls = checkEditable(currentUser, ev);
            Instant at = rules.now();
            ev.redraft(
                    draftOf(
                            cls,
                            ev.getChildId(),
                            childName(ev),
                            ev.getPeriodType(),
                            ev.getPeriodStart(),
                            ev.getPeriodEnd(),
                            confirmedMonths(ev)),
                    at);
            evaluationRepository.saveAndFlush(ev);
            eventRepository.save(
                    ChildEvaluationEvent.of(ev.getId(), EvaluationAction.AI_REDRAFTED, currentUser.userId(), at));
            log.info("{}|END", logPrefix);
            return toRows(currentUser, List.of(ev), Map.of(cls.id(), cls), true).getFirst();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EvaluationRow markProgram(CurrentUser currentUser, Long id, Boolean completed) {
        String logPrefix = LOG_PREFIX + "|markProgram|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        try {
            ChildEvaluation ev = find(id);
            ClassroomInfoResponse cls = checkEditable(currentUser, ev);
            if (!programApplicable(ev, cls, ageFromMonths())) {
                throw GlobalException.of(ApiCode.EVALUATION_PROGRAM_NOT_APPLICABLE);
            }
            if (completed == null) {
                throw GlobalException.of(ApiCode.EVALUATION_PROGRAM_REQUIRED);
            }
            ev.markProgram(completed);
            evaluationRepository.saveAndFlush(ev);
            log.info("{}|completed={}|END", logPrefix, completed);
            return toRows(currentUser, List.of(ev), Map.of(cls.id(), cls), true).getFirst();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ProgramCompletionResponse programReport(CurrentUser currentUser, Long schoolYearId, Long campusId) {
        String logPrefix = LOG_PREFIX + "|programReport|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (!rules.isLeader(currentUser)) {
                throw GlobalException.of(ApiCode.PROGRAM_REPORT_FORBIDDEN);
            }
            if (campusId != null && !rules.leadsCampus(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            Map<Long, Integer> ageFrom = ageFromMonths();
            List<ClassroomInfoResponse> scoped = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> rules.leadsCampus(currentUser, c.campusId()))
                    .filter(c -> isProgramClass(c, ageFrom))
                    .sorted(Comparator.comparing(ClassroomInfoResponse::campusId)
                            .thenComparing(ClassroomInfoResponse::name))
                    .toList();
            List<ClassroomInfoResponse> visible = scoped.stream()
                    .filter(c -> campusId == null || campusId.equals(c.campusId()))
                    .toList();
            Map<Long, List<ChildEvaluation>> confirmed = visible.isEmpty()
                    ? Map.of()
                    : evaluationRepository
                            .findByClassroomIdInAndPeriodType(
                                    visible.stream()
                                            .map(ClassroomInfoResponse::id)
                                            .toList(),
                                    EvaluationPeriodType.YEAR)
                            .stream()
                            .filter(ChildEvaluation::isConfirmed)
                            .collect(Collectors.groupingBy(ChildEvaluation::getClassroomId));
            List<ProgramRow> rows = visible.stream()
                    .map(c -> {
                        LocalDate at = maxDate(c.yearStart(), minDate(rules.today(), c.yearEnd()));
                        int children = enrollmentService
                                .findChildrenInClassroom(c.id(), at)
                                .size();
                        List<ChildEvaluation> evs = confirmed.getOrDefault(c.id(), List.of());
                        int done = (int) evs.stream()
                                .filter(e -> Boolean.TRUE.equals(e.getProgramCompleted()))
                                .count();
                        int notDone = (int) evs.stream()
                                .filter(e -> Boolean.FALSE.equals(e.getProgramCompleted()))
                                .count();
                        int total = Math.max(children, done + notDone);
                        return new ProgramRow(
                                c.id(),
                                c.name(),
                                c.campusId(),
                                total,
                                done,
                                notDone,
                                total - done - notDone,
                                rate(done, total));
                    })
                    .toList();
            int children = rows.stream().mapToInt(ProgramRow::children).sum();
            int done = rows.stream().mapToInt(ProgramRow::completed).sum();
            int notDone = rows.stream().mapToInt(ProgramRow::notCompleted).sum();
            log.info("{}|classes={}|END", logPrefix, rows.size());
            return new ProgramCompletionResponse(
                    scoped.stream()
                            .map(ClassroomInfoResponse::campusId)
                            .distinct()
                            .toList(),
                    rows,
                    new ProgramTotals(children, done, notDone, children - done - notDone, rate(done, children)));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<DevelopmentChildRow> developmentChildren(CurrentUser currentUser, Long schoolYearId, Long classId) {
        String logPrefix = LOG_PREFIX + "|developmentChildren|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            List<ClassroomInfoResponse> visible = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> teaches(currentUser, c) || rules.leadsCampus(currentUser, c.campusId()))
                    .filter(c -> classId == null || classId.equals(c.id()))
                    .toList();
            if (classId != null && visible.isEmpty()) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            Collator vi = Collator.getInstance(Locale.of("vi"));
            List<DevelopmentChildRow> rows = visible.stream()
                    .flatMap(c -> enrollmentService
                            .findChildrenInClassroom(
                                    c.id(), maxDate(c.yearStart(), minDate(rules.today(), c.yearEnd())))
                            .stream()
                            .map(ch -> new DevelopmentChildRow(
                                    ch.id(), ch.fullName(), ch.dateOfBirth(), c.id(), c.name(), c.campusId())))
                    .sorted(Comparator.comparing(DevelopmentChildRow::className, vi)
                            .thenComparing(DevelopmentChildRow::fullName, vi))
                    .toList();
            log.info("{}|rows={}|END", logPrefix, rows.size());
            return rows;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DevelopmentResponse development(CurrentUser currentUser, Long childId, LocalDate start, LocalDate end) {
        String logPrefix = LOG_PREFIX + "|development|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START|start={}|end={}", logPrefix, start, end);
        try {
            LocalDate today = rules.today();
            if (start == null || end == null || start.isAfter(end) || start.isAfter(today)) {
                throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
            }
            if (ChronoUnit.DAYS.between(start, end) + 1 > DEVELOPMENT_MAX_DAYS) {
                throw GlobalException.of(ApiCode.DATE_RANGE_TOO_LONG, DEVELOPMENT_MAX_DAYS);
            }
            LocalDate to = minDate(end, today);
            // Lớp của trẻ ở cuối khoảng (trẻ chuyển lớp giữa kỳ: lớp mới), không có thì lớp ở đầu khoảng
            List<Long> classIds = enrollmentService.findActiveClassroomIds(childId, to);
            LocalDate at = to;
            if (classIds.isEmpty()) {
                classIds = enrollmentService.findActiveClassroomIds(childId, start);
                at = start;
            }
            if (classIds.isEmpty()) {
                throw GlobalException.of(ApiCode.DEVELOPMENT_CHILD_NO_CLASS);
            }
            ClassroomInfoResponse cls = rules.findClass(classIds.getFirst());
            boolean teacher = teaches(currentUser, cls);
            if (!teacher && !rules.leadsCampus(currentUser, cls.campusId())) {
                throw GlobalException.of(ApiCode.CHILD_ACCESS_DENIED);
            }
            ClassChildResponse child = enrollmentService.findChildrenInClassroom(cls.id(), at).stream()
                    .filter(c -> c.id().equals(childId))
                    .findFirst()
                    .orElse(null);
            EvaluationSources.ChildData data =
                    sources.load(cls, List.of(childId), start, to, true).get(childId);
            List<EvaluationDay> days = data == null ? List.of() : data.days();

            List<ChildEvaluation> confirmed =
                    evaluationRepository.findByChildIdAndStatusAndPeriodEndGreaterThanEqualAndPeriodStartLessThanEqual(
                            childId, EvaluationStatus.CONFIRMED, start, to);
            Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
            classes.put(cls.id(), cls);
            confirmed.stream()
                    .map(ChildEvaluation::getClassroomId)
                    .distinct()
                    .filter(id -> !classes.containsKey(id))
                    .forEach(id -> classroomService.findClassroomInfo(id).ifPresent(c -> classes.put(id, c)));
            List<EvaluationRow> rows = toRows(currentUser, confirmed, classes, false);

            List<TicketRef> tickets =
                    ticketRepository
                            .findByChildIdInAndPeriodStartBetween(List.of(childId), start.minusMonths(1), to)
                            .stream()
                            .filter(t -> !t.getPeriodEnd().isBefore(start))
                            .sorted(Comparator.comparing(BehaviourTicket::getPeriodStart)
                                    .reversed())
                            .map(t -> new TicketRef(t.getId(), t.getType(), t.getPeriodStart(), t.getPeriodEnd()))
                            .toList();
            log.info("{}|days={}|evaluations={}|END", logPrefix, days.size(), rows.size());
            return new DevelopmentResponse(
                    new ChildRef(
                            childId,
                            child == null ? null : child.fullName(),
                            child == null ? null : child.dateOfBirth()),
                    new ClassRef(
                            cls.id(),
                            cls.name(),
                            cls.campusId(),
                            teacher,
                            cls.yearStatus() == SchoolYearStatus.ACTIVE && rules.canRecord(currentUser, cls)),
                    new DateRange(start, to),
                    data == null ? 0 : data.presentDays(),
                    data == null ? 0 : data.flagCount(),
                    data == null ? null : data.structured(),
                    days,
                    cls.mealSessions(),
                    weeks(start, to, today, data),
                    rows.stream()
                            .filter(r -> r.periodType() == EvaluationPeriodType.MONTH)
                            .toList(),
                    rows.stream()
                            .filter(r -> r.periodType() == EvaluationPeriodType.YEAR)
                            .toList(),
                    tickets);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Tuần thứ 2–6 phủ khoảng ngày; chỉ đếm ngày trong khoảng
    private static List<DevelopmentWeek> weeks(
            LocalDate start, LocalDate to, LocalDate today, EvaluationSources.ChildData data) {
        Map<LocalDate, EvaluationDay> byDate = new HashMap<>();
        if (data != null) {
            data.days().forEach(d -> byDate.put(d.date(), d));
        }
        List<DevelopmentWeek> weeks = new ArrayList<>();
        for (LocalDate monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                !monday.isAfter(to);
                monday = monday.plusWeeks(1)) {
            LocalDate friday = monday.plusDays(4);
            int assessments = 0;
            int present = 0;
            int flags = 0;
            int met = 0;
            int scored = 0;
            for (LocalDate d = maxDate(monday, start); !d.isAfter(minDate(friday, to)); d = d.plusDays(1)) {
                if (data != null && data.presentDates().contains(d)) {
                    present++;
                }
                if (data != null && data.flagDates().contains(d)) {
                    flags++;
                }
                EvaluationDay day = byDate.get(d);
                if (day != null) {
                    assessments++;
                    met += day.met().size();
                    scored += day.met().size()
                            + day.effort().size()
                            + day.notMet().size();
                }
            }
            weeks.add(new DevelopmentWeek(
                    monday,
                    friday,
                    assessments,
                    present,
                    flags,
                    scored == 0 ? null : rate(met, scored),
                    friday.isBefore(today)));
        }
        return weeks;
    }

    private static int rate(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 100f / total);
    }

    private Map<Long, Integer> ageFromMonths() {
        Map<Long, Integer> ageFrom = new HashMap<>();
        for (AgeGroupDetailResponse g : ageGroupService.getAgeGroups()) {
            if (g.ageFromMonths() != null) {
                ageFrom.put(g.id(), g.ageFromMonths());
            }
        }
        return ageFrom;
    }

    private static boolean isProgramClass(ClassroomInfoResponse cls, Map<Long, Integer> ageFrom) {
        return cls != null
                && cls.ageGroupId() != null
                && ageFrom.getOrDefault(cls.ageGroupId(), 0) >= PROGRAM_AGE_FROM_MONTHS;
    }

    private static boolean programApplicable(ChildEvaluation e, ClassroomInfoResponse cls, Map<Long, Integer> ageFrom) {
        return e.getPeriodType() == EvaluationPeriodType.YEAR && isProgramClass(cls, ageFrom);
    }

    // Đánh giá cuối năm mở từ ngày 1 tháng cuối của năm học
    private boolean yearEndOpen(ClassroomInfoResponse cls) {
        return !rules.today().isBefore(cls.yearEnd().withDayOfMonth(1));
    }

    // ---------- tạo bản nháp ----------

    // Tháng: mỗi tháng đã kết thúc của năm học, trẻ có đi học hoặc có đánh giá trong tháng.
    // Cuối năm: từ ngày 1 tháng cuối của năm học, mọi trẻ đang học lớp.
    private void ensureDrafts(ClassroomInfoResponse cls, EvaluationPeriodType type) {
        LocalDate today = rules.today();
        Set<String> existing = evaluationRepository.findByClassroomIdInAndPeriodType(List.of(cls.id()), type).stream()
                .map(e -> e.getChildId() + "|" + e.getPeriodStart())
                .collect(Collectors.toCollection(HashSet::new));
        List<ChildEvaluation> created = new ArrayList<>();
        if (type == EvaluationPeriodType.MONTH) {
            for (LocalDate m = cls.yearStart().withDayOfMonth(1); !m.isAfter(cls.yearEnd()); m = m.plusMonths(1)) {
                LocalDate start = maxDate(m, cls.yearStart());
                LocalDate end = minDate(m.with(TemporalAdjusters.lastDayOfMonth()), cls.yearEnd());
                if (!end.isBefore(today)) {
                    break;
                }
                created.addAll(draftsFor(cls, type, start, end, existing, Map.of()));
            }
        } else if (yearEndOpen(cls)) {
            Map<Long, Integer> months =
                    evaluationRepository
                            .findByClassroomIdInAndPeriodType(List.of(cls.id()), EvaluationPeriodType.MONTH)
                            .stream()
                            .filter(ChildEvaluation::isConfirmed)
                            .collect(Collectors.toMap(ChildEvaluation::getChildId, e -> 1, Integer::sum));
            created.addAll(draftsFor(cls, type, cls.yearStart(), cls.yearEnd(), existing, months));
        }
        if (created.isEmpty()) {
            return;
        }
        List<ChildEvaluation> saved = evaluationRepository.saveAllAndFlush(created);
        Instant at = rules.now();
        eventRepository.saveAll(saved.stream()
                .map(e -> ChildEvaluationEvent.of(e.getId(), EvaluationAction.AI_DRAFTED, null, at))
                .toList());
        log.info("{}|ensureDrafts|classId={}|type={}|created={}", LOG_PREFIX, cls.id(), type, saved.size());
    }

    private List<ChildEvaluation> draftsFor(
            ClassroomInfoResponse cls,
            EvaluationPeriodType type,
            LocalDate start,
            LocalDate end,
            Set<String> existing,
            Map<Long, Integer> confirmedMonths) {
        LocalDate today = rules.today();
        List<ClassChildResponse> children =
                enrollmentService.findChildrenInClassroom(cls.id(), minDate(end, today)).stream()
                        .filter(c -> !existing.contains(c.id() + "|" + start))
                        .toList();
        if (children.isEmpty()) {
            return List.of();
        }
        Map<Long, EvaluationSources.ChildData> data = sources.load(
                cls, children.stream().map(ClassChildResponse::id).toList(), start, minDate(end, today), false);
        Instant at = rules.now();
        List<ChildEvaluation> drafts = new ArrayList<>();
        for (ClassChildResponse child : children) {
            EvaluationSources.ChildData d = data.get(child.id());
            boolean noData =
                    d == null || (d.presentDays() == 0 && d.structured().observedDays() == 0);
            if (type == EvaluationPeriodType.MONTH && noData) {
                continue;
            }
            String content = d == null
                    ? null
                    : EvaluationDraftWriter.write(
                            type,
                            child.fullName(),
                            start,
                            d.presentDays(),
                            d.flagCount(),
                            d.structured(),
                            confirmedMonths.getOrDefault(child.id(), 0));
            drafts.add(ChildEvaluation.draft(
                    child.id(), cls.id(), cls.campusId(), cls.schoolYearId(), type, start, end, content, at));
            existing.add(child.id() + "|" + start);
        }
        return drafts;
    }

    @SuppressWarnings("java:S107")
    private String draftOf(
            ClassroomInfoResponse cls,
            Long childId,
            String childName,
            EvaluationPeriodType type,
            LocalDate start,
            LocalDate end,
            int confirmedMonths) {
        EvaluationSources.ChildData d = sources.load(cls, List.of(childId), start, minDate(end, rules.today()), false)
                .get(childId);
        return d == null
                ? null
                : EvaluationDraftWriter.write(
                        type, childName, start, d.presentDays(), d.flagCount(), d.structured(), confirmedMonths);
    }

    private int confirmedMonths(ChildEvaluation ev) {
        return ev.getPeriodType() == EvaluationPeriodType.YEAR
                ? evaluationRepository
                        .findByChildIdAndPeriodTypeAndStatusAndPeriodStartBetweenOrderByPeriodStartAsc(
                                ev.getChildId(),
                                EvaluationPeriodType.MONTH,
                                EvaluationStatus.CONFIRMED,
                                ev.getPeriodStart(),
                                ev.getPeriodEnd())
                        .size()
                : 0;
    }

    // ---------- kiểm tra ----------

    private ChildEvaluation find(Long id) {
        return evaluationRepository.findById(id).orElseThrow(() -> GlobalException.of(ApiCode.EVALUATION_NOT_FOUND));
    }

    // GV xem bản nháp: đang dạy lớp hoặc từng dạy lớp của năm đã kết thúc
    private boolean teaches(CurrentUser user, ClassroomInfoResponse cls) {
        return cls != null
                && user.hasRole(CareNestRole.TEACHER)
                && (rules.canRecord(user, cls) || rules.taughtClosedYear(user, cls));
    }

    private ClassroomInfoResponse checkEditable(CurrentUser user, ChildEvaluation ev) {
        ClassroomInfoResponse cls = rules.findClass(ev.getClassroomId());
        if (!rules.canRecord(user, cls)) {
            throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        }
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        }
        if (ev.isConfirmed()) {
            throw GlobalException.of(ApiCode.EVALUATION_ALREADY_CONFIRMED);
        }
        return cls;
    }

    static String checkContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw GlobalException.of(ApiCode.EVALUATION_CONTENT_REQUIRED);
        }
        if (text.length() < CONTENT_MIN) {
            throw GlobalException.of(ApiCode.EVALUATION_CONTENT_TOO_SHORT, CONTENT_MIN);
        }
        if (text.length() > CONTENT_MAX) {
            throw GlobalException.of(ApiCode.EVALUATION_CONTENT_TOO_LONG, CONTENT_MAX);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        DailyAssessmentServiceImpl.DIAGNOSTIC_TERMS.stream()
                .filter(lower::contains)
                .findFirst()
                .ifPresent(term -> {
                    throw GlobalException.of(ApiCode.EVALUATION_DIAGNOSTIC_TERM, term);
                });
        return text;
    }

    private void checkMonth(LocalDate month, ClassroomInfoResponse year) {
        LocalDate first = year.yearStart().withDayOfMonth(1);
        LocalDate last = minDate(rules.today(), year.yearEnd()).withDayOfMonth(1);
        if (month.getDayOfMonth() != 1 || month.isBefore(first) || month.isAfter(last)) {
            throw GlobalException.of(
                    ApiCode.EVALUATION_MONTH_INVALID, MONTH_TEXT.format(first), MONTH_TEXT.format(last));
        }
    }

    // ---------- dòng trả về ----------

    private List<EvaluationRow> toRows(
            CurrentUser user,
            List<ChildEvaluation> evaluations,
            Map<Long, ClassroomInfoResponse> classes,
            boolean withHistory) {
        if (evaluations.isEmpty()) {
            return List.of();
        }
        Map<String, String> childNames = childNames(evaluations);
        Map<Long, List<ChildEvaluationEvent>> events = withHistory
                ? eventRepository
                        .findByEvaluationIdInOrderByAtAscIdAsc(
                                evaluations.stream().map(ChildEvaluation::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(ChildEvaluationEvent::getEvaluationId))
                : Map.of();
        Map<Long, String> userNames = accountService.findFullNames(Stream.concat(
                        evaluations.stream().map(ChildEvaluation::getConfirmedBy),
                        events.values().stream().flatMap(List::stream).map(ChildEvaluationEvent::getUserId))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<Long, Map<LocalDate, Boolean>> flags = flagsOf(evaluations);
        Map<Long, Integer> ageFrom = evaluations.stream().anyMatch(e -> e.getPeriodType() == EvaluationPeriodType.YEAR)
                ? ageFromMonths()
                : Map.of();
        return evaluations.stream()
                .map(e -> {
                    ClassroomInfoResponse cls = classes.get(e.getClassroomId());
                    boolean teacher = teaches(user, cls);
                    int flagCount = (int) flags.getOrDefault(e.getChildId(), Map.of()).keySet().stream()
                            .filter(d -> !d.isBefore(e.getPeriodStart()) && !d.isAfter(e.getPeriodEnd()))
                            .count();
                    return new EvaluationRow(
                            e.getId(),
                            e.getChildId(),
                            childNames.get(e.getClassroomId() + "|" + e.getChildId()),
                            e.getClassroomId(),
                            cls == null ? null : cls.name(),
                            e.getCampusId(),
                            e.getSchoolYearId(),
                            e.getPeriodType(),
                            e.getPeriodStart(),
                            e.getPeriodEnd(),
                            e.getStatus(),
                            teacher && e.getDraftContent() != null
                                    ? new Draft(e.getDraftContent(), e.getDraftAt())
                                    : null,
                            e.isConfirmed() || teacher ? e.getFinalContent() : null,
                            programApplicable(e, cls, ageFrom),
                            e.getProgramCompleted(),
                            e.getConfirmedBy(),
                            e.getConfirmedBy() == null ? null : userNames.get(e.getConfirmedBy()),
                            e.getConfirmedAt(),
                            e.getPeriodType() == EvaluationPeriodType.YEAR ? null : flagCount,
                            events.getOrDefault(e.getId(), List.of()).stream()
                                    .map(ev -> new EventRow(
                                            ev.getId(),
                                            ev.getAction(),
                                            ev.getUserId(),
                                            ev.getUserId() == null ? null : userNames.get(ev.getUserId()),
                                            ev.getAt()))
                                    .toList());
                })
                .sorted(Comparator.comparing(EvaluationRow::periodStart)
                        .reversed()
                        .thenComparing(r -> r.childName() == null ? "" : r.childName()))
                .toList();
    }

    // Tên trẻ theo lớp tại ngày cuối kỳ (trẻ đã chuyển lớp sau đó vẫn có tên)
    private Map<String, String> childNames(Collection<ChildEvaluation> evaluations) {
        Map<String, String> names = new HashMap<>();
        evaluations.stream()
                .map(e -> Map.entry(e.getClassroomId(), nameDate(e)))
                .distinct()
                .forEach(k -> enrollmentService
                        .findChildrenInClassroom(k.getKey(), k.getValue())
                        .forEach(c -> names.put(k.getKey() + "|" + c.id(), c.fullName())));
        return names;
    }

    private ClassChildResponse childOf(ChildEvaluation ev) {
        return enrollmentService.findChildrenInClassroom(ev.getClassroomId(), nameDate(ev)).stream()
                .filter(c -> c.id().equals(ev.getChildId()))
                .findFirst()
                .orElse(null);
    }

    private String childName(ChildEvaluation ev) {
        ClassChildResponse child = childOf(ev);
        return child == null ? "" : child.fullName();
    }

    private LocalDate nameDate(ChildEvaluation e) {
        return maxDate(e.getPeriodStart(), minDate(e.getPeriodEnd(), rules.today()));
    }

    // Ngày có cờ của từng trẻ (cờ chỉ ghi ở ngày trẻ có mặt)
    private Map<Long, Map<LocalDate, Boolean>> flagsOf(List<ChildEvaluation> evaluations) {
        LocalDate from = evaluations.stream()
                .map(ChildEvaluation::getPeriodStart)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate to = evaluations.stream()
                .map(ChildEvaluation::getPeriodEnd)
                .max(LocalDate::compareTo)
                .orElseThrow();
        Set<Long> childIds =
                evaluations.stream().map(ChildEvaluation::getChildId).collect(Collectors.toSet());
        Map<Long, Map<LocalDate, Boolean>> flags = new HashMap<>();
        for (DailyObservation o : observationRepository.findByChildIdInAndDateBetween(childIds, from, to)) {
            if (Boolean.TRUE.equals(o.getFlag())) {
                flags.computeIfAbsent(o.getChildId(), x -> new HashMap<>()).put(o.getDate(), true);
            }
        }
        return flags;
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
