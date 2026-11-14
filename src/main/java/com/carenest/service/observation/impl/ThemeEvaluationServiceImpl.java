package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.ThemeEvaluationDtos.Cell;
import com.carenest.dto.observation.ThemeEvaluationDtos.CellInput;
import com.carenest.dto.observation.ThemeEvaluationDtos.ChildRow;
import com.carenest.dto.observation.ThemeEvaluationDtos.ClassItem;
import com.carenest.dto.observation.ThemeEvaluationDtos.GoalColumn;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationRequest;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationResponse;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeItem;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeSigners;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.entity.observation.ThemeEvaluation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository.GoalRow;
import com.carenest.repository.observation.ThemeEvaluationRepository.ThemeRow;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.eduplan.EduPlanCalendarQueryService;
import com.carenest.service.observation.EvaluationDeadlineService;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.EvaluationLockKind;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeEvaluationServiceImpl implements ThemeEvaluationService {

    private static final String LOG_PREFIX = "[ThemeEvaluationServiceImpl]";
    // OBS-27 (chốt 05/11/2026): trẻ đạt chủ đề khi đạt từ 70% số MT đã chấm; ngưỡng cố định
    static final int PASS_PERCENT = 70;
    private static final TypeReference<List<StoredCell>> CELLS = new TypeReference<>() {};

    private final ObservationDayRules rules;
    private final ThemeEvaluationRepository evaluationRepository;
    private final DailyActivityResultRepository resultRepository;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final EduLessonQueryService lessonQueryService;
    private final EduPlanCalendarQueryService calendarQueryService;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;
    private final EvaluationDeadlineService evaluationDeadlineService;

    /** Ô lưu trong JSON: c = trẻ, g = dòng gốc MT, m = đạt, x = GV sửa tay. */
    record StoredCell(Long c, Long g, Boolean m, boolean x) {}

    private record Key(Long childId, Long goal) {}

    private record ChildDay(Long childId, LocalDate date) {}

    @Override
    @Transactional(readOnly = true)
    public List<ClassItem> listClasses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|listClasses|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> ledGroups = rules.teamLeaderAgeGroupIds(currentUser, schoolYearId);
            List<ClassroomInfoResponse> year = schoolYearId == null
                    ? classroomService.findActiveYearClassrooms()
                    : classroomService.findYearClassrooms(schoolYearId);
            // Cùng phạm vi xem với bảng đánh giá: GV lớp mình, tổ trưởng lớp nhóm tuổi mình, HT/PHT theo điểm trường;
            // mọi nhóm tuổi (Cần chốt 04/11/2026, gồm cả nhà trẻ)
            List<ClassItem> items = year.stream()
                    .filter(c -> c.ageGroupId() != null)
                    .map(c -> {
                        boolean teaches = rules.canRecord(currentUser, c) || rules.taughtClosedYear(currentUser, c);
                        boolean reads = teaches
                                || rules.readsCampus(currentUser, c.campusId())
                                || ledGroups.contains(c.ageGroupId());
                        return reads ? new ClassItem(c.id(), c.name(), c.campusId(), c.ageGroupId(), teaches) : null;
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ClassItem::teaches)
                            .reversed()
                            .thenComparing(ClassItem::campusId)
                            .thenComparing(ClassItem::name))
                    .toList();
            log.info("{}|classes={}|END", logPrefix, items.size());
            return items;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThemeItem> listThemes(CurrentUser currentUser, Long classId) {
        String logPrefix = LOG_PREFIX + "|listThemes|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = readableClass(currentUser, classId);
            Map<Long, ThemeEvaluation> saved = evaluationRepository.findByClassroomId(classId).stream()
                    .collect(Collectors.toMap(ThemeEvaluation::getThemeId, Function.identity()));
            LocalDate today = rules.today();
            List<ThemeRow> themes = effectiveThemes(cls);
            List<ThemeItem> items = themes.stream()
                    .map(t -> new ThemeItem(
                            t.getId(),
                            t.getName(),
                            t.getAbbr(),
                            t.getStartDate(),
                            t.getEndDate(),
                            period(t.getStartDate(), t.getEndDate(), today),
                            status(saved.get(t.getLineage()))))
                    .toList();
            Map<Long, ThemeItem> union = new LinkedHashMap<>();
            items.forEach(item -> union.put(item.themeId(), item));
            for (ThemeEvaluation evaluation : saved.values()) {
                ThemeEvaluationResponse frozen = snapshot(evaluation);
                if (evaluation.isConfirmed() && frozen != null) {
                    Long displayId = themes.stream()
                            .filter(t -> t.getLineage().equals(evaluation.getThemeId()))
                            .map(ThemeRow::getId)
                            .findFirst()
                            .orElse(frozen.themeId());
                    union.put(
                            displayId,
                            new ThemeItem(
                                    displayId,
                                    frozen.themeName(),
                                    null,
                                    frozen.startDate(),
                                    frozen.endDate(),
                                    period(frozen.startDate(), frozen.endDate(), today),
                                    "CONFIRMED"));
                }
            }
            items = union.values().stream()
                    .sorted(Comparator.comparing(ThemeItem::startDate))
                    .toList();
            log.info("{}|themes={}|END", logPrefix, items.size());
            return items;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ThemeEvaluationResponse get(CurrentUser currentUser, Long classId, Long themeId) {
        String logPrefix = LOG_PREFIX + "|get|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = readableClass(currentUser, classId);
            ThemeEvaluation frozen = evaluationRepository.findByClassroomId(classId).stream()
                    .filter(e -> e.isConfirmed()
                            && (e.getThemeId().equals(themeId)
                                    || snapshot(e) != null
                                            && snapshot(e).themeId().equals(themeId)))
                    .findFirst()
                    .orElse(null);
            if (frozen != null && snapshot(frozen) != null) {
                log.info("{}|END", logPrefix);
                return snapshot(frozen);
            }
            ThemeRow theme = findTheme(cls, themeId);
            ThemeEvaluation saved = evaluationRepository
                    .findByClassroomIdAndThemeId(classId, theme.getLineage())
                    .orElse(null);
            ThemeEvaluationResponse response = view(currentUser, cls, theme, saved);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ThemeEvaluationResponse save(CurrentUser currentUser, ThemeEvaluationRequest request) {
        String logPrefix = LOG_PREFIX + "|save|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = readableClass(currentUser, request.classId());
            ThemeRow theme = findTheme(cls, request.themeId());
            ThemeEvaluation evaluation = editable(currentUser, cls, theme, Permission.ASSESSMENT_RECORD);
            Matrix matrix = matrixOf(cls, theme, evaluation);
            evaluation.save(
                    writeCells(overrides(matrix, request.cells())), note(request), currentUser.userId(), rules.now());
            evaluationRepository.saveAndFlush(evaluation);
            ThemeEvaluationResponse response = view(currentUser, cls, theme, evaluation);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ThemeEvaluationResponse confirm(CurrentUser currentUser, ThemeEvaluationRequest request) {
        String logPrefix = LOG_PREFIX + "|confirm|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = readableClass(currentUser, request.classId());
            ThemeRow theme = findTheme(cls, request.themeId());
            ThemeEvaluation evaluation = editable(currentUser, cls, theme, Permission.ASSESSMENT_CONFIRM);
            if (rules.today().isBefore(theme.getEndDate())) {
                throw GlobalException.of(ApiCode.THEME_EVALUATION_NOT_ENDED);
            }
            Matrix base = matrixOf(cls, theme, evaluation);
            List<StoredCell> manual = overrides(base, request.cells());
            // Xác nhận: lưu cả bảng (kết quả tính + ô sửa tay) để sau này kế hoạch hay đánh giá ngày đổi cũng không đổi
            Matrix finalMatrix = base.withManual(manual);
            List<StoredCell> all = new ArrayList<>();
            finalMatrix.cells.forEach((k, v) -> all.add(new StoredCell(k.childId(), k.goal(), v.met(), v.manual())));
            evaluation.confirm(writeCells(all), note(request), currentUser.userId(), rules.now());
            ThemeEvaluationResponse response = view(currentUser, cls, theme, evaluation);
            evaluation.setPresentationSnapshot(objectMapper.writeValueAsString(response));
            evaluationRepository.saveAndFlush(evaluation);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ThemeGoalResult> childThemeResults(Long classId, Long childId) {
        return childThemeResults(classId, childId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ThemeGoalResult> childThemeResults(Long classId, Long childId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|childThemeResults|classId=" + classId + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            Map<Long, ThemeGoalResult> results = new LinkedHashMap<>();
            if (cls.ageGroupId() == null) {
                log.info("{}|goals=0|END", logPrefix);
                return results;
            }
            LocalDate today = rules.today();
            // Trẻ chuyển lớp giữa năm (Cần chốt 04/11/2026): lấy cả lớp cũ cùng năm, cùng nhóm tuổi (cùng kế hoạch năm,
            // MT khớp); theo thứ tự vào lớp, lớp hiện tại cuối cùng
            List<ClassroomInfoResponse> classes = new ArrayList<>();
            enrollmentService.findChildStays(childId, cls.yearStart(), cls.yearEnd()).stream()
                    .map(EnrollmentService.ChildClassStay::classroomId)
                    .distinct()
                    .filter(id -> !id.equals(classId))
                    .map(rules::findClass)
                    .filter(c -> cls.schoolYearId().equals(c.schoolYearId())
                            && cls.ageGroupId().equals(c.ageGroupId()))
                    .forEach(classes::add);
            classes.add(cls);
            // Gộp (Cần + Tiền, 11/11/2026): lịch chủ đề theo lớp (effectiveThemes) và bảng đã xác nhận dùng snapshot
            // (Tiền);
            // chỉ chủ đề đã tới ngày kết thúc, lọc theo kỳ, một chủ đề tính một lần, có lớp Đạt là Đạt, MT Đạt khi ≥50%
            // số chủ đề có kết quả (OBS-28, Cần)
            Map<ThemeGoal, ThemeValue> byTheme = new LinkedHashMap<>();
            java.util.function.BiFunction<ThemeValue, ThemeValue, ThemeValue> union = (a, b) -> {
                Set<String> names = new TreeSet<>(a.classNames());
                names.addAll(b.classNames());
                return new ThemeValue(a.met() || b.met(), a.themeName(), Set.copyOf(names));
            };
            for (ClassroomInfoResponse c : classes) {
                Map<Long, ThemeEvaluation> savedByTheme = evaluationRepository.findByClassroomId(c.id()).stream()
                        .collect(Collectors.toMap(ThemeEvaluation::getThemeId, Function.identity()));
                String className = c.id().equals(classId) ? null : c.name();
                Set<String> classNames = className == null ? Set.of() : Set.of(className);
                List<ThemeRow> themes = effectiveThemes(c);
                Set<Long> lineages = new HashSet<>();
                for (ThemeRow theme : themes) {
                    lineages.add(theme.getLineage());
                    if (outsidePeriod(theme.getEndDate(), today, from, to)) {
                        continue;
                    }
                    ThemeEvaluation saved = savedByTheme.get(theme.getLineage());
                    ThemeEvaluationResponse frozen = snapshot(saved);
                    if (frozen != null) {
                        mergeFrozen(byTheme, theme.getLineage(), frozen, childId, classNames, union);
                        continue;
                    }
                    Matrix matrix = matrixOf(c, theme, saved);
                    for (GoalRow goal : matrix.goals()) {
                        Value v = matrix.cells().get(new Key(childId, goal.getLineage()));
                        if (v != null && v.met() != null) {
                            byTheme.merge(
                                    new ThemeGoal(theme.getLineage(), goal.getLineage()),
                                    new ThemeValue(v.met(), theme.getName(), classNames),
                                    union);
                        }
                    }
                }
                // Bảng đã xác nhận của chủ đề không còn trong lịch hiện tại (Tiền): vẫn tính theo snapshot đã chốt
                for (ThemeEvaluation saved : savedByTheme.values()) {
                    ThemeEvaluationResponse frozen = snapshot(saved);
                    if (frozen == null
                            || lineages.contains(saved.getThemeId())
                            || outsidePeriod(frozen.endDate(), today, from, to)) {
                        continue;
                    }
                    mergeFrozen(byTheme, saved.getThemeId(), frozen, childId, classNames, union);
                }
            }
            Map<Long, List<ThemeValue>> byGoal = new LinkedHashMap<>();
            byTheme.forEach((key, value) -> byGoal.computeIfAbsent(key.goal(), ignored -> new ArrayList<>())
                    .add(value));
            byGoal.forEach((goal, values) -> {
                int met = (int) values.stream().filter(ThemeValue::met).count();
                int total = values.size();
                String themeNames =
                        values.stream().map(ThemeValue::themeName).distinct().collect(Collectors.joining(", "));
                String classNames = values.stream()
                        .flatMap(value -> value.classNames().stream())
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(", "));
                results.put(
                        goal,
                        new ThemeGoalResult(
                                met * 2L >= total, met, total, themeNames, classNames.isBlank() ? null : classNames));
            });
            log.info("{}|goals={}|END", logPrefix, results.size());
            return results;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private record ThemeGoal(Long theme, Long goal) {}

    private record ThemeValue(boolean met, String themeName, Set<String> classNames) {}

    // Chủ đề chưa tới ngày kết thúc (đang dạy) hoặc kết thúc ngoài kỳ [from, to] thì không tính
    private static boolean outsidePeriod(LocalDate endDate, LocalDate today, LocalDate from, LocalDate to) {
        return endDate == null
                || today.isBefore(endDate)
                || (from != null && endDate.isBefore(from))
                || (to != null && endDate.isAfter(to));
    }

    private static void mergeFrozen(
            Map<ThemeGoal, ThemeValue> byTheme,
            Long theme,
            ThemeEvaluationResponse frozen,
            Long childId,
            Set<String> classNames,
            java.util.function.BiFunction<ThemeValue, ThemeValue, ThemeValue> union) {
        frozen.rows().stream()
                .filter(r -> r.childId().equals(childId))
                .forEach(row -> row.cells().stream()
                        .filter(cell -> cell.met() != null)
                        .forEach(cell -> byTheme.merge(
                                new ThemeGoal(theme, cell.goalId()),
                                new ThemeValue(cell.met(), frozen.themeName(), classNames),
                                union)));
    }

    // ---------- quyền, chủ đề ----------

    // Xem: GV lớp (cả năm đã kết thúc), HT/PHT mọi điểm trường (AUTH-20), tổ trưởng các lớp nhóm tuổi mình (AUTH-23)
    private ClassroomInfoResponse readableClass(CurrentUser user, Long classId) {
        ClassroomInfoResponse cls = rules.findClass(classId);
        boolean reads = rules.canRecord(user, cls)
                || rules.taughtClosedYear(user, cls)
                || rules.readsCampus(user, cls.campusId())
                || rules.leadsClass(user, cls);
        if (!reads) {
            throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
        }
        return cls;
    }

    private List<ThemeRow> effectiveThemes(ClassroomInfoResponse cls) {
        Map<Long, ThemeRow> source =
                evaluationRepository.findApprovedThemes(cls.schoolYearId(), cls.ageGroupId()).stream()
                        .collect(Collectors.toMap(ThemeRow::getId, Function.identity()));
        List<ThemeRow> themes = new ArrayList<>(calendarQueryService.findApprovedThemes(cls.id()).stream()
                .map(t -> (ThemeRow) new ThemeRow() {
                    public Long getId() {
                        return t.themeId();
                    }

                    public Long getLineage() {
                        return t.lineageId();
                    }

                    public String getName() {
                        return t.name();
                    }

                    public String getAbbr() {
                        return source.containsKey(t.themeId())
                                ? source.get(t.themeId()).getAbbr()
                                : null;
                    }

                    public LocalDate getStartDate() {
                        return t.startDate();
                    }

                    public LocalDate getEndDate() {
                        return t.endDate();
                    }
                })
                .toList());
        for (ThemeEvaluation saved : evaluationRepository.findByClassroomId(cls.id())) {
            if (!saved.isConfirmed() || snapshot(saved) != null) continue;
            Long id = themes.stream()
                    .filter(t -> t.getLineage().equals(saved.getThemeId()))
                    .map(ThemeRow::getId)
                    .findFirst()
                    .orElse(saved.getThemeId());
            themes.removeIf(t -> t.getLineage().equals(saved.getThemeId()));
            themes.add(new ThemeRow() {
                public Long getId() {
                    return id;
                }

                public Long getLineage() {
                    return saved.getThemeId();
                }

                public String getName() {
                    return saved.getThemeName();
                }

                public String getAbbr() {
                    return null;
                }

                public LocalDate getStartDate() {
                    return saved.getStartDate();
                }

                public LocalDate getEndDate() {
                    return saved.getEndDate();
                }
            });
        }
        return themes.stream()
                .sorted(Comparator.comparing(ThemeRow::getStartDate))
                .toList();
    }

    private ThemeEvaluationResponse snapshot(ThemeEvaluation saved) {
        if (saved == null || !saved.isConfirmed() || saved.getPresentationSnapshot() == null) return null;
        var node = objectMapper.readTree(saved.getPresentationSnapshot());
        return node.has("themeId") ? objectMapper.treeToValue(node, ThemeEvaluationResponse.class) : null;
    }

    private ThemeRow findTheme(ClassroomInfoResponse cls, Long themeId) {
        return effectiveThemes(cls).stream()
                // Link cũ của bản kế hoạch trước vẫn mở được: khớp id hoặc dòng gốc
                .filter(t -> t.getId().equals(themeId) || t.getLineage().equals(themeId))
                .findFirst()
                .orElseThrow(() -> GlobalException.of(ApiCode.THEME_EVALUATION_THEME_NOT_FOUND));
    }

    // Sửa: GV lớp, năm học đang hoạt động, chủ đề đã bắt đầu, bảng chưa xác nhận; tạo bảng tuần tự theo lớp
    private ThemeEvaluation editable(
            CurrentUser user, ClassroomInfoResponse cls, ThemeRow theme, Permission requiredPermission) {
        if (!user.hasPermission(requiredPermission) || !rules.canRecord(user, cls)) {
            throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        }
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        }
        if (rules.today().isBefore(theme.getStartDate())) {
            throw GlobalException.of(ApiCode.THEME_EVALUATION_NOT_STARTED);
        }
        // OBS-27: quá hạn sau ngày kết thúc chủ đề thì khóa cả lớp, trừ khi BGH đang mở lại
        evaluationDeadlineService.checkWritable(cls, EvaluationLockKind.THEME, String.valueOf(theme.getLineage()));
        rules.lockClassForUpdate(cls.id());
        ThemeEvaluation evaluation = evaluationRepository
                .findByClassroomIdAndThemeId(cls.id(), theme.getLineage())
                .orElseGet(() -> ThemeEvaluation.open(
                        cls.id(),
                        cls.campusId(),
                        cls.schoolYearId(),
                        theme.getLineage(),
                        theme.getName(),
                        theme.getStartDate(),
                        theme.getEndDate(),
                        rules.now()));
        if (evaluation.isConfirmed()) {
            throw GlobalException.of(ApiCode.EVALUATION_ALREADY_CONFIRMED);
        }
        return evaluation;
    }

    // ---------- tính bảng ----------

    private record Value(Boolean met, boolean manual) {}

    private record Matrix(
            List<ClassChildResponse> children, List<GoalRow> goals, Map<Key, Value> cells, Map<Key, Boolean> auto) {

        Matrix withManual(List<StoredCell> manual) {
            Map<Key, Value> next = new LinkedHashMap<>();
            auto.forEach((k, v) -> next.put(k, new Value(v, false)));
            manual.forEach(c -> next.put(new Key(c.c(), c.g()), new Value(c.m(), true)));
            return new Matrix(children, goals, next, auto);
        }
    }

    private Matrix matrixOf(ClassroomInfoResponse cls, ThemeRow theme, ThemeEvaluation saved) {
        List<GoalRow> goals = evaluationRepository.findThemeGoals(theme.getId());
        LocalDate today = rules.today();
        LocalDate to = theme.getEndDate().isAfter(today) ? today : theme.getEndDate();
        List<ClassChildResponse> children = to.isBefore(theme.getStartDate())
                ? List.of()
                : enrollmentService.findChildrenInClassroomDuring(cls.id(), theme.getStartDate(), to).stream()
                        .sorted(Comparator.comparing(ClassChildResponse::fullName))
                        .toList();
        if (saved != null && saved.isConfirmed() && saved.getPresentationSnapshot() != null) {
            var baseline = objectMapper.readTree(saved.getPresentationSnapshot());
            if (baseline.has("origin")) {
                children = objectMapper.convertValue(
                        baseline.path("children"), new TypeReference<List<ClassChildResponse>>() {});
                goals = new ArrayList<>();
                for (var g : baseline.path("goals")) {
                    goals.add(new GoalRow() {
                        public Long getId() {
                            return g.path("id").asLong();
                        }

                        public Long getLineage() {
                            return g.path("lineage").asLong();
                        }

                        public Short getGoalNo() {
                            return g.path("goalNo").isNull()
                                    ? null
                                    : (short) g.path("goalNo").asInt();
                        }

                        public String getText() {
                            return g.path("text").asText();
                        }
                    });
                }
            }
        }
        Set<Long> lineages = goals.stream().map(GoalRow::getLineage).collect(Collectors.toSet());
        Map<Key, Boolean> auto = saved != null && saved.isConfirmed()
                ? Map.of()
                : autoResults(cls, children, lineages, theme.getStartDate(), to);
        Matrix base = new Matrix(children, goals, Map.of(), auto);
        List<StoredCell> stored = saved == null ? List.of() : readCells(saved.getCells());
        if (saved != null && saved.isConfirmed()) {
            // Bảng đã xác nhận: hiện đúng bảng đã lưu
            Map<Key, Value> frozen = new LinkedHashMap<>();
            stored.forEach(c -> frozen.put(new Key(c.c(), c.g()), new Value(c.m(), c.x())));
            return new Matrix(children, goals, frozen, auto);
        }
        return base.withManual(stored.stream().filter(StoredCell::x).toList());
    }

    // OBS-27 (chốt 09/11/2026): 1 mục tiêu chỉ cần có 1 hoạt động (mọi ngày chủ đề) mà trẻ đạt → Đạt.
    // Trẻ nghỉ / chưa chấm (không có hoạt động nào được chấm) → Chưa đạt của ngày đó. Nối MT qua dòng gốc.
    private Map<Key, Boolean> autoResults(
            ClassroomInfoResponse cls,
            List<ClassChildResponse> children,
            Set<Long> lineages,
            LocalDate from,
            LocalDate to) {
        if (children.isEmpty() || lineages.isEmpty() || to.isBefore(from)) {
            return Map.of();
        }
        Set<ChildDay> presentDays = attendanceService.findClassDays(List.of(cls.id()), from, to).stream()
                .filter(d -> d.status() == AttendanceStatus.PRESENT)
                .map(d -> new ChildDay(d.childId(), d.date()))
                .collect(Collectors.toSet());
        List<DailyActivityResult> results = resultRepository
                .findByChildIdInAndDateBetween(
                        children.stream().map(ClassChildResponse::id).toList(), from, to)
                .stream()
                .filter(r -> cls.id().equals(r.getClassroomId()))
                .filter(r -> presentDays.contains(new ChildDay(r.getChildId(), r.getDate())))
                .toList();
        Set<Long> goalIds = new HashSet<>();
        results.forEach(r -> {
            goalIds.addAll(r.getGoalsMet());
            goalIds.addAll(r.getGoalsNotMet());
            goalIds.addAll(r.getGoalsEffort());
        });
        Map<Long, Long> lineageOf = lessonQueryService.findGoalLineages(goalIds);
        Map<Key, Boolean> anyMet = new HashMap<>();
        for (DailyActivityResult r : results) {
            Map<Key, Boolean> activity = new HashMap<>();
            mark(activity, r.getChildId(), r.getGoalsMet(), true, lineageOf, lineages);
            mark(activity, r.getChildId(), r.getGoalsNotMet(), false, lineageOf, lineages);
            mark(activity, r.getChildId(), r.getGoalsEffort(), false, lineageOf, lineages);
            activity.forEach((key, met) -> {
                if (met) {
                    anyMet.put(key, true);
                } else if (!anyMet.containsKey(key)) {
                    anyMet.put(key, false);
                }
            });
        }
        // Trẻ nghỉ/chưa chấm: mọi mục tiêu còn trống của trẻ đó → Chưa đạt (không để trống)
        for (ClassChildResponse child : children) {
            for (Long lineage : lineages) {
                Key key = new Key(child.id(), lineage);
                if (!anyMet.containsKey(key)) {
                    anyMet.put(key, false);
                }
            }
        }
        return anyMet;
    }

    private static void mark(
            Map<Key, Boolean> latest,
            Long childId,
            List<Long> goals,
            boolean met,
            Map<Long, Long> lineageOf,
            Set<Long> lineages) {
        for (Long goal : goals) {
            Long lineage = lineageOf.getOrDefault(goal, goal);
            if (lineages.contains(lineage)) {
                latest.put(new Key(childId, lineage), met);
            }
        }
    }

    // Ô GV gửi: khác kết quả tính thì giữ như sửa tay, met null hoặc trùng kết quả tính thì bỏ sửa tay
    private List<StoredCell> overrides(Matrix matrix, List<CellInput> inputs) {
        Set<Long> childIds =
                matrix.children().stream().map(ClassChildResponse::id).collect(Collectors.toSet());
        Set<Long> lineages = matrix.goals().stream().map(GoalRow::getLineage).collect(Collectors.toSet());
        Map<Key, Value> current = new LinkedHashMap<>(matrix.cells());
        for (CellInput in : inputs == null ? List.<CellInput>of() : inputs) {
            if (!childIds.contains(in.childId()) || !lineages.contains(in.goalId())) {
                throw GlobalException.of(ApiCode.THEME_EVALUATION_CELL_INVALID);
            }
            Key key = new Key(in.childId(), in.goalId());
            Boolean auto = matrix.auto().get(key);
            if (in.met() == null || in.met().equals(auto)) {
                current.remove(key);
            } else {
                current.put(key, new Value(in.met(), true));
            }
        }
        return current.entrySet().stream()
                .filter(e -> e.getValue().manual())
                .map(e -> new StoredCell(
                        e.getKey().childId(), e.getKey().goal(), e.getValue().met(), true))
                .toList();
    }

    // ---------- trả về ----------

    private ThemeEvaluationResponse view(
            CurrentUser user, ClassroomInfoResponse cls, ThemeRow theme, ThemeEvaluation saved) {
        ThemeEvaluationResponse frozen = snapshot(saved);
        if (frozen != null) return frozen;
        Matrix matrix = matrixOf(cls, theme, saved);
        LocalDate today = rules.today();
        boolean confirmed = saved != null && saved.isConfirmed();
        boolean canEdit = !confirmed
                && user.hasPermission(Permission.ASSESSMENT_RECORD)
                && rules.canRecord(user, cls)
                && cls.yearStatus() == SchoolYearStatus.ACTIVE
                && !today.isBefore(theme.getStartDate());
        List<ChildRow> rows = new ArrayList<>();
        Map<Long, int[]> perGoal = new HashMap<>();
        int passed = 0;
        int notPassed = 0;
        for (ClassChildResponse child : matrix.children()) {
            List<Cell> cells = new ArrayList<>();
            int met = 0;
            int notMet = 0;
            for (GoalRow goal : matrix.goals()) {
                Key key = new Key(child.id(), goal.getLineage());
                Value v = matrix.cells().get(key);
                Boolean result = v == null ? null : v.met();
                cells.add(new Cell(
                        goal.getLineage(),
                        result,
                        v != null && v.manual(),
                        matrix.auto().get(key)));
                if (result != null) {
                    int[] counts = perGoal.computeIfAbsent(goal.getLineage(), k -> new int[2]);
                    counts[1]++;
                    if (result) {
                        counts[0]++;
                        met++;
                    } else {
                        notMet++;
                    }
                }
            }
            int assessed = met + notMet;
            Boolean pass = assessed == 0 ? null : met * 100 >= PASS_PERCENT * assessed;
            if (Boolean.TRUE.equals(pass)) {
                passed++;
            } else if (Boolean.FALSE.equals(pass)) {
                notPassed++;
            }
            rows.add(new ChildRow(
                    child.id(),
                    child.fullName(),
                    child.dateOfBirth(),
                    cells,
                    met,
                    notMet,
                    assessed == 0 ? null : Math.round(met * 100f / assessed),
                    pass));
        }
        List<GoalColumn> goals = matrix.goals().stream()
                .map(g -> {
                    int[] c = perGoal.getOrDefault(g.getLineage(), new int[2]);
                    return new GoalColumn(
                            g.getLineage(),
                            g.getGoalNo(),
                            g.getText(),
                            c[0],
                            c[1],
                            c[1] == 0 ? null : Math.round(c[0] * 100f / c[1]));
                })
                .toList();
        String confirmedByName = confirmed
                ? accountService.findFullNames(List.of(saved.getConfirmedBy())).get(saved.getConfirmedBy())
                : null;
        return new ThemeEvaluationResponse(
                cls.id(),
                cls.name(),
                theme.getId(),
                theme.getName(),
                theme.getStartDate(),
                theme.getEndDate(),
                period(theme.getStartDate(), theme.getEndDate(), today),
                status(saved),
                saved == null ? null : saved.getNote(),
                goals,
                rows,
                passed + notPassed,
                passed,
                notPassed,
                PASS_PERCENT,
                canEdit,
                !confirmed
                        && user.hasPermission(Permission.ASSESSMENT_CONFIRM)
                        && rules.canRecord(user, cls)
                        && cls.yearStatus() == SchoolYearStatus.ACTIVE
                        && !today.isBefore(theme.getEndDate()),
                confirmedByName,
                confirmed ? saved.getConfirmedAt() : null,
                signers(cls, theme, today));
    }

    // Tên tại ngày cuối chủ đề (chủ đề chưa kết thúc: hôm nay); PHT không rõ người ban hành thì lấy PHT duy nhất
    private ThemeSigners signers(ClassroomInfoResponse cls, ThemeRow theme, LocalDate today) {
        return signersOf(cls, theme.getEndDate().isAfter(today) ? today : theme.getEndDate());
    }

    @Override
    @Transactional(readOnly = true)
    public ThemeSigners signersAt(Long classId, LocalDate at) {
        String logPrefix = LOG_PREFIX + "|signersAt|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ThemeSigners signers = signersOf(rules.findClass(classId), at);
            log.info("{}|END", logPrefix);
            return signers;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private ThemeSigners signersOf(ClassroomInfoResponse cls, LocalDate at) {
        // Mẫu ký "Giáo viên chủ nhiệm": GV chủ nhiệm của lớp, lớp chưa có chủ nhiệm thì các GV lớp
        Long homeroom = staffAssignmentService
                .findHomeroomTeacherIds(List.of(cls.id()), at)
                .get(cls.id());
        List<Long> teacherIds = homeroom != null
                ? List.of(homeroom)
                : staffAssignmentService
                        .findCurrentTeacherIds(List.of(cls.id()), at)
                        .getOrDefault(cls.id(), List.of());
        Set<Long> leaderIds = cls.ageGroupId() == null
                ? Set.of()
                : staffAssignmentService.findYearTeamLeaderIds(cls.ageGroupId(), cls.schoolYearId(), at);
        // Ô "Phó hiệu trưởng": PHT phụ trách điểm trường của lớp tại ngày in, theo phân công năm học đó; không có thì
        // để
        // trống cho ghi tay (không lấy người ban hành kế hoạch vì có thể là HT)
        List<Long> vpIds = staffAssignmentService.findCampusVicePrincipalIds(cls.campusId(), at).stream()
                .sorted()
                .toList();
        List<Long> ids = new ArrayList<>(teacherIds);
        ids.addAll(leaderIds);
        ids.addAll(vpIds);
        Map<Long, String> names = accountService.findFullNames(ids);
        String vicePrincipal = vpIds.stream()
                .map(names::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new ThemeSigners(
                teacherIds.stream().map(names::get).filter(Objects::nonNull).toList(),
                leaderIds.stream()
                        .map(names::get)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList(),
                vicePrincipal);
    }

    private static String period(LocalDate start, LocalDate end, LocalDate today) {
        return today.isBefore(start) ? "UPCOMING" : today.isAfter(end) ? "ENDED" : "ONGOING";
    }

    private static String status(ThemeEvaluation saved) {
        return saved == null ? "NONE" : saved.isConfirmed() ? "CONFIRMED" : "DRAFT";
    }

    private static String note(ThemeEvaluationRequest request) {
        return request.note() == null || request.note().isBlank()
                ? null
                : request.note().trim();
    }

    private List<StoredCell> readCells(String json) {
        return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, CELLS);
    }

    private String writeCells(List<StoredCell> cells) {
        return objectMapper.writeValueAsString(cells);
    }
}
