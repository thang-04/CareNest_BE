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
        return new CurrentUser(id, "kc-" + username, username, "User " + username, null, Set.of(role));
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
    void calendarPreviewRejectsAnnualAsClassSource() {
        Long source = jdbc.queryForObject(
                "SELECT curriculum_source_plan_id FROM edu_team_plans WHERE id=?", Long.class, teamPlanId);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> classDocService.previewClassCalendar(teacher, previewRequest(source, -1)))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
    }
}
