package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.utils.ApiCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
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

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduWorkflowServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EduWorkflowService workflowService;

    @Autowired
    private EduPlanRepository yearPlanRepository;

    @Autowired
    private EduClassDocRepository classDocRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("EDU-54: recordAuthor ghi và cập nhật thời gian, lấy union authors cho dossier")
    void recordAuthorAndUnionAuthors() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        long ownerId = createAccount("wf-owner");
        long coauthorId = createAccount("wf-coauthor");
        long yearId = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-T','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        long ageId = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);

        EduPlan yearPlan = legacyDraft("WF-P1", yearId, ageId, "Kế hoạch năm thử", "Mô tả", ownerId, "TL", now);
        yearPlanRepository.saveAndFlush(yearPlan);

        workflowService.recordAuthor(yearPlan.getId(), null, coauthorId, "CONTENT_WRITE", now);
        Set<Long> yearAuthors = workflowService.getAuthorUserIdsForYearPlan(yearPlan.getId());
        assertThat(yearAuthors).contains(ownerId, coauthorId);

        long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('C-WF','Campus WF') RETURNING id", Long.class);
        long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms(name,campus_id,school_year_id,age_group_id) VALUES ('Lớp WF',?,?,?) RETURNING id",
                Long.class,
                campus,
                yearId,
                ageId);

        Long themeId = jdbc.queryForObject(
                "INSERT INTO edu_themes(team_plan_id,name,abbr,sort_order) VALUES (?,'Chủ đề 1','CD1',1) RETURNING id",
                Long.class,
                yearPlan.getId());

        EduClassDoc themeDoc =
                EduClassDoc.create("theme-doc-01", "theme", classroom, yearPlan.getId(), themeId, null, ownerId);
        classDocRepository.saveAndFlush(themeDoc);

        EduClassDoc weekDoc = EduClassDoc.create(
                "week-doc-01", "week", classroom, yearPlan.getId(), themeId, LocalDate.of(2026, 9, 7), coauthorId);
        classDocRepository.saveAndFlush(weekDoc);

        long weekAuthorId = createAccount("wf-week-author");
        workflowService.recordAuthor(null, weekDoc.getId(), weekAuthorId, "CONTENT_WRITE", now);

        Set<Long> dossierAuthors = workflowService.getUnionAuthorUserIdsForDossier(themeDoc);
        assertThat(dossierAuthors).contains(ownerId, coauthorId, weekAuthorId);

        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                weekAuthorId,
                ageId);
        jdbc.update("UPDATE edu_class_docs SET status='REVIEW' WHERE id=?", themeDoc.getId());
        entityManager.refresh(themeDoc);
        var evaluation = workflowService
                .evaluateClassDocs(java.util.List.of(themeDoc), LocalDate.now())
                .get(themeDoc.getId());
        assertThat(evaluation.waitingRole()).isEqualTo(EduWorkflowPolicy.Role.VICE_PRINCIPAL);
        long otherLeader = createAccount("wf-other-leader");
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                otherLeader,
                ageId);
        assertThat(workflowService
                        .evaluateClassDocs(java.util.List.of(themeDoc), LocalDate.now())
                        .get(themeDoc.getId())
                        .waitingRole())
                .isEqualTo(EduWorkflowPolicy.Role.TEAM_LEADER);
    }

    @Test
    @DisplayName("EDU-54: validateClassDocVersion kiểm tra stale version và sentinel -1")
    void validatesClassDocVersions() {
        long ownerId = createAccount("wf-ver-owner");
        long yearId = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-V','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        long ageId = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('C-V','Campus V') RETURNING id", Long.class);
        long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms(name,campus_id,school_year_id,age_group_id) VALUES ('Lớp V',?,?,?) RETURNING id",
                Long.class,
                campus,
                yearId,
                ageId);

        EduPlan yearPlan =
                legacyDraft("WF-P2", yearId, ageId, "Kế hoạch năm thử V", "Mô tả", ownerId, "TL", Instant.now());
        yearPlanRepository.saveAndFlush(yearPlan);

        EduClassDoc doc = EduClassDoc.create("doc-v-01", "branch", classroom, yearPlan.getId(), null, null, ownerId);
        classDocRepository.saveAndFlush(doc);

        assertThatCode(() -> workflowService.validateClassDocVersion(doc, 0L, null))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> workflowService.validateClassDocVersion(doc, 999L, null))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.CONCURRENT_UPDATE);

        assertThatThrownBy(() -> workflowService.validateClassDocVersion(doc, -1L, null))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.CONCURRENT_UPDATE);
    }

    @Autowired
    private com.carenest.service.eduplan.EduClassDocService classDocs;

    @Autowired
    private tools.jackson.databind.ObjectMapper mapper;

    @Test
    void changingReviewPolicyRetainsRealApprovalAndDoesNotReopenPublishedDossier() {
        var fixture = classFixture(true, true);
        var principal = workflowUser("wf-principal", com.carenest.utils.enums.CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        var doc = classDocRepository.findByDocKey(fixture.themeKey()).orElseThrow();
        doc.submit(1);
        var approval = new EduWorkflowPolicy.Approval(
                fixture.vp().userId(), "PHT", EduWorkflowPolicy.Role.VICE_PRINCIPAL, Instant.now(), "Đồng ý", 1);
        doc.advanceReview(workflowService.serializeApprovals(java.util.List.of(approval)), Instant.now());
        classDocRepository.saveAndFlush(doc);
        var policies = new java.util.HashMap<String, com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry>();
        workflowService
                .getPolicy(principal)
                .policies()
                .forEach((key, value) -> policies.put(
                        key,
                        new com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry(
                                value.creators(), value.reviewers())));
        policies.put(
                "theme",
                new com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry(
                        java.util.List.of("TEACHER"), java.util.List.of("VICE_PRINCIPAL")));
        workflowService.savePolicy(
                principal,
                new com.carenest.dto.eduplan.EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), policies));
        entityManager.flush();
        entityManager.refresh(doc);
        assertThat(doc.getStatus()).isEqualTo(com.carenest.utils.enums.EduClassDocStatus.APPROVED);
        assertThat(workflowService.parseApprovals(doc.getApprovals())).containsExactly(approval);
        String snapshot = doc.getPrintSnapshot();
        policies.put(
                "theme",
                new com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry(
                        java.util.List.of("TEACHER"), java.util.List.of("TEAM_LEADER", "VICE_PRINCIPAL")));
        workflowService.savePolicy(
                principal,
                new com.carenest.dto.eduplan.EduWorkflowPolicyRequest(workflowService.getPolicyRevision(), policies));
        entityManager.flush();
        entityManager.refresh(doc);
        assertThat(doc.getStatus()).isEqualTo(com.carenest.utils.enums.EduClassDocStatus.APPROVED);
        assertThat(doc.getPrintSnapshot()).isEqualTo(snapshot);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "false,true,false",
        "true,true,true",
        "false,true,true",
        "false,false,true"
    })
    void configuredClassReviewPreservesFinalStepAndPublishesWithoutInventedApprovals(
            boolean hasLeader, boolean hasVp, boolean includeLeader) {
        var fixture = classFixture(hasLeader, hasVp);
        String reviewers = includeLeader
                ? "[\"TEAM_LEADER\",\"VICE_PRINCIPAL\"]"
                : hasVp ? "[\"VICE_PRINCIPAL\"]" : "[\"TEAM_LEADER\",\"VICE_PRINCIPAL\"]";
        jdbc.update(
                "UPDATE edu_settings SET workflow_policy=jsonb_set(workflow_policy,'{theme}',?::jsonb)",
                "{\"creators\":[\"TEACHER\"],\"reviewers\":" + reviewers + "}");
        String dossierKey = "ds_" + fixture.themeKey().substring(3);
        var doc = classDocRepository.findByDocKey(dossierKey).orElseThrow();
        var sent = classDocs.actOnClassDoc(
                fixture.teacher(),
                "dossier",
                dossierKey,
                new com.carenest.dto.eduplan.ClassDocActionRequest("submit", null),
                doc.getVersion());
        assertThat(sent.path("status").asText()).isEqualTo("REVIEW");
        assertThat(sent.path("review").path("waitingRole").asText())
                .isEqualTo(hasLeader ? "TEAM_LEADER" : "VICE_PRINCIPAL");
        assertThat(sent.path("approvals").size()).isZero();
        assertThatThrownBy(() -> classDocs.actOnClassDoc(
                        fixture.teacher(),
                        "dossier",
                        dossierKey,
                        new com.carenest.dto.eduplan.ClassDocActionRequest("approve", null),
                        doc.getVersion()))
                .isInstanceOf(GlobalException.class);
        if (!hasVp) return;
        if (hasLeader) {
            assertThatThrownBy(() -> classDocs.actOnClassDoc(
                            fixture.vp(),
                            "dossier",
                            dossierKey,
                            new com.carenest.dto.eduplan.ClassDocActionRequest("approve", null),
                            doc.getVersion()))
                    .isInstanceOf(GlobalException.class);
            classDocs.actOnClassDoc(
                    fixture.leader(),
                    "dossier",
                    dossierKey,
                    new com.carenest.dto.eduplan.ClassDocActionRequest("approve", null),
                    doc.getVersion());
        }
        var issued = classDocs.actOnClassDoc(
                fixture.vp(),
                "dossier",
                dossierKey,
                new com.carenest.dto.eduplan.ClassDocActionRequest("approve", null),
                doc.getVersion());
        assertThat(issued.path("status").asText()).isEqualTo("APPROVED");
        assertThat(issued.path("approvals").size()).isEqualTo(hasLeader ? 2 : 1);
        assertThat(doc.getPrintSnapshot()).contains("VICE_PRINCIPAL");
    }

    @Test
    void teacherCannotReorderLargeThemesThroughSaveOrPreview() {
        var fixture = classFixture(false, true);
        var themes = jdbc.queryForList(
                "SELECT id FROM edu_themes WHERE team_plan_id=? ORDER BY sort_order", Long.class, fixture.planId());
        var doc = mapper.createObjectNode().put("classId", fixture.classId()).put("yearPlanId", fixture.planId());
        doc.putArray("branches");
        var schedules = doc.putArray("themeSchedules");
        for (int i = 0; i < themes.size(); i++)
            schedules
                    .addObject()
                    .put("themeId", themes.get(i))
                    .put("startDate", LocalDate.of(2026, 9, 7).plusWeeks(i).toString())
                    .put("endDate", LocalDate.of(2026, 9, 11).plusWeeks(i).toString());
        doc.set("orderedThemeIds", mapper.valueToTree(themes.reversed()));
        String branchKey = "bp_" + fixture.classId() + "_" + fixture.planId();
        var branch = classDocRepository.findByDocKey(branchKey).orElseThrow();
        assertThatThrownBy(() -> classDocs.previewClassCalendar(
                        fixture.teacher(),
                        new com.carenest.dto.eduplan.ClassCalendarPreviewRequest(
                                fixture.classId(), fixture.planId(), branch.getVersion(), null, themes.reversed())))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.EDUCATION_CALENDAR_INVALID);
        assertThatThrownBy(() ->
                        classDocs.saveClassDoc(fixture.teacher(), "branch", branchKey, doc, branch.getVersion(), null))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.EDUCATION_CALENDAR_INVALID);
        assertThat(branch.getContent()).contains("Nhánh");
    }

    @Test
    void annualPublicationCreatesAssignedTeamAndLaterVersionsKeepDraftWorkAndPublishedSource() {
        var fixture = classFixture(true, true);
        var principal = workflowUser("auto-principal", com.carenest.utils.enums.CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        long year = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('AUTO-2098-2099','2026-09-01','2027-05-31') RETURNING id",
                Long.class);
        long age =
                jdbc.queryForObject("SELECT age_group_id FROM edu_team_plans WHERE id=?", Long.class, fixture.planId());
        long domain = jdbc.queryForObject("SELECT id FROM edu_domains ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update(
                "UPDATE edu_settings SET duration_policy='{\"theme\":{\"minWeeks\":1,\"maxWeeks\":4},\"branch\":{\"minWeeks\":1,\"maxWeeks\":2}}'::jsonb");
        var themes = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> new com.carenest.dto.eduplan.YearPlanRequest.Theme(
                        "t" + i,
                        null,
                        "Chủ đề " + i,
                        "T" + i,
                        LocalDate.of(2026, 9, 7).plusWeeks(i),
                        LocalDate.of(2026, 9, 11).plusWeeks(i),
                        "",
                        ""))
                .toList();
        var request = new com.carenest.dto.eduplan.YearPlanRequest(
                year,
                age,
                "Kế hoạch năm",
                "",
                java.util.List.of(new com.carenest.dto.eduplan.YearPlanRequest.Domain(
                        domain,
                        java.util.List.of(
                                new com.carenest.dto.eduplan.YearPlanRequest.Row(
                                        null, "group", "Kết quả mong đợi", java.util.List.of()),
                                new com.carenest.dto.eduplan.YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "Mục tiêu",
                                        java.util.List.of(new com.carenest.dto.eduplan.YearPlanRequest.Content(
                                                null, "Nội dung", java.util.List.of())))))),
                themes,
                java.util.List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                java.util.List.of());
        var annual = yearPlans.createYearPlan(fixture.vp(), request, editContext(0L));
        var sent = yearPlans.submitYearPlan(fixture.vp(), annual.id(), null, editContext(annual.revision()));
        var issue = editContext(sent.revision());
        var published = yearPlans.issueYearPlan(principal, annual.id(), null, issue);
        yearPlans.issueYearPlan(principal, annual.id(), null, issue);
        var teams = yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                com.carenest.utils.enums.EduPlanLevel.TEAM, year, age);
        assertThat(teams).hasSize(1);
        var team = teams.getFirst();
        assertThat(team.getStatus()).isEqualTo(com.carenest.utils.enums.EduPlanStatus.DRAFT);
        assertThat(team.getOwnerId()).isEqualTo(fixture.leader().userId());
        assertThat(team.getCurriculumSourcePlanId()).isEqualTo(annual.id());
        var detail = yearPlans.getYearPlan(fixture.leader(), team.getId());
        assertThat(detail.permissions().canEdit()).isTrue();
        assertThat(detail.themes()).hasSize(9);
        assertThat(detail.domains().stream().filter(d -> !d.rows().isEmpty())).hasSize(1);
        team.updateInfo("Tổ đã nhập tên", "Riêng", clock.instant());
        entityManager.flush();
        var version2 = yearPlans.createVersion(fixture.vp(), published.id(), editContext(published.revision()));
        var sent2 = yearPlans.submitYearPlan(fixture.vp(), version2.id(), null, editContext(version2.revision()));
        var published2 = yearPlans.issueYearPlan(principal, version2.id(), null, editContext(sent2.revision()));
        entityManager.flush();
        assertThat(team.getCurriculumSourcePlanId()).isEqualTo(version2.id());
        assertThat(team.getTitle()).isEqualTo("Tổ đã nhập tên");
        assertThat(yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                        com.carenest.utils.enums.EduPlanLevel.TEAM, year, age))
                .hasSize(1);
        team.issue(fixture.vp().userId(), clock.instant());
        entityManager.flush();
        var version3 = yearPlans.createVersion(fixture.vp(), published2.id(), editContext(published2.revision()));
        var sent3 = yearPlans.submitYearPlan(fixture.vp(), version3.id(), null, editContext(version3.revision()));
        yearPlans.issueYearPlan(principal, version3.id(), null, editContext(sent3.revision()));
        entityManager.flush();
        teams = yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                com.carenest.utils.enums.EduPlanLevel.TEAM, year, age);
        assertThat(teams).hasSize(2);
        assertThat(team.getStatus()).isEqualTo(com.carenest.utils.enums.EduPlanStatus.APPROVED);
        assertThat(team.getCurriculumSourcePlanId()).isEqualTo(version2.id());
        assertThat(teams.getLast().getStatus()).isEqualTo(com.carenest.utils.enums.EduPlanStatus.DRAFT);
        assertThat(teams.getLast().getCurriculumSourcePlanId()).isEqualTo(version3.id());
        workflowService.activateLegacyPending(clock.instant());
        entityManager.flush();
        teams = yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                com.carenest.utils.enums.EduPlanLevel.TEAM, year, age);
        assertThat(teams).hasSize(2);
        assertThat(teams.getLast().getCurriculumSourcePlanId()).isEqualTo(version3.id());
    }

    @Test
    @DisplayName("Khởi động: tạo bù bản in cho giáo án lớp đã duyệt giữ nguyên người sửa cuối")
    void legacyBackfillKeepsUpdatedByOfApprovedClassDoc() {
        var fixture = classFixture(true, true);
        Long id = jdbc.queryForObject(
                "SELECT min(id) FROM edu_class_docs WHERE team_plan_id=?", Long.class, fixture.planId());
        jdbc.update("UPDATE edu_class_docs SET status='APPROVED', print_snapshot=NULL WHERE id=?", id);
        Long updatedBy = jdbc.queryForObject("SELECT updated_by FROM edu_class_docs WHERE id=?", Long.class, id);
        entityManager.clear();

        workflowService.activateLegacyPending(java.time.Instant.now());
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                        "SELECT print_snapshot IS NOT NULL FROM edu_class_docs WHERE id=?", Boolean.class, id))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT updated_by FROM edu_class_docs WHERE id=?", Long.class, id))
                .isEqualTo(updatedBy);
    }

    private com.carenest.dto.eduplan.YearPlanEditRequest editContext(Long revision) {
        return new com.carenest.dto.eduplan.YearPlanEditRequest(null, revision, java.util.UUID.randomUUID());
    }

    @Autowired
    private com.carenest.service.eduplan.EduYearPlanService yearPlans;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("teamDeleteCases")
    void teamCannotBeDeletedOrHiddenInAnyRoleOrStatus(
            com.carenest.utils.enums.CareNestRole role, com.carenest.utils.enums.EduPlanStatus status) {
        var fixture = classFixture(true, true);
        jdbc.update("UPDATE edu_team_plans SET status=? WHERE id=?", status.name(), fixture.planId());
        entityManager.clear();
        var user =
                new com.carenest.security.CurrentUser(fixture.teacher().userId(), "wf", "wf", "wf", "", Set.of(role));
        assertThatThrownBy(() -> yearPlans.deleteYearPlan(
                        user,
                        fixture.planId(),
                        new com.carenest.dto.eduplan.YearPlanEditRequest(null, 0L, java.util.UUID.randomUUID())))
                .isInstanceOf(GlobalException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT disabled FROM edu_team_plans WHERE id=?", Boolean.class, fixture.planId()))
                .isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_class_docs WHERE team_plan_id=?", Long.class, fixture.planId()))
                .isEqualTo(5L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_themes WHERE team_plan_id=?", Long.class, fixture.planId()))
                .isEqualTo(2L);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> teamDeleteCases() {
        return java.util.Arrays.stream(com.carenest.utils.enums.CareNestRole.values())
                .flatMap(role -> java.util.Arrays.stream(com.carenest.utils.enums.EduPlanStatus.values())
                        .map(status -> org.junit.jupiter.params.provider.Arguments.of(role, status)));
    }

    @Autowired
    private java.time.Clock clock;

    @Test
    void editingPublishedThemeRequiresRevisionAndKeepsPublicationEvidence() {
        var fixture = classFixture(false, true);
        LocalDate future =
                LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        var themes = jdbc.queryForList(
                "SELECT id FROM edu_themes WHERE team_plan_id=? ORDER BY sort_order", Long.class, fixture.planId());
        jdbc.update(
                "UPDATE edu_themes SET start_date=?,end_date=? WHERE id=?", future, future.plusDays(4), themes.get(0));
        jdbc.update(
                "UPDATE edu_themes SET start_date=?,end_date=? WHERE id=?",
                future.plusWeeks(1),
                future.plusWeeks(1).plusDays(4),
                themes.get(1));
        var root = classDocRepository.findByDocKey(fixture.themeKey()).orElseThrow();
        var other = EduClassDoc.create(
                "tp_" + fixture.classId() + "_" + themes.get(1),
                "theme",
                fixture.classId(),
                fixture.planId(),
                themes.get(1),
                null,
                fixture.teacher().userId());
        other.publish(fixture.vp().userId(), "[]", 1L, "{}", "{\"proof\":true}", clock.instant());
        classDocRepository.saveAndFlush(other);
        root.publish(fixture.vp().userId(), "[]", 1L, "{}", "{\"proof\":true}", clock.instant());
        var dossier = publishDossier(
                fixture.classId(), fixture.planId(), themes.get(0), fixture.vp().userId());
        entityManager.flush();
        long otherVersion = other.getVersion();
        var body = mapper.createObjectNode();
        body.put("classId", fixture.classId())
                .put("yearPlanId", fixture.planId())
                .put("themeId", themes.get(0));
        body.put("environment", "Môi trường mới");
        // Spec 004 Q3: bản đã ban hành không sửa thẳng
        assertThatThrownBy(() -> classDocs.saveClassDoc(
                        fixture.teacher(), "theme", root.getDocKey(), body, root.getVersion(), null))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CLASS_DOC_LOCKED);
        classDocs.actOnClassDoc(
                fixture.teacher(),
                "dossier",
                dossier.getDocKey(),
                new com.carenest.dto.eduplan.ClassDocActionRequest("revise", null),
                dossier.getVersion());
        entityManager.flush();
        var result =
                classDocs.saveClassDoc(fixture.teacher(), "theme", root.getDocKey(), body, root.getVersion(), null);
        assertThat(result.path("status").asString()).isEqualTo("DRAFT");
        assertThat(root.getHistory()).contains("revise_future", "previousPrintSnapshot", "proof");
        entityManager.flush();
        entityManager.refresh(other);
        assertThat(other.getStatus()).isEqualTo(com.carenest.utils.enums.EduClassDocStatus.APPROVED);
        assertThat(other.getVersion()).isEqualTo(otherVersion);
        assertThat(other.getPrintSnapshot()).contains("proof");
    }

    @Test
    void publishedWeekNeedsRevisionAndReviewRemainsLocked() {
        var fixture = classFixture(false, true);
        var root = classDocRepository.findByDocKey(fixture.themeKey()).orElseThrow();
        LocalDate start =
                LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        jdbc.update(
                "UPDATE edu_themes SET start_date=?,end_date=? WHERE id=?",
                start,
                start.plusDays(4),
                root.getThemeId());
        var week = EduClassDoc.create(
                "wp_" + fixture.classId() + "_" + start,
                "week",
                fixture.classId(),
                fixture.planId(),
                root.getThemeId(),
                start,
                fixture.teacher().userId());
        root.publish(fixture.vp().userId(), "[]", 1L, "{}", "{}", clock.instant());
        week.publish(fixture.vp().userId(), "[]", 1L, "{}", "{}", clock.instant());
        classDocRepository.saveAndFlush(week);
        var dossier = publishDossier(
                fixture.classId(),
                fixture.planId(),
                root.getThemeId(),
                fixture.vp().userId());
        var body = mapper.createObjectNode();
        body.put("classId", fixture.classId())
                .put("yearPlanId", fixture.planId())
                .put("themeId", root.getThemeId())
                .put("weekStart", start.toString())
                .put("prep", "Mới");
        assertThatThrownBy(() -> classDocs.saveClassDoc(
                        fixture.teacher(), "week", week.getDocKey(), body, week.getVersion(), dossier.getVersion()))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CLASS_DOC_LOCKED);
        classDocs.actOnClassDoc(
                fixture.teacher(),
                "dossier",
                dossier.getDocKey(),
                new com.carenest.dto.eduplan.ClassDocActionRequest("revise", null),
                dossier.getVersion());
        entityManager.flush();
        var saved = classDocs.saveClassDoc(
                fixture.teacher(), "week", week.getDocKey(), body, week.getVersion(), dossier.getVersion());
        assertThat(saved.path("status").asString()).isEqualTo("DRAFT");
        var branch = classDocRepository
                .findByDocKey("bp_" + fixture.classId() + "_" + fixture.planId())
                .orElseThrow();
        branch.changeContent(
                "{\"names\":{\"" + start + "\":\"Nhánh mới\"}}",
                fixture.teacher().userId());
        entityManager.flush();
        classDocRepository.saveAndFlush(EduClassDoc.create(
                "dd_" + fixture.classId() + "_" + start,
                "day",
                fixture.classId(),
                fixture.planId(),
                root.getThemeId(),
                start,
                fixture.teacher().userId()));
        classDocs.actOnClassDoc(
                fixture.teacher(),
                "dossier",
                dossier.getDocKey(),
                new com.carenest.dto.eduplan.ClassDocActionRequest("submit", null),
                dossier.getVersion());
        entityManager.flush();
        assertThatThrownBy(() -> classDocs.saveClassDoc(
                        fixture.teacher(), "week", week.getDocKey(), body, week.getVersion(), dossier.getVersion()))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CLASS_DOC_LOCKED);
    }

    @Test
    void teachingStartDayIsLockedEvenForPublishedOwnerAndOtherTeachersCannotRevise() {
        var fixture = classFixture(true, true);
        var root = classDocRepository.findByDocKey(fixture.themeKey()).orElseThrow();
        root.publish(fixture.vp().userId(), "[]", 1L, "{}", "{\"proof\":true}", clock.instant());
        publishDossier(
                fixture.classId(),
                fixture.planId(),
                root.getThemeId(),
                fixture.vp().userId());
        jdbc.update("UPDATE edu_themes SET start_date=? WHERE id=?", LocalDate.now(clock), root.getThemeId());
        entityManager.flush();
        var body = mapper.createObjectNode();
        body.put("classId", fixture.classId())
                .put("yearPlanId", fixture.planId())
                .put("themeId", root.getThemeId())
                .put("environment", "Sai");
        assertThatThrownBy(() -> classDocs.saveClassDoc(
                        fixture.teacher(), "theme", root.getDocKey(), body, root.getVersion(), null))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CLASS_DOC_LOCKED);
        assertThatThrownBy(() -> classDocs.saveClassDoc(
                        fixture.leader(), "theme", root.getDocKey(), body, root.getVersion(), null))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CLASS_DOC_TEACHER_ONLY);
        assertThat(root.getStatus()).isEqualTo(com.carenest.utils.enums.EduClassDocStatus.APPROVED);
        assertThat(root.getContent()).isEqualTo("{}");
        assertThat(root.getPrintSnapshot()).contains("proof");
    }

    @Test
    void editingBranchNoLongerReopensPublishedThemesAndStaleWritesAreRejected() {
        var fixture = classFixture(false, true);
        LocalDate start =
                LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        var themes = jdbc.queryForList(
                "SELECT id FROM edu_themes WHERE team_plan_id=? ORDER BY sort_order", Long.class, fixture.planId());
        for (int i = 0; i < themes.size(); i++)
            jdbc.update(
                    "UPDATE edu_themes SET start_date=?,end_date=? WHERE id=?",
                    start.plusWeeks(i),
                    start.plusWeeks(i).plusDays(4),
                    themes.get(i));
        var root = classDocRepository.findByDocKey(fixture.themeKey()).orElseThrow();
        root.publish(fixture.vp().userId(), "[]", 1L, "{}", "{\"proof\":true}", clock.instant());
        var other = EduClassDoc.create(
                "tp_" + fixture.classId() + "_" + themes.get(1),
                "theme",
                fixture.classId(),
                fixture.planId(),
                themes.get(1),
                null,
                fixture.teacher().userId());
        other.publish(fixture.vp().userId(), "[]", 1L, "{}", "{}", clock.instant());
        classDocRepository.saveAndFlush(other);
        var branch = classDocRepository
                .findByDocKey("bp_" + fixture.classId() + "_" + fixture.planId())
                .orElseThrow();
        var content = mapper.createObjectNode();
        content.putObject("names")
                .put(start.toString(), "Nhánh cũ")
                .put(start.plusWeeks(1).toString(), "Giữ nguyên");
        branch.changeContent(
                mapper.writeValueAsString(content), fixture.teacher().userId());
        entityManager.flush();
        long oldVersion = branch.getVersion();
        long otherVersion = other.getVersion();
        ((tools.jackson.databind.node.ObjectNode) content.path("names")).put(start.toString(), "Nhánh mới");
        content.put("classId", fixture.classId()).put("yearPlanId", fixture.planId());
        classDocs.saveClassDoc(fixture.teacher(), "branch", branch.getDocKey(), content, oldVersion, null);
        entityManager.flush();
        // Spec 004: sửa lịch nhánh không tự mở lại hồ sơ đã ban hành (năm chưa cấu hình học kỳ: không có gói khóa
        // nhánh)
        assertThat(root.getStatus()).isEqualTo(com.carenest.utils.enums.EduClassDocStatus.APPROVED);
        assertThat(other.getStatus()).isEqualTo(com.carenest.utils.enums.EduClassDocStatus.APPROVED);
        assertThat(other.getVersion()).isEqualTo(otherVersion);
        assertThatThrownBy(() -> classDocs.saveClassDoc(
                        fixture.teacher(), "branch", branch.getDocKey(), content, oldVersion, null))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CONCURRENT_UPDATE);
    }

    /** Hồ sơ chủ đề đã ban hành: gốc APPROVED + một bản ban hành (spec 003). */
    private EduClassDoc publishDossier(Long classId, Long planId, Long themeId, Long approver) {
        String key = "ds_" + classId + "_" + themeId;
        var dossier = classDocRepository
                .findByDocKey(key)
                .orElseGet(() -> EduClassDoc.create(key, "dossier", classId, planId, themeId, null, approver));
        dossier.publish(approver, "[]", 1L, "{}", "{\"proof\":true}", clock.instant());
        dossier = classDocRepository.saveAndFlush(dossier);
        jdbc.update(
                "INSERT INTO edu_class_doc_releases(class_doc_id,dossier_version,print_snapshot,approved_by,released_at) "
                        + "VALUES (?,?,'{\"members\":[]}'::jsonb,?,now())",
                dossier.getId(),
                dossier.getVersion(),
                approver);
        return dossier;
    }

    private record ClassFixture(
            Long classId,
            Long planId,
            String themeKey,
            com.carenest.security.CurrentUser teacher,
            com.carenest.security.CurrentUser leader,
            com.carenest.security.CurrentUser vp) {}

    private ClassFixture classFixture(boolean hasLeader, boolean hasVp) {
        var teacher = workflowUser("wf-teacher", com.carenest.utils.enums.CareNestRole.TEACHER);
        var leader = workflowUser("wf-leader", com.carenest.utils.enums.CareNestRole.TEACHER);
        var vp = workflowUser("wf-vp", com.carenest.utils.enums.CareNestRole.VICE_PRINCIPAL);
        long year = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('WF-year','2026-09-01','2027-05-31') RETURNING id",
                Long.class);
        long age = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('WF-C','WF campus') RETURNING id", Long.class);
        long cls = jdbc.queryForObject(
                "INSERT INTO classrooms(name,campus_id,school_year_id,age_group_id) VALUES ('WF class',?,?,?) RETURNING id",
                Long.class,
                campus,
                year,
                age);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,classroom_id,valid_from,is_homeroom) VALUES (?,'TEACHER',?,'2020-01-01',true)",
                teacher.userId(),
                cls);
        if (hasLeader)
            jdbc.update(
                    "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                    leader.userId(),
                    age);
        if (hasVp)
            jdbc.update(
                    "INSERT INTO staff_assignments(user_id,staff_role,campus_id,valid_from) VALUES (?,'VICE_PRINCIPAL',?,'2020-01-01')",
                    vp.userId(),
                    campus);
        var plan = legacyDraft("WF-TEAM", year, age, "WF plan", "", teacher.userId(), "GV", Instant.now());
        plan.issue(vp.userId(), Instant.now());
        yearPlanRepository.saveAndFlush(plan);
        Long theme = null;
        for (int i = 0; i < 2; i++) {
            Long id = jdbc.queryForObject(
                    "INSERT INTO edu_themes(team_plan_id,name,abbr,sort_order,start_date,end_date) VALUES (?,'Chủ đề',?,?,?,?) RETURNING id",
                    Long.class,
                    plan.getId(),
                    "T" + i,
                    i,
                    LocalDate.of(2026, 9, 7).plusWeeks(i),
                    LocalDate.of(2026, 9, 11).plusWeeks(i));
            if (i == 0) theme = id;
        }
        var branch = EduClassDoc.create(
                "bp_" + cls + "_" + plan.getId(), "branch", cls, plan.getId(), null, null, teacher.userId());
        branch.changeContent("{\"names\":{\"2026-09-07\":\"Nhánh 1\",\"2026-09-14\":\"Nhánh 2\"}}", teacher.userId());
        classDocRepository.saveAndFlush(branch);
        String key = "tp_" + cls + "_" + theme;
        classDocRepository.saveAndFlush(
                EduClassDoc.create(key, "theme", cls, plan.getId(), theme, null, teacher.userId()));
        classDocRepository.saveAndFlush(EduClassDoc.create(
                "wp_" + cls + "_2026-09-07",
                "week",
                cls,
                plan.getId(),
                theme,
                LocalDate.of(2026, 9, 7),
                teacher.userId()));
        // Spec 003: hồ sơ chủ đề là gốc gửi/duyệt; giáo án ngày tách khỏi giáo án tuần
        classDocRepository.saveAndFlush(EduClassDoc.create(
                "ds_" + cls + "_" + theme, "dossier", cls, plan.getId(), theme, null, teacher.userId()));
        classDocRepository.saveAndFlush(EduClassDoc.create(
                "dd_" + cls + "_2026-09-07",
                "day",
                cls,
                plan.getId(),
                theme,
                LocalDate.of(2026, 9, 7),
                teacher.userId()));
        return new ClassFixture(cls, plan.getId(), key, teacher, leader, vp);
    }

    private com.carenest.security.CurrentUser workflowUser(String name, com.carenest.utils.enums.CareNestRole role) {
        long id = createAccount(name);
        return new com.carenest.security.CurrentUser(id, name, name, name, "", Set.of(role));
    }

    private long createAccount(String username) {
        return jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES (?,?,?,?) RETURNING id",
                Long.class,
                username,
                username,
                "Người thử",
                "ACTIVE");
    }

    private static com.carenest.entity.eduplan.EduPlan legacyDraft(
            String code,
            Long yearId,
            Long ageId,
            String title,
            String description,
            Long ownerId,
            String ownerTitle,
            java.time.Instant now) {
        var plan = com.carenest.entity.eduplan.EduPlan.createDraft(
                code, yearId, ageId, title, description, ownerId, ownerTitle, now);
        plan.initializePlanSource(
                com.carenest.utils.enums.EduPlanLevel.TEAM, null, true, "{\"mode\":\"LEGACY_UNBOUNDED\"}");
        return plan;
    }
}
