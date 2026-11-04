package com.carenest.service.observation.impl;

import com.carenest.dto.observation.YearEndSheetDtos.SheetDomain;
import com.carenest.dto.observation.YearEndSheetDtos.SheetGoal;
import com.carenest.dto.observation.YearEndSheetDtos.SheetGoalInput;
import com.carenest.dto.observation.YearEndSheetDtos.SheetMeasurement;
import com.carenest.dto.observation.YearEndSheetDtos.SheetNotes;
import com.carenest.dto.observation.YearEndSheetDtos.YearEndSheetRequest;
import com.carenest.dto.observation.YearEndSheetDtos.YearEndSheetResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository.YearGoalRow;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.service.observation.ThemeEvaluationService.ThemeGoalResult;
import com.carenest.service.observation.YearEndSheetService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
public class YearEndSheetServiceImpl implements YearEndSheetService {

    private static final String LOG_PREFIX = "[YearEndSheetServiceImpl]";
    static final int NOTE_MAX = 1000;
    private static final TypeReference<List<StoredGoal>> GOALS = new TypeReference<>() {};

    private final ObservationDayRules rules;
    private final ChildEvaluationRepository evaluationRepository;
    private final ThemeEvaluationRepository themeRepository;
    private final ThemeEvaluationService themeEvaluationService;
    private final HealthMeasurementService healthMeasurementService;
    private final AgeGroupService ageGroupService;
    private final StaffAssignmentService staffAssignmentService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    /** Ô lưu trong JSON: g = dòng gốc MT, m = đạt, x = GV sửa tay. */
    record StoredGoal(Long g, Boolean m, boolean x) {}

    private record Cell(Boolean met, boolean manual) {}

