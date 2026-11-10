package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.entity.eduplan.EduDocumentAuthor;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduDocumentAuthorRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduDocumentAuthorIntegrationTest {

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
    private EduYearPlanService yearPlanService;

    @Autowired
    private EduClassDocService classDocService;

    @Autowired
    private EduWorkflowService workflowService;

    @Autowired
    private EduDocumentAuthorRepository authorRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<Long, UUID> sessions = new ConcurrentHashMap<>();

    private long schoolYear;
    private long ageGroup3;
    private long campus;
    private long classId;
    private CurrentUser leaderA;
    private CurrentUser leaderB;
    private CurrentUser teacher1;
    private CurrentUser teacher2;
    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private Long annualPlanId;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-AUTH', 'Campus Auth') RETURNING id");
        schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2026-2027-AU', '2026-09-01', '2027-05-31') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        classId = insertId(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'MGB-AU', ?) "
                        + "RETURNING id",
                campus,
                schoolYear,
                ageGroup3);

        leaderA = user("leaderA", CareNestRole.TEACHER);
        insertAssignment(leaderA.userId(), "AGE_GROUP_TEAM_LEADER", null, ageGroup3, null);

        leaderB = user("leaderB", CareNestRole.TEACHER);

        teacher1 = user("teacher1", CareNestRole.TEACHER);
        // Chỉ GV chủ nhiệm được soạn hồ sơ lớp
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', true)",
                teacher1.userId(),
                classId);

        teacher2 = user("teacher2", CareNestRole.TEACHER);
        insertAssignment(teacher2.userId(), "TEACHER", null, null, classId);

        vicePrincipal = user("vp-auth", CareNestRole.VICE_PRINCIPAL);
        insertAssignment(vicePrincipal.userId(), "VICE_PRINCIPAL", campus, null, null);

        principal = user("ht-auth", CareNestRole.PRINCIPAL);
        insertAssignment(principal.userId(), "PRINCIPAL", null, null, null);

        long domainPhysical = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);
        var annualReq = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Annual Auth",
                "",
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(
                                new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()),
                                new YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "Mục tiêu 1",
                                        List.of(new YearPlanRequest.Content(null, "ND", List.of())))))),
                annualThemes(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of());
        UUID vpSession = UUID.randomUUID();
        var annualDraft = yearPlanService.createYearPlan(
                vicePrincipal, annualReq, new YearPlanEditRequest(vpSession, 0L, UUID.randomUUID()));
        var annualSub = yearPlanService.submitYearPlan(
                vicePrincipal,
                annualDraft.id(),
                null,
                new YearPlanEditRequest(vpSession, annualDraft.revision(), UUID.randomUUID()));
        annualPlanId = yearPlanService
                .issueYearPlan(
                        principal,
                        annualDraft.id(),
                        "Approved",
                        new YearPlanEditRequest(null, annualSub.revision(), UUID.randomUUID()))
                .id();
        // Ban hành năm tự tạo bản tổ cho tổ trưởng hiện hành (leaderA); leaderB phân công sau đó
        insertAssignment(leaderB.userId(), "AGE_GROUP_TEAM_LEADER", null, ageGroup3, null);
    }

    /** Kế hoạch năm cần tối thiểu 9 chủ đề liền nhau: chủ đề 1 (3 tuần) + 8 chủ đề phụ 2 tuần. */
    private static List<YearPlanRequest.Theme> annualThemes() {
        var themes = new java.util.ArrayList<YearPlanRequest.Theme>();
        themes.add(new YearPlanRequest.Theme(
                "t-1", null, "Chủ đề 1", "CD1", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25), "", ""));
        for (int i = 0; i < 8; i++) {
            LocalDate monday = LocalDate.of(2026, 9, 28).plusWeeks(2L * i);
            themes.add(new YearPlanRequest.Theme(
                    "t-x" + i, null, "Chủ đề phụ " + i, "CDX" + i, monday, monday.plusDays(11), "", ""));
        }
        return themes;
    }

    /** Bản tổ nháp do hệ thống tạo khi ban hành năm; tick nội dung vào chủ đề đầu để gửi duyệt được. */
    private YearPlanDetailResponse autoTeamPlan(CurrentUser owner) {
        Long teamId = jdbc.queryForObject(
                "SELECT id FROM edu_team_plans WHERE school_year_id = ? AND age_group_id = ? ORDER BY version_no LIMIT 1",
                Long.class,
                schoolYear,
                ageGroup3);
        jdbc.update(
                "INSERT INTO edu_content_theme_ticks (content_id, theme_id) "
                        + "SELECT c.id, (SELECT id FROM edu_themes WHERE team_plan_id = ? ORDER BY sort_order LIMIT 1) "
                        + "FROM edu_goal_contents c WHERE c.goal_id IN (SELECT id FROM edu_plan_goals WHERE team_plan_id = ?) "
                        + "ON CONFLICT DO NOTHING",
                teamId,
                teamId);
        entityManager.clear();
        return yearPlanService.getYearPlan(owner, teamId);
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private com.carenest.repository.eduplan.EduClassDocRepository classDocRepository;

    private UUID session(CurrentUser user) {
        return sessions.computeIfAbsent(user.userId(), ignored -> UUID.randomUUID());
    }

    @Test
    @DisplayName("T041 [US3]: Content write của creator và co-author được ghi nhận vào ledger")
    void contentWriteRecordedInAuthorLedger() {
        YearPlanRequest req = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch khối MGB",
                "",
                List.of(),
                List.of(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                annualPlanId,
                List.of());

        YearPlanDetailResponse plan = autoTeamPlan(leaderA);

        // Creator được ghi với origin CREATOR
        List<EduDocumentAuthor> authors = authorRepository.findAllByYearPlanId(plan.id());
        assertThat(authors).hasSize(1);
        assertThat(authors.getFirst().getUserId()).isEqualTo(leaderA.userId());
        assertThat(authors.getFirst().getOrigin()).isEqualTo("CREATOR");

        // leaderA nhả khóa soạn
        yearPlanService.finishEditing(
                leaderA, plan.id(), new YearPlanEditRequest(session(leaderA), plan.revision(), UUID.randomUUID()));

        // Co-author leaderB lấy khóa và sửa nội dung -> được ghi với origin
        // CONTENT_WRITE
        yearPlanService.startEditing(
                leaderB, plan.id(), new YearPlanEditRequest(session(leaderB), plan.revision(), UUID.randomUUID()));
        var updateReq = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch khối MGB sửa",
                "",
                plan.domains().stream()
                        .map(d -> new YearPlanRequest.Domain(
                                d.domainId(),
                                d.rows().stream()
                                        .map(r -> new YearPlanRequest.Row(
                                                r.id(),
                                                r.kind(),
                                                r.text(),
                                                r.contents().stream()
                                                        .map(c -> new YearPlanRequest.Content(
                                                                c.id(), c.text(), List.of("t-1")))
                                                        .toList()))
                                        .toList()))
                        .toList(),
                List.of(new YearPlanRequest.Theme(
                        "t-1",
                        plan.themes().getFirst().id(),
                        plan.themes().getFirst().name(),
                        plan.themes().getFirst().abbr(),
                        plan.themes().getFirst().startDate(),
                        plan.themes().getFirst().endDate(),
                        plan.themes().getFirst().festivals(),
                        "")),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                annualPlanId,
                plan.branchTemplates().stream()
                        .map(t -> new YearPlanRequest.BranchTemplate(
                                t.id().toString(), t.id(), "t-1", t.name(), t.startDate(), t.endDate()))
                        .toList());
        yearPlanService.updateYearPlan(
                leaderB,
                plan.id(),
                updateReq,
                new YearPlanEditRequest(session(leaderB), plan.revision(), UUID.randomUUID()));

        authors = authorRepository.findAllByYearPlanId(plan.id());
        assertThat(authors).hasSize(2);
        assertThat(authors.stream().map(EduDocumentAuthor::getUserId))
                .containsExactlyInAnyOrder(leaderA.userId(), leaderB.userId());
        assertThat(authors.stream()
                        .filter(a -> a.getUserId().equals(leaderB.userId()))
                        .findFirst()
                        .get()
                        .getOrigin())
                .isEqualTo("CONTENT_WRITE");
    }

    @Test
    @DisplayName("T041 [US3]: Contributor của kế hoạch năm bị cấm duyệt kế hoạch đó (chống tự duyệt)")
    void yearPlanContributorForbiddenFromApproving() {
        YearPlanRequest req = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch khối MGB",
                "",
                List.of(),
                List.of(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                annualPlanId,
                List.of());

        // leaderA (người phụ trách bản tổ tự tạo) gửi duyệt
        YearPlanDetailResponse plan = autoTeamPlan(leaderA);
        YearPlanDetailResponse submitted = yearPlanService.submitYearPlan(
                leaderA,
                plan.id(),
                "Xin duyệt",
                new YearPlanEditRequest(session(leaderA), plan.revision(), UUID.randomUUID()));
        assertThat(submitted.status()).isEqualTo(EduPlanStatus.REVIEW);

        // Sau đó, leaderA được bổ nhiệm thêm vai trò VICE_PRINCIPAL (reviewer)
        insertAssignment(leaderA.userId(), "VICE_PRINCIPAL", campus, null, null);

        // leaderA lúc này có quyền reviewer, nhưng vì đã từng soạn kế hoạch này nên bị
        // cấm duyệt
        assertThatThrownBy(() -> yearPlanService.issueYearPlan(
                        leaderA,
                        plan.id(),
                        "Tôi duyệt bản tôi soạn",
                        new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID())))
                .isInstanceOf(GlobalException.class)
                .satisfies(
                        ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.NOT_YOUR_REVIEW_STEP));

        // vicePrincipal độc lập (không phải tác giả) duyệt -> thành công
        YearPlanDetailResponse approved = yearPlanService.issueYearPlan(
                vicePrincipal,
                plan.id(),
                "PHT ban hành",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));
        assertThat(approved.status()).isEqualTo(EduPlanStatus.APPROVED);
    }

    @Test
    @DisplayName(
            "T041 [US3]: Contributor của một tuần trong dossier chủ đề bị cấm duyệt dossier chủ đề đó (union authors)")
    void themeDossierWeekContributorForbiddenFromApproving() {
        // Ban hành bản tổ trước
        YearPlanDetailResponse yearPlan = autoTeamPlan(leaderA);
        YearPlanDetailResponse submittedYear = yearPlanService.submitYearPlan(
                leaderA,
                yearPlan.id(),
                "Gửi",
                new YearPlanEditRequest(session(leaderA), yearPlan.revision(), UUID.randomUUID()));
        yearPlanService.issueYearPlan(
                vicePrincipal,
                yearPlan.id(),
                "Ban hành",
                new YearPlanEditRequest(null, submittedYear.revision(), UUID.randomUUID()));

        Long themeId = yearPlan.themes().getFirst().id();

        // Spec 003: hồ sơ chủ đề (dossier) là gốc gửi/duyệt; dựng sẵn tài liệu lớp, teacher2 soạn tuần 1
        String themeKey = "ds_" + classId + "_" + themeId;
        var branch = com.carenest.entity.eduplan.EduClassDoc.create(
                "bp_" + classId + "_" + yearPlan.id(), "branch", classId, yearPlan.id(), null, null, teacher1.userId());
        branch.changeContent(
                "{\"names\":{\"2026-09-07\":\"Nhánh 1\",\"2026-09-14\":\"Nhánh 2\",\"2026-09-21\":\"Nhánh 3\"}}",
                teacher1.userId());
        classDocRepository.saveAndFlush(branch);
        classDocRepository.saveAndFlush(com.carenest.entity.eduplan.EduClassDoc.create(
                "tp_" + classId + "_" + themeId, "theme", classId, yearPlan.id(), themeId, null, teacher1.userId()));
        classDocRepository.saveAndFlush(com.carenest.entity.eduplan.EduClassDoc.create(
                themeKey, "dossier", classId, yearPlan.id(), themeId, null, teacher1.userId()));
        for (int week = 0; week < 3; week++) {
            LocalDate monday = LocalDate.of(2026, 9, 7).plusWeeks(week);
            Long author = week == 0 ? teacher2.userId() : teacher1.userId();
            classDocRepository.saveAndFlush(com.carenest.entity.eduplan.EduClassDoc.create(
                    "wp_" + classId + "_" + monday, "week", classId, yearPlan.id(), themeId, monday, author));
            classDocRepository.saveAndFlush(com.carenest.entity.eduplan.EduClassDoc.create(
                    "dd_" + classId + "_" + monday, "day", classId, yearPlan.id(), themeId, monday, author));
        }

        // teacher1 gửi duyệt hồ sơ chủ đề
        classDocService.actOnClassDoc(
                teacher1, "dossier", themeKey, new ClassDocActionRequest("submit", null), versionOf(themeKey));

        // leaderA (Tổ trưởng không soạn bất kỳ phần nào của dossier) duyệt bước Tổ
        // trưởng -> thành công
        ObjectNode approvedByLeader = classDocService.actOnClassDoc(
                leaderA,
                "dossier",
                themeKey,
                new ClassDocActionRequest("approve", "Tổ trưởng duyệt"),
                versionOf(themeKey));
        assertThat(approvedByLeader.path("status").asText()).isEqualTo("REVIEW");

        // Sau đó, teacher2 được bổ nhiệm thêm vai trò VICE_PRINCIPAL cho trường
        insertAssignment(teacher2.userId(), "VICE_PRINCIPAL", campus, null, null);
        CurrentUser teacher2Vp = new CurrentUser(
                teacher2.userId(),
                teacher2.keycloakUserId(),
                teacher2.username(),
                teacher2.fullName(),
                null,
                Set.of(CareNestRole.TEACHER, CareNestRole.VICE_PRINCIPAL));

        // teacher2 là Phó Hiệu trưởng, NHƯNG đã soạn tuần 1 trong dossier này
        // teacher2 cố duyệt hồ sơ chủ đề ở bước PHT -> bị cấm do union authors!
        assertThatThrownBy(() -> classDocService.actOnClassDoc(
                        teacher2Vp,
                        "dossier",
                        themeKey,
                        new ClassDocActionRequest("approve", "Tôi duyệt"),
                        versionOf(themeKey)))
                .isInstanceOf(GlobalException.class)
                .satisfies(
                        ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.NOT_YOUR_REVIEW_STEP));

        // vicePrincipal (Phó Hiệu trưởng không soạn bất kỳ phần nào) duyệt -> thành
        // công ban hành
        ObjectNode approvedByVp = classDocService.actOnClassDoc(
                vicePrincipal,
                "dossier",
                themeKey,
                new ClassDocActionRequest("approve", "PHT duyệt"),
                versionOf(themeKey));
        assertThat(approvedByVp.path("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("T041 [US3]: Reviewer precedence cấm người có vai trò reviewer soạn cùng loại dù có nhiều vai trò")
    void reviewerPrecedenceDeniesAuthoring() {
        CurrentUser teacherVp = user("teacher-vp", CareNestRole.TEACHER, CareNestRole.VICE_PRINCIPAL);
        insertAssignment(teacherVp.userId(), "TEACHER", null, null, classId);
        insertAssignment(teacherVp.userId(), "VICE_PRINCIPAL", campus, null, null);

        // Đối với kế hoạch năm: VP là reviewer nên teacherVp bị cấm soạn
        assertThat(workflowService.hasReviewerPrecedence(teacherVp, "year", ageGroup3, classId))
                .isTrue();
        assertThat(workflowService.isAuthorEligible(teacherVp, "year", ageGroup3, classId))
                .isFalse();

        // Cố tạo year plan bị cấm tạo
        YearPlanRequest req = new YearPlanRequest(schoolYear, ageGroup3, "KH", "", List.of(), List.of(), List.of());
        assertThatThrownBy(() -> yearPlanService.createYearPlan(
                        teacherVp, req, new YearPlanEditRequest(session(teacherVp), 0L, UUID.randomUUID())))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex ->
                        assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.YEAR_PLAN_CREATE_FORBIDDEN));
    }

    private CurrentUser user(String username, CareNestRole... roles) {
        long id = insertId(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name, status) VALUES (?, ?, ?, 'ACTIVE') RETURNING id",
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(id, "kc-" + username, username, "Demo " + username, null, Set.of(roles));
    }

    private void insertAssignment(Long userId, String staffRole, Long campusId, Long ageGroupId, Long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, age_group_id, classroom_id, valid_from) "
                        + "VALUES (?, ?, ?, ?, ?, '2026-09-01')",
                userId,
                staffRole,
                campusId,
                ageGroupId,
                classroomId);
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private Long versionOf(String key) {
        var versions = jdbc.queryForList("SELECT version FROM edu_class_docs WHERE doc_key=?", Long.class, key);
        return versions.isEmpty() ? -1L : versions.getFirst();
    }
}
