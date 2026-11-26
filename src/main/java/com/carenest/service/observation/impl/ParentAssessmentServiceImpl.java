package com.carenest.service.observation.impl;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.observation.DailySheetResponse.ActivityResult;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentActivity;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentDailyAssessment;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentDomain;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentEvaluationsResponse;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentFlag;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentGoal;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentRewardsResponse;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentSemesterResult;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentThemeResult;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentTicket;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentYearEndResult;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentYearEndTitle;
import com.carenest.dto.observation.SemesterAssessmentDtos;
import com.carenest.dto.observation.YearEndSheetDtos.YearEndSheetResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.entity.observation.DailyObservation;
import com.carenest.entity.observation.RewardList;
import com.carenest.entity.observation.RewardListItem;
import com.carenest.entity.observation.SemesterAssessment;
import com.carenest.entity.observation.ThemeEvaluation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.repository.observation.RewardListItemRepository;
import com.carenest.repository.observation.RewardListRepository;
import com.carenest.repository.observation.SemesterAssessmentRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.observation.ParentAssessmentService;
import com.carenest.service.observation.YearEndSheetService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.EvaluationStatus;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.RewardStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AUTH-04, OBS-36 (Cần chốt 24/11/2026): phụ huynh chỉ xem con đã liên kết (khác ⇒ 4108), khoảng tối đa 62 ngày trong
 * một năm học (khác ⇒ 4109). Chỉ dữ liệu đã chốt: đánh giá xác nhận, phiếu đã phát, danh sách khen thưởng đã duyệt; ngày
 * lớp đang được mở lại sửa thì ẩn phần cô vừa sửa; mỗi dòng mang lớp của thời điểm đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentAssessmentServiceImpl implements ParentAssessmentService {

    private static final String LOG_PREFIX = "[ParentAssessmentServiceImpl]";
    private static final int MAX_DAYS = 62;
    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);
    private static final TypeReference<Map<MealSession, EatingLevel>> MEALS = new TypeReference<>() {};

    private final DailyObservationRepository observationRepository;
    private final DailyActivityResultRepository resultRepository;
    private final AssessmentDayUnlockRepository unlockRepository;
    private final BehaviourTicketRepository ticketRepository;
    private final RewardListRepository rewardListRepository;
    private final RewardListItemRepository rewardItemRepository;
    private final ThemeEvaluationRepository themeRepository;
    private final SemesterAssessmentRepository semesterRepository;
    private final ChildEvaluationRepository evaluationRepository;
    private final YearEndSheetService yearEndSheetService;
    private final AttendanceService attendanceService;
    private final DailyHealthService dailyHealthService;
    private final EduLessonQueryService lessonQueryService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;
    private final AccountService accountService;
    private final ObservationDayRules rules;
    private final ObjectMapper objectMapper;

    @Override
    public List<ParentDailyAssessment> getDailyAssessments(
            CurrentUser currentUser, Long childId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getDailyAssessments|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            Stays stays = checkedRange(childId, from, to);
            LocalDate last = to.isAfter(rules.today()) ? rules.today() : to;
            Map<LocalDate, AttendanceService.ChildDayStatus> attendance =
                    attendanceService.findDayStatuses(List.of(childId), from, last).stream()
                            .collect(Collectors.toMap(
                                    AttendanceService.ChildDayStatus::date, Function.identity(), (a, b) -> a));
            Map<LocalDate, DailyObservation> observations =
                    observationRepository.findByChildIdInAndDateBetween(List.of(childId), from, last).stream()
                            .collect(Collectors.toMap(DailyObservation::getDate, Function.identity(), (a, b) -> a));
            List<DailyActivityResult> results =
                    resultRepository.findByChildIdInAndDateBetween(List.of(childId), from, last);
            Map<LocalDate, List<DailyActivityResult>> resultsByDay =
                    results.stream().collect(Collectors.groupingBy(DailyActivityResult::getDate));
            Map<LocalDate, DailyHealthService.ChildDaySigns> health =
                    dailyHealthService.findLatestDaySigns(List.of(childId), from, last).stream()
                            .collect(Collectors.toMap(
                                    DailyHealthService.ChildDaySigns::date, Function.identity(), (a, b) -> a));
            Map<LocalDate, ApprovedDayLessonResponse> lessons = new HashMap<>();
            Map<Long, List<AssessmentDayUnlock>> unlocks = new HashMap<>();
            for (EnrollmentService.ChildClassStay stay : stays.list()) {
                LocalDate end = stay.to().isAfter(last) ? last : stay.to();
                if (stay.from().isAfter(end)) {
                    continue;
                }
                lessons.putAll(lessonQueryService.findApprovedDayLessons(stay.classroomId(), stay.from(), end));
                unlocks.put(
                        stay.classroomId(),
                        unlockRepository.findByClassroomIdInAndDateBetweenAndValidUntilAfter(
                                List.of(stay.classroomId()), stay.from(), end, rules.now()));
            }
            Map<DailyActivityResult, ActivityResult> projected =
                    AssessmentResultProjection.project(lessonQueryService, lessons, results);
            Set<Long> staff = new java.util.HashSet<>();
            observations
                    .values()
                    .forEach(o -> staff.add(o.getUpdatedBy() != null ? o.getUpdatedBy() : o.getRecordedBy()));
            Map<Long, String> names = accountService.findFullNames(staff);

            List<ParentDailyAssessment> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(last); d = d.plusDays(1)) {
                ClassroomInfoResponse cls = stays.classOn(d);
                if (cls == null || !rules.isSchoolDay(d)) {
                    continue;
                }
                DailyObservation o = observations.get(d);
                List<DailyActivityResult> dayResults = resultsByDay.getOrDefault(d, List.of());
                AttendanceService.ChildDayStatus att = attendance.get(d);
                boolean absent = att != null && att.status() != AttendanceStatus.PRESENT;
                boolean recorded = o != null || !dayResults.isEmpty();
                boolean editing = recorded && editedWhileReopened(unlocks.get(cls.id()), d, o, dayResults);
                if (absent || att == null || !recorded || editing) {
                    days.add(emptyDay(d, cls, absent ? "ABSENT" : "NOT_RECORDED"));
                    continue;
                }
                days.add(assessedDay(d, cls, o, dayResults, health.get(d), projected, lessons.get(d), names));
            }
            log.info("{}|days={}|END", logPrefix, days.size());
            return days;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ParentEvaluationsResponse getEvaluations(CurrentUser currentUser, Long childId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getEvaluations|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            Stays all = new Stays(childId, EPOCH, rules.today().plusYears(1));
            Long yearId = schoolYearId != null
                    ? schoolYearId
                    : all.list().stream()
                            .reduce((a, b) -> b)
                            .map(s -> all.classInfo(s.classroomId()))
                            .map(ClassroomInfoResponse::schoolYearId)
                            .orElse(null);
            List<Long> classIds = all.list().stream()
                    .filter(s -> yearId != null
                            && Optional.ofNullable(all.classInfo(s.classroomId()))
                                    .map(c -> yearId.equals(c.schoolYearId()))
                                    .orElse(false))
                    .map(EnrollmentService.ChildClassStay::classroomId)
                    .distinct()
                    .toList();
            if (classIds.isEmpty()) {
                log.info("{}|END", logPrefix);
                return new ParentEvaluationsResponse(childId, yearId, List.of(), List.of(), null);
            }
            List<ChildEvaluation> confirmed = evaluationRepository.findByChildIdAndSchoolYearIdAndStatusAndPeriodTypeIn(
                    childId, yearId, EvaluationStatus.CONFIRMED, List.of(EvaluationPeriodType.YEAR));
            ParentEvaluationsResponse response = new ParentEvaluationsResponse(
                    childId,
                    yearId,
                    themes(childId, yearId, classIds, all),
                    semesters(yearId, childId, classIds, all),
                    yearEnd(confirmed, all));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ParentRewardsResponse getRewards(CurrentUser currentUser, Long childId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getRewards|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            Stays stays = checkedRange(childId, from, to);
            List<DailyObservation> observations =
                    observationRepository.findByChildIdInAndDateBetween(List.of(childId), from, to);
            Set<LocalDate> presentDays = attendanceService.findDayStatuses(List.of(childId), from, to).stream()
                    .filter(a -> a.status() == AttendanceStatus.PRESENT)
                    .map(AttendanceService.ChildDayStatus::date)
                    .collect(Collectors.toSet());
            Map<Long, List<AssessmentDayUnlock>> unlocks = new HashMap<>();
            stays.list().stream()
                    .map(EnrollmentService.ChildClassStay::classroomId)
                    .distinct()
                    .forEach(classId -> unlocks.put(
                            classId,
                            unlockRepository.findByClassroomIdInAndDateBetweenAndValidUntilAfter(
                                    List.of(classId), from, to, rules.now())));
            // Như phiếu ngày (OBS-36): cờ chỉ hiện ở ngày trẻ có mặt và không phải phần cô đang mở lại sửa
            List<ParentFlag> flags = observations.stream()
                    .filter(o -> o.getFlag() != null && stays.classOn(o.getDate()) != null)
                    .filter(o -> presentDays.contains(o.getDate())
                            && !editedWhileReopened(
                                    unlocks.get(stays.classOn(o.getDate()).id()), o.getDate(), o, List.of()))
                    .sorted(Comparator.comparing(DailyObservation::getDate))
                    .map(o -> {
                        ClassroomInfoResponse cls = stays.classOn(o.getDate());
                        return new ParentFlag(o.getDate(), o.getFlag(), cls.id(), cls.name());
                    })
                    .toList();
            List<BehaviourTicket> issued =
                    ticketRepository.findByChildIdInAndPeriodStartBetween(List.of(childId), from, to);
            Map<Long, String> names = accountService.findFullNames(
                    issued.stream().map(BehaviourTicket::getIssuedBy).distinct().toList());
            List<ParentTicket> tickets = issued.stream()
                    .sorted(Comparator.comparing(BehaviourTicket::getPeriodStart))
                    .map(t -> {
                        ClassroomInfoResponse cls = stays.classInfo(t.getClassroomId());
                        return new ParentTicket(
                                t.getId(),
                                t.getType(),
                                t.getPeriodStart(),
                                t.getPeriodEnd(),
                                t.getPresentDays(),
                                t.getFlagCount(),
                                t.getWeeklyTickets(),
                                names.get(t.getIssuedBy()),
                                t.getIssuedAt(),
                                t.getClassroomId(),
                                cls == null ? null : cls.name());
                    })
                    .toList();
            ParentRewardsResponse response =
                    new ParentRewardsResponse(childId, from, to, flags, tickets, yearEndTitles(childId, stays));
            log.info("{}|flags={}|tickets={}|END", logPrefix, flags.size(), tickets.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // AUTH-04: chỉ phụ huynh đã liên kết với trẻ
    private void checkParent(CurrentUser user, Long childId) {
        if (!user.hasRole(CareNestRole.PARENT) || !guardianChildLinkService.isGuardianOf(user.userId(), childId)) {
            throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
        }
        // CHD-04 (user chốt 26/11/2026): trẻ đã thôi học hoặc ra trường thì phụ huynh mất quyền xem ngay
        if (!enrollmentService
                .findChildIdsLeftSchool(java.util.Set.of(childId), rules.today())
                .isEmpty()) {
            throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
        }
    }

    // OBS-36: tối đa 62 ngày, nằm trong một năm học (theo lớp trẻ học trong khoảng)
    private Stays checkedRange(Long childId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw GlobalException.of(ApiCode.PARENT_RANGE_INVALID, MAX_DAYS);
        }
        Stays stays = new Stays(childId, from, to);
        List<ClassroomInfoResponse> classes = stays.list().stream()
                .map(s -> stays.classInfo(s.classroomId()))
                .filter(Objects::nonNull)
                .toList();
        boolean oneYear = classes.stream()
                        .map(ClassroomInfoResponse::schoolYearId)
                        .distinct()
                        .count()
                <= 1;
        boolean insideYear = classes.stream()
                .allMatch(c -> (c.yearStart() == null || !from.isBefore(c.yearStart()))
                        && (c.yearEnd() == null || !to.isAfter(c.yearEnd())));
        if (!oneYear || !insideYear) {
            throw GlobalException.of(ApiCode.PARENT_RANGE_INVALID, MAX_DAYS);
        }
        return stays;
    }

    /** Các đoạn học lớp của trẻ trong khoảng và thông tin lớp (đọc một lần). */
    private final class Stays {
        private final List<EnrollmentService.ChildClassStay> stays;
        private final Map<Long, ClassroomInfoResponse> classes = new HashMap<>();

        Stays(Long childId, LocalDate from, LocalDate to) {
            stays = enrollmentService.findChildStays(childId, from, to);
        }

        List<EnrollmentService.ChildClassStay> list() {
            return stays;
        }

        ClassroomInfoResponse classInfo(Long classId) {
            return classes.computeIfAbsent(
                    classId, id -> classroomService.findClassroomInfo(id).orElse(null));
        }

        ClassroomInfoResponse classOn(LocalDate date) {
            return stays.stream()
                    .filter(s -> !date.isBefore(s.from()) && !date.isAfter(s.to()))
                    .findFirst()
                    .map(s -> classInfo(s.classroomId()))
                    .orElse(null);
        }
    }

    // Lớp đang được mở lại sửa đánh giá ngày đó: phần cô ghi / sửa sau lúc mở lại chưa chốt nên chưa hiện
    private static boolean editedWhileReopened(
            List<AssessmentDayUnlock> unlocks, LocalDate date, DailyObservation o, List<DailyActivityResult> results) {
        if (unlocks == null) {
            return false;
        }
        return unlocks.stream().filter(u -> u.getDate().equals(date)).anyMatch(u -> {
            Stream<Instant> touched = Stream.concat(
                    o == null
                            ? Stream.<Instant>empty()
                            : Stream.of(o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getRecordedAt()),
                    results.stream().map(r -> r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getRecordedAt()));
            return touched.anyMatch(at -> at.isAfter(u.getUnlockedAt()));
        });
    }

    private static ParentDailyAssessment emptyDay(LocalDate date, ClassroomInfoResponse cls, String status) {
        return new ParentDailyAssessment(
                date,
                status,
                cls.id(),
                cls.name(),
                null,
                null,
                List.of(),
                "",
                Map.of(),
                null,
                null,
                null,
                List.of(),
                "",
                List.of(),
                null,
                "");
    }

    private ParentDailyAssessment assessedDay(
            LocalDate date,
            ClassroomInfoResponse cls,
            DailyObservation o,
            List<DailyActivityResult> results,
            DailyHealthService.ChildDaySigns health,
            Map<DailyActivityResult, ActivityResult> projected,
            ApprovedDayLessonResponse lesson,
            Map<Long, String> names) {
        Map<Long, String> goalText = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        if (lesson != null) {
            lesson.activities().forEach(a -> {
                labels.put(a.key(), a.label());
                a.goals().forEach(g -> goalText.put(g.id(), g.text()));
            });
        }
        List<ParentActivity> activities = results.stream()
                .map(r -> {
                    ActivityResult p = projected.get(r);
                    return new ParentActivity(
                            r.getActivityKey(),
                            labels.get(r.getActivityKey()),
                            r.getParticipation(),
                            texts(p.goalsMet(), goalText),
                            texts(p.goalsEffort(), goalText),
                            texts(p.goalsNotMet(), goalText));
                })
                .toList();
        boolean overridden = o != null && o.isHealthOverridden();
        List<HealthSymptom> symptoms = overridden ? o.getHealthSigns() : health == null ? List.of() : health.signs();
        String healthNote =
                o != null && !o.getHealthNote().isEmpty() ? o.getHealthNote() : health == null ? "" : health.note();
        Map<MealSession, EatingLevel> meals = new EnumMap<>(MealSession.class);
        if (o != null && o.getMeals() != null && !o.getMeals().isBlank()) {
            meals.putAll(objectMapper.readValue(o.getMeals(), MEALS));
        }
        Instant savedAt = Stream.concat(
                        o == null
                                ? Stream.<Instant>empty()
                                : Stream.of(o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getRecordedAt()),
                        results.stream().map(r -> r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getRecordedAt()))
                .max(Comparator.naturalOrder())
                .orElse(null);
        Long teacher = o == null ? null : o.getUpdatedBy() != null ? o.getUpdatedBy() : o.getRecordedBy();
        return new ParentDailyAssessment(
                date,
                "ASSESSED",
                cls.id(),
                cls.name(),
                teacher == null ? null : names.get(teacher),
                savedAt,
                symptoms,
                healthNote,
                meals,
                o == null ? null : o.getNap(),
                o == null ? null : o.getEmotion(),
                o == null ? null : o.getParticipation(),
                o == null ? List.of() : o.getBehaviors(),
                o == null ? "" : o.getBehaviorNote(),
                activities,
                o == null ? null : o.getFlag(),
                o == null ? "" : o.getComment());
    }

    private static List<String> texts(List<Long> ids, Map<Long, String> text) {
        return ids.stream().map(text::get).filter(Objects::nonNull).toList();
    }

    // OBS-27: bảng cuối chủ đề đã xác nhận lưu cả bảng; chỉ lấy dòng của trẻ
    private List<com.carenest.dto.observation.ParentAssessmentDtos.ParentThemeResult> themes(
            Long childId, Long yearId, List<Long> classIds, Stays stays) {
        List<ParentThemeResult> result = new ArrayList<>();
        themeRepository.findByClassroomIdInAndSchoolYearIdAndConfirmedAtIsNotNull(classIds, yearId).stream()
                .sorted(Comparator.comparing(ThemeEvaluation::getStartDate))
                .forEach(e -> {
                    if (e.getPresentationSnapshot() == null) {
                        return;
                    }
                    JsonNode snapshot = objectMapper.readTree(e.getPresentationSnapshot());
                    JsonNode row = null;
                    for (JsonNode r : snapshot.path("rows")) {
                        if (r.path("childId").asLong() == childId) {
                            row = r;
                        }
                    }
                    if (row == null) {
                        return;
                    }
                    Map<Long, Boolean> met = new HashMap<>();
                    for (JsonNode c : row.path("cells")) {
                        met.put(
                                c.path("goalId").asLong(),
                                c.path("met").isNull() ? null : c.path("met").asBoolean());
                    }
                    List<ParentGoal> goals = new ArrayList<>();
                    for (JsonNode g : snapshot.path("goals")) {
                        Boolean value = met.get(g.path("goalId").asLong());
                        goals.add(new ParentGoal(
                                (short) g.path("goalNo").asInt(), g.path("text").asString(""), value));
                    }
                    ClassroomInfoResponse cls = stays.classInfo(e.getClassroomId());
                    result.add(new ParentThemeResult(
                            e.getClassroomId(),
                            cls == null ? null : cls.name(),
                            e.getThemeName(),
                            e.getStartDate(),
                            e.getEndDate(),
                            (int) goals.stream()
                                    .filter(g -> Boolean.TRUE.equals(g.met()))
                                    .count(),
                            (int) goals.stream()
                                    .filter(g -> Boolean.FALSE.equals(g.met()))
                                    .count(),
                            goals,
                            e.getConfirmedAt()));
                });
        return result;
    }

    // OBS-25: kết quả Đánh giá học kỳ đã xác nhận (chụp lúc xác nhận). Cần chốt 24/11/2026: bỏ nhận xét học kỳ cá nhân
    // ⇒ comment luôn null (giữ trường cho client cũ)
    private List<ParentSemesterResult> semesters(Long yearId, Long childId, List<Long> classIds, Stays stays) {
        List<ParentSemesterResult> result = new ArrayList<>();
        for (SemesterAssessment sa :
                semesterRepository.findByClassroomIdInAndSchoolYearIdAndConfirmedAtIsNotNull(classIds, yearId)) {
            if (sa.getSnapshot() == null) {
                continue;
            }
            SemesterAssessmentDtos.Response snapshot =
                    objectMapper.readValue(sa.getSnapshot(), SemesterAssessmentDtos.Response.class);
            SemesterAssessmentDtos.Child row = snapshot.rows().stream()
                    .filter(c -> c.childId().equals(childId))
                    .findFirst()
                    .orElse(null);
            if (row == null) {
                continue;
            }
            ClassroomInfoResponse cls = stays.classInfo(sa.getClassroomId());
            result.add(new ParentSemesterResult(
                    sa.getClassroomId(),
                    cls == null ? null : cls.name(),
                    sa.getSemester(),
                    sa.getStart(),
                    sa.getEnd(),
                    row.domains().stream()
                            .map(d -> new ParentDomain(
                                    d.name(),
                                    d.goals().stream()
                                            .map(g -> new ParentGoal(g.goalNo(), g.text(), g.met()))
                                            .toList(),
                                    null))
                            .toList(),
                    null,
                    sa.getConfirmedAt()));
        }
        result.sort(Comparator.comparing(ParentSemesterResult::semester));
        return result;
    }

    // OBS-28: phiếu cuối năm đã xác nhận
    private ParentYearEndResult yearEnd(List<ChildEvaluation> confirmed, Stays stays) {
        return confirmed.stream()
                .filter(e -> e.getPeriodType() == EvaluationPeriodType.YEAR)
                .max(Comparator.comparing(ChildEvaluation::getPeriodEnd))
                .map(e -> {
                    YearEndSheetResponse sheet = yearEndSheetService.getConfirmedSheet(e.getId());
                    ClassroomInfoResponse cls = stays.classInfo(e.getClassroomId());
                    return new ParentYearEndResult(
                            e.getClassroomId(),
                            cls == null ? null : cls.name(),
                            sheet.domains().stream()
                                    .map(d -> new ParentDomain(
                                            d.name(),
                                            d.goals().stream()
                                                    .map(g -> new ParentGoal(g.goalNo(), g.text(), g.met()))
                                                    .toList(),
                                            d.comment()))
                                    .toList(),
                            sheet.metCount(),
                            sheet.notMetCount(),
                            e.getProgramCompleted(),
                            e.getFinalContent(),
                            e.getNoteForParent(),
                            e.getConfirmedAt());
                })
                .orElse(null);
    }

    // Danh hiệu cuối năm: chỉ danh sách đã được Hiệu trưởng phê duyệt, của lớp trẻ học trong khoảng
    private List<ParentYearEndTitle> yearEndTitles(Long childId, Stays stays) {
        List<Long> classIds = stays.list().stream()
                .map(EnrollmentService.ChildClassStay::classroomId)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        Map<Long, RewardList> lists =
                rewardListRepository.findByClassroomIdInAndStatus(classIds, RewardStatus.APPROVED).stream()
                        .collect(Collectors.toMap(RewardList::getId, Function.identity()));
        if (lists.isEmpty()) {
            return List.of();
        }
        List<ParentYearEndTitle> titles = new ArrayList<>();
        for (RewardListItem item : rewardItemRepository.findByListIdIn(lists.keySet())) {
            if (!item.getChildId().equals(childId) || item.titleList().isEmpty()) {
                continue;
            }
            RewardList list = lists.get(item.getListId());
            ClassroomInfoResponse cls = stays.classInfo(list.getClassroomId());
            titles.add(new ParentYearEndTitle(
                    list.getSchoolYearId(),
                    list.getClassroomId(),
                    cls == null ? null : cls.name(),
                    item.titleList(),
                    item.getNote()));
        }
        return titles;
    }
}
