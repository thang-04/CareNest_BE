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
import com.carenest.service.observation.SemesterAssessmentService;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
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
    private final ObservationDayRules rules;
    private final SemesterAssessmentRepository repository;
    private final ThemeEvaluationRepository themes;
    private final SchoolYearService years;
    private final EnrollmentService enrollment;
    private final AccountService accounts;
    private final ObjectMapper mapper;
    private final ThemeEvaluationService themeEvaluationService;

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
        boolean leadsGroup = user.hasPermission(Permission.ASSESSMENT_VIEW)
                && rules.teamLeaderAgeGroupIds(user).contains(cls.ageGroupId());
        if (!rules.canView(user, cls) && !leadsGroup) throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
        return cls;
    }

    private Response write(CurrentUser user, Request req, boolean confirm) {
        var cls = visible(user, req.classId());
        if (!rules.canRecord(user, cls)) throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        rules.lockClassForUpdate(cls.id());
        var board = build(user, cls.id(), req.semester(), null);
        if (board.status().equals("CONFIRMED")) throw GlobalException.of(ApiCode.EVALUATION_ALREADY_CONFIRMED);
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
        return build(user, cls.id(), req.semester(), entity);
    }

    private static boolean incomplete(Response b) {
        return b.rows().isEmpty()
                || b.domains().isEmpty()
                || b.rows().stream().anyMatch(c -> c.domains().stream().anyMatch(d -> d.met() == null));
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
                    b.rows(),
                    false,
                    false,
                    saved.getConfirmedAt(),
                    signer,
                    signersOn(classId, b.endDate()));
        }
        var year = years.getSchoolYear(user, cls.schoolYearId());
        LocalDate from = semester == 1 ? year.semester1Start() : year.semester2Start();
        LocalDate end = semester == 1 ? year.semester1End() : year.semester2End();
        if (from == null || end == null)
            throw GlobalException.of(ApiCode.SEMESTER_DATE_REQUIRED, "ngày học kỳ trong Cấu hình thời gian");
        LocalDate to = end.isAfter(rules.today()) ? rules.today() : end;
        Set<Long> selected = new HashSet<>();
        if (cls.ageGroupId() != null)
            for (var t : themes.findApprovedThemes(cls.schoolYearId(), cls.ageGroupId())) {
                // OBS-25 (Cần chốt 10/11/2026): chủ đề thuộc học kỳ = ngày kết thúc chủ đề nằm trong học kỳ
                if (!t.getEndDate().isBefore(from) && !t.getEndDate().isAfter(end))
                    themes.findThemeGoals(t.getId()).forEach(g -> selected.add(g.getLineage()));
            }
        var goals = cls.ageGroupId() == null
                ? List.<ThemeEvaluationRepository.YearGoalRow>of()
                : themes.findYearGoals(cls.schoolYearId(), cls.ageGroupId()).stream()
                        .filter(g -> selected.contains(g.getLineage()))
                        .toList();
        var children = to.isBefore(from)
                ? List.<com.carenest.dto.child.ClassChildResponse>of()
                : enrollment.findChildrenInClassroomDuring(classId, from, to);
        // OBS-25 (Cần chốt 10/11/2026): MT học kỳ lấy từ kết quả cuối các chủ đề đã xong trong kỳ (≥50% chủ đề Đạt)
        Map<Long, Map<Long, ThemeEvaluationService.ThemeGoalResult>> results = new HashMap<>();
        children.forEach(
                c -> results.put(c.id(), themeEvaluationService.childThemeResults(classId, c.id(), from, end)));
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
                    if (Objects.equals(g.getDomainId(), domain.domainId())) {
                        var r = results.get(child.id()).get(g.getLineage());
                        cells.add(new Goal(
                                g.getLineage(),
                                g.getGoalNo(),
                                g.getText(),
                                r == null ? null : r.met(),
                                r == null ? 0 : r.metThemes(),
                                r == null ? 0 : r.totalThemes()));
                    }
                domains.add(summarize(domain.domainId(), domain.name(), cells));
            }
            rows.add(new Child(child.id(), child.fullName(), domains));
        }
        boolean edit = rules.canRecord(user, cls)
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
                rows,
                edit,
                false,
                null,
                null,
                null);
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
                rows,
                edit,
                edit && !rules.today().isBefore(end) && !incomplete(response),
                null,
                null,
                signersOn(classId, end));
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
