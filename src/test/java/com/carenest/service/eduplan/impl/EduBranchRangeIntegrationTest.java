package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduBranchRangeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private EduClassDocService classDocService;

    @Autowired
    private EduYearPlanService yearPlanService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private CurrentUser teacher;
    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private CurrentUser leader;
    private long classroomId;
    private long teamPlanId;
    private long theme1Id;
    private long theme2Id;

    @BeforeEach
    void setUp() {
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-BR', 'Campus BR') RETURNING id");
        long schoolYear = insertId(
                "INSERT INTO school_years (name, start_date, end_date) VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        long ageGroup = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        classroomId = insertId(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'MGB-BR', ?) RETURNING id",
                campus,
                schoolYear,
                ageGroup);

        teacher = user("teacher-br", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) VALUES (?, 'TEACHER', ?, '2026-09-01', true)",
                teacher.userId(),
                classroomId);

        vicePrincipal = user("vp-br", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                vicePrincipal.userId(),
                campus);

        principal = user("principal-br", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-09-01')",
                principal.userId());

        leader = user("leader-br", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, '2026-09-01')",
                leader.userId(),
                ageGroup);

        // Create and issue annual plan
        long domainId = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);
        var annualReq = new YearPlanRequest(
                schoolYear,
                ageGroup,
                "Annual BR",
                "",
                List.of(new YearPlanRequest.Domain(
                        domainId,
                        List.of(
                                new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()),
                                new YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "MT",
                                        List.of(new YearPlanRequest.Content(null, "ND", List.of("th1"))))))),
                List.of(
                        new YearPlanRequest.Theme(
                                "th1",
                                null,
                                "Chủ đề 1",
                                "CD1",
                                LocalDate.of(2026, 9, 7),
                                LocalDate.of(2026, 9, 18),
                                "",
                                ""),
                        new YearPlanRequest.Theme(
                                "th2",
                                null,
                                "Chủ đề 2",
                                "CD2",
                                LocalDate.of(2026, 9, 21),
                                LocalDate.of(2026, 10, 16),
                                "",
                                ""),
                        // Quy tắc hiện hành: kế hoạch năm cần tối thiểu 9 chủ đề
                        extraTheme(0),
                        extraTheme(1),
                        extraTheme(2),
                        extraTheme(3),
                        extraTheme(4),
                        extraTheme(5),
                        extraTheme(6)),
                List.of(),
                EduPlanLevel.SCHOOL_YEAR,
                null,
                // Mẫu nhánh cần đủ ngày bắt đầu/kết thúc
                List.of(new YearPlanRequest.BranchTemplate(
                        "tmpl1", null, "th1", "Mẫu dự án 1", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11))));
        UUID vpSession = UUID.randomUUID();
        var annualDraft = yearPlanService.createYearPlan(
                vicePrincipal, annualReq, new YearPlanEditRequest(vpSession, 0L, UUID.randomUUID()));
        var annualSub = yearPlanService.submitYearPlan(
                vicePrincipal,
                annualDraft.id(),
                null,
                new YearPlanEditRequest(vpSession, annualDraft.revision(), UUID.randomUUID()));
        var annualApp = yearPlanService.issueYearPlan(
                principal,
                annualDraft.id(),
                "Approved",
                new YearPlanEditRequest(null, annualSub.revision(), UUID.randomUUID()));

        // Ban hành kế hoạch năm tự tạo bản nháp tổ cho nhóm tuổi; tổ trưởng gửi duyệt bản đó
        UUID leaderSession = UUID.randomUUID();
        Long teamId = jdbc.queryForObject(
                "SELECT id FROM edu_team_plans WHERE school_year_id = ? AND age_group_id = ? ORDER BY version_no LIMIT 1",
                Long.class,
                schoolYear,
                ageGroup);
        // Kế hoạch năm không nhận tick: tick nội dung bản tổ vào chủ đề đầu để đủ điều kiện gửi duyệt
        jdbc.update(
                "INSERT INTO edu_content_theme_ticks (content_id, theme_id) "
                        + "SELECT c.id, (SELECT id FROM edu_themes WHERE team_plan_id = ? ORDER BY sort_order LIMIT 1) "
                        + "FROM edu_goal_contents c WHERE c.goal_id IN (SELECT id FROM edu_plan_goals WHERE team_plan_id = ?) "
                        + "ON CONFLICT DO NOTHING",
                teamId,
                teamId);
        entityManager.clear();
        var teamDraft = yearPlanService.getYearPlan(leader, teamId);
        var teamSub = yearPlanService.submitYearPlan(
                leader,
                teamDraft.id(),
                null,
                new YearPlanEditRequest(leaderSession, teamDraft.revision(), UUID.randomUUID()));
        var teamApp = yearPlanService.issueYearPlan(
                vicePrincipal,
                teamDraft.id(),
                "Approved",
                new YearPlanEditRequest(null, teamSub.revision(), UUID.randomUUID()));
        teamPlanId = teamApp.id();

        theme1Id = teamApp.themes().get(0).id();
        theme2Id = teamApp.themes().get(1).id();
    }

    /** Chủ đề phụ 2 tuần nối tiếp sau chủ đề 2 (từ 19/10/2026), đúng giới hạn thời lượng mặc định. */
    private static YearPlanRequest.Theme extraTheme(int no) {
        LocalDate monday = LocalDate.of(2026, 10, 19).plusWeeks(2L * no);
        return new YearPlanRequest.Theme(
                "x" + no, null, "Chủ đề phụ " + no, "CDX" + no, monday, monday.plusDays(11), "", "");
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username,
                username,
                "User " + username);
        return new CurrentUser(
                id,
                "kc-" + username,
                username,
                "User " + username,
                null,
                Set.of(role),
                com.carenest.security.TestPermissions.eduDefaultsOf(role));
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private ClassCalendarPreviewRequest previewRequest(Long planId, long expectedVersion) {
        var draft = new ClassCalendarPreviewRequest.CalendarDraft(
                List.of(
                        new ClassCalendarPreviewRequest.ThemeSchedule(
                                theme1Id, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 18)),
                        new ClassCalendarPreviewRequest.ThemeSchedule(
                                theme2Id, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 16))),
                List.of());
        return new ClassCalendarPreviewRequest(
                classroomId, planId, expectedVersion, draft, List.of(theme2Id, theme1Id));
    }

    @Test
    void calendarPreviewRejectsNonAuthorAndStaleVersionWithoutWriting() {
        Long before = jdbc.queryForObject("SELECT count(*) FROM edu_class_docs", Long.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> classDocService.previewClassCalendar(vicePrincipal, previewRequest(teamPlanId, -1)))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> classDocService.previewClassCalendar(teacher, previewRequest(teamPlanId, 9)))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_class_docs", Long.class))
                .isEqualTo(before);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "EDU-100: học kỳ chưa duyệt xóa được nhánh kèm kế hoạch tuần, giáo án Nháp; đã duyệt chỉ đổi tên/ngày; kế hoạch ngày đã ban hành khóa nhánh")
    void classBranchEditFollowsSemesterAndDossierStatus() {
        jdbc.update(
                "UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2027-01-15', "
                        + "semester2_start='2027-01-18', semester2_end='2027-05-31' "
                        + "WHERE id = (SELECT school_year_id FROM classrooms WHERE id = ?)",
                classroomId);
        var themes = jdbc.queryForList(
                "SELECT id, start_date, end_date FROM edu_themes WHERE team_plan_id = ? ORDER BY sort_order",
                teamPlanId);
        // Chủ đề phụ 1 (02/11–13/11/2026) còn ở tương lai so với đồng hồ 22/10/2026, thuộc học kỳ 1
        long themeId = ((Number) themes.get(3).get("id")).longValue();
        String key = "bp_" + classroomId + "_" + teamPlanId;
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        ObjectNode saved = classDocService.saveClassDoc(
                teacher,
                "branch",
                key,
                calendar(
                        themes,
                        branch(a, themeId, "2026-11-02", "2026-11-06", "Nhánh A"),
                        branch(b, themeId, "2026-11-09", "2026-11-13", "Nhánh B")),
                -1L,
                null);
        for (String prefix : List.of("wp_", "dd_"))
            jdbc.update(
                    "INSERT INTO edu_class_docs (doc_key, kind, classroom_id, theme_id, week_start, created_by, updated_by, team_plan_id) "
                            + "VALUES (?, ?, ?, ?, '2026-11-09', ?, ?, ?)",
                    prefix + classroomId + "_2026-11-09",
                    prefix.equals("wp_") ? "week" : "day",
                    classroomId,
                    themeId,
                    teacher.userId(),
                    teacher.userId(),
                    teamPlanId);
        entityManager.flush();
        entityManager.clear();

        // (1)+(3) học kỳ chưa duyệt: xóa nhánh B ⇒ xóa kế hoạch tuần, giáo án ngày Nháp của tuần 09/11
        saved = classDocService.saveClassDoc(
                teacher,
                "branch",
                key,
                calendar(themes, branch(a, themeId, "2026-11-02", "2026-11-13", "Nhánh A")),
                saved.path("version").asLong(),
                null);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_class_docs WHERE classroom_id = ? AND week_start = '2026-11-09'",
                        Long.class,
                        classroomId))
                .isZero();

        // (2) EDU-111 (1) Tiền 25/11/2026: khóa theo hồ sơ chủ đề, không theo học kỳ ⇒ học kỳ 1 đã duyệt mà chủ đề chưa
        // gửi thì vẫn thêm, đổi tên/ngày nhánh được
        String sp = "sp_" + classroomId + "_" + teamPlanId + "_1";
        assertThat(jdbc.update("UPDATE edu_class_docs SET status = 'APPROVED' WHERE doc_key = ?", sp))
                .isOne();
        entityManager.clear();
        long version = saved.path("version").asLong();
        saved = classDocService.saveClassDoc(
                teacher,
                "branch",
                key,
                calendar(
                        themes,
                        branch(a, themeId, "2026-11-02", "2026-11-06", "Nhánh A"),
                        branch(UUID.randomUUID().toString(), themeId, "2026-11-09", "2026-11-13", "Nhánh mới")),
                version,
                null);
        assertThat(saved.path("branches")).hasSize(2);
        saved = classDocService.saveClassDoc(
                teacher,
                "branch",
                key,
                calendar(themes, branch(a, themeId, "2026-11-02", "2026-11-13", "Nhánh A đổi tên")),
                saved.path("version").asLong(),
                null);
        assertThat(saved.path("branches").get(0).path("name").asString()).isEqualTo("Nhánh A đổi tên");

        // (2b) phiên bản điều chỉnh: kế hoạch giáo dục chủ đề về Nháp, còn bản ban hành cũ ⇒ sửa như Nháp, thêm nhánh
        // được
        jdbc.update(
                "INSERT INTO edu_class_doc_releases (class_doc_id, dossier_version, print_snapshot, approved_by, released_at) "
                        + "SELECT id, version, '{}', ?, now() FROM edu_class_docs WHERE doc_key = ?",
                teacher.userId(),
                sp);
        jdbc.update("UPDATE edu_class_docs SET status = 'DRAFT' WHERE doc_key = ?", sp);
        entityManager.clear();
        saved = classDocService.saveClassDoc(
                teacher,
                "branch",
                key,
                calendar(
                        themes,
                        branch(a, themeId, "2026-11-02", "2026-11-06", "Nhánh A đổi tên"),
                        branch(b, themeId, "2026-11-09", "2026-11-13", "Nhánh B thêm lại")),
                saved.path("version").asLong(),
                null);
        assertThat(saved.path("branches")).hasSize(2);

        // (2a) kế hoạch giáo dục ngày của chủ đề đã ban hành ⇒ nhánh của chủ đề khóa
        jdbc.update(
                "INSERT INTO edu_class_docs (doc_key, kind, classroom_id, theme_id, status, created_by, updated_by, team_plan_id) "
                        + "VALUES (?, 'dossier', ?, ?, 'APPROVED', ?, ?, ?) "
                        + "ON CONFLICT (doc_key) DO UPDATE SET status = 'APPROVED'",
                "ds_" + classroomId + "_" + themeId,
                classroomId,
                themeId,
                teacher.userId(),
                teacher.userId(),
                teamPlanId);
        entityManager.clear();
        long last = saved.path("version").asLong();
        assertCalendarInvalid(() -> classDocService.saveClassDoc(
                teacher,
                "branch",
                key,
                calendar(themes, branch(a, themeId, "2026-11-02", "2026-11-06", "Nhánh A lần 2")),
                last,
                null));

        // (2b) phiên bản điều chỉnh của kế hoạch giáo dục ngày (về Nháp) ⇒ nhánh của chủ đề sửa lại được
        jdbc.update(
                "UPDATE edu_class_docs SET status = 'DRAFT' WHERE kind = 'dossier' AND classroom_id = ? AND theme_id = ?",
                classroomId,
                themeId);
        entityManager.clear();
        saved = classDocService.saveClassDoc(
                teacher,
                "branch",
                key,
                calendar(themes, branch(a, themeId, "2026-11-02", "2026-11-13", "Nhánh A lần 2")),
                last,
                null);
        assertThat(saved.path("branches")).hasSize(1);
    }

    private void assertCalendarInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        org.assertj.core.api.Assertions.assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        com.carenest.exception.GlobalException.class,
                        e -> assertThat(e.getApiCode())
                                .isEqualTo(com.carenest.utils.ApiCode.EDUCATION_CALENDAR_INVALID));
    }

    private ObjectNode branch(String key, long themeId, String start, String end, String name) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", key);
        node.put("themeId", themeId);
        node.putNull("sourceTemplateId");
        node.put("name", name);
        node.put("startDate", start);
        node.put("endDate", end);
        node.putObject("weekNotes");
        return node;
    }

    private ObjectNode calendar(List<java.util.Map<String, Object>> themes, ObjectNode... branches) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("schemaVersion", 2);
        doc.put("classId", String.valueOf(classroomId));
        doc.put("yearPlanId", String.valueOf(teamPlanId));
        var schedules = doc.putArray("themeSchedules");
        for (var t : themes) {
            ObjectNode s = schedules.addObject();
            s.put("themeId", ((Number) t.get("id")).longValue());
            s.put("startDate", t.get("start_date").toString());
            s.put("endDate", t.get("end_date").toString());
        }
        var list = doc.putArray("branches");
        for (ObjectNode b : branches) list.add(b);
        return doc;
    }

    @Test
    void calendarPreviewRejectsAnnualAsClassSource() {
        Long source = jdbc.queryForObject(
                "SELECT curriculum_source_plan_id FROM edu_team_plans WHERE id=?", Long.class, teamPlanId);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> classDocService.previewClassCalendar(teacher, previewRequest(source, -1)))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
    }
}
