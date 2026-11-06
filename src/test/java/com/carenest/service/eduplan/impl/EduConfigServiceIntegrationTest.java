package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.EduConfigRequest;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.EduWorkflowPolicyRequest;
import com.carenest.dto.eduplan.EduWorkflowPolicyResponse;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduYearPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Cấu hình kế hoạch giáo dục (EDU-11) qua service + policy thật trên PostgreSQL. Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduConfigServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EduConfigService service;

    @Autowired
    private EduWorkflowService workflowService;

    @Autowired
    private EduYearPlanRepository yearPlanRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private CurrentUser teacher;

    @BeforeEach
    void setUp() {
        vicePrincipal = user("vp", CareNestRole.VICE_PRINCIPAL);
        principal = user("principal", CareNestRole.PRINCIPAL);
        teacher = user("teacher", CareNestRole.TEACHER);
        // AUTH-20: quyền cấu hình của HT/PHT cần phân công còn hiệu lực
        java.sql.Date from = java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(1));
        Long campus = jdbc.queryForObject(
                "INSERT INTO campuses (code, name) VALUES ('CS-CFG', 'Campus cấu hình') RETURNING id", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, 'VICE_PRINCIPAL', ?, ?)",
                vicePrincipal.userId(),
                campus,
                from);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', ?)",
                principal.userId(),
                from);
    }

    @Test
    @DisplayName("EDU-21: cấu hình trả họ tên Hiệu trưởng, Phó HT đang hoạt động để in dưới chỗ ký tay")
    void signerNamesOfActivePrincipalAndVicePrincipals() {
        account("ht-ky", "Hiệu trưởng Ký Thử", "PRINCIPAL", "ACTIVE");
        account("pht-ky-a", "Phó HT Ký A", "VICE_PRINCIPAL", "ACTIVE");
        account("pht-ky-b", "Phó HT Ký B", "VICE_PRINCIPAL", "ACTIVE");
        account("pht-nghi", "Phó HT Đã Nghỉ", "VICE_PRINCIPAL", "INACTIVE");
        account("gv-ky", "Giáo viên Không Ký", "TEACHER", "ACTIVE");
        long leader = account("tt-ky", "Tổ trưởng Ký Thử", "TEACHER", "ACTIVE");
        long ag4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?)",
                leader,
                ag4,
                java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(1)));

        String currentYear = "9001-9002";
        jdbc.update(
                "INSERT INTO school_years (name, start_date, end_date) VALUES (?, ?, ?)",
                currentYear,
                java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(30)),
                java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(300)));

        EduConfigResponse.SignerNames names = service.getEduConfig().signerNames();

        assertThat(names.principals()).contains("Hiệu trưởng Ký Thử").doesNotContain("Giáo viên Không Ký");
        assertThat(names.vicePrincipals())
                .contains("Phó HT Ký A", "Phó HT Ký B")
                .doesNotContain("Phó HT Đã Nghỉ", "Giáo viên Không Ký");
        // Phân công bắt đầu hôm qua: có trong năm học chứa hôm nay
        assertThat(names.teamLeaders().get(currentYear).get("ag-4")).contains("Tổ trưởng Ký Thử");
        assertThat(names.teamLeaders().values().stream()
                        .flatMap(m -> m.values().stream())
                        .flatMap(List::stream))
                .doesNotContain("Giáo viên Không Ký");
    }

    @Test
    @DisplayName("EDU-11: cấu hình mặc định có 5 lĩnh vực chuẩn, chuỗi TT → PHT, 8 dòng hoạt động tuần")
    void defaultConfig() {
        EduConfigResponse config = service.getEduConfig();

        assertThat(config.domains()).hasSize(5).allMatch(EduConfigResponse.Domain::standard);
        assertThat(config.signers().branch()).containsExactly("TEAM_LEADER", "VICE_PRINCIPAL");
        assertThat(config.weekRows()).hasSize(8);
        assertThat(config.weekRows())
                .filteredOn(r -> r.key().equals("ngoai_troi"))
                .singleElement()
                .satisfies(r -> assertThat(r.extraLabel()).isEqualTo("Chơi tự do (cả tuần)"));
    }

    @Test
    @DisplayName("EDU-11: Phó HT đổi tên lĩnh vực, thêm/bỏ lĩnh vực và dòng tuần khi gửi echo người ký")
    void vicePrincipalUpdatesConfig() {
        String physical = service.getEduConfig().domains().getFirst().name();
        EduConfigResponse.Signers echo = service.getEduConfig().signers();
        EduConfigRequest.Signers echoReq = new EduConfigRequest.Signers(echo.year(), echo.branch(), echo.week());

        EduConfigResponse saved = service.updateEduConfig(
                vicePrincipal,
                new EduConfigRequest(
                        Map.of(physical, "Thể chất (đổi tên)", "Lĩnh vực mới", "Tên hiển thị mới"),
                        List.of("Lĩnh vực mới"),
                        echoReq,
                        List.of(row("hoc", "Hoạt động học", false, false), row("don", "Đón trẻ", false, true))));

        assertThat(saved.domains()).hasSize(6);
        assertThat(saved.domains().getFirst().displayLabel()).isEqualTo("Thể chất (đổi tên)");
        assertThat(saved.domains().getLast())
                .satisfies(d -> assertThat(d.name()).isEqualTo("Lĩnh vực mới"))
                .satisfies(d -> assertThat(d.displayLabel()).isEqualTo("Tên hiển thị mới"))
                .satisfies(d -> assertThat(d.standard()).isFalse());
        assertThat(saved.signers().branch()).containsExactly("TEAM_LEADER", "VICE_PRINCIPAL");
        // Mặc định cả tuần thì dòng đó cũng được gộp
        assertThat(saved.weekRows()).extracting(EduConfigResponse.WeekRow::key).containsExactly("hoc", "don");
        assertThat(saved.weekRows().getLast().canMerge()).isTrue();

        // Bỏ lĩnh vực thêm: ẩn khỏi cấu hình; tên trống thì dùng lại tên gốc
        EduConfigResponse removed = service.updateEduConfig(
                principal,
                new EduConfigRequest(
                        Map.of(physical, ""), List.of(), echoReq, List.of(row("hoc", "Hoạt động học", false, false))));
        assertThat(removed.domains()).hasSize(5);
        assertThat(removed.domains().getFirst().displayLabel()).isNull();
        assertThat(service.getEduConfig().weekRows()).hasSize(1);
    }

    @Test
    @DisplayName("EDU-11: giáo viên không sửa được; đổi người ký bị chặn; không bỏ dòng Hoạt động học")
    void rejectsInvalidConfig() {
        List<EduConfigRequest.WeekRow> rows = List.of(row("hoc", "Hoạt động học", false, false));
        EduConfigResponse.Signers echo = service.getEduConfig().signers();
        EduConfigRequest.Signers echoReq = new EduConfigRequest.Signers(echo.year(), echo.branch(), echo.week());

        // Giáo viên không có quyền
        assertApiError(
                () -> service.updateEduConfig(teacher, new EduConfigRequest(Map.of(), List.of(), echoReq, rows)),
                ApiCode.FORBIDDEN);
        // Đổi chuỗi người ký qua PUT config cũ bị chặn cho mọi caller (kể cả PHT)
        assertApiError(
                () -> service.updateEduConfig(
                        vicePrincipal,
                        new EduConfigRequest(
                                Map.of(),
                                List.of(),
                                new EduConfigRequest.Signers(List.of("TEAM_LEADER"), echo.branch(), echo.week()),
                                rows)),
                ApiCode.FORBIDDEN);
        // Không bỏ được dòng học
        assertApiError(
                () -> service.updateEduConfig(
                        vicePrincipal,
                        new EduConfigRequest(Map.of(), List.of(), echoReq, List.of(row("don", "Đón trẻ", true, true)))),
                ApiCode.BAD_REQUEST);
        String physical = service.getEduConfig().domains().getFirst().name();
        assertApiError(
                () -> service.updateEduConfig(
                        vicePrincipal, new EduConfigRequest(Map.of(), List.of(physical.toUpperCase()), echoReq, rows)),
                ApiCode.DOMAIN_NAME_EXISTS);
    }

    @Test
    @DisplayName("T016: HT lưu policy mới tăng revision, GET config trả workflow, canConfigure và yearCreationScopes")
    void savePolicyUpdatesWorkflowPolicyAndRevision() {
        EduWorkflowPolicyRequest req = new EduWorkflowPolicyRequest(
                1L,
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

        EduWorkflowPolicyResponse response = workflowService.savePolicy(principal, req);
        assertThat(response.revision()).isEqualTo(2);
        assertThat(response.canConfigure()).isTrue();

        EduConfigResponse configPrincipal = service.getEduConfig(principal);
        assertThat(configPrincipal.workflow()).isNotNull();
        assertThat(configPrincipal.workflow().revision()).isEqualTo(2);
        assertThat(configPrincipal.canConfigure()).isTrue();

        EduConfigResponse configVp = service.getEduConfig(vicePrincipal);
        assertThat(configVp.canConfigure()).isFalse();
    }

    @Test
    @DisplayName("T016: Lưu policy tự động reconcile kế hoạch năm đang REVIEW: giữ approvals cũ, cập nhật waitingRole")
    void savePolicyReconcilesPendingYearPlan() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        long ownerId = account("owner-p", "Owner P", "TEACHER", "ACTIVE");
        long approver1 = account("app-1", "Approver 1", "TEACHER", "ACTIVE");
        long yearId = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-C','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        long ageId = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);

        EduYearPlan plan =
                EduYearPlan.createDraft("P-REC", yearId, ageId, "Kế hoạch reconcile", "Mô tả", ownerId, "TL", now);
        plan.submit(ownerId, now);
        String approvalsJson = "[{\"userId\":" + approver1
                + ",\"name\":\"Approver 1\",\"roleKey\":\"TEAM_LEADER\",\"round\":1,\"at\":\"" + now
                + "\",\"note\":\"OK\"}]";
        plan.updateWorkflowState(EduPlanStatus.REVIEW, 1, approvalsJson, 1L, null, null, null, null, null, now);
        yearPlanRepository.saveAndFlush(plan);

        // Đổi policy: creators TEACHER, reviewers year thành [TEAM_LEADER, VICE_PRINCIPAL, PRINCIPAL]
        EduWorkflowPolicyRequest req = new EduWorkflowPolicyRequest(
                1L,
                Map.of(
                        "year",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL", "PRINCIPAL")),
                        "branch",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "theme",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));

        workflowService.savePolicy(principal, req);

        EduYearPlan reloaded = yearPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(reloaded.getApprovals()).contains("TEAM_LEADER");
        EduWorkflowPolicy.Evaluation eval =
                workflowService.evaluateYearPlan(reloaded, LocalDate.ofInstant(now, java.time.ZoneOffset.UTC));
        assertThat(eval.waitingRole()).isEqualTo(EduWorkflowPolicy.Role.VICE_PRINCIPAL);
    }

    @Test
    @DisplayName("T016: Hoàn tất do đổi policy chuyển status APPROVED với completionMode=POLICY_CHANGE")
    void savePolicyProspectiveCompletionByPolicyChange() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        long ownerId = account("owner-pc", "Owner PC", "TEACHER", "ACTIVE");
        long leaderId = account("leader-pc", "Leader PC", "TEACHER", "ACTIVE");
        long yearId = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-PC','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        long ageId = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);

        // Bước 1: đổi policy thành [TEAM_LEADER, VICE_PRINCIPAL] (revision 2)
        EduWorkflowPolicyRequest step1Req = new EduWorkflowPolicyRequest(
                1L,
                Map.of(
                        "year",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "branch",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "theme",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));
        workflowService.savePolicy(principal, step1Req);

        EduYearPlan plan =
                EduYearPlan.createDraft("P-AUTO", yearId, ageId, "Kế hoạch auto", "Mô tả", ownerId, "TL", now);
        plan.submit(ownerId, now);
        String approvalsJson =
                "[{\"userId\":" + leaderId + ",\"name\":\"Leader PC\",\"roleKey\":\"TEAM_LEADER\",\"round\":1,\"at\":\""
                        + now + "\",\"note\":\"Duyệt\"}]";
        plan.updateWorkflowState(EduPlanStatus.REVIEW, 1, approvalsJson, 2L, null, null, null, null, null, now);
        yearPlanRepository.saveAndFlush(plan);
        // Thêm mục tiêu để thỏa mãn điều kiện sẵn sàng ban hành (PLAN_GOAL_REQUIRED)
        jdbc.update(
                "INSERT INTO edu_plan_goals (id, year_plan_id, domain_id, sort_order, text, goal_no) VALUES (nextval('edu_plan_rows_seq'), ?, 1, 1, 'Mục tiêu kiểm thử', 1)",
                plan.getId());

        // Bước 2: đổi policy year thành chỉ cần TEAM_LEADER ký (revision 2 -> 3)
        // -> bước cuối đã duyệt -> auto APPROVED
        EduWorkflowPolicyRequest step2Req = new EduWorkflowPolicyRequest(
                2L,
                Map.of(
                        "year", new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEACHER"), List.of("TEAM_LEADER")),
                        "branch",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "theme",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));

        workflowService.savePolicy(principal, step2Req);

        EduYearPlan reloaded = yearPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(reloaded.getCompletionMode()).isEqualTo("POLICY_CHANGE");
    }

    @Test
    @DisplayName("T016: Rollback toàn bộ policy change khi prospective publication validation thất bại")
    void savePolicyRollsBackWhenPublicationPrerequisitesFail() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        long ownerId = account("owner-rb", "Owner RB", "TEACHER", "ACTIVE");
        long leaderId = account("leader-rb", "Leader RB", "TEACHER", "ACTIVE");
        long yearId = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-BAD','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        long ageId = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);

        // Bước 1: đổi policy thành [TEAM_LEADER, VICE_PRINCIPAL] (revision 2)
        EduWorkflowPolicyRequest step1Req = new EduWorkflowPolicyRequest(
                1L,
                Map.of(
                        "year",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "branch",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "theme",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));
        workflowService.savePolicy(principal, step1Req);

        EduYearPlan plan = EduYearPlan.createDraft("P-BAD", yearId, ageId, "Kế hoạch bad", "Mô tả", ownerId, "TL", now);
        plan.submit(ownerId, now);
        String approvalsJson =
                "[{\"userId\":" + leaderId + ",\"name\":\"Leader RB\",\"roleKey\":\"TEAM_LEADER\",\"round\":1,\"at\":\""
                        + now + "\",\"note\":\"Duyệt\"}]";
        plan.updateWorkflowState(EduPlanStatus.REVIEW, 1, approvalsJson, 2L, null, null, null, null, null, now);
        plan.updateInfo("", "Mô tả", now); // Tiêu đề rỗng vi phạm validation
        yearPlanRepository.saveAndFlush(plan);

        // Bước 2: đổi policy để auto-publish -> validation lỗi -> rollback
        EduWorkflowPolicyRequest step2Req = new EduWorkflowPolicyRequest(
                2L,
                Map.of(
                        "year", new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEACHER"), List.of("TEAM_LEADER")),
                        "branch",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                        "theme",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL"))));

        assertThatThrownBy(() -> workflowService.savePolicy(principal, step2Req))
                .isInstanceOf(GlobalException.class);

        // Transaction bị đánh dấu rollback do validation thất bại trong cùng transaction
        assertThat(org.springframework.test.context.transaction.TestTransaction.isFlaggedForRollback())
                .isTrue();
    }

    @Test
    @DisplayName("T016: PUT /education-plans/config cũ từ chối thay đổi signers cho mọi caller (kể cả HT)")
    void legacyConfigEndpointRejectsSignersModification() {
        List<EduConfigRequest.WeekRow> rows = List.of(row("hoc", "Hoạt động học", false, false));
        // Gửi signers khác với server projection (ví dụ thêm PRINCIPAL)
        EduConfigRequest req = new EduConfigRequest(
                Map.of(),
                List.of(),
                new EduConfigRequest.Signers(
                        List.of("VICE_PRINCIPAL", "PRINCIPAL"),
                        List.of("TEAM_LEADER", "VICE_PRINCIPAL"),
                        List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
                rows);

        // Kể cả HT cũng không được đổi qua config cũ
        assertApiError(() -> service.updateEduConfig(principal, req), ApiCode.FORBIDDEN);
        assertApiError(() -> service.updateEduConfig(vicePrincipal, req), ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName(
            "T016: PUT /education-plans/config cũ chấp nhận signers echo đúng server projection và cập nhật field khác")
    void legacyConfigEndpointAcceptsSignersEcho() {
        String physical = service.getEduConfig().domains().getFirst().name();
        EduConfigResponse current = service.getEduConfig();
        EduConfigRequest.Signers echoSigners = new EduConfigRequest.Signers(
                current.signers().year(),
                current.signers().branch(),
                current.signers().week());

        EduConfigResponse updated = service.updateEduConfig(
                vicePrincipal,
                new EduConfigRequest(
                        Map.of(physical, "Thể chất mới"),
                        List.of(),
                        echoSigners,
                        List.of(row("hoc", "Hoạt động học", false, false), row("don", "Đón trẻ", false, true))));

        assertThat(updated.domains().getFirst().displayLabel()).isEqualTo("Thể chất mới");
    }

    private static EduConfigRequest request(
            Map<String, String> labels, List<String> extras, List<String> chain, List<EduConfigRequest.WeekRow> rows) {
        return new EduConfigRequest(labels, extras, new EduConfigRequest.Signers(chain, chain, chain), rows);
    }

    private static EduConfigRequest.WeekRow row(String key, String label, boolean canMerge, boolean allWeek) {
        return new EduConfigRequest.WeekRow(key, label, canMerge, allWeek, null);
    }

    private static void assertApiError(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode().getHttpStatus())
                        .isEqualTo(code.getHttpStatus()));
    }

    private long account(String username, String fullName, String role, String status) {
        return jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role, status) VALUES (?, ?, ?, ?, ?)"
                        + " RETURNING id",
                Long.class,
                "kc-" + username,
                username,
                fullName,
                role,
                status);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(id, "kc-" + username, username, "Demo " + username, null, Set.of(role));
    }
}
