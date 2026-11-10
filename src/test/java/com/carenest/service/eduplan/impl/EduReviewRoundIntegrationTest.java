package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.eduplan.EduWorkflowPolicyRequest;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.repository.eduplan.EduSettingsRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.EduYearPlanService;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T042 [US3]: Kiểm tra quản lý review round và approvals qua các biến động chính sách và vòng lặp duyệt:
 * - Policy change giữ nguyên round và approvals của round hiện tại.
 * - Trả lại (reject) về DRAFT, sau đó sửa và gửi lại bắt đầu round mới (round + 1) và reset approvals về rỗng.
 * - Tạo phiên bản mới (createVersion) khởi tạo document mới với reviewRound = 0 và approvals = [].
 * - Policy change làm cho tất cả các reviewer trong chuỗi mới đều đã được duyệt trước đó -> auto completion với completionMode = "POLICY_CHANGE" và không tạo fake approval.
 */
@SpringBootTest
@Testcontainers
class EduReviewRoundIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private EduYearPlanService yearPlanService;

    @Autowired
    private EduWorkflowService workflowService;

    @Autowired
    private EduPlanRepository yearPlanRepository;

    @Autowired
    private EduSettingsRepository settingsRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private ObjectMapper objectMapper;

    private final Map<Long, UUID> sessions = new ConcurrentHashMap<>();

    private long schoolYear;
    private long ageGroup3;
    private long campus;
    private CurrentUser leader;
    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private Long annualPlanId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        campus = insertId(
                "INSERT INTO campuses (code, name) VALUES (?, ?) RETURNING id",
                "CS-" + suffix,
                "Campus Round " + suffix);
        schoolYear = insertId(
                "INSERT INTO school_years (name, start_date, end_date) "
                        + "VALUES (?, '2026-09-01', '2027-05-31') RETURNING id",
                "SY-" + suffix);
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);

        // Lớp test không rollback: kết thúc tổ trưởng của test trước vì tạo bản tổ cần đúng một tổ trưởng
        jdbc.update("UPDATE staff_assignments SET valid_to='2026-09-01' WHERE staff_role='AGE_GROUP_TEAM_LEADER' "
                + "AND age_group_id=? AND valid_to IS NULL", ageGroup3);
        jdbc.update("UPDATE edu_settings SET duration_policy = '{\"theme\":{\"minWeeks\":1,\"maxWeeks\":4},\"branch\":{\"minWeeks\":1,\"maxWeeks\":2}}'::jsonb");
        // Test khác có thể đổi chính sách kế hoạch năm; khôi phục mặc định PHT soạn, HT duyệt
        jdbc.update("UPDATE edu_settings SET workflow_policy = workflow_policy || "
                + "'{\"schoolYear\":{\"creators\":[\"VICE_PRINCIPAL\"],\"reviewers\":[\"PRINCIPAL\"]}}'::jsonb");
        leader = user("leader-" + suffix, CareNestRole.TEACHER);
        insertAssignment(leader.userId(), "AGE_GROUP_TEAM_LEADER", null, ageGroup3, null);

        vicePrincipal = user("vp-" + suffix, CareNestRole.VICE_PRINCIPAL);
        insertAssignment(vicePrincipal.userId(), "VICE_PRINCIPAL", campus, null, null);

        principal = user("ht-" + suffix, CareNestRole.PRINCIPAL);
        insertAssignment(principal.userId(), "PRINCIPAL", null, null, null);

        var annualReq = annualRequest(schoolYear, "Annual Round " + suffix,
                List.of(new YearPlanRequest.BranchTemplate(
                        "tmpl-1", null, "t-1", "Mẫu 1", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11))));
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

        // Thiết lập policy ban đầu cho year plan: creators=[TEAM_LEADER], reviewers=[VICE_PRINCIPAL, PRINCIPAL]
        EduWorkflowPolicyRequest initialPolicyReq = new EduWorkflowPolicyRequest(
                workflowService.getPolicyRevision(),
                Map.of(
                        "year",
                        new EduWorkflowPolicyRequest.PolicyEntry(
                                List.of("TEAM_LEADER"), List.of("VICE_PRINCIPAL", "PRINCIPAL")),
                        "branch",
                        new EduWorkflowPolicyRequest.PolicyEntry(
                                List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "theme",
                        new EduWorkflowPolicyRequest.PolicyEntry(
                                List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));
        workflowService.savePolicy(principal, initialPolicyReq);
    }

    private UUID session(CurrentUser user) {
        return sessions.computeIfAbsent(user.userId(), ignored -> UUID.randomUUID());
    }

    private YearPlanRequest sampleRequest() {
        return new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch khối 3 tuổi - Review Round",
                "Mô tả",
                List.of(),
                List.of(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                annualPlanId,
                List.of());
    }

    /** Kế hoạch năm hợp lệ theo quy tắc hiện hành: ≥9 chủ đề, có nhóm kết quả, nội dung được tick vào chủ đề. */
    private YearPlanRequest annualRequest(long yearId, String title, List<YearPlanRequest.BranchTemplate> templates) {
        long domain = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);
        var themes = new java.util.ArrayList<YearPlanRequest.Theme>();
        themes.add(new YearPlanRequest.Theme(
                "t-1", null, "Chủ đề 1", "CD1", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25), "", ""));
        for (int i = 0; i < 8; i++) {
            LocalDate monday = LocalDate.of(2026, 9, 28).plusWeeks(i);
            themes.add(new YearPlanRequest.Theme(
                    "t-x" + i, null, "Chủ đề phụ " + i, "CDX" + i, monday, monday.plusDays(4), "", ""));
        }
        return new YearPlanRequest(
                yearId,
                ageGroup3,
                title,
                "",
                List.of(new YearPlanRequest.Domain(
                        domain,
                        List.of(
                                new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()),
                                new YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "Mục tiêu thể chất",
                                        List.of(new YearPlanRequest.Content(null, "Nội dung", List.of("t-1"))))))),
                themes,
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                templates);
    }

    /** Ban hành kế hoạch năm tự tạo bản nháp tổ cho nhóm tuổi; test dùng bản đó thay vì tạo mới. */
    private Long autoTeamPlanId() {
        Long teamId = jdbc.queryForObject(
                "SELECT id FROM edu_team_plans WHERE school_year_id = ? AND age_group_id = ? ORDER BY version_no LIMIT 1",
                Long.class,
                schoolYear,
                ageGroup3);
        // Kế hoạch năm không nhận tick: tick nội dung bản tổ vào chủ đề đầu để đủ điều kiện gửi duyệt
        jdbc.update(
                "INSERT INTO edu_content_theme_ticks (content_id, theme_id) "
                        + "SELECT c.id, (SELECT id FROM edu_themes WHERE team_plan_id = ? ORDER BY sort_order LIMIT 1) "
                        + "FROM edu_goal_contents c WHERE c.goal_id IN (SELECT id FROM edu_plan_goals WHERE team_plan_id = ?) "
                        + "ON CONFLICT DO NOTHING",
                teamId,
                teamId);
        return teamId;
    }

    private YearPlanDetailResponse createAndSubmitSamplePlan() {

        YearPlanDetailResponse created = yearPlanService.getYearPlan(leader, autoTeamPlanId());

        return yearPlanService.submitYearPlan(
                leader,
                created.id(),
                "Gửi duyệt vòng 1",
                new YearPlanEditRequest(session(leader), created.revision(), UUID.randomUUID()));
    }

    @Test
    void policySaveSerializesWithApprovalAndRejectsStaleRevision() throws Exception {
        var submitted = createAndSubmitSamplePlan();
        var policyEntries = new java.util.HashMap<String, EduWorkflowPolicyRequest.PolicyEntry>();
        workflowService
                .getPolicy(principal)
                .policies()
                .forEach((key, value) -> policyEntries.put(
                        key, new EduWorkflowPolicyRequest.PolicyEntry(value.creators(), value.reviewers())));
        policyEntries.put("branch", new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEACHER"), List.of("PRINCIPAL")));
        var request = new EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), policyEntries);
        var saved = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var policy =
                    executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                            .execute(status -> {
                                workflowService.savePolicy(principal, request);
                                saved.countDown();
                                try {
                                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                        throw new AssertionError("barrier timed out");
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(ex);
                                }
                                return null;
                            }));
            assertThat(saved.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var approval =
                    executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                            .execute(status -> {
                                jdbc.execute("SET LOCAL lock_timeout='400ms'");
                                return yearPlanService.issueYearPlan(
                                        vicePrincipal,
                                        submitted.id(),
                                        "",
                                        new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));
                            }));
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> approval.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            release.countDown();
            policy.get(10, java.util.concurrent.TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> yearPlanService.issueYearPlan(
                            vicePrincipal,
                            submitted.id(),
                            "",
                            new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID())))
                    .isInstanceOf(com.carenest.exception.GlobalException.class);
            var current = yearPlanService.getYearPlan(vicePrincipal, submitted.id());
            assertThat(yearPlanService
                            .issueYearPlan(
                                    vicePrincipal,
                                    current.id(),
                                    "",
                                    new YearPlanEditRequest(null, current.revision(), UUID.randomUUID()))
                            .status())
                    .isEqualTo(EduPlanStatus.REVIEW);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("EDU-54: reviewer chưa tới lượt không được trả lại hồ sơ")
    void reviewerMustWaitBeforeReturning() {
        var submitted = createAndSubmitSamplePlan();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> yearPlanService.returnYearPlan(
                        principal,
                        submitted.id(),
                        "Cần sửa",
                        new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID())))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        assertThat(yearPlanRepository.findById(submitted.id()).orElseThrow().getStatus())
                .isEqualTo(EduPlanStatus.REVIEW);
        var request = new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID());
        var returned = yearPlanService.returnYearPlan(vicePrincipal, submitted.id(), "Sửa", request);
        var retried = yearPlanService.returnYearPlan(vicePrincipal, submitted.id(), "Sửa", request);
        assertThat(retried.revision()).isEqualTo(returned.revision());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_plan_events WHERE team_plan_id=? AND action='RETURN'",
                        Long.class,
                        submitted.id()))
                .isEqualTo(1);
    }

    @Test
    void vicePrincipalAndLeaderCannotReturnBeforePrincipal() {
        var policies = new java.util.HashMap<String, EduWorkflowPolicyRequest.PolicyEntry>();
        workflowService
                .getPolicy(principal)
                .policies()
                .forEach((key, value) -> policies.put(
                        key, new EduWorkflowPolicyRequest.PolicyEntry(value.creators(), value.reviewers())));
        policies.put(
                "year",
                new EduWorkflowPolicyRequest.PolicyEntry(
                        List.of("TEACHER"), List.of("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER")));
        workflowService.savePolicy(
                principal, new EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), policies));
        CurrentUser author = user("plain-" + UUID.randomUUID().toString().substring(0, 8), CareNestRole.TEACHER);
        long classroom = insertId(
                "INSERT INTO classrooms(campus_id,school_year_id,age_group_id,name) VALUES (?,?,?,'Synthetic') RETURNING id",
                campus,
                schoolYear,
                ageGroup3);
        insertAssignment(author.userId(), "TEACHER", null, null, classroom);
        // Bản tổ tự tạo giao cho tổ trưởng; giao lại cho giáo viên để tổ trưởng vẫn là người duyệt
        Long teamId = autoTeamPlanId();
        jdbc.update("UPDATE edu_team_plans SET owner_id=? WHERE id=?", author.userId(), teamId);
        var created = yearPlanService.getYearPlan(author, teamId);
        var submitted = yearPlanService.submitYearPlan(
                author,
                created.id(),
                "Gửi",
                new YearPlanEditRequest(session(author), created.revision(), UUID.randomUUID()));
        for (CurrentUser outOfTurn : List.of(vicePrincipal, leader)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> yearPlanService.returnYearPlan(
                            outOfTurn,
                            submitted.id(),
                            "Sửa",
                            new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID())))
                    .isInstanceOf(com.carenest.exception.GlobalException.class);
        }
        assertThat(yearPlanService
                        .returnYearPlan(
                                principal,
                                submitted.id(),
                                "Sửa",
                                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()))
                        .status())
                .isEqualTo(EduPlanStatus.DRAFT);
    }

    @Test
    @DisplayName("EDU-54: lỗi lưu policy ban hành rollback cả lượt duyệt")
    void publicationPolicySerializationFailureRollsBack() {
        var policies = new java.util.HashMap<String, EduWorkflowPolicyRequest.PolicyEntry>();
        workflowService
                .getPolicy(principal)
                .policies()
                .forEach((k, v) ->
                        policies.put(k, new EduWorkflowPolicyRequest.PolicyEntry(v.creators(), v.reviewers())));
        policies.put(
                "year", new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEAM_LEADER"), List.of("VICE_PRINCIPAL")));
        workflowService.savePolicy(
                principal, new EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), policies));
        var submitted = createAndSubmitSamplePlan();
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic serialization failure"))
                .when(objectMapper)
                .writeValueAsString(org.mockito.ArgumentMatchers.isA(EduWorkflowPolicy.RolePolicy.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> yearPlanService.issueYearPlan(
                        vicePrincipal,
                        submitted.id(),
                        "",
                        new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID())))
                .isInstanceOf(RuntimeException.class);
        assertThat(yearPlanRepository.findById(submitted.id()).orElseThrow().getStatus())
                .isEqualTo(EduPlanStatus.REVIEW);
    }

    @Test
    @DisplayName("EDU-54: đổi thứ tự sau duyệt giữ người duyệt của vai trò cuối khi hoàn tất")
    void completingEarlierStepUsesActualFinalRolePublisher() {
        var submitted = createAndSubmitSamplePlan();
        var partial = yearPlanService.issueYearPlan(
                vicePrincipal,
                submitted.id(),
                "",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));
        var policies = new java.util.HashMap<String, EduWorkflowPolicyRequest.PolicyEntry>();
        workflowService
                .getPolicy(principal)
                .policies()
                .forEach((k, v) ->
                        policies.put(k, new EduWorkflowPolicyRequest.PolicyEntry(v.creators(), v.reviewers())));
        policies.put(
                "year",
                new EduWorkflowPolicyRequest.PolicyEntry(
                        List.of("TEAM_LEADER"), List.of("PRINCIPAL", "VICE_PRINCIPAL")));
        workflowService.savePolicy(
                principal, new EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), policies));
        var current = yearPlanService.getYearPlan(principal, partial.id());
        yearPlanService.issueYearPlan(
                principal, current.id(), "", new YearPlanEditRequest(null, current.revision(), UUID.randomUUID()));
        assertThat(yearPlanRepository.findById(current.id()).orElseThrow().getPublishedBy())
                .isEqualTo(vicePrincipal.userId());
    }

    @Test
    @DisplayName("T042 [US3]: Policy change giữ nguyên reviewRound và approvals của round hiện tại")
    void policyChangePreservesRoundAndApprovals() {
        YearPlanDetailResponse submitted = createAndSubmitSamplePlan();
        assertThat(submitted.review().round()).isEqualTo(1);
        assertThat(submitted.review().waitingRole()).isEqualTo("VICE_PRINCIPAL");

        // Phó HT duyệt bước 1 -> tiến trình duyệt ghi nhận approval bước VICE_PRINCIPAL, vẫn ở trạng thái REVIEW
        YearPlanDetailResponse approvedByVp = yearPlanService.issueYearPlan(
                vicePrincipal,
                submitted.id(),
                "Phó HT duyệt bước 1",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));

        assertThat(approvedByVp.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(approvedByVp.review().round()).isEqualTo(1);
        assertThat(approvedByVp.review().waitingRole()).isEqualTo("PRINCIPAL");
        assertThat(approvedByVp.review().approvals()).hasSize(1);
        assertThat(approvedByVp.review().approvals().getFirst().roleKey()).isEqualTo("VICE_PRINCIPAL");
        assertThat(approvedByVp.review().approvals().getFirst().round()).isEqualTo(1);

        // Hiệu trưởng thay đổi cấu hình policy cho branch (không đổi cấu trúc year)
        EduWorkflowPolicyRequest newPolicyReq = new EduWorkflowPolicyRequest(
                workflowService.getPolicyRevision(),
                Map.of(
                        "year",
                        new EduWorkflowPolicyRequest.PolicyEntry(
                                List.of("TEAM_LEADER"), List.of("VICE_PRINCIPAL", "PRINCIPAL")),
                        "branch",
                        new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEACHER"), List.of("VICE_PRINCIPAL")),
                        "theme",
                        new EduWorkflowPolicyRequest.PolicyEntry(
                                List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));
        workflowService.savePolicy(principal, newPolicyReq);

        // Nạp lại kế hoạch năm từ DB sau khi reconcilePending kích hoạt
        EduPlan reloaded = yearPlanRepository.findById(submitted.id()).orElseThrow();
        assertThat(reloaded.publicStatus()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(reloaded.getReviewRound()).isEqualTo(1);

        List<EduWorkflowPolicy.Approval> approvals = workflowService.parseApprovals(reloaded.getApprovals());
        assertThat(approvals).hasSize(1);
        assertThat(approvals.getFirst().roleKey()).isEqualTo(EduWorkflowPolicy.Role.VICE_PRINCIPAL);
        assertThat(approvals.getFirst().round()).isEqualTo(1);
    }

    @Test
    @DisplayName("T042 [US3]: Trả lại về DRAFT, sau đó sửa và gửi lại tăng reviewRound và reset approvals")
    void returnAndResubmitIncrementsRoundAndResetsApprovals() {
        YearPlanDetailResponse submitted = createAndSubmitSamplePlan();

        // Phó HT duyệt bước 1
        YearPlanDetailResponse approvedByVp = yearPlanService.issueYearPlan(
                vicePrincipal,
                submitted.id(),
                "Phó HT duyệt",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));
        assertThat(approvedByVp.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(approvedByVp.review().round()).isEqualTo(1);
        assertThat(approvedByVp.review().approvals()).hasSize(1);

        // Hiệu trưởng trả lại kế hoạch để sửa
        YearPlanDetailResponse returned = yearPlanService.returnYearPlan(
                principal,
                submitted.id(),
                "Cần bổ sung thêm mục tiêu phát triển thẩm mỹ",
                new YearPlanEditRequest(null, approvedByVp.revision(), UUID.randomUUID()));

        assertThat(returned.status()).isEqualTo(EduPlanStatus.DRAFT);

        // Tác giả mở lại để sửa -> lấy editing session
        YearPlanDetailResponse editingStarted = yearPlanService.startEditing(
                leader,
                submitted.id(),
                new YearPlanEditRequest(session(leader), returned.revision(), UUID.randomUUID()));

        // Tác giả sửa kế hoạch
        var firstTheme = editingStarted.themes().getFirst();
        String themeKey = "t-" + firstTheme.id();
        List<YearPlanRequest.Theme> updateThemes = editingStarted.themes().stream()
                .map(t -> new YearPlanRequest.Theme(
                        "t-" + t.id(), t.id(), t.name(), t.abbr(), t.startDate(), t.endDate(), t.festivals(), t.note()))
                .toList();

        List<YearPlanRequest.Domain> updateDomains = editingStarted.domains().stream()
                .map(d -> new YearPlanRequest.Domain(
                        d.domainId(),
                        d.rows().stream()
                                .map(r -> new YearPlanRequest.Row(
                                        r.id(),
                                        r.kind(),
                                        r.text(),
                                        r.contents().stream()
                                                .map(c -> new YearPlanRequest.Content(
                                                        c.id(), c.text(), List.of(themeKey)))
                                                .toList()))
                                .toList()))
                .toList();

        YearPlanRequest updateReq = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch khối 3 tuổi - Đã sửa sau trả lại",
                "Đã bổ sung mục tiêu",
                updateDomains,
                updateThemes,
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                annualPlanId,
                editingStarted.branchTemplates().stream()
                        .map(t -> new YearPlanRequest.BranchTemplate(
                                t.id().toString(), t.id(), "t-" + t.themeId(), t.name(), t.startDate(), t.endDate()))
                        .toList());
        YearPlanDetailResponse updated = yearPlanService.updateYearPlan(
                leader,
                submitted.id(),
                updateReq,
                new YearPlanEditRequest(session(leader), editingStarted.revision(), UUID.randomUUID()));

        // Tác giả gửi duyệt lại -> round tăng lên 2, approvals reset về rỗng
        YearPlanDetailResponse resubmitted = yearPlanService.submitYearPlan(
                leader,
                submitted.id(),
                "Gửi duyệt vòng 2",
                new YearPlanEditRequest(session(leader), updated.revision(), UUID.randomUUID()));

        assertThat(resubmitted.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(resubmitted.review().round()).isEqualTo(2);
        assertThat(resubmitted.review().approvals()).isEmpty();
        assertThat(resubmitted.review().waitingRole()).isEqualTo("VICE_PRINCIPAL");
    }

    @Test
    @DisplayName(
            "T042 [US3]: Tạo phiên bản mới (createVersion) khởi tạo document mới với reviewRound = 0 và approvals rỗng")
    void createVersionResetsRoundToZero() {
        YearPlanDetailResponse submitted = createAndSubmitAnnualPlan();

        // Duyệt bước 1: Phó HT
        YearPlanDetailResponse approvedByVp = yearPlanService.issueYearPlan(
                vicePrincipal,
                submitted.id(),
                "Phó HT duyệt",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));

        // Duyệt bước 2: Hiệu trưởng -> Ban hành
        YearPlanDetailResponse published = yearPlanService.issueYearPlan(
                principal,
                submitted.id(),
                "Hiệu trưởng duyệt và ban hành",
                new YearPlanEditRequest(null, approvedByVp.revision(), UUID.randomUUID()));
        assertThat(published.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(published.review().round()).isEqualTo(1);
        assertThat(published.review().approvals()).hasSize(2);

        // Tạo phiên bản mới (v2) từ bản v1 đã ban hành
        YearPlanDetailResponse v2 = yearPlanService.createVersion(
                leader,
                submitted.id(),
                new YearPlanEditRequest(session(leader), published.revision(), UUID.randomUUID()));

        assertThat(v2.status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.review().round()).isEqualTo(0);
        assertThat(v2.review().approvals()).isEmpty();

        EduPlan v2Entity = yearPlanRepository.findById(v2.id()).orElseThrow();
        assertThat(v2Entity.getReviewRound()).isEqualTo(0);
        assertThat(v2Entity.getApprovals()).isEqualTo("[]");
    }

    private YearPlanDetailResponse createAndSubmitAnnualPlan() {
        var entries = new java.util.HashMap<String, EduWorkflowPolicyRequest.PolicyEntry>();
        workflowService.getPolicy(principal).policies().forEach((key, policy) -> entries.put(
                key, new EduWorkflowPolicyRequest.PolicyEntry(policy.creators(), policy.reviewers())));
        entries.put("schoolYear", new EduWorkflowPolicyRequest.PolicyEntry(
                List.of("TEAM_LEADER"), List.of("VICE_PRINCIPAL", "PRINCIPAL")));
        workflowService.savePolicy(principal, new EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), entries));
        long versionYear = insertId(
                "INSERT INTO school_years(name,start_date,end_date) VALUES (?, '2026-09-01','2027-05-31') RETURNING id",
                "V-" + UUID.randomUUID().toString().substring(0, 8));
        var request = annualRequest(versionYear, "Kế hoạch năm kiểm tra vòng duyệt", List.of());
        var created = yearPlanService.createYearPlan(
                leader, request, new YearPlanEditRequest(session(leader), 0L, UUID.randomUUID()));
        return yearPlanService.submitYearPlan(leader, created.id(), "Gửi duyệt vòng 1",
                new YearPlanEditRequest(session(leader), created.revision(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("T042 [US3]: Policy change tự động hoàn tất khi tất cả reviewer mới đều đã duyệt (POLICY_CHANGE)")
    void policyChangeAutoCompletesWhenAllRemainingReviewersAlreadyApproved() {
        YearPlanDetailResponse submitted = createAndSubmitSamplePlan();

        // Phó HT duyệt bước 1 (VICE_PRINCIPAL)
        YearPlanDetailResponse approvedByVp = yearPlanService.issueYearPlan(
                vicePrincipal,
                submitted.id(),
                "Phó HT duyệt bước 1",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));

        assertThat(approvedByVp.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(approvedByVp.review().waitingRole()).isEqualTo("PRINCIPAL");

        // Bây giờ Hiệu trưởng đổi policy của year plan: chỉ còn [VICE_PRINCIPAL] duyệt (bỏ bước PRINCIPAL)
        EduWorkflowPolicyRequest streamlinedPolicyReq = new EduWorkflowPolicyRequest(
                workflowService.getPolicyRevision(),
                Map.of(
                        "year",
                        new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEAM_LEADER"), List.of("VICE_PRINCIPAL")),
                        "branch",
                        new EduWorkflowPolicyRequest.PolicyEntry(
                                List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "theme",
                        new EduWorkflowPolicyRequest.PolicyEntry(
                                List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));
        workflowService.savePolicy(principal, streamlinedPolicyReq);

        // ReconcilePending đã chạy tự động trong updatePolicy
        EduPlan reloaded = yearPlanRepository.findById(submitted.id()).orElseThrow();
        assertThat(reloaded.publicStatus()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(reloaded.getCompletionMode()).isEqualTo("POLICY_CHANGE");
        assertThat(reloaded.getCompletedAt()).isNotNull();
        assertThat(reloaded.getPublishedBy()).isEqualTo(vicePrincipal.userId());
        assertThat(reloaded.getContentRevision()).isGreaterThan(approvedByVp.revision());
        assertThat(jdbc.queryForObject(
                        "SELECT actor_id FROM edu_plan_events WHERE team_plan_id=? AND action='COMPLETED_BY_POLICY'",
                        Long.class,
                        submitted.id()))
                .isEqualTo(principal.userId());

        // Print snapshot phải giữ snapshot ô ký hợp lệ, chứa tên người duyệt thật là Phó HT, không bịa tên Hiệu trưởng
        String printSnapshotJson = reloaded.getPrintSnapshot();
        assertThat(printSnapshotJson).isNotNull().isNotBlank();
        try {
            JsonNode snapshot = objectMapper.readTree(printSnapshotJson);
            assertThat(snapshot.path("origin").asText()).isEqualTo("PUBLICATION");
            JsonNode slots = snapshot.path("slots");
            assertThat(slots.isArray()).isTrue();
            // Chuỗi mới chỉ có 1 slot VICE_PRINCIPAL
            assertThat(slots.size()).isEqualTo(2);
            assertThat(slots.get(0).path("roleKey").asText()).isEqualTo("CREATOR");
            JsonNode vpSlot = slots.get(1);
            assertThat(vpSlot.path("roleKey").asText()).isEqualTo("VICE_PRINCIPAL");
            assertThat(vpSlot.path("names").get(0).asText()).isEqualTo(vicePrincipal.fullName());
        } catch (Exception ex) {
            throw new AssertionError("Invalid print snapshot JSON", ex);
        }
    }

    @Test
    void policyCompletionUsesActualPrincipalApprovalAndDoesNotInventVicePrincipalSigner() {
        var submitted = createAndSubmitSamplePlan();
        var base = workflowService.getPolicy(principal);
        var entries = new java.util.HashMap<String, EduWorkflowPolicyRequest.PolicyEntry>();
        base.policies()
                .forEach((key, entry) -> entries.put(
                        key, new EduWorkflowPolicyRequest.PolicyEntry(entry.creators(), entry.reviewers())));
        entries.put(
                "year",
                new EduWorkflowPolicyRequest.PolicyEntry(
                        List.of("TEAM_LEADER"), List.of("PRINCIPAL", "VICE_PRINCIPAL")));
        workflowService.savePolicy(principal, new EduWorkflowPolicyRequest(base.revision(), entries));
        var pending = yearPlanService.getYearPlan(principal, submitted.id());
        var approved = yearPlanService.issueYearPlan(
                principal,
                submitted.id(),
                "Đã duyệt",
                new YearPlanEditRequest(null, pending.revision(), UUID.randomUUID()));
        entries.put("year", new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEAM_LEADER"), List.of("PRINCIPAL")));
        workflowService.savePolicy(
                principal, new EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), entries));
        var completed = yearPlanRepository.findById(submitted.id()).orElseThrow();
        assertThat(completed.getPublishedBy()).isEqualTo(principal.userId());
        assertThat(completed.getIssuedBy()).isNull();
        assertThat(completed.getApprovals()).contains(principal.fullName());
        assertThat(completed.getReviewRound()).isEqualTo(approved.review().round());
        assertThat(completed.getCompletionMode()).isEqualTo("POLICY_CHANGE");
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
}
