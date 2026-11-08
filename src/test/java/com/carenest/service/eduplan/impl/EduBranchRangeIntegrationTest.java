package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.eduplan.ClassCalendarPreviewResponse;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduBranchRangeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

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
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from) VALUES (?, 'TEACHER', ?, '2026-09-01')",
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
                        List.of(new YearPlanRequest.Row(
                                null, "goal", "MT", List.of(new YearPlanRequest.Content(null, "ND", List.of())))))),
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
                                "")),
                List.of(),
                EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of(new YearPlanRequest.BranchTemplate("tmpl1", null, "th1", "Mẫu dự án 1", null, null)));
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

        // Create and issue team plan
        UUID leaderSession = UUID.randomUUID();
        var teamDraft = yearPlanService.createYearPlan(
                leader,
                new YearPlanRequest(
                        schoolYear,
                        ageGroup,
                        "Team BR",
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        EduPlanLevel.TEAM,
                        annualApp.id(),
                        List.of()),
                new YearPlanEditRequest(leaderSession, 0L, UUID.randomUUID()));
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

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username,
                username,
                "User " + username);
        return new CurrentUser(id, "kc-" + username, username, "User " + username, null, Set.of(role));
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    @DisplayName("T040: previewClassCalendar is read-only and calculates reordered dates")
    void previewClassCalendarIsReadOnly() {
        Long initialDocCount = jdbc.queryForObject("SELECT count(*) FROM edu_class_docs", Long.class);

        var draft = new ClassCalendarPreviewRequest.CalendarDraft(
                List.of(
                        new ClassCalendarPreviewRequest.ThemeSchedule(
                                theme1Id, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 18)),
                        new ClassCalendarPreviewRequest.ThemeSchedule(
                                theme2Id, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 16))),
                List.of(new ClassCalendarPreviewRequest.BranchSchedule(
                        "22222222-2222-4222-8222-222222222222",
                        theme1Id,
                        null,
                        "Nhánh 1",
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2026, 9, 18),
                        Map.of("0", "N1", "1", "N2"))));

        var req = new ClassCalendarPreviewRequest(classroomId, teamPlanId, -1L, draft, List.of(theme2Id, theme1Id));
        ClassCalendarPreviewResponse preview = classDocService.previewClassCalendar(teacher, req);

        assertThat(preview.countsBefore().themes()).containsEntry(String.valueOf(theme1Id), 2);
        assertThat(preview.countsBefore().themes()).containsEntry(String.valueOf(theme2Id), 4);
        assertThat(preview.calendar().themeSchedules().getFirst().themeId()).isEqualTo(theme2Id);
        assertThat(preview.calendar().themeSchedules().getFirst().startDate()).isEqualTo(LocalDate.of(2026, 9, 7));

        // Verify nothing was persisted
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_class_docs", Long.class))
                .isEqualTo(initialDocCount);
    }

    @Test
    @DisplayName("T040: saveClassDoc with orderedThemeIds reorders and saves with schemaVersion=2 projections")
    void saveClassDocWithOrderedThemeIds() {
        String docKey = "bp_" + classroomId + "_" + teamPlanId;
        ObjectNode docNode = objectMapper.createObjectNode();
        docNode.put("classId", classroomId);
        docNode.put("yearPlanId", teamPlanId);
        docNode.put("schemaVersion", 2);

        ArrayNode themeSchedules = docNode.putArray("themeSchedules");
        ObjectNode t1 = themeSchedules.addObject();
        t1.put("themeId", theme1Id);
        t1.put("startDate", "2026-09-07");
        t1.put("endDate", "2026-09-18");
        ObjectNode t2 = themeSchedules.addObject();
        t2.put("themeId", theme2Id);
        t2.put("startDate", "2026-09-21");
        t2.put("endDate", "2026-10-16");

        ArrayNode branches = docNode.putArray("branches");
        ObjectNode b1 = branches.addObject();
        b1.put("key", "11111111-1111-4111-8111-111111111111");
        b1.put("themeId", theme1Id);
        b1.put("name", "Bản lớp nhánh 1");
        b1.put("startDate", "2026-09-07");
        b1.put("endDate", "2026-09-18");
        ObjectNode weekNotes = b1.putObject("weekNotes");
        weekNotes.put("0", "Ghi chú tuần 1");
        weekNotes.put("1", "Ghi chú tuần 2");

        // Include orderedThemeIds to switch theme2 to first
        ArrayNode ordered = docNode.putArray("orderedThemeIds");
        ordered.add(theme2Id);
        ordered.add(theme1Id);

        ObjectNode saved = classDocService.saveClassDoc(teacher, "branch", docKey, docNode, -1L, null);

        assertThat(saved.path("calendarRevision").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(saved.path("names").path("2026-10-05").asText()).isEqualTo("Bản lớp nhánh 1");
        assertThat(saved.path("notes").path("2026-10-05").asText()).isEqualTo("Ghi chú tuần 1");
        assertThat(saved.path("notes").path("2026-10-12").asText()).isEqualTo("Ghi chú tuần 2");
        JsonNode snapshot = saved.path("durationPolicySnapshot").deepCopy();
        jdbc.update(
                "UPDATE edu_settings SET duration_policy = '{\"theme\":{\"minWeeks\":2,\"maxWeeks\":8},\"branch\":{\"minWeeks\":1,\"maxWeeks\":4}}'::jsonb, duration_revision = duration_revision + 1 WHERE id=1");
        ObjectNode resave = saved.deepCopy();
        resave.remove("orderedThemeIds");
        resave.remove("names");
        resave.remove("notes");
        ObjectNode again = classDocService.saveClassDoc(
                teacher, "branch", docKey, resave, saved.path("version").asLong(), null);
        assertThat(again.path("durationPolicySnapshot")).isEqualTo(snapshot);
        jdbc.update("UPDATE edu_class_docs SET status='APPROVED' WHERE doc_key=?", docKey);
        entityManager.clear();
        ObjectNode themeDoc = objectMapper.createObjectNode();
        themeDoc.put("classId", classroomId);
        themeDoc.put("yearPlanId", teamPlanId);
        themeDoc.put("themeId", theme1Id);
        ObjectNode root = classDocService.saveClassDoc(
                teacher, "theme", "tp_" + classroomId + "_" + theme1Id, themeDoc, -1L, null);
        assertThat(root.path("effectiveCalendar")
                        .path("themeSchedules")
                        .get(0)
                        .path("themeId")
                        .asLong())
                .isEqualTo(theme2Id);
        ObjectNode week = themeDoc.deepCopy();
        week.put("weekStart", "2026-10-05");
        ObjectNode firstWeek = classDocService.saveClassDoc(
                teacher,
                "week",
                "wp_" + classroomId + "_2026-10-05",
                week,
                -1L,
                root.path("version").asLong());
        week.put("weekStart", "2026-10-12");
        classDocService.saveClassDoc(
                teacher,
                "week",
                "wp_" + classroomId + "_2026-10-12",
                week,
                -1L,
                firstWeek.path("dossierVersion").asLong());
        assertThat(jdbc.queryForObject("SELECT start_date FROM edu_themes WHERE id=?", LocalDate.class, theme1Id))
                .isEqualTo(LocalDate.of(2026, 9, 7));
        week.put("weekStart", "2026-09-07");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> classDocService.saveClassDoc(
                        teacher, "week", "wp_" + classroomId + "_2026-09-07", week, -1L, 0L))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        // Kiểm tra downgrade/source trên hồ sơ nháp riêng với dữ liệu đã lưu.
        jdbc.update("UPDATE edu_class_docs SET status='DRAFT' WHERE doc_key=?", docKey);
        entityManager.clear();
        ObjectNode downgrade = again.deepCopy();
        downgrade.remove("schemaVersion");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> classDocService.saveClassDoc(
                        teacher,
                        "branch",
                        docKey,
                        downgrade,
                        again.path("version").asLong(),
                        null))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        ObjectNode forged = again.deepCopy();
        ((ObjectNode) forged.path("branches").get(0)).put("sourceTemplateId", 999999L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> classDocService.saveClassDoc(
                        teacher, "branch", docKey, forged, again.path("version").asLong(), null))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
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
    void calendarPreviewRejectsAnnualAsClassSource() {
        Long source = jdbc.queryForObject(
                "SELECT curriculum_source_plan_id FROM edu_year_plans WHERE id=?", Long.class, teamPlanId);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> classDocService.previewClassCalendar(teacher, previewRequest(source, -1)))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
    }
}