    @Override
    @Transactional(readOnly = true)
    public YearEndSheetResponse get(CurrentUser currentUser, Long evaluationId) {
        String logPrefix = LOG_PREFIX + "|get|userId=" + currentUser.userId() + "|id=" + evaluationId;
        log.info("{}|START", logPrefix);
        try {
            ChildEvaluation ev = findYearEnd(evaluationId);
            ClassroomInfoResponse cls = rules.findClass(ev.getClassroomId());
            boolean teacher = teaches(currentUser, cls);
            // Xem như đánh giá cuối năm: GV lớp; đã xác nhận thì PHT / HT điểm trường và tổ trưởng nhóm tuổi của lớp
            boolean reader = rules.readsCampus(currentUser, cls.campusId())
                    || (cls.ageGroupId() != null
                            && rules.teamLeaderAgeGroupIds(currentUser).contains(cls.ageGroupId()));
            if (!teacher && !(ev.isConfirmed() && reader)) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            YearEndSheetResponse response = view(currentUser, ev, cls);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearEndSheetResponse save(CurrentUser currentUser, Long evaluationId, YearEndSheetRequest request) {
        String logPrefix = LOG_PREFIX + "|save|userId=" + currentUser.userId() + "|id=" + evaluationId;
        log.info("{}|START", logPrefix);
        try {
            ChildEvaluation ev = findYearEnd(evaluationId);
            ClassroomInfoResponse cls = editable(currentUser, ev);
            List<YearGoalRow> goals = yearGoals(cls);
            Map<Long, ThemeGoalResult> auto = themeEvaluationService.childThemeResults(cls.id(), ev.getChildId());
            Map<Long, Cell> manual = manualCells(ev);
            Set<Long> lineages = goals.stream().map(YearGoalRow::getLineage).collect(Collectors.toSet());
            for (SheetGoalInput in : request.goals() == null ? List.<SheetGoalInput>of() : request.goals()) {
                if (!lineages.contains(in.goalId())) {
                    throw GlobalException.of(ApiCode.YEAR_SHEET_GOAL_INVALID);
                }
                ThemeGoalResult a = auto.get(in.goalId());
                Boolean autoMet = a == null ? null : a.met();
                // Trùng kết quả tổng hợp hoặc bỏ chọn: bỏ sửa tay (ô quay về kết quả hệ thống)
                if (in.met() == null || in.met().equals(autoMet)) {
                    manual.remove(in.goalId());
                } else {
                    manual.put(in.goalId(), new Cell(in.met(), true));
                }
            }
            List<StoredGoal> stored = new ArrayList<>();
            manual.forEach((g, c) -> stored.add(new StoredGoal(g, c.met(), true)));
            ev.saveSheet(
                    objectMapper.writeValueAsString(stored),
                    note(request.forChild()),
                    note(request.forTeacher()),
                    note(request.forSchool()),
                    note(request.forParent()));
            evaluationRepository.saveAndFlush(ev);
            YearEndSheetResponse response = view(currentUser, ev, cls);
            log.info("{}|manual={}|END", logPrefix, stored.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void freeze(ChildEvaluation evaluation) {
        String logPrefix = LOG_PREFIX + "|freeze|id=" + evaluation.getId();
        log.info("{}|START", logPrefix);
        try {
            if (evaluation.getPeriodType() != EvaluationPeriodType.YEAR) {
                log.info("{}|skip|END", logPrefix);
                return;
            }
            ClassroomInfoResponse cls = rules.findClass(evaluation.getClassroomId());
            Map<Long, Cell> cells = cells(evaluation, cls, yearGoals(cls), autoOf(evaluation, cls));
            List<StoredGoal> all = new ArrayList<>();
            cells.forEach((g, c) -> all.add(new StoredGoal(g, c.met(), c.manual())));
            evaluation.freezeGoals(objectMapper.writeValueAsString(all));
            log.info("{}|goals={}|END", logPrefix, all.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // ---------- tính phiếu ----------

    private List<YearGoalRow> yearGoals(ClassroomInfoResponse cls) {
        return cls.ageGroupId() == null
                ? List.of()
                : themeRepository.findYearGoals(cls.schoolYearId(), cls.ageGroupId());
    }

    // Đã xác nhận: không tính lại (phiếu đã chốt không đổi khi kế hoạch hay cuối chủ đề đổi)
    private Map<Long, ThemeGoalResult> autoOf(ChildEvaluation ev, ClassroomInfoResponse cls) {
        return frozen(ev) ? Map.of() : themeEvaluationService.childThemeResults(cls.id(), ev.getChildId());
    }

    // Mỗi MT: phiếu đã xác nhận lấy bản đã chốt; chưa xác nhận = ô sửa tay, không thì kết quả tổng hợp cuối chủ đề
    private Map<Long, Cell> cells(
            ChildEvaluation ev, ClassroomInfoResponse cls, List<YearGoalRow> goals, Map<Long, ThemeGoalResult> auto) {
        Map<Long, Cell> result = new LinkedHashMap<>();
        if (frozen(ev)) {
            readGoals(ev.getGoalResults()).forEach(s -> result.put(s.g(), new Cell(s.m(), s.x())));
            return result;
        }
        Map<Long, Cell> manual = manualCells(ev);
        for (YearGoalRow goal : goals) {
            Cell m = manual.get(goal.getLineage());
            ThemeGoalResult a = auto.get(goal.getLineage());
            if (m != null) {
                result.put(goal.getLineage(), m);
            } else if (a != null) {
                result.put(goal.getLineage(), new Cell(a.met(), false));
            }
        }
        return result;
    }

    // Đánh giá xác nhận trước khi có phiếu (chưa chốt ô nào) vẫn hiện kết quả tổng hợp, chỉ xem
    private static boolean frozen(ChildEvaluation ev) {
        return ev.isConfirmed() && ev.getGoalResults() != null;
    }

    private Map<Long, Cell> manualCells(ChildEvaluation ev) {
        Map<Long, Cell> manual = new LinkedHashMap<>();
        readGoals(ev.getGoalResults()).stream()
                .filter(StoredGoal::x)
                .forEach(s -> manual.put(s.g(), new Cell(s.m(), true)));
        return manual;
    }

    private YearEndSheetResponse view(CurrentUser user, ChildEvaluation ev, ClassroomInfoResponse cls) {
        List<YearGoalRow> goals = yearGoals(cls);
        Map<Long, ThemeGoalResult> auto = autoOf(ev, cls);
        Map<Long, Cell> cells = cells(ev, cls, goals, auto);
        Map<Long, SheetDomain> domains = new LinkedHashMap<>();
        int met = 0;
        int notMet = 0;
        for (YearGoalRow goal : goals) {
            Cell c = cells.get(goal.getLineage());
            ThemeGoalResult a = auto.get(goal.getLineage());
            Boolean result = c == null ? null : c.met();
            if (Boolean.TRUE.equals(result)) {
                met++;
            } else if (Boolean.FALSE.equals(result)) {
                notMet++;
            }
            domains.computeIfAbsent(
                            goal.getDomainId() == null ? -1L : goal.getDomainId(),
                            k -> new SheetDomain(goal.getDomainId(), goal.getDomainName(), new ArrayList<>()))
                    .goals()
                    .add(new SheetGoal(
                            goal.getLineage(),
                            goal.getGoalNo(),
                            goal.getText(),
                            result,
                            c != null && c.manual(),
                            a == null ? null : a.met(),
                            a == null ? null : source(a)));
        }
        LocalDate at = ev.getPeriodEnd().isAfter(rules.today()) ? rules.today() : ev.getPeriodEnd();
        SheetMeasurement measurement = healthMeasurementService
                .findLatestMeasurement(ev.getChildId(), at)
                .map(m -> new SheetMeasurement(m.date(), m.weightKg(), m.heightCm()))
                .orElse(null);
        boolean canEdit =
                !ev.isConfirmed() && rules.canRecord(user, cls) && cls.yearStatus() == SchoolYearStatus.ACTIVE;
        return new YearEndSheetResponse(
                ev.getId(),
                cls.id(),
                ageGroupName(cls.ageGroupId()),
                teacherNames(cls, at),
                measurement,
                List.copyOf(domains.values()),
                met,
                notMet,
                new SheetNotes(
                        ev.getNoteForChild(), ev.getNoteForTeacher(), ev.getNoteForSchool(), ev.getNoteForParent()),
                ev.getStatus(),
                canEdit,
                ev.getConfirmedAt());
    }

    // Kết quả lấy ở lớp cũ (trẻ chuyển lớp cùng nhóm tuổi) ghi kèm tên lớp
    private static String source(ThemeGoalResult r) {
        return r.className() == null ? r.themeName() : r.themeName() + " (lớp " + r.className() + ")";
    }

    // Chỗ ký "Giáo viên": GV chủ nhiệm, lớp chưa có chủ nhiệm thì các GV lớp (như bản in cuối chủ đề)
    private List<String> teacherNames(ClassroomInfoResponse cls, LocalDate at) {
        Long homeroom = staffAssignmentService
                .findHomeroomTeacherIds(List.of(cls.id()), at)
                .get(cls.id());
        List<Long> ids = homeroom != null
                ? List.of(homeroom)
                : staffAssignmentService
                        .findCurrentTeacherIds(List.of(cls.id()), at)
                        .getOrDefault(cls.id(), List.of());
        Map<Long, String> names = accountService.findFullNames(ids);
        return ids.stream().map(names::get).filter(Objects::nonNull).toList();
    }

    private String ageGroupName(Long ageGroupId) {
        return ageGroupId == null
                ? null
                : ageGroupService.getAgeGroups().stream()
                        .filter(g -> ageGroupId.equals(g.id()))
                        .map(AgeGroupDetailResponse::name)
                        .findFirst()
                        .orElse(null);
    }

    // ---------- kiểm tra ----------

    private ChildEvaluation findYearEnd(Long id) {
        ChildEvaluation ev =
                evaluationRepository.findById(id).orElseThrow(() -> GlobalException.of(ApiCode.EVALUATION_NOT_FOUND));
        if (ev.getPeriodType() != EvaluationPeriodType.YEAR) {
            throw GlobalException.of(ApiCode.YEAR_SHEET_ONLY_YEAR_END);
        }
        return ev;
    }

    // GV xem: đang dạy lớp hoặc từng dạy lớp của năm đã kết thúc (như đánh giá cuối năm)
    private boolean teaches(CurrentUser user, ClassroomInfoResponse cls) {
        return user.hasRole(CareNestRole.TEACHER) && (rules.canRecord(user, cls) || rules.taughtClosedYear(user, cls));
    }

    private ClassroomInfoResponse editable(CurrentUser user, ChildEvaluation ev) {
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

    private static String note(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        if (t.length() > NOTE_MAX) {
            throw GlobalException.of(ApiCode.YEAR_SHEET_NOTE_TOO_LONG, NOTE_MAX);
        }
        return t;
    }

    private List<StoredGoal> readGoals(String json) {
        return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, GOALS);
    }
}
