package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Luồng kế hoạch năm học SD-01..SD-08 qua service + policy thật trên PostgreSQL. Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class SchoolYearTeamPlanIntegrationTest {

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
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EduPlanAccessPolicy policy;

    private long schoolYear;
    private long ageGroup3;
    private long ageGroup4;
    private long domainPhysical;
    private long domainLanguage;
    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private CurrentUser leader3;
    private CurrentUser leader4;
    private CurrentUser teacher3;

    @Autowired
    private com.carenest.service.eduplan.EduWorkflowService policyService;

    @Autowired
    private com.carenest.service.eduplan.EduDurationPolicyService durations;

    private final Map<Long, UUID> sessions = new HashMap<>();

    @BeforeEach
    void setUp() {
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-A', 'Campus A') RETURNING id");
        schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        ageGroup4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        domainPhysical = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);
        domainLanguage = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 3", Long.class);
        long class3 = insertId(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'MGB1', ?) "
                        + "RETURNING id",
                campus,
                schoolYear,
                ageGroup3);

        vicePrincipal = user("vp", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                vicePrincipal.userId(),
                campus);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-09-01')",
                principal.userId());
        leader3 = user("leader3", CareNestRole.TEACHER);
        insertLeader(leader3, ageGroup3);
        leader4 = user("leader4", CareNestRole.TEACHER);
        insertLeader(leader4, ageGroup4);
        teacher3 = user("teacher3", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01')",
                teacher3.userId(),
                class3);
    }

    private UUID session(CurrentUser user) {
        return sessions.computeIfAbsent(user.userId(), ignored -> UUID.randomUUID());
    }

    private YearPlanEditRequest edit(CurrentUser user, Long id, boolean acquire) {
        YearPlanDetailResponse detail = service.getYearPlan(user, id);
        YearPlanEditRequest context = new YearPlanEditRequest(session(user), detail.revision(), UUID.randomUUID());
        if (acquire && detail.editing() == null && detail.permissions().canEdit()) {
            service.startEditing(user, id, context);
        }
        return context;
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

    private YearPlanRequest annualRequest() {
        return new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch năm học kiểm thử",
                "",
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(
                                new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()),
                                new YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "Mục tiêu nguồn",
                                        List.of(new YearPlanRequest.Content(null, "Nội dung nguồn", List.of())))))),
                annualThemes(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                // Mẫu nhánh cần đủ ngày bắt đầu/kết thúc
                List.of(new YearPlanRequest.BranchTemplate(
                        "template-a",
                        null,
                        "theme-a",
                        "Dự án gợi ý",
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2026, 9, 11))));
    }

    /** Kế hoạch năm cần tối thiểu 9 chủ đề liền nhau: chủ đề A + 8 chủ đề phụ 2 tuần (đúng giới hạn mặc định). */
    private static List<YearPlanRequest.Theme> annualThemes() {
        var themes = new java.util.ArrayList<YearPlanRequest.Theme>();
        themes.add(new YearPlanRequest.Theme(
                "theme-a", null, "Chủ đề A", "A", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 18), "", ""));
        for (int i = 0; i < 8; i++) {
            LocalDate monday = LocalDate.of(2026, 9, 21).plusWeeks(2L * i);
            themes.add(new YearPlanRequest.Theme(
                    "theme-x" + i, null, "Chủ đề phụ " + i, "X" + i, monday, monday.plusDays(11), "", ""));
        }
        return themes;
    }

    private static YearPlanDetailResponse.Row goal(YearPlanDetailResponse plan) {
        return plan.domains().getFirst().rows().stream()
                .filter(r -> r.kind().equals("goal"))
                .findFirst()
                .orElseThrow();
    }

    private YearPlanDetailResponse issuedAnnual() {
        var draft = service.createYearPlan(
                vicePrincipal, annualRequest(), new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var reviewed = service.submitYearPlan(vicePrincipal, draft.id(), null, edit(vicePrincipal, draft.id(), true));
        return service.issueYearPlan(
                principal, draft.id(), "Đạt", new YearPlanEditRequest(null, reviewed.revision(), UUID.randomUUID()));
    }

    /** Ban hành kế hoạch năm tự tạo bản nháp tổ cho nhóm tuổi; dùng bản đó thay vì tạo mới. */
    private YearPlanDetailResponse teamFrom(YearPlanDetailResponse source) {
        Long teamId = jdbc.queryForObject(
                "SELECT id FROM edu_team_plans WHERE school_year_id = ? AND age_group_id = ? "
                        + "AND curriculum_source_plan_id = ? ORDER BY version_no LIMIT 1",
                Long.class,
                schoolYear,
                ageGroup3,
                source.id());
        return service.getYearPlan(leader3, teamId);
    }

    @Test
    @DisplayName("FR006/009: PHT soạn annual, HT duyệt; mẫu tên không bắt lịch và không gán tick annual")
    void annualWorkflowAndNameOnlyTemplate() {
        var draft = service.createYearPlan(
                vicePrincipal, annualRequest(), new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertThat(draft.planLevel()).isEqualTo(com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR);
        assertThat(draft.permissions().canEdit()).isTrue();
        assertThat(draft.branchTemplates())
                .singleElement()
                .satisfies(t -> assertThat(t.name()).isEqualTo("Dự án gợi ý"));
        var pending = service.submitYearPlan(vicePrincipal, draft.id(), null, edit(vicePrincipal, draft.id(), true));
        assertThat(pending.review().waitingRole()).isEqualTo("PRINCIPAL");
        assertThatThrownBy(() -> service.issueYearPlan(
                        vicePrincipal,
                        draft.id(),
                        "Tự duyệt",
                        new YearPlanEditRequest(null, pending.revision(), UUID.randomUUID())))
                .isInstanceOf(GlobalException.class);
        var approved = service.issueYearPlan(
                principal, draft.id(), "Đạt", new YearPlanEditRequest(null, pending.revision(), UUID.randomUUID()));
        assertThat(approved.status()).isEqualTo(EduPlanStatus.APPROVED);
    }

    @Test
    @DisplayName("FR013/014: hai cấp cùng năm/tuổi/version1; TEAM clone local IDs và đúng nguồn")
    void levelsHaveIndependentChainsAndCloneTheIssuedSource() {
        var annual = issuedAnnual();
        var team = teamFrom(annual);
        assertThat(team.versionNo()).isEqualTo(1);
        assertThat(annual.versionNo()).isEqualTo(1);
        assertThat(team.curriculumSourcePlanId()).isEqualTo(annual.id());
        assertThat(team.sourcePlanId()).isNull();
        assertThat(team.sourceMode()).isEqualTo("INHERITED");
        assertThat(team.themes().getFirst().id())
                .isNotEqualTo(annual.themes().getFirst().id());
        assertThat(team.branchTemplates().getFirst().sourceTemplateId())
                .isEqualTo(annual.branchTemplates().getFirst().id());
        assertThat(service.getYearPlans(
                                vicePrincipal,
                                schoolYear,
                                null,
                                null,
                                PageRequest.of(0, 20),
                                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR)
                        .items())
                .hasSize(1);
        // EDU-73: bản tổ Nháp chỉ tổ trưởng phụ trách thấy
        assertThat(service.getYearPlans(leader3, schoolYear, null, null, PageRequest.of(0, 20))
                        .items())
                .hasSize(1);
    }

    @Test
    @DisplayName("FR008/013: annual mới reuse TEAM draft và giữ nội dung local để rà soát")
    void publishingNewAnnualReusesOpenTeamDraftAndPreservesLocalContents() {
        var annual = issuedAnnual();
        var originalTeam = teamFrom(annual);
        var annualV2 = service.createVersion(vicePrincipal, annual.id(), edit(vicePrincipal, annual.id(), true));
        var reviewed =
                service.submitYearPlan(vicePrincipal, annualV2.id(), null, edit(vicePrincipal, annualV2.id(), true));
        var issued = service.issueYearPlan(
                principal, annualV2.id(), "Đạt", new YearPlanEditRequest(null, reviewed.revision(), UUID.randomUUID()));

        var teamVersions = jdbc.queryForList(
                "SELECT id, version_no, status, curriculum_source_plan_id FROM edu_team_plans WHERE school_year_id=? AND age_group_id=? ORDER BY id",
                schoolYear,
                ageGroup3);
        assertThat(teamVersions).hasSize(1);
        assertThat(((Number) teamVersions.getFirst().get("id")).longValue()).isEqualTo(originalTeam.id());
        var detail = service.getYearPlan(leader3, originalTeam.id());
        assertThat(detail.status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(detail.curriculumSourcePlanId()).isEqualTo(issued.id());
        assertThat(detail.previousSourcePlanId()).isEqualTo(annual.id());
        assertThat(goal(detail).contents()).isNotEmpty();
        assertThat(goal(detail).contents().getFirst().id())
                .isEqualTo(goal(originalTeam).contents().getFirst().id());
    }

    @Test
    @DisplayName("FR014: không tạo mới TEAM thiếu nguồn; từ chối trước mutation")
    void newTeamRequiresIssuedAnnualSource() {
        Long before = jdbc.queryForObject("SELECT count(*) FROM edu_year_plans", Long.class);
        assertThatThrownBy(() -> service.createYearPlan(
                        leader3,
                        new YearPlanRequest(schoolYear, ageGroup3, "Tổ", "", List.of(), List.of(), List.of()),
                        new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID())))
                .isInstanceOf(GlobalException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_year_plans", Long.class))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("EDU57: PHT không được xóa TEAM và capability không hiện quyền xóa")
    void vicePrincipalCannotDeleteTeam() {
        var team = teamFrom(issuedAnnual());
        // EDU-73(3): PHT không phụ trách không mở được bản tổ Nháp; người phụ trách cũng không có quyền xóa
        assertThat(team.permissions().canDelete()).isFalse();
        assertThatThrownBy(() -> service.deleteYearPlan(
                        vicePrincipal, team.id(), new YearPlanEditRequest(null, team.revision(), UUID.randomUUID())))
                .isInstanceOf(GlobalException.class);
        assertThat(service.getYearPlan(leader3, team.id()).id()).isEqualTo(team.id());
    }

    @Test
    @DisplayName("FR011: chỉ HT cấu hình, revision riêng và snapshot hồ sơ không hồi tố")
    void durationConfigurationAndSnapshots() {
        var annual = service.createYearPlan(
                vicePrincipal, annualRequest(), new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var before = durations.getDurationPolicy(principal);
        var request = new com.carenest.dto.eduplan.EduDurationPolicyRequest(
                before.revision(),
                new com.carenest.dto.eduplan.EduDurationPolicyRequest.Limits(2, 5),
                new com.carenest.dto.eduplan.EduDurationPolicyRequest.Limits(1, 3));
        assertThatThrownBy(() -> durations.saveDurationPolicy(vicePrincipal, request))
                .isInstanceOf(GlobalException.class);
        var changed = durations.saveDurationPolicy(principal, request);
        assertThat(changed.revision()).isEqualTo(before.revision() + 1);
        assertThat(service.getYearPlan(vicePrincipal, annual.id()).durationPolicySnapshot())
                .isEqualTo(annual.durationPolicySnapshot());
        assertThatThrownBy(() -> durations.saveDurationPolicy(principal, request))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("FR006: PUT policy ba key giữ cấu hình schoolYear đang có")
    void oldPolicyPutKeepsAnnualPolicy() {
        var config = policyService.getPolicy(principal);
        var entries =
                new java.util.LinkedHashMap<String, com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry>();
        config.policies().forEach((key, value) -> {
            if (!key.equals("schoolYear"))
                entries.put(
                        key,
                        new com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry(
                                value.creators(), value.reviewers()));
        });
        var saved = policyService.savePolicy(
                principal, new com.carenest.dto.eduplan.EduWorkflowPolicyRequest(config.revision(), entries));
        assertThat(saved.policies().get("schoolYear"))
                .isEqualTo(config.policies().get("schoolYear"));
    }

    private YearPlanRequest inheritedRequest(YearPlanDetailResponse plan, String themeName) {
        return new YearPlanRequest(
                schoolYear,
                ageGroup3,
                plan.title(),
                plan.description(),
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
                                                                c.id(), c.text(), List.of()))
                                                        .toList(),
                                                r.curriculumGoalId(),
                                                r.curriculumSubDomainId(),
                                                r.curriculumExpectedResultId()))
                                        .toList()))
                        .toList(),
                plan.themes().stream()
                        .map(t -> new YearPlanRequest.Theme(
                                t.id().toString(),
                                t.id(),
                                themeName == null ? t.name() : themeName,
                                t.abbr(),
                                t.startDate(),
                                t.endDate(),
                                t.festivals(),
                                t.note()))
                        .toList(),
                plan.breaks().stream()
                        .map(b -> new YearPlanRequest.Break(b.id(), b.label(), b.startDate(), b.endDate()))
                        .toList(),
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                plan.curriculumSourcePlanId(),
                plan.branchTemplates().stream()
                        .map(t -> new YearPlanRequest.BranchTemplate(
                                t.id().toString(),
                                t.id(),
                                t.themeId().toString(),
                                t.name(),
                                t.startDate(),
                                t.endDate()))
                        .toList());
    }

    @Test
    void inheritedTeamAcceptsUnchangedTablesAndRejectsThemeMutation() {
        var team = teamFrom(issuedAnnual());
        var saved = service.updateYearPlan(
                leader3, team.id(), inheritedRequest(team, null), edit(leader3, team.id(), true));
        assertThat(saved.themes()).isEqualTo(team.themes());
        assertThatThrownBy(() -> service.updateYearPlan(
                        leader3,
                        team.id(),
                        inheritedRequest(saved, "Không được sửa tên"),
                        edit(leader3, team.id(), true)))
                .isInstanceOf(GlobalException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT name FROM edu_themes WHERE id=?",
                        String.class,
                        team.themes().getFirst().id()))
                .isEqualTo(team.themes().getFirst().name());
    }

    @Test
    void creatingTeamRejectsSubmittedCurriculumDifferentFromAnnual() {
        var annual = issuedAnnual();
        var request = annualRequest();
        Long before = jdbc.queryForObject("SELECT count(*) FROM edu_year_plans", Long.class);
        assertThatThrownBy(() -> service.createYearPlan(
                        leader3,
                        new YearPlanRequest(
                                schoolYear,
                                ageGroup3,
                                "Tổ",
                                "",
                                request.domains(),
                                request.themes(),
                                request.breaks(),
                                com.carenest.utils.enums.EduPlanLevel.TEAM,
                                annual.id(),
                                request.branchTemplates()),
                        new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID())))
                .isInstanceOf(GlobalException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_year_plans", Long.class))
                .isEqualTo(before);
    }

    @Test
    void templateDatesOutsideParentAreRejectedByServer() {
        var source = annualRequest();
        var request = new YearPlanRequest(
                source.schoolYearId(),
                source.ageGroupId(),
                source.title(),
                source.description(),
                source.domains(),
                source.themes(),
                source.breaks(),
                source.planLevel(),
                null,
                List.of(new YearPlanRequest.BranchTemplate(
                        "invalid",
                        null,
                        "theme-a",
                        "Ngoài cha",
                        LocalDate.of(2026, 9, 14),
                        LocalDate.of(2026, 9, 25))));
        assertThatThrownBy(() -> service.createYearPlan(
                        vicePrincipal, request, new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID())))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        error -> assertThat(error.getApiCode())
                                .isEqualTo(com.carenest.utils.ApiCode.EDUCATION_CALENDAR_INVALID));
    }

    private YearPlanRequest labelRequest(YearPlanDetailResponse detail, String label) {
        var base = annualRequest();
        var domains = detail == null
                ? base.domains()
                : detail.domains().stream()
                        .map(d -> new YearPlanRequest.Domain(
                                d.domainId(),
                                d.rows().stream()
                                        .map(r -> new YearPlanRequest.Row(
                                                r.id(),
                                                r.kind(),
                                                r.text(),
                                                r.contents().stream()
                                                        .map(c -> new YearPlanRequest.Content(
                                                                c.id(),
                                                                c.text(),
                                                                c.themeIds().stream()
                                                                        .map(String::valueOf)
                                                                        .toList()))
                                                        .toList(),
                                                r.curriculumGoalId(),
                                                r.curriculumSubDomainId(),
                                                r.curriculumExpectedResultId()))
                                        .toList()))
                        .toList();
        domains = domains.stream()
                .map(d -> new YearPlanRequest.Domain(
                        d.domainId(),
                        d.rows().stream()
                                .map(r -> new YearPlanRequest.Row(
                                        r.id(),
                                        r.kind(),
                                        r.text(),
                                        r.contents(),
                                        r.curriculumGoalId(),
                                        r.curriculumSubDomainId(),
                                        r.curriculumExpectedResultId(),
                                        "goal".equals(r.kind()) ? label : null))
                                .toList()))
                .toList();
        return new YearPlanRequest(
                schoolYear,
                ageGroup3,
                detail == null ? base.title() : detail.title(),
                "",
                domains,
                detail == null
                        ? base.themes()
                        : detail.themes().stream()
                                .map(t -> new YearPlanRequest.Theme(
                                        String.valueOf(t.id()),
                                        t.id(),
                                        t.name(),
                                        t.abbr(),
                                        t.startDate(),
                                        t.endDate(),
                                        t.festivals(),
                                        t.note()))
                                .toList(),
                List.of(),
                detail == null ? base.planLevel() : detail.planLevel(),
                detail == null ? null : detail.curriculumSourcePlanId(),
                List.of());
    }

    @Test
    @DisplayName("EDU-58: nhãn mục tiêu lưu/tải, client cũ giữ nhãn, rỗng xóa nhãn nhưng không đổi số/ID")
    void goalDisplayLabelRoundTripAndCompatibility() {
        var draft = service.createYearPlan(
                vicePrincipal,
                labelRequest(null, "Mục tiêu 01"),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var row = goal(draft);
        assertThat(row.displayLabel()).isEqualTo("Mục tiêu 01");
        assertThat(row.goalNo()).isEqualTo((short) 1);
        var retained = service.updateYearPlan(
                vicePrincipal, draft.id(), labelRequest(draft, null), edit(vicePrincipal, draft.id(), true));
        assertThat(goal(retained).displayLabel()).isEqualTo("Mục tiêu 01");
        var cleared = service.updateYearPlan(
                vicePrincipal, draft.id(), labelRequest(retained, ""), edit(vicePrincipal, draft.id(), true));
        entityManager.flush();
        entityManager.clear();
        var reloaded = goal(service.getYearPlan(vicePrincipal, cleared.id()));
        assertThat(reloaded.displayLabel()).isNull();
        assertThat(reloaded.id()).isEqualTo(row.id());
        assertThat(reloaded.goalNo()).isEqualTo(row.goalNo());
    }

    @Test
    @DisplayName("EDU-58: nhãn theo annual sang TEAM và version, TEAM không đổi nhãn nguồn")
    void goalDisplayLabelClonesAndTeamCannotChangeIt() {
        var draft = service.createYearPlan(
                vicePrincipal,
                labelRequest(null, "Mục tiêu 01"),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var reviewed = service.submitYearPlan(vicePrincipal, draft.id(), null, edit(vicePrincipal, draft.id(), true));
        var annual = service.issueYearPlan(
                principal, draft.id(), null, new YearPlanEditRequest(null, reviewed.revision(), UUID.randomUUID()));
        var team = teamFrom(annual);
        assertThat(goal(team).displayLabel()).isEqualTo("Mục tiêu 01");
        assertThatThrownBy(() -> service.updateYearPlan(
                        leader3, team.id(), labelRequest(team, "Đã đổi 99"), edit(leader3, team.id(), true)))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        e -> assertThat(e.getApiCode())
                                .isEqualTo(com.carenest.utils.ApiCode.INHERITED_CURRICULUM_READ_ONLY));
        assertThat(goal(service.getYearPlan(leader3, team.id())).displayLabel()).isEqualTo("Mục tiêu 01");
        var version = service.createVersion(
                vicePrincipal,
                annual.id(),
                new YearPlanEditRequest(session(vicePrincipal), annual.revision(), UUID.randomUUID()));
        assertThat(goal(version).displayLabel()).isEqualTo("Mục tiêu 01");
    }

    @Test
    @DisplayName("EDU-58: Bean Validation chặn nhãn vượt 100 ký tự")
    void rejectsOverlongGoalLabel() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var row = new YearPlanRequest.Row(null, "goal", "Mục tiêu", List.of(), null, null, null, "x".repeat(101));
            assertThat(factory.getValidator().validate(row))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("displayLabel"));
        }
    }

    private YearPlanRequest annualWithBranches(List<YearPlanRequest.BranchTemplate> branches) {
        var base = annualRequest();
        return new YearPlanRequest(
                base.schoolYearId(),
                base.ageGroupId(),
                base.title(),
                base.description(),
                base.domains(),
                base.themes(),
                base.breaks(),
                base.planLevel(),
                base.curriculumSourcePlanId(),
                branches);
    }

    @Test
    @DisplayName("EDU-59: nháp giữ ngày nhánh lệch thứ, gửi duyệt từ chối")
    void branchWeekdaysAreCheckedOnSubmitButDraftRemainsEditable() {
        var draft = service.createYearPlan(
                vicePrincipal,
                annualWithBranches(List.of(new YearPlanRequest.BranchTemplate(
                        "bad", null, "theme-a", "Lệch thứ", LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 11)))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertThat(draft.branchTemplates().getFirst().startDate()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThatThrownBy(() ->
                        service.submitYearPlan(vicePrincipal, draft.id(), null, edit(vicePrincipal, draft.id(), true)))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        e -> assertThat(e.getApiCode())
                                .isEqualTo(com.carenest.utils.ApiCode.EDUCATION_CALENDAR_INVALID));
    }

    @Test
    @DisplayName("EDU-59: nháp giữ hai nhánh trùng lịch, gửi duyệt từ chối")
    void overlappingSiblingDraftCannotBeSubmitted() {
        var draft = service.createYearPlan(
                vicePrincipal,
                annualWithBranches(List.of(
                        new YearPlanRequest.BranchTemplate(
                                "a", null, "theme-a", "Một", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 18)),
                        new YearPlanRequest.BranchTemplate(
                                "b", null, "theme-a", "Hai", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertThat(draft.branchTemplates()).hasSize(2);
        assertThatThrownBy(() ->
                        service.submitYearPlan(vicePrincipal, draft.id(), null, edit(vicePrincipal, draft.id(), true)))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        e -> assertThat(e.getApiCode())
                                .isEqualTo(com.carenest.utils.ApiCode.EDUCATION_CALENDAR_INVALID));
    }

    @Test
    @DisplayName("EDU-60: tổ trưởng chỉ đọc annual ban hành đúng tuổi, list và detail cùng scope")
    void annualVisibilityMatchesPublicationAndAssignment() {
        var draft = service.createYearPlan(
                vicePrincipal, annualRequest(), new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        // EDU-73(3): trong phạm vi xem nhưng chưa ban hành ⇒ 7384
        assertThatThrownBy(() -> service.getYearPlan(leader3, draft.id()))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", com.carenest.utils.ApiCode.EDU_PLAN_NOT_RELEASED);
        assertThat(service.getYearPlans(
                                leader3,
                                schoolYear,
                                null,
                                null,
                                PageRequest.of(0, 1),
                                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR)
                        .items())
                .isEmpty();
        var review = service.submitYearPlan(vicePrincipal, draft.id(), null, edit(vicePrincipal, draft.id(), true));
        assertThatThrownBy(() -> service.getYearPlan(leader3, draft.id())).isInstanceOf(GlobalException.class);
        var published = service.issueYearPlan(
                principal, draft.id(), "Đạt", new YearPlanEditRequest(null, review.revision(), UUID.randomUUID()));
        assertThat(service.getYearPlan(leader3, published.id()).id()).isEqualTo(published.id());
        assertThatThrownBy(() -> service.getYearPlan(leader4, published.id())).isInstanceOf(GlobalException.class);
        var page = service.getYearPlans(
                leader3,
                schoolYear,
                null,
                null,
                PageRequest.of(0, 1),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR);
        assertThat(page.items()).extracting(x -> x.id()).containsExactly(published.id());
        assertThat(service.getYearPlans(
                                leader4,
                                schoolYear,
                                ageGroup3,
                                null,
                                PageRequest.of(0, 1),
                                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR)
                        .items())
                .isEmpty();
        jdbc.update("UPDATE staff_assignments SET valid_from = '2026-10-23' WHERE user_id = ?", leader3.userId());
        entityManager.clear();
        assertThatThrownBy(() -> service.getYearPlan(leader3, published.id())).isInstanceOf(GlobalException.class);
        jdbc.update(
                "UPDATE staff_assignments SET valid_from = '2026-09-01', valid_to = '2026-10-21' WHERE user_id = ?",
                leader3.userId());
        entityManager.clear();
        assertThatThrownBy(() -> service.getYearPlan(leader3, published.id())).isInstanceOf(GlobalException.class);
        jdbc.update("UPDATE staff_assignments SET valid_to = '2026-10-22' WHERE user_id = ?", leader3.userId());
        entityManager.clear();
        assertThat(service.getYearPlan(leader3, published.id()).id()).isEqualTo(published.id());
        jdbc.update("DELETE FROM staff_assignments WHERE user_id = ?", leader3.userId());
        entityManager.clear();
        assertThatThrownBy(() -> service.getYearPlan(leader3, published.id())).isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("EDU-60: draft version không lộ qua chain hoặc changes của bản ban hành")
    void hiddenAnnualVersionDoesNotLeakThroughHistory() {
        var annual = issuedAnnual();
        var draft = service.createVersion(
                vicePrincipal,
                annual.id(),
                new YearPlanEditRequest(session(vicePrincipal), annual.revision(), UUID.randomUUID()),
                null);
        var view = service.getYearPlan(leader3, annual.id());
        assertThat(view.versions()).extracting(v -> v.id()).containsExactly(annual.id());
        assertThat(view.current()).isTrue();
        // EDU-73(3): bản chưa ban hành trong phạm vi xem ⇒ 7384
        assertThatThrownBy(() -> service.getChanges(leader3, annual.id(), draft.id()))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", com.carenest.utils.ApiCode.EDU_PLAN_NOT_RELEASED);
        assertThat(service.getYearPlan(vicePrincipal, draft.id()).id()).isEqualTo(draft.id());
    }

    @Autowired
    private org.springframework.web.context.WebApplicationContext webContext;

    @Test
    @DisplayName("EDU-60: HTTP controller thật và PostgreSQL kiểm scope tài khoản/age, không mock service")
    void annualHttpReadsEnforceSameScope() throws Exception {
        var annual = issuedAnnual();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        var token = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(leader3.keycloakUserId()))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TEACHER"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/education-plans/goals")
                        .param("planLevel", "SCHOOL_YEAR")
                        .with(token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.data.totalElements")
                        .value(1));
        var otherToken =
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.subject(leader4.keycloakUserId()))
                        .authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TEACHER"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/education-plans/goals/" + annual.id())
                        .with(otherToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode")
                        .value(7004));
    }
}
