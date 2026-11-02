package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.observation.AssessmentDayUnlockResponse;
import com.carenest.dto.observation.DailySheetResponse;
import com.carenest.dto.observation.DailySheetResponse.ActivityResult;
import com.carenest.dto.observation.DailySheetResponse.Health;
import com.carenest.dto.observation.DailySheetResponse.Row;
import com.carenest.dto.observation.SaveDailyGeneralRequest;
import com.carenest.dto.observation.SaveDailyResponse;
import com.carenest.dto.observation.SaveDailySkillsRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.ClassDailyComment;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.entity.observation.DailyObservation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.ClassDailyCommentRepository;
import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.observation.DailyAssessmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DailyAssessmentServiceImpl implements DailyAssessmentService {

    private static final String LOG_PREFIX = "[DailyAssessmentServiceImpl]";
    // Thuật ngữ chẩn đoán không dùng trong nhận xét / ghi chú (giáo viên mô tả hành vi quan sát được)
    static final List<String> DIAGNOSTIC_TERMS = List.of(
            "tự kỷ",
            "tăng động",
            "giảm chú ý",
            "adhd",
            "chậm phát triển",
            "rối loạn",
            "khuyết tật",
            "trầm cảm",
            "thiểu năng",
            "chậm nói",
            "bệnh lý");

    private final ObservationDayRules rules;
    private final DailyObservationRepository observationRepository;
    private final DailyActivityResultRepository resultRepository;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final DailyHealthService dailyHealthService;
    private final EduLessonQueryService lessonQueryService;
    private final ObjectMapper objectMapper;
    private final ClassDailyCommentRepository classCommentRepository;

    @Override
    @Transactional(readOnly = true)
    public DailySheetResponse getDailySheet(CurrentUser currentUser, Long classId, LocalDate day) {
        String logPrefix = LOG_PREFIX + "|getDailySheet|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate date = day == null ? rules.today() : day;
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canView(currentUser, cls)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            checkDate(cls, date);
            List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(classId, date);
            Map<Long, AttendanceStatus> statuses = statusesOf(children, date);
            List<ClassChildResponse> present = children.stream()
                    .filter(c -> statuses.get(c.id()) == AttendanceStatus.PRESENT)
                    .toList();
            List<Long> ids = present.stream().map(ClassChildResponse::id).toList();
            Map<Long, DailyObservation> observations = ids.isEmpty()
                    ? Map.of()
                    : observationRepository.findByChildIdInAndDate(ids, date).stream()
                            .collect(Collectors.toMap(DailyObservation::getChildId, Function.identity()));
            Map<Long, List<DailyActivityResult>> results = ids.isEmpty()
                    ? Map.of()
                    : resultRepository.findByChildIdInAndDate(ids, date).stream()
                            .collect(Collectors.groupingBy(DailyActivityResult::getChildId));
            Map<Long, DailyHealthService.ChildDaySigns> health =
                    dailyHealthService.findLatestDaySigns(ids, date, date).stream()
                            .collect(Collectors.toMap(DailyHealthService.ChildDaySigns::childId, Function.identity()));
            ApprovedDayLessonResponse lesson = lessonQueryService
                    .findApprovedDayLessons(classId, date, date)
                    .get(date);
            Map<DailyActivityResult, ActivityResult> projected = AssessmentResultProjection.project(
                    lessonQueryService,
                    lesson == null ? Map.of() : Map.of(date, lesson),
                    results.values().stream().flatMap(List::stream).toList());
            List<Row> rows = present.stream()
                    .map(c -> toRow(
                            c,
                            observations.get(c.id()),
                            results.getOrDefault(c.id(), List.of()),
                            health.get(c.id()),
                            projected))
                    .toList();
            boolean yearReadOnly = cls.yearStatus() != SchoolYearStatus.ACTIVE;
            boolean baseLocked = rules.isBaseLocked(date);
            AssessmentDayUnlockResponse unlock = baseLocked && !yearReadOnly
                    ? rules.activeUnlock(classId, date)
                            .map(u -> rules.toView(u, cls.name()))
                            .orElse(null)
                    : null;
            DailySheetResponse response = new DailySheetResponse(
                    cls.id(),
                    cls.name(),
                    cls.campusId(),
                    date,
                    rules.today(),
                    cls.yearStart(),
                    rules.canRecord(currentUser, cls) && !yearReadOnly,
                    yearReadOnly,
                    baseLocked && unlock == null,
                    unlock,
                    !statuses.isEmpty(),
                    cls.mealSessions(),
                    lesson,
                    rows,
                    classCommentRepository
                            .findByClassroomIdAndDate(classId, date)
                            .map(c -> rules.commentViews(List.of(c)).getFirst())
                            .orElse(null));
            log.info("{}|rows={}|END", logPrefix, rows.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SaveDailyResponse saveGeneral(CurrentUser currentUser, Long classId, SaveDailyGeneralRequest request) {
        String logPrefix = LOG_PREFIX + "|saveGeneral|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate date = request.date();
            ClassroomInfoResponse cls = checkWritable(currentUser, classId, date);
            if (request.entries().isEmpty() && request.classComment() == null) {
                throw GlobalException.of(ApiCode.ASSESSMENT_NOTHING_TO_SAVE);
            }
            if (request.classComment() != null) {
                String text = trim(request.classComment());
                String lower = text.toLowerCase(java.util.Locale.ROOT);
                if (DIAGNOSTIC_TERMS.stream().anyMatch(lower::contains)) {
                    throw GlobalException.of(ApiCode.CLASS_COMMENT_DIAGNOSTIC_TERM);
                }
                ClassDailyComment comment = classCommentRepository
                        .findByClassroomIdAndDate(classId, date)
                        .orElseGet(() -> ClassDailyComment.create(classId, cls.campusId(), date));
                comment.record(text, currentUser.userId(), rules.now());
                classCommentRepository.save(comment);
            }
            Map<Long, ClassChildResponse> present = presentChildren(classId, date);
            Map<Long, DailyObservation> existing = observationsOf(present.keySet(), date);
            Instant at = rules.now();
            for (SaveDailyGeneralRequest.Entry e : request.entries()) {
                ClassChildResponse child = requirePresent(present, e.childId());
                checkText(e.comment(), e.healthNote(), e.behaviorNote());
                DailyObservation o = existing.computeIfAbsent(
                        e.childId(),
                        id -> DailyObservation.start(id, classId, cls.campusId(), date, currentUser.userId(), at));
                o.recordDay(
                        e.healthOverridden(),
                        orEmpty(e.healthSigns()),
                        trim(e.healthNote()),
                        mealsJson(cls.mealSessions(), e.meals()),
                        e.nap(),
                        e.emotion(),
                        e.participation(),
                        orEmpty(e.behaviors()),
                        trim(e.behaviorNote()),
                        e.flag());
                o.changeComment(trim(e.comment()));
                o.touch(currentUser.userId(), at);
                observationRepository.save(o);
                log.debug("{}|childId={}|saved", logPrefix, child.id());
            }
            observationRepository.flush();
            SaveDailyResponse response = new SaveDailyResponse(request.entries().size(), at);
            log.info("{}|saved={}|END", logPrefix, response.saved());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SaveDailyResponse saveSkills(CurrentUser currentUser, Long classId, SaveDailySkillsRequest request) {
        String logPrefix = LOG_PREFIX + "|saveSkills|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate date = request.date();
            ClassroomInfoResponse cls = checkWritable(currentUser, classId, date);
            // EDU-19: chấm theo MT của giáo án đã duyệt; chưa có giáo án duyệt thì khóa phần này
            ApprovedDayLessonResponse lesson = lessonQueryService
                    .findApprovedDayLessons(classId, date, date)
                    .get(date);
            if (lesson == null) {
                throw GlobalException.of(ApiCode.ASSESSMENT_NO_APPROVED_LESSON);
            }
            Map<String, Set<Long>> goalsByActivity = lesson.activities().stream()
                    .collect(Collectors.toMap(
                            ApprovedDayLessonResponse.Activity::key,
                            a -> a.goals().stream()
                                    .map(ApprovedDayLessonResponse.Goal::id)
                                    .collect(Collectors.toSet())));
            Map<Long, ClassChildResponse> present = presentChildren(classId, date);
            Map<Long, DailyObservation> observations = observationsOf(present.keySet(), date);
            Map<String, DailyActivityResult> results = new LinkedHashMap<>();
            if (!present.isEmpty()) {
                resultRepository
                        .findByChildIdInAndDate(present.keySet(), date)
                        .forEach(r -> results.put(r.getChildId() + "|" + r.getActivityKey(), r));
            }
            Instant at = rules.now();
            int saved = 0;
            for (SaveDailySkillsRequest.Entry e : request.entries()) {
                requirePresent(present, e.childId());
                checkText(e.comment());
                boolean changed = false;
                for (SaveDailySkillsRequest.Activity a :
                        e.activities() == null ? List.<SaveDailySkillsRequest.Activity>of() : e.activities()) {
                    Set<Long> offered = goalsByActivity.get(a.key());
                    if (offered == null) {
                        throw GlobalException.of(ApiCode.ASSESSMENT_ACTIVITY_NOT_IN_LESSON);
                    }
                    List<Long> met = orEmpty(a.goalsMet());
                    List<Long> effort = orEmpty(a.goalsEffort());
                    List<Long> notMet = orEmpty(a.goalsNotMet());
                    checkGoals(offered, met, effort, notMet);
                    String key = e.childId() + "|" + a.key();
                    DailyActivityResult existing = results.get(key);
                    // Không chấm gì: không lưu (không tự ghi Đạt); có bản cũ thì xóa
                    if (met.isEmpty() && effort.isEmpty() && notMet.isEmpty() && a.participation() == null) {
                        if (existing != null) {
                            resultRepository.delete(existing);
                            results.remove(key);
                            changed = true;
                        }
                        continue;
                    }
                    DailyActivityResult r = existing != null
                            ? existing
                            : DailyActivityResult.start(
                                    e.childId(), classId, cls.campusId(), date, a.key(), currentUser.userId(), at);
                    r.record(lesson.classDocId(), a.participation(), met, effort, notMet);
                    r.touch(currentUser.userId(), at);
                    resultRepository.save(r);
                    results.put(key, r);
                    changed = true;
                }
                // Nhận xét 1 bản / trẻ / ngày dùng chung với view Hoạt động chung; null = giữ nguyên
                if (e.comment() != null) {
                    DailyObservation o = observations.computeIfAbsent(
                            e.childId(),
                            id -> DailyObservation.start(id, classId, cls.campusId(), date, currentUser.userId(), at));
                    o.changeComment(trim(e.comment()));
                    o.touch(currentUser.userId(), at);
                    observationRepository.save(o);
                    changed = true;
                }
                if (changed) {
                    saved++;
                }
            }
            if (saved == 0) {
                throw GlobalException.of(ApiCode.ASSESSMENT_NOTHING_TO_SAVE);
            }
            resultRepository.flush();
            observationRepository.flush();
            SaveDailyResponse response = new SaveDailyResponse(saved, at);
            log.info("{}|saved={}|END", logPrefix, saved);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void checkDate(ClassroomInfoResponse cls, LocalDate date) {
        if (date.isAfter(rules.today())) {
            throw GlobalException.of(ApiCode.ASSESSMENT_FUTURE_DATE);
        }
        if (!ObservationDayRules.isSchoolDay(date)) {
            throw GlobalException.of(ApiCode.ASSESSMENT_NOT_SCHOOL_DAY);
        }
        if (cls.yearStart() != null && date.isBefore(cls.yearStart())
                || cls.yearEnd() != null && date.isAfter(cls.yearEnd())) {
            throw GlobalException.of(ApiCode.ASSESSMENT_DATE_OUT_OF_YEAR, cls.yearStart());
        }
    }

    /** Thứ tự: quyền (giáo viên lớp) → năm học → ngày → khóa / mở lại. */
    private ClassroomInfoResponse checkWritable(CurrentUser user, Long classId, LocalDate date) {
        ClassroomInfoResponse cls = rules.findClass(classId);
        if (!rules.canRecord(user, cls)) {
            throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
        }
        // CFG-YR-01: năm đã kết thúc (hoặc chưa kích hoạt) chỉ xem
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        }
        checkDate(cls, date);
        if (rules.isBaseLocked(date) && rules.activeUnlock(classId, date).isEmpty()) {
            throw GlobalException.of(ApiCode.ASSESSMENT_DAY_LOCKED);
        }
        return cls;
    }

    private Map<Long, AttendanceStatus> statusesOf(List<ClassChildResponse> children, LocalDate date) {
        if (children.isEmpty()) {
            return Map.of();
        }
        Map<Long, AttendanceStatus> statuses = new LinkedHashMap<>();
        attendanceService
                .findDayStatuses(children.stream().map(ClassChildResponse::id).toList(), date, date)
                .forEach(s -> statuses.put(s.childId(), s.status()));
        return statuses;
    }

    private Map<Long, ClassChildResponse> presentChildren(Long classId, LocalDate date) {
        List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(classId, date);
        Map<Long, AttendanceStatus> statuses = statusesOf(children, date);
        Map<Long, ClassChildResponse> present = new LinkedHashMap<>();
        children.stream()
                .filter(c -> statuses.get(c.id()) == AttendanceStatus.PRESENT)
                .forEach(c -> present.put(c.id(), c));
        return present;
    }

    private Map<Long, DailyObservation> observationsOf(Set<Long> childIds, LocalDate date) {
        Map<Long, DailyObservation> map = new LinkedHashMap<>();
        if (!childIds.isEmpty()) {
            observationRepository.findByChildIdInAndDate(childIds, date).forEach(o -> map.put(o.getChildId(), o));
        }
        return map;
    }

    /** Chỉ trẻ đã điểm danh Có mặt của lớp trong ngày mới được đánh giá. */
    private ClassChildResponse requirePresent(Map<Long, ClassChildResponse> present, Long childId) {
        ClassChildResponse child = present.get(childId);
        if (child == null) {
            throw GlobalException.of(ApiCode.ASSESSMENT_CHILD_NOT_PRESENT, "Trẻ");
        }
        return child;
    }

    private static void checkGoals(Set<Long> offered, List<Long> met, List<Long> effort, List<Long> notMet) {
        Set<Long> seen = new HashSet<>();
        for (List<Long> list : List.of(met, effort, notMet)) {
            for (Long id : list) {
                if (!offered.contains(id)) {
                    throw GlobalException.of(ApiCode.ASSESSMENT_GOAL_NOT_IN_ACTIVITY);
                }
                if (!seen.add(id)) {
                    throw GlobalException.of(ApiCode.ASSESSMENT_GOAL_TWO_RESULTS);
                }
            }
        }
    }

    private static void checkText(String... texts) {
        for (String text : texts) {
            String lower = text == null ? "" : text.toLowerCase();
            List<String> found =
                    DIAGNOSTIC_TERMS.stream().filter(lower::contains).toList();
            if (!found.isEmpty()) {
                throw GlobalException.of(ApiCode.ASSESSMENT_DIAGNOSTIC_TERM, String.join("\", \"", found));
            }
        }
    }

    private String mealsJson(List<MealSession> sessions, Map<MealSession, EatingLevel> meals) {
        Map<MealSession, EatingLevel> kept = new EnumMap<>(MealSession.class);
        if (meals != null) {
            meals.forEach((session, level) -> {
                if (sessions.contains(session) && level != null) {
                    kept.put(session, level);
                }
            });
        }
        return objectMapper.writeValueAsString(kept);
    }

    private Map<MealSession, EatingLevel> mealsOf(String json) {
        return json == null || json.isBlank()
                ? Map.of()
                : objectMapper.readValue(json, new TypeReference<Map<MealSession, EatingLevel>>() {});
    }

    private Row toRow(
            ClassChildResponse child,
            DailyObservation o,
            List<DailyActivityResult> results,
            DailyHealthService.ChildDaySigns health,
            Map<DailyActivityResult, ActivityResult> projected) {
        List<HealthSymptom> prefill = health == null ? List.of() : health.signs();
        boolean overridden = o != null && o.isHealthOverridden();
        Health h = new Health(
                overridden ? o.getHealthSigns() : prefill,
                o != null && !o.getHealthNote().isEmpty() ? o.getHealthNote() : health == null ? "" : health.note(),
                overridden,
                prefill);
        List<ActivityResult> activities = results.stream().map(projected::get).toList();
        return new Row(
                child.id(),
                child.fullName(),
                AttendanceStatus.PRESENT,
                h,
                o == null ? Map.of() : mealsOf(o.getMeals()),
                o == null ? null : o.getNap(),
                o == null ? null : o.getEmotion(),
                o == null ? null : o.getParticipation(),
                o == null ? List.of() : o.getBehaviors(),
                o == null ? "" : o.getBehaviorNote(),
                o == null ? null : o.getFlag(),
                o == null ? "" : o.getComment(),
                o != null || !results.isEmpty(),
                activities);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
