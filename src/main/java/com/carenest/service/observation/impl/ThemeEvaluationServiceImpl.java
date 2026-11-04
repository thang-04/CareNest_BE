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
import com.carenest.dto.organization.AgeGroupDetailResponse;
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
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
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
    // OBS-27 (Cần chốt 04/11/2026): trẻ đạt chủ đề khi đạt từ 70% số MT đã chấm; ngưỡng cố định
    static final int PASS_PERCENT = 70;
    private static final int KINDERGARTEN_FROM_MONTHS = 36;
    private static final TypeReference<List<StoredCell>> CELLS = new TypeReference<>() {};

    private final ObservationDayRules rules;
    private final ThemeEvaluationRepository evaluationRepository;
    private final DailyActivityResultRepository resultRepository;
    private final EnrollmentService enrollmentService;
    private final EduLessonQueryService lessonQueryService;
    private final AgeGroupService ageGroupService;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    /** Ô lưu trong JSON: c = trẻ, g = dòng gốc MT, m = đạt, x = GV sửa tay. */
    record StoredCell(Long c, Long g, Boolean m, boolean x) {}

    private record Key(Long childId, Long goal) {}

    @Override
    @Transactional(readOnly = true)
    public List<ClassItem> listClasses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|listClasses|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> kindergarten = kindergartenAgeGroupIds();
            Set<Long> ledGroups = rules.teamLeaderAgeGroupIds(currentUser);
            List<ClassroomInfoResponse> year = schoolYearId == null
                    ? classroomService.findActiveYearClassrooms()
                    : classroomService.findYearClassrooms(schoolYearId);
            // Cùng phạm vi xem với bảng đánh giá: GV lớp mình, tổ trưởng lớp nhóm tuổi mình, HT/PHT theo điểm trường
            List<ClassItem> items = year.stream()
                    .filter(c -> kindergarten.contains(c.ageGroupId()))
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
            ClassroomInfoResponse cls = readableKindergarten(currentUser, classId);
            Map<Long, ThemeEvaluation> saved = evaluationRepository.findByClassroomId(classId).stream()
                    .collect(Collectors.toMap(ThemeEvaluation::getThemeId, Function.identity()));
            LocalDate today = rules.today();
            List<ThemeItem> items =
                    evaluationRepository.findApprovedThemes(cls.schoolYearId(), cls.ageGroupId()).stream()
                            .map(t -> new ThemeItem(
                                    t.getId(),
                                    t.getName(),
                                    t.getAbbr(),
                                    t.getStartDate(),
                                    t.getEndDate(),
                                    period(t.getStartDate(), t.getEndDate(), today),
                                    status(saved.get(t.getLineage()))))
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
            ClassroomInfoResponse cls = readableKindergarten(currentUser, classId);
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
            ClassroomInfoResponse cls = readableKindergarten(currentUser, request.classId());
            ThemeRow theme = findTheme(cls, request.themeId());
            ThemeEvaluation evaluation = editable(currentUser, cls, theme);
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
            ClassroomInfoResponse cls = readableKindergarten(currentUser, request.classId());
            ThemeRow theme = findTheme(cls, request.themeId());
            ThemeEvaluation evaluation = editable(currentUser, cls, theme);
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
            evaluationRepository.saveAndFlush(evaluation);
            ThemeEvaluationResponse response = view(currentUser, cls, theme, evaluation);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // ---------- quyền, chủ đề ----------

    // Xem: GV lớp (cả năm đã kết thúc), HT/PHT mọi điểm trường (AUTH-20), tổ trưởng các lớp nhóm tuổi mình (AUTH-23)
    private ClassroomInfoResponse readableKindergarten(CurrentUser user, Long classId) {
        ClassroomInfoResponse cls = rules.findClass(classId);
        boolean reads = rules.canRecord(user, cls)
                || rules.taughtClosedYear(user, cls)
                || rules.readsCampus(user, cls.campusId())
                || rules.teamLeaderAgeGroupIds(user).contains(cls.ageGroupId());
        if (!reads) {
            throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
        }
        if (!kindergartenAgeGroupIds().contains(cls.ageGroupId())) {
            throw GlobalException.of(ApiCode.THEME_EVALUATION_KINDERGARTEN_ONLY);
        }
        return cls;
    }

    // Nhóm tuổi mẫu giáo: từ 36 tháng (nhà trẻ không đánh giá cuối chủ đề)
    private Set<Long> kindergartenAgeGroupIds() {
        return ageGroupService.getAgeGroups().stream()
                .filter(g -> g.ageFromMonths() != null && g.ageFromMonths() >= KINDERGARTEN_FROM_MONTHS)
                .map(AgeGroupDetailResponse::id)
                .collect(Collectors.toSet());
    }

    private ThemeRow findTheme(ClassroomInfoResponse cls, Long themeId) {
        return evaluationRepository.findApprovedThemes(cls.schoolYearId(), cls.ageGroupId()).stream()
                // Link cũ của bản kế hoạch trước vẫn mở được: khớp id hoặc dòng gốc
                .filter(t -> t.getId().equals(themeId) || t.getLineage().equals(themeId))
                .findFirst()
                .orElseThrow(() -> GlobalException.of(ApiCode.THEME_EVALUATION_THEME_NOT_FOUND));
    }

    // Sửa: GV lớp, năm học đang hoạt động, chủ đề đã bắt đầu, bảng chưa xác nhận; tạo bảng tuần tự theo lớp
    private ThemeEvaluation editable(CurrentUser user, ClassroomInfoResponse cls, ThemeRow theme) {
        if (!rules.canRecord(user, cls)) {
            throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        }
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        }
        if (rules.today().isBefore(theme.getStartDate())) {
            throw GlobalException.of(ApiCode.THEME_EVALUATION_NOT_STARTED);
        }
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
        Set<Long> lineages = goals.stream().map(GoalRow::getLineage).collect(Collectors.toSet());
        Map<Key, Boolean> auto = autoResults(cls, children, lineages, theme.getStartDate(), to);
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

    // Mỗi ô = lần chấm cuối cùng của MT trong thời gian chủ đề, chỉ kết quả ghi ở lớp này (trẻ chuyển lớp không mang
    // sang); MT trên giáo án nối với MT chủ đề qua dòng gốc (kế hoạch có bản mới vẫn khớp)
    private Map<Key, Boolean> autoResults(
            ClassroomInfoResponse cls,
            List<ClassChildResponse> children,
            Set<Long> lineages,
            LocalDate from,
            LocalDate to) {
        if (children.isEmpty() || lineages.isEmpty() || to.isBefore(from)) {
            return Map.of();
        }
        List<DailyActivityResult> results = resultRepository
                .findByChildIdInAndDateBetween(
                        children.stream().map(ClassChildResponse::id).toList(), from, to)
                .stream()
                .filter(r -> cls.id().equals(r.getClassroomId()))
                .sorted(Comparator.comparing(DailyActivityResult::getDate)
                        .thenComparing(r -> r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getRecordedAt()))
                .toList();
        Set<Long> goalIds = new HashSet<>();
        results.forEach(r -> {
            goalIds.addAll(r.getGoalsMet());
            goalIds.addAll(r.getGoalsNotMet());
            goalIds.addAll(r.getGoalsEffort());
        });
        Map<Long, Long> lineageOf = lessonQueryService.findGoalLineages(goalIds);
        Map<Key, Boolean> latest = new HashMap<>();
        for (DailyActivityResult r : results) {
            mark(latest, r.getChildId(), r.getGoalsMet(), true, lineageOf, lineages);
            mark(latest, r.getChildId(), r.getGoalsNotMet(), false, lineageOf, lineages);
            mark(latest, r.getChildId(), r.getGoalsEffort(), false, lineageOf, lineages);
        }
        return latest;
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
        Matrix matrix = matrixOf(cls, theme, saved);
        LocalDate today = rules.today();
        boolean confirmed = saved != null && saved.isConfirmed();
        boolean canEdit = !confirmed
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
                canEdit && !today.isBefore(theme.getEndDate()),
                confirmedByName,
                confirmed ? saved.getConfirmedAt() : null,
                signers(cls, theme, today));
    }

    // Tên tại ngày cuối chủ đề (chủ đề chưa kết thúc: hôm nay); PHT không rõ người ban hành thì lấy PHT duy nhất
    private ThemeSigners signers(ClassroomInfoResponse cls, ThemeRow theme, LocalDate today) {
        LocalDate at = theme.getEndDate().isAfter(today) ? today : theme.getEndDate();
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
                : staffAssignmentService.findActiveTeamLeaderIds(cls.ageGroupId(), at);
        Long issuer = cls.ageGroupId() == null
                ? null
                : evaluationRepository.findApprovedPlanIssuer(cls.schoolYearId(), cls.ageGroupId());
        List<Long> ids = new ArrayList<>(teacherIds);
        ids.addAll(leaderIds);
        if (issuer != null) {
            ids.add(issuer);
        }
        Map<Long, String> names = accountService.findFullNames(ids);
        String vicePrincipal = issuer != null ? names.get(issuer) : null;
        if (vicePrincipal == null) {
            List<String> vps = accountService.findActiveFullNamesByRole(CareNestRole.VICE_PRINCIPAL);
            vicePrincipal = vps.size() == 1 ? vps.getFirst() : null;
        }
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
