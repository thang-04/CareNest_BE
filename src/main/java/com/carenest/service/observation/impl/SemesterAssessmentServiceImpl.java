package com.carenest.service.observation.impl;

import com.carenest.dto.observation.SemesterAssessmentDtos.*;
import com.carenest.dto.observation.ThemeEvaluationDtos;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.SemesterAssessment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.SemesterAssessmentRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.eduplan.EduPlanCalendarQueryService;
import com.carenest.service.observation.EvaluationDeadlineService;
import com.carenest.service.observation.SemesterAssessmentService;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.EvaluationLockKind;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterAssessmentServiceImpl implements SemesterAssessmentService {
    private static final String LOG_PREFIX = "[SemesterAssessmentServiceImpl]";
    private static final String CONFIRMED = "CONFIRMED";
    private final ObservationDayRules rules;
    private final SemesterAssessmentRepository repository;
    private final ThemeEvaluationRepository themes;
    private final SchoolYearService years;
    private final EnrollmentService enrollment;
    private final com.carenest.service.attendance.ChildClassHistoryService childClassHistoryService;
    private final AccountService accounts;
    private final ObjectMapper mapper;
    private final ThemeEvaluationService themeEvaluationService;
    private final EduPlanCalendarQueryService calendar;
    private final EvaluationDeadlineService evaluationDeadlineService;
    private final EvaluationNotifier evaluationNotifier;

    @Override
    @Transactional(readOnly = true)
    public Response get(CurrentUser user, Long classId, int semester) {
        String logPrefix = LOG_PREFIX + "|get|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            var response = build(user, classId, semester, null);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public Response save(CurrentUser user, Request request) {
        String logPrefix = LOG_PREFIX + "|save|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            var response = write(user, request, false);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public Response confirm(CurrentUser user, Request request) {
        String logPrefix = LOG_PREFIX + "|confirm|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            var response = write(user, request, true);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private ClassroomInfoResponse visible(CurrentUser user, Long id) {
        var cls = rules.findClass(id);
        // AUTH-23 (Cần chốt 10/11/2026): tổ trưởng xem (chỉ xem) bảng học kỳ các lớp nhóm tuổi mình
        boolean leadsGroup = user.hasPermission(Permission.ASSESSMENT_VIEW) && rules.leadsClass(user, cls);
        if (!rules.canView(user, cls) && !rules.hasReadableHistory(user, cls) && !leadsGroup)
            throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
        return cls;
    }

    private Response write(CurrentUser user, Request req, boolean confirm) {
        var cls = visible(user, req.classId());
        // Phân quyền động (Cần chốt 10/11/2026): HT thu hồi quyền Ghi / Xác nhận đánh giá thì GV không lưu / xác nhận
        // được
        Permission needed = confirm ? Permission.ASSESSMENT_CONFIRM : Permission.ASSESSMENT_RECORD;
        if (!user.hasPermission(needed) || !rules.canRecordPeriod(user, cls))
            throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        if (confirm && !rules.isHomeroom(user, cls)) throw GlobalException.of(ApiCode.CONFIRM_HOMEROOM_ONLY);
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        rules.lockClassForUpdate(cls.id());
        var board = build(user, cls.id(), req.semester(), null);
        if (board.status().equals("CONFIRMED")) throw GlobalException.of(ApiCode.EVALUATION_ALREADY_CONFIRMED);
        // OBS-06 (Cần chốt 18/11/2026): quá hạn 5 ngày học sau ngày kết thúc học kỳ thì khóa, trừ khi BGH đang mở lại
        evaluationDeadlineService.checkWritable(cls, EvaluationLockKind.SEMESTER, String.valueOf(req.semester()));
        if (rules.today().isBefore(board.startDate())) throw GlobalException.of(ApiCode.THEME_EVALUATION_NOT_STARTED);
        if (confirm && rules.today().isBefore(board.endDate()))
            throw GlobalException.of(ApiCode.SEMESTER_ASSESSMENT_NOT_ENDED);
        if (confirm && incomplete(board)) throw GlobalException.of(ApiCode.SEMESTER_ASSESSMENT_INCOMPLETE);
        var entity = repository
                .findByClassroomIdAndSemester(cls.id(), req.semester())
                .orElseGet(() -> SemesterAssessment.open(
                        cls.id(),
                        cls.campusId(),
                        cls.schoolYearId(),
                        req.semester(),
                        board.startDate(),
                        board.endDate()));
        entity.save(req.note() == null ? null : req.note().trim());
        if (confirm) {
            var snapshot = build(user, cls.id(), req.semester(), entity);
            entity.confirm(mapper.writeValueAsString(snapshot), user.userId(), rules.now());
        }
        repository.saveAndFlush(entity);
        // Cần chốt 17/11/2026: xác nhận đánh giá học kỳ ⇒ báo phụ huynh từng trẻ (mẫu "Đánh giá mới của bé")
        if (confirm) board.rows().forEach(c -> evaluationNotifier.childEvaluationConfirmed(c.childId()));
        return build(user, cls.id(), req.semester(), entity);
    }

    private static boolean incomplete(Response b) {
        return b.rows().isEmpty()
                || b.domains().isEmpty()
                || b.rows().stream()
                        .anyMatch(c ->
                                c.domains().isEmpty() || c.domains().stream().anyMatch(d -> d.met() == null));
    }

    private void requireReadPeriod(CurrentUser user, ClassroomInfoResponse cls, LocalDate from, LocalDate to) {
        if (!rules.canViewRange(user, cls, from, to) && !rules.leadsClass(user, cls))
            throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
    }

    private Response build(CurrentUser user, Long classId, int semester, SemesterAssessment given) {
        var cls = visible(user, classId);
        if (semester != 1 && semester != 2)
            throw GlobalException.of(ApiCode.SEMESTER_DATE_REQUIRED, "Học kỳ I hoặc II");
        var saved = given != null
                ? given
                : repository.findByClassroomIdAndSemester(classId, semester).orElse(null);
        if (saved != null && saved.getConfirmedAt() != null) {
            var b = mapper.readValue(saved.getSnapshot(), Response.class);
            requireReadPeriod(user, cls, b.startDate(), b.endDate());
            String signer =
                    accounts.findFullNames(List.of(saved.getConfirmedBy())).get(saved.getConfirmedBy());
            return new Response(
                    b.classId(),
                    b.className(),
                    b.schoolYearId(),
                    b.yearName(),
                    b.semester(),
                    b.startDate(),
                    b.endDate(),
                    "CONFIRMED",
                    saved.getNote(),
                    b.domains(),
                    labelRows(b.rows(), cls),
                    false,
                    false,
                    CONFIRMED,
                    saved.getConfirmedAt(),
                    signer,
                    signersOn(classId, b.endDate()));
        }
        var year = years.getSchoolYear(user, cls.schoolYearId());
        LocalDate from = semester == 1 ? year.semester1Start() : year.semester2Start();
        LocalDate end = semester == 1 ? year.semester1End() : year.semester2End();
        if (from == null || end == null)
            throw GlobalException.of(ApiCode.SEMESTER_DATE_REQUIRED, "ngày học kỳ trong Cấu hình thời gian");
        requireReadPeriod(user, cls, from, end);
        LocalDate to = end.isAfter(rules.today()) ? rules.today() : end;
        Set<Long> selected = new HashSet<>();
        if (cls.ageGroupId() != null)
            // Lịch chủ đề theo lớp (lịch lớp đã duyệt, chưa có thì lịch tổ) như bảng đánh giá chủ đề
            for (var t : calendar.findApprovedThemes(classId)) {
                // OBS-25 (Cần chốt 10/11/2026): chủ đề thuộc học kỳ = ngày kết thúc chủ đề nằm trong học kỳ
                if (t.endDate() != null
                        && !t.endDate().isBefore(from)
                        && !t.endDate().isAfter(end))
                    themes.findThemeGoals(t.themeId()).forEach(g -> selected.add(g.getLineage()));
            }
        var children = to.isBefore(from)
                ? List.<com.carenest.dto.child.ClassChildResponse>of()
                // Một trẻ chỉ nằm trên bảng của lớp đang phụ trách tại cuối kỳ/đến hôm nay; dữ liệu lớp cũ vẫn được
                // childThemeResults tổng hợp theo lịch sử học, tránh xuất hiện đồng thời ở hai lớp sau chuyển lớp.
                : enrollment.findChildrenInClassroom(classId, to);
        // OBS-25 (Cần chốt 10/11/2026): MT học kỳ lấy từ kết quả cuối các chủ đề đã xong trong kỳ (≥50% chủ đề Đạt)
        Map<Long, Map<Long, ThemeEvaluationService.ThemeGoalResult>> results = new HashMap<>();
        children.forEach(
                c -> results.put(c.id(), themeEvaluationService.childThemeResults(classId, c.id(), from, end)));
        Set<Long> classroomGoals = Set.copyOf(selected);
        // Giữ mục tiêu đã học ở lớp cũ, kể cả lịch chủ đề lớp mới không gắn mục tiêu đó.
        results.values().forEach(r -> selected.addAll(r.keySet()));
        List<ThemeEvaluationRepository.YearGoalRow> goals = new ArrayList<>(
                cls.ageGroupId() == null
                        ? List.<ThemeEvaluationRepository.YearGoalRow>of()
                        : themes.findYearGoals(cls.schoolYearId(), cls.ageGroupId()).stream()
                                .filter(g -> selected.contains(g.getLineage()))
                                .toList());
        Set<Long> historicalOnly = new HashSet<>(selected);
        goals.forEach(g -> historicalOnly.remove(g.getLineage()));
        if (cls.ageGroupId() != null && !historicalOnly.isEmpty()) {
            goals.addAll(themes.findHistoricalYearGoals(cls.schoolYearId(), cls.ageGroupId(), historicalOnly));
        }
        Map<Long, String> names = new LinkedHashMap<>();
        goals.forEach(g -> names.put(g.getDomainId(), g.getDomainName() == null ? "Mục tiêu khác" : g.getDomainName()));
        List<Domain> columns = names.entrySet().stream()
                .map(e -> new Domain(e.getKey(), e.getValue(), List.of(), 0, 0, 0, null))
                .toList();
        List<Child> rows = new ArrayList<>();
        for (var child : children) {
            List<Domain> domains = new ArrayList<>();
            for (var domain : columns) {
                List<Goal> cells = new ArrayList<>();
                for (var g : goals)
                    if (Objects.equals(g.getDomainId(), domain.domainId())
                            && (classroomGoals.contains(g.getLineage())
                                    || results.get(child.id()).containsKey(g.getLineage()))) {
                        var r = results.get(child.id()).get(g.getLineage());
                        cells.add(new Goal(
                                g.getLineage(),
                                g.getGoalNo(),
                                g.getText(),
                                r == null ? null : r.met(),
                                r == null ? 0 : r.metThemes(),
                                r == null ? 0 : r.totalThemes(),
                                r == null ? null : r.className()));
                    }
                if (!cells.isEmpty()) domains.add(summarize(domain.domainId(), domain.name(), cells));
            }
            rows.add(new Child(child.id(), child.fullName(), domains, null));
        }
        boolean edit = rules.canRecordPeriod(user, cls)
                && cls.yearStatus() == SchoolYearStatus.ACTIVE
                && !rules.today().isBefore(from);
        var response = new Response(
                classId,
                cls.name(),
                year.id(),
                year.name(),
                semester,
                from,
                end,
                saved == null ? "NONE" : "DRAFT",
                saved == null ? null : saved.getNote(),
                columns,
                labelRows(rows, cls),
                edit,
                false,
                null,
                null,
                null,
                null);
        String blocked = confirmBlockedReason(user, cls, semester, response, end);
        return new Response(
                classId,
                cls.name(),
                year.id(),
                year.name(),
                semester,
                from,
                end,
                response.status(),
                response.note(),
                columns,
                labelRows(rows, cls),
                edit,
                blocked == null,
                blocked,
                null,
                null,
                signersOn(classId, end));
    }

    // Lý do nút Xác nhận khóa, theo đúng thứ tự kiểm của write(); null = bấm được
    private String confirmBlockedReason(
            CurrentUser user, ClassroomInfoResponse cls, int semester, Response board, LocalDate end) {
        if (!user.hasPermission(Permission.ASSESSMENT_CONFIRM) || !rules.canRecordPeriod(user, cls))
            return "NO_PERMISSION";
        if (!rules.isHomeroom(user, cls)) return "NOT_HOMEROOM";
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) return "YEAR_NOT_ACTIVE";
        boolean locked = evaluationDeadlineService.periodsOf(cls).stream()
                .anyMatch(p -> p.kind() == EvaluationLockKind.SEMESTER
                        && String.valueOf(semester).equals(p.periodKey())
                        && !p.writable());
        if (locked) return "DEADLINE_LOCKED";
        if (rules.today().isBefore(end)) return "NOT_ENDED";
        if (incomplete(board)) return "INCOMPLETE";
        return null;
    }

    private List<Child> labelRows(List<Child> rows, com.carenest.dto.organization.ClassroomInfoResponse cls) {
        var at = rules.today().isAfter(cls.yearEnd()) ? cls.yearEnd() : rules.today();
        var history = childClassHistoryService.statuses(
                cls.id(), rows.stream().map(Child::childId).toList(), at);
        return rows.stream()
                .map(c -> new Child(c.childId(), c.fullName(), c.domains(), history.get(c.childId())))
                .toList();
    }

    // Tên tại ngày cuối học kỳ (học kỳ chưa kết thúc: hôm nay)
    private ThemeEvaluationDtos.ThemeSigners signersOn(Long classId, LocalDate end) {
        LocalDate today = rules.today();
        return themeEvaluationService.signersAt(classId, end.isAfter(today) ? today : end);
    }

    static Domain summarize(Long id, String name, List<Goal> goals) {
        int met = (int) goals.stream().filter(g -> Boolean.TRUE.equals(g.met())).count();
        int notMet =
                (int) goals.stream().filter(g -> Boolean.FALSE.equals(g.met())).count();
        int missing = goals.size() - met - notMet;
        Boolean result = goals.isEmpty() || missing > 0 ? null : met * 2L >= goals.size();
        return new Domain(id, name, List.copyOf(goals), met, notMet, missing, result);
    }
}
