package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import jakarta.persistence.EntityManager;
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

/**
 * T048 [US3]: Producer compatibility tests cho edu workflow:
 * - actual publisher ở published_by/completed_at
 * - DTO issued đúng người ban hành
 * - issued_by legacy chỉ lưu actual VP approval hoặc null nếu không có VP
 * - Trạng thái cuối luôn là APPROVED.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduWorkflowCompatibilityIntegrationTest {

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
    private EduYearPlanService service;

    @Autowired
    private EduPlanRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long schoolYear;
    private long ageGroup3;
    private long ageGroup4;
    private long ageGroup5;
    private long domainPhysical;
    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private CurrentUser leader3;
    private CurrentUser leader4;
    private CurrentUser leader5;
    private final Map<Long, Long> annualPlanIds = new java.util.HashMap<>();

    private void createAndIssueAnnualPlan(long ageGroupId) {
        var req = new YearPlanRequest(
                schoolYear,
                ageGroupId,
                "Kế hoạch năm học nguồn nhóm " + ageGroupId,
                "Mô tả nguồn",
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(
                                new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()),
                                new YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "Trẻ phát triển thể chất",
                                        List.of(new YearPlanRequest.Content(
                                                null, "Nội dung vận động", List.of("t-1"))))))),
                annualThemes(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of());
        UUID vpSession = UUID.randomUUID();
        var draft =
                service.createYearPlan(vicePrincipal, req, new YearPlanEditRequest(vpSession, 0L, UUID.randomUUID()));
        var sub = service.submitYearPlan(
                vicePrincipal,
                draft.id(),
                null,
                new YearPlanEditRequest(vpSession, draft.revision(), UUID.randomUUID()));
        var app = service.issueYearPlan(
                principal, draft.id(), "Ban hành", new YearPlanEditRequest(null, sub.revision(), UUID.randomUUID()));
        annualPlanIds.put(ageGroupId, app.id());
    }

    /** Quy tắc hiện hành: kế hoạch năm cần tối thiểu 9 chủ đề (chủ đề phụ 1 tuần nối tiếp). */
    private static List<YearPlanRequest.Theme> annualThemes() {
        var themes = new java.util.ArrayList<YearPlanRequest.Theme>();
        themes.add(new YearPlanRequest.Theme(
                "t-1", null, "Trường mầm non", "TMN", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25), "", ""));
        for (int i = 0; i < 8; i++) {
            LocalDate monday = LocalDate.of(2026, 9, 28).plusWeeks(i);
            themes.add(new YearPlanRequest.Theme(
                    "t-x" + i, null, "Chủ đề phụ " + i, "CDX" + i, monday, monday.plusDays(4), "", ""));
        }
        return themes;
    }

    /** Ban hành kế hoạch năm tự tạo bản nháp tổ cho nhóm tuổi (tổ trưởng là người soạn). */
    private YearPlanDetailResponse autoTeamPlan(CurrentUser leader, long ageGroupId) {
        Long id = jdbc.queryForObject(
                "SELECT id FROM edu_team_plans WHERE school_year_id = ? AND age_group_id = ? ORDER BY version_no LIMIT 1",
                Long.class,
                schoolYear,
                ageGroupId);
        // Kế hoạch năm không nhận tick: tick nội dung bản tổ vào chủ đề đầu để đủ điều kiện gửi duyệt
        jdbc.update(
                "INSERT INTO edu_content_theme_ticks (content_id, theme_id) "
                        + "SELECT c.id, (SELECT id FROM edu_themes WHERE team_plan_id = ? ORDER BY sort_order LIMIT 1) "
                        + "FROM edu_goal_contents c WHERE c.goal_id IN (SELECT id FROM edu_plan_goals WHERE team_plan_id = ?) "
                        + "ON CONFLICT DO NOTHING",
                id,
                id);
        entityManager.clear();
        return service.getYearPlan(leader, id);
    }

    @BeforeEach
    void setUp() {
        jdbc.update(
                "UPDATE edu_settings SET duration_policy = '{\"theme\":{\"minWeeks\":1,\"maxWeeks\":4},\"branch\":{\"minWeeks\":1,\"maxWeeks\":2}}'::jsonb");
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-COMPAT', 'Campus Compat') RETURNING id");
        schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        ageGroup4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        ageGroup5 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-5'", Long.class);
        domainPhysical = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);

        vicePrincipal = user("vp_compat", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                vicePrincipal.userId(),
                campus);

        principal = user("ht_compat", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-09-01')",
                principal.userId());

        leader3 = user("leader3_compat", CareNestRole.TEACHER);
        insertLeader(leader3, ageGroup3);

        leader4 = user("leader4_compat", CareNestRole.TEACHER);
        insertLeader(leader4, ageGroup4);

        leader5 = user("leader5_compat", CareNestRole.TEACHER);
        insertLeader(leader5, ageGroup5);

        createAndIssueAnnualPlan(ageGroup3);
        createAndIssueAnnualPlan(ageGroup4);
        createAndIssueAnnualPlan(ageGroup5);
    }

    @Test
    @DisplayName(
            "T048 [US3]: PHT duyệt và ban hành -> published_by = PHT, issued_by = PHT, completed_at != null, DTO issued = PHT, status = APPROVED")
    void vicePrincipalReviewsAndPublishes() {
        // Cấu hình policy: TEAM_LEADER -> VICE_PRINCIPAL (mặc định)
        YearPlanDetailResponse draft = autoTeamPlan(leader3, ageGroup3);
        YearPlanDetailResponse submitted =
                service.submitYearPlan(leader3, draft.id(), "Gửi duyệt", edit(leader3, draft.id(), true));

        YearPlanDetailResponse issued = service.issueYearPlan(
                vicePrincipal,
                draft.id(),
                "PHT duyệt và ban hành",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));

        assertThat(issued.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(issued.issued()).isNotNull();
        assertThat(issued.issued().userId()).isEqualTo(vicePrincipal.userId());

        entityManager.flush();
        entityManager.clear();

        EduPlan entity = repository.findById(draft.id()).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(entity.getPublishedBy()).isEqualTo(vicePrincipal.userId());
        assertThat(entity.getIssuedBy()).isEqualTo(vicePrincipal.userId());
        assertThat(entity.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName(
            "T048 [US3]: HT duyệt và ban hành thẳng (không qua PHT) -> published_by = HT, issued_by = null, completed_at != null, DTO issued = HT, status = APPROVED")
    void principalReviewsAndPublishesDirectlyWithoutVp() {
        // Cấu hình policy: TEAM_LEADER -> PRINCIPAL (chỉ HT duyệt)
        jdbc.update("UPDATE edu_settings SET workflow_policy = "
                + "'{\"year\":{\"creators\":[\"TEAM_LEADER\"],\"reviewers\":[\"PRINCIPAL\"]},"
                + "\"branch\":{\"creators\":[\"TEACHER\"],\"reviewers\":[\"TEAM_LEADER\"]},"
                + "\"theme\":{\"creators\":[\"TEACHER\"],\"reviewers\":[\"TEAM_LEADER\"]}}'::jsonb, "
                + "workflow_revision = workflow_revision + 1");
        entityManager.clear();

        YearPlanDetailResponse draft = autoTeamPlan(leader4, ageGroup4);
        YearPlanDetailResponse submitted =
                service.submitYearPlan(leader4, draft.id(), "Gửi duyệt cho HT", edit(leader4, draft.id(), true));

        YearPlanDetailResponse issued = service.issueYearPlan(
                principal,
                draft.id(),
                "HT duyệt ban hành trực tiếp",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));

        assertThat(issued.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(issued.issued()).isNotNull();
        assertThat(issued.issued().userId()).isEqualTo(principal.userId());

        entityManager.flush();
        entityManager.clear();

        EduPlan entity = repository.findById(draft.id()).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(entity.getPublishedBy()).isEqualTo(principal.userId());
        // Vì không có Phó HT duyệt trong chuỗi, issued_by legacy phải là null
        assertThat(entity.getIssuedBy()).isNull();
        assertThat(entity.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName(
            "T048 [US3]: Chuỗi 2 bước PHT duyệt bước 1, HT ban hành bước 2 -> published_by = HT, issued_by = PHT, DTO issued = HT, status = APPROVED")
    void twoStepReviewVicePrincipalThenPrincipal() {
        // Cấu hình policy 2 bước: TEAM_LEADER -> [VICE_PRINCIPAL, PRINCIPAL]
        jdbc.update("UPDATE edu_settings SET workflow_policy = "
                + "'{\"year\":{\"creators\":[\"TEAM_LEADER\"],\"reviewers\":[\"VICE_PRINCIPAL\",\"PRINCIPAL\"]},"
                + "\"branch\":{\"creators\":[\"TEACHER\"],\"reviewers\":[\"TEAM_LEADER\",\"VICE_PRINCIPAL\"]},"
                + "\"theme\":{\"creators\":[\"TEACHER\"],\"reviewers\":[\"TEAM_LEADER\",\"VICE_PRINCIPAL\"]}}'::jsonb, "
                + "workflow_revision = workflow_revision + 1");
        entityManager.clear();

        YearPlanDetailResponse draft = autoTeamPlan(leader5, ageGroup5);
        YearPlanDetailResponse submitted =
                service.submitYearPlan(leader5, draft.id(), "Gửi duyệt 2 bước", edit(leader5, draft.id(), true));
        assertThat(submitted.status()).isEqualTo(EduPlanStatus.REVIEW);

        // Bước 1: PHT duyệt
        YearPlanDetailResponse advanced = service.issueYearPlan(
                vicePrincipal,
                draft.id(),
                "PHT duyệt bước 1",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));
        assertThat(advanced.status()).isEqualTo(EduPlanStatus.REVIEW);

        // Bước 2: HT duyệt ban hành
        YearPlanDetailResponse approved = service.issueYearPlan(
                principal,
                draft.id(),
                "HT duyệt ban hành",
                new YearPlanEditRequest(null, advanced.revision(), UUID.randomUUID()));
        assertThat(approved.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(approved.issued()).isNotNull();
        // DTO issued phải chỉ đúng actual publisher (HT)
        assertThat(approved.issued().userId()).isEqualTo(principal.userId());

        entityManager.flush();
        entityManager.clear();

        EduPlan entity = repository.findById(draft.id()).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(EduPlanStatus.APPROVED);
        // published_by là actual publisher (HT)
        assertThat(entity.getPublishedBy()).isEqualTo(principal.userId());
        // issued_by legacy lưu PHT vì có PHT duyệt trong chuỗi
        assertThat(entity.getIssuedBy()).isEqualTo(vicePrincipal.userId());
        assertThat(entity.getCompletedAt()).isNotNull();
    }

    private YearPlanEditRequest edit(CurrentUser user, Long planId, boolean acquire) {
        YearPlanDetailResponse detail = service.getYearPlan(user, planId);
        YearPlanEditRequest context = new YearPlanEditRequest(session(user), detail.revision(), UUID.randomUUID());
        if (acquire && detail.editing() == null && detail.permissions().canEdit()) {
            service.startEditing(user, planId, context);
        }
        return context;
    }

    private UUID session(CurrentUser user) {
        return UUID.nameUUIDFromBytes(("compat-sess-" + user.userId()).getBytes());
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(id, "kc-" + username, username, "Demo " + username, null, Set.of(role));
    }

    private void insertLeader(CurrentUser user, long ageGroupId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, '2026-09-01')",
                user.userId(),
                ageGroupId);
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
