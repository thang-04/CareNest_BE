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
        insertAssignment(leaderB.userId(), "AGE_GROUP_TEAM_LEADER", null, ageGroup3, null);

        teacher1 = user("teacher1", CareNestRole.TEACHER);
        insertAssignment(teacher1.userId(), "TEACHER", null, null, classId);

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
                        List.of(new YearPlanRequest.Row(
                                null,
                                "goal",
                                "Mục tiêu 1",
                                List.of(new YearPlanRequest.Content(null, "ND", List.of())))))),
                List.of(new YearPlanRequest.Theme(
                        "t-1", null, "Chủ đề 1", "CD1", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25), "", "")),
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
    }

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

        YearPlanDetailResponse plan = yearPlanService.createYearPlan(
                leaderA, req, new YearPlanEditRequest(session(leaderA), 0L, UUID.randomUUID()));

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

        // leaderA tạo và gửi duyệt kế hoạch năm
        YearPlanDetailResponse plan = yearPlanService.createYearPlan(
                leaderA, req, new YearPlanEditRequest(session(leaderA), 0L, UUID.randomUUID()));
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
        // Ban hành 1 year plan trước
        YearPlanDetailResponse yearPlan = yearPlanService.createYearPlan(
                leaderA,
                new YearPlanRequest(
                        schoolYear,
                        ageGroup3,
                        "Kế hoạch năm",
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        com.carenest.utils.enums.EduPlanLevel.TEAM,
                        annualPlanId,
                        List.of()),
                new YearPlanEditRequest(session(leaderA), 0L, UUID.randomUUID()));
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

        // Branch plan được tạo và duyệt xong
        String branchKey = "bp_" + classId + "_" + yearPlan.id();
        ObjectNode branchDoc = objectMapper.createObjectNode();
        branchDoc.put("classId", classId);
        branchDoc.put("yearPlanId", yearPlan.id());
        ObjectNode names = objectMapper.createObjectNode();
        names.put("2026-09-07", "Nhánh 1");
        names.put("2026-09-14", "Nhánh 2");
        names.put("2026-09-21", "Nhánh 3");
        branchDoc.set("names", names);
        classDocService.saveClassDoc(teacher1, "branch", branchKey, branchDoc, -1L, null);
        classDocService.actOnClassDoc(
                teacher1, "branch", branchKey, new ClassDocActionRequest("submit", null), versionOf(branchKey));
        classDocService.actOnClassDoc(
                leaderA, "branch", branchKey, new ClassDocActionRequest("approve", "Duyệt"), versionOf(branchKey));
        classDocService.actOnClassDoc(
                vicePrincipal,
                "branch",
                branchKey,
                new ClassDocActionRequest("approve", "Duyệt"),
                versionOf(branchKey));

        // teacher1 tạo theme doc
        String themeKey = "tp_" + classId + "_" + themeId;
        ObjectNode themeDoc = objectMapper.createObjectNode();
        themeDoc.put("classId", classId);
        themeDoc.put("yearPlanId", yearPlan.id());
        themeDoc.put("themeId", themeId);
        themeDoc.set("goals", objectMapper.createArrayNode());
        classDocService.saveClassDoc(teacher1, "theme", themeKey, themeDoc, -1L, null);

        // teacher2 soạn tuần 1
        String weekKey1 = "wp_" + classId + "_2026-09-07";
        ObjectNode weekDoc1 = objectMapper.createObjectNode();
        weekDoc1.put("classId", classId);
        weekDoc1.put("yearPlanId", yearPlan.id());
        weekDoc1.put("themeId", themeId);
        weekDoc1.put("weekStart", "2026-09-07");
        weekDoc1.set("days", objectMapper.createArrayNode());
        classDocService.saveClassDoc(teacher2, "week", weekKey1, weekDoc1, -1L, versionOf(themeKey));

        // teacher1 soạn tuần 2 và tuần 3 để đủ điều kiện gửi duyệt hồ sơ chủ đề
        String weekKey2 = "wp_" + classId + "_2026-09-14";
        ObjectNode weekDoc2 = objectMapper.createObjectNode();
        weekDoc2.put("classId", classId);
        weekDoc2.put("yearPlanId", yearPlan.id());
        weekDoc2.put("themeId", themeId);
        weekDoc2.put("weekStart", "2026-09-14");
        weekDoc2.set("days", objectMapper.createArrayNode());
        classDocService.saveClassDoc(teacher1, "week", weekKey2, weekDoc2, -1L, versionOf(themeKey));

        String weekKey3 = "wp_" + classId + "_2026-09-21";
        ObjectNode weekDoc3 = objectMapper.createObjectNode();
        weekDoc3.put("classId", classId);
        weekDoc3.put("yearPlanId", yearPlan.id());
        weekDoc3.put("themeId", themeId);
        weekDoc3.put("weekStart", "2026-09-21");
        weekDoc3.set("days", objectMapper.createArrayNode());
        classDocService.saveClassDoc(teacher1, "week", weekKey3, weekDoc3, -1L, versionOf(themeKey));

        // teacher1 gửi duyệt hồ sơ chủ đề
        classDocService.actOnClassDoc(
                teacher1, "theme", themeKey, new ClassDocActionRequest("submit", null), versionOf(themeKey));

        // leaderA (Tổ trưởng không soạn bất kỳ phần nào của dossier) duyệt bước Tổ
        // trưởng -> thành công
        ObjectNode approvedByLeader = classDocService.actOnClassDoc(
                leaderA,
                "theme",
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
                        "theme",
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
                "theme",
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
