package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

/** Tài liệu của lớp EDU-12..18 qua service + policy thật trên PostgreSQL. Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduClassDocServiceIntegrationTest {

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
    private EduClassDocService service;

    @Autowired
    private EduYearPlanService yearPlanService;

    @Autowired
    private EduPlanRepository planRepository;

    @Autowired
    private com.carenest.service.eduplan.EduWorkflowService workflow;

    @Autowired
    private com.carenest.service.eduplan.LearningResourceService learningResources;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.carenest.integration.storage.StorageService storage;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private long schoolYear;
    private long ageGroup3;
    private long classId;
    private long otherClassId;
    private long annualPlanId;
    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private CurrentUser leader;
    private CurrentUser teacher;
    private CurrentUser otherTeacher;
    private YearPlanDetailResponse plan;
    private String theme1;
    private String theme2;

    @BeforeEach
    void setUp() {
        java.util.Map<String, byte[]> stored = new java.util.HashMap<>();
        org.mockito.Mockito.when(
                        storage.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> {
                    String key = java.util.UUID.randomUUID().toString();
                    stored.put(key, ((java.io.InputStream) call.getArgument(0)).readAllBytes());
                    return key;
                });
        org.mockito.Mockito.when(storage.load(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> new org.springframework.core.io.ByteArrayResource(stored.get(call.getArgument(0))));
        org.mockito.Mockito.doAnswer(call -> {
                    stored.remove(call.getArgument(0));
                    return null;
                })
                .when(storage)
                .remove(org.mockito.ArgumentMatchers.anyString());
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-A', 'Campus A') RETURNING id");
        schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        // Spec 003: HK1 gồm chủ đề 1–2; lớp mở sau ngày bắt đầu nên hạn đã qua không tính quá hạn (C2)
        jdbc.update("UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2026-10-04', "
                + "semester2_start='2026-10-05', semester2_end='2027-05-31' WHERE id=?", schoolYear);
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long domain = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);
        classId = insertId(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'MGB1', ?) "
                        + "RETURNING id",
                campus,
                schoolYear,
                ageGroup3);
        otherClassId = insertId(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'MGB2', ?) "
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
        leader = user("leader", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, '2026-09-01')",
                leader.userId(),
                ageGroup3);
        teacher = user("teacher", CareNestRole.TEACHER);
        assignTeacher(teacher, classId);
        otherTeacher = user("other", CareNestRole.TEACHER);
        assignTeacher(otherTeacher, otherClassId);

        principal = user("ht", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) "
                        + "VALUES (?, 'PRINCIPAL', '2026-09-01')",
                principal.userId());
        jdbc.update(
                "UPDATE edu_settings SET duration_policy = '{\"theme\":{\"minWeeks\":1,\"maxWeeks\":4},\"branch\":{\"minWeeks\":1,\"maxWeeks\":2}}'::jsonb");

        YearPlanRequest annualReq = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch năm học",
                "",
                List.of(new YearPlanRequest.Domain(
                        domain,
                        List.of(new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()), new YearPlanRequest.Row(
                                null,
                                "goal",
                                "Trẻ thực hiện đủ động tác",
                                List.of(new YearPlanRequest.Content(null, "Hô hấp", List.of())))))),
                List.of(
                        new YearPlanRequest.Theme(
                                "t-1",
                                null,
                                "Trường mầm non",
                                "TMN",
                                LocalDate.of(2026, 9, 7),
                                LocalDate.of(2026, 9, 25),
                                "",
                                ""),
                        new YearPlanRequest.Theme(
                                "t-2",
                                null,
                                "Gia đình",
                                "GĐ",
                                LocalDate.of(2026, 9, 28),
                                LocalDate.of(2026, 10, 2),
                                "",
                                ""),
                        // Quy tắc hiện hành: kế hoạch năm cần tối thiểu 9 chủ đề (YearPlanPublicationValidator)
                        extraTheme(3), extraTheme(4), extraTheme(5), extraTheme(6), extraTheme(7), extraTheme(8),
                        extraTheme(9)),
                List.of(new YearPlanRequest.Break(null, "Nghỉ", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18))),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of(new YearPlanRequest.BranchTemplate("tmpl-1", null, "t-1", "Dự án mẫu", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11))));

        java.util.UUID vpSession = java.util.UUID.randomUUID();
        YearPlanDetailResponse annualDraft = yearPlanService.createYearPlan(
                vicePrincipal,
                annualReq,
                new com.carenest.dto.eduplan.YearPlanEditRequest(vpSession, 0L, java.util.UUID.randomUUID()));
        YearPlanDetailResponse annualSub = yearPlanService.submitYearPlan(
                vicePrincipal,
                annualDraft.id(),
                null,
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        vpSession, annualDraft.revision(), java.util.UUID.randomUUID()));
        YearPlanDetailResponse annualIssued = yearPlanService.issueYearPlan(
                principal,
                annualDraft.id(),
                "Ban hành KH năm học",
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        null, annualSub.revision(), java.util.UUID.randomUUID()));
        annualPlanId = annualIssued.id();

        // Chủ đề 1 có tuần nghỉ ở giữa (2 tuần học), chủ đề 2 bắt đầu Chủ nhật (1 tuần học)
        java.util.UUID editSession = java.util.UUID.randomUUID();
        // Ban hành kế hoạch năm tự tạo bản nháp kế hoạch tổ cho nhóm tuổi (quy tắc hiện hành)
        Long teamId = jdbc.queryForObject(
                "SELECT id FROM edu_team_plans WHERE school_year_id = ? AND age_group_id = ? ORDER BY version_no LIMIT 1",
                Long.class,
                schoolYear,
                ageGroup3);
        YearPlanDetailResponse draft = yearPlanService.getYearPlan(leader, teamId);
        jdbc.update(
                "UPDATE edu_team_plans SET legacy_team_without_source = true, curriculum_source_plan_id = NULL WHERE id = ?",
                draft.id());
        entityManager.clear();
        Long contentId = jdbc.queryForObject(
                "SELECT id FROM edu_goal_contents WHERE goal_id IN (SELECT id FROM edu_plan_goals WHERE team_plan_id = ?) LIMIT 1",
                Long.class,
                draft.id());
        Long themeId1 = jdbc.queryForObject(
                "SELECT id FROM edu_themes WHERE team_plan_id = ? AND abbr = 'TMN'", Long.class, draft.id());
        jdbc.update("INSERT INTO edu_content_theme_ticks (content_id, theme_id) VALUES (?, ?)", contentId, themeId1);

        YearPlanDetailResponse submitted = yearPlanService.submitYearPlan(
                leader,
                draft.id(),
                "Gửi duyệt",
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        editSession, draft.revision(), java.util.UUID.randomUUID()));
        plan = yearPlanService.issueYearPlan(
                vicePrincipal,
                draft.id(),
                "Ban hành",
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        null, submitted.revision(), java.util.UUID.randomUUID()));
        jdbc.update(
                "UPDATE edu_team_plans SET legacy_team_without_source = true, curriculum_source_plan_id = NULL WHERE id = ?",
                plan.id());
        entityManager.clear();
        theme1 = String.valueOf(plan.themes().get(0).id());
        theme2 = String.valueOf(plan.themes().get(1).id());
    }

    @Test
    @DisplayName("EDU-12: giáo viên chỉ thấy lớp mình; tổ trưởng thấy các lớp của nhóm tuổi; kèm tên giáo viên")
    void visibleClassesFollowAssignments() {
        ClassDocsResponse own = service.getClassDocs(teacher, schoolYear);
        assertThat(own.classes()).extracting(ClassDocsResponse.ClassItem::id).containsExactly(String.valueOf(classId));
        assertThat(own.classes().getFirst().ageGroupId()).isEqualTo("ag-3");
        assertThat(own.classes().getFirst().teacherIds()).containsExactly(teacher.userId());
        assertThat(own.staff())
                .extracting(ClassDocsResponse.StaffItem::role)
                .containsExactlyInAnyOrder("TEAM_LEADER", "TEACHER");

        assertThat(service.getClassDocs(leader, schoolYear).classes()).hasSize(2);
        assertThat(service.getClassDocs(vicePrincipal, schoolYear).classes()).hasSize(2);
    }

    @Test
    @DisplayName("EDU-47: xem trước ghép tuần hệ thống, báo chủ đề lạ/thừa và không ghi tài liệu")
    void previewBranchesUsesCurrentScheduleWithoutWriting() {
        long before = jdbc.queryForObject("SELECT count(*) FROM edu_class_docs", Long.class);
        var preview = service.previewBranchFile(
                teacher,
                classId,
                plan.id(),
                branchFile(
                        "Tháng,Chủ đề,Chủ đề nhánh,Số tuần\n9, TRƯỜNG  MẦM NON ,Nhánh 1: Bé đến trường,Tuần 99 năm 2000\n"
                                + ",,Nhánh 2: Lớp của bé,Tuần 1 năm 1900\n,,Nhánh 3: Thừa tên,\n10,Gia đình,Gia đình bé,\n11,Chủ đề lạ,Tên lạ,\n"));
        assertThat(preview.matches())
                .extracting(com.carenest.dto.eduplan.BranchFilePreviewResponse.Match::weekStart)
                .containsExactly(
                        LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-28"));
        assertThat(preview.matches())
                .extracting(com.carenest.dto.eduplan.BranchFilePreviewResponse.Match::name)
                .containsExactly("Bé đến trường", "Lớp của bé", "Gia đình bé");
        assertThat(preview.warnings()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_class_docs", Long.class))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("EDU-12/18/47: preview kiểm phân công, trạng thái khóa và lỗi file đúng ngữ cảnh")
    void previewBranchesChecksScopeAndStatus() {
        var file = branchFile("Chủ đề,Chủ đề nhánh\nTrường mầm non,Bé đến trường\n");
        assertApiError(
                () -> service.previewBranchFile(otherTeacher, classId, plan.id(), file),
                ApiCode.CLASS_DOC_TEACHER_ONLY);
        assertApiError(
                () -> service.previewBranchFile(vicePrincipal, classId, plan.id(), file),
                ApiCode.CLASS_DOC_TEACHER_ONLY);
        assertApiError(
                () -> service.previewBranchFile(teacher, classId, plan.id(), branchFile("Tên,Ngày\nA,B")),
                ApiCode.BRANCH_FILE_NO_TABLE);
        assertApiError(
                () -> service.previewBranchFile(
                        teacher,
                        classId,
                        plan.id(),
                        new org.springframework.mock.web.MockMultipartFile(
                                "file", "x.csv", "text/csv", new byte[2 * 1024 * 1024 + 1])),
                ApiCode.BRANCH_FILE_TOO_LARGE);
        // Spec 003: gói học kỳ đang Chờ duyệt thì khóa nhập lịch nhánh
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        act(teacher, "branch", key, submit());
        assertApiError(() -> service.previewBranchFile(teacher, classId, plan.id(), file), ApiCode.CLASS_DOC_SUBMITTED);
    }

    private static org.springframework.mock.web.MockMultipartFile branchFile(String text) {
        return new org.springframework.mock.web.MockMultipartFile(
                "file", "branches.csv", "text/csv", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("EDU-47: tên chủ đề đích trùng không được đoán, thiếu tên chỉ tạo match đã có")
    void previewBranchesDoesNotGuessAmbiguousThemes() {
        var one = service.previewBranchFile(
                teacher, classId, plan.id(), branchFile("Chủ đề,Chủ đề nhánh\nTrường mầm non,Nhánh 1: Một tên\n"));
        assertThat(one.matches()).hasSize(1);
        jdbc.update("UPDATE edu_themes SET name = ? WHERE id = ?", "TRƯỜNG  MẦM NON", Long.parseLong(theme2));
        entityManager.clear();
        var ambiguous = service.previewBranchFile(
                teacher, classId, plan.id(), branchFile("Chủ đề,Chủ đề nhánh\nTrường mầm non,Một tên\n"));
        assertThat(ambiguous.matches()).isEmpty();
        assertThat(ambiguous.warnings()).hasSize(1);
    }

    @Test
    @DisplayName("EDU-12/26/47: kế hoạch sai nhóm tuổi, chưa ban hành hoặc có phiên bản mới bị chặn")
    void previewBranchesRequiresCurrentIssuedPlanForClass() {
        var file = branchFile("Chủ đề,Chủ đề nhánh\nTrường mầm non,Tên\n");
        assertApiError(
                () -> service.previewBranchFile(teacher, classId, annualPlanId, file), ApiCode.YEAR_PLAN_CLASS_MISMATCH);
        Long otherAgeGroup = jdbc.queryForObject(
                "SELECT id FROM age_groups WHERE id <> ? ORDER BY id LIMIT 1", Long.class, ageGroup3);
        jdbc.update("UPDATE classrooms SET age_group_id = ? WHERE id = ?", otherAgeGroup, classId);
        entityManager.clear();
        assertApiError(
                () -> service.previewBranchFile(teacher, classId, plan.id(), file), ApiCode.YEAR_PLAN_CLASS_MISMATCH);
        jdbc.update("UPDATE classrooms SET age_group_id = ? WHERE id = ?", ageGroup3, classId);
        jdbc.update("UPDATE edu_team_plans SET status = 'DRAFT' WHERE id = ?", plan.id());
        entityManager.clear();
        assertApiError(
                () -> service.previewBranchFile(teacher, classId, plan.id(), file), ApiCode.YEAR_PLAN_NOT_ISSUED);
        jdbc.update("UPDATE edu_team_plans SET status = 'APPROVED' WHERE id = ?", plan.id());
        jdbc.update(
                "INSERT INTO edu_team_plans (code, school_year_id, age_group_id, version_no, source_plan_id, title, description, status, owner_id, content_revision, legacy_team_without_source, created_at, updated_at) SELECT code || '-V2', school_year_id, age_group_id, 2, id, title, description, 'APPROVED', owner_id, 0, true, now(), now() FROM edu_team_plans WHERE id = ?",
                plan.id());
        entityManager.clear();
        assertApiError(
                () -> service.previewBranchFile(teacher, classId, plan.id(), file), ApiCode.YEAR_PLAN_SUPERSEDED);
    }

    @Test
    @DisplayName("EDU-26/62: năm có phiên bản cao hơn không khóa hồ sơ của tổ đang ban hành")
    void annualVersionDoesNotSupersedeTeamClassDocs() {
        String key = "bp_" + classId + "_" + plan.id();
        ObjectNode content = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        service.saveClassDoc(teacher, "branch", key, content, versionOf(key), rootVersionOf("branch", content));
        jdbc.update("UPDATE edu_year_plans SET version_no=2, source_plan_id=id WHERE id=?", annualPlanId);
        entityManager.clear();

        assertThat(service.getClassDocs(teacher, schoolYear).branchPlans())
                .filteredOn(d -> d.path("id").asString().equals(key))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("superseded").asBoolean()).isFalse();
                    assertThat(d.path("permissions").path("canEdit").asBoolean()).isTrue();
                });
        assertThat(service.previewBranchFile(
                        teacher, classId, plan.id(), branchFile("Chủ đề,Chủ đề nhánh\nTrường mầm non,Tên\n"))
                .matches()).isNotEmpty();
    }

    @Test
    @DisplayName("EDU-26: tổ mới ban hành khóa soạn bản cũ nhưng người duyệt vẫn xử lý hồ sơ đang chờ")
    void reviewersCanFinishPendingClassDocsOfSupersededTeam() {
        String key = "bp_" + classId + "_" + plan.id();
        ObjectNode content = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        service.saveClassDoc(teacher, "branch", key, content, versionOf(key), rootVersionOf("branch", content));
        act(teacher, "branch", key, submit());
        jdbc.update(
                "INSERT INTO edu_team_plans (code, school_year_id, age_group_id, version_no, source_plan_id, title, description, status, owner_id, content_revision, legacy_team_without_source, created_at, updated_at) SELECT code || '-V2', school_year_id, age_group_id, 2, id, title, description, 'APPROVED', owner_id, 0, true, now(), now() FROM edu_team_plans WHERE id = ?",
                plan.id());
        entityManager.clear();

        assertThat(act(leader, "branch", key, approve())
                .path("status").asString()).isEqualTo("REVIEW");
        ObjectNode approved = act(vicePrincipal, "branch", key, approve());
        assertThat(approved.path("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.path("superseded").asBoolean()).isTrue();
        assertThat(approved.path("permissions").path("canEdit").asBoolean()).isFalse();
        assertThat(approved.path("approvals")).hasSize(2);
    }

    @Test
    @DisplayName("EDU-54: REVIEW dùng policy hiện tại thay sign_chain đã chụp")
    void currentPolicyControlsClassReview() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        act(teacher, "branch", key, submit());
        jdbc.update("UPDATE edu_class_docs SET sign_chain=NULL WHERE doc_key=?", key);
        jdbc.update(
                "UPDATE edu_settings SET workflow_policy=jsonb_set(workflow_policy,'{branch,reviewers}','[\"VICE_PRINCIPAL\"]'::jsonb),workflow_revision=workflow_revision+1 WHERE id=1");
        entityManager.flush();
        entityManager.clear();
        ObjectNode result = act(vicePrincipal, "branch", key, approve());
        assertThat(result.path("status").asString()).isEqualTo("APPROVED");
        assertThat(result.path("approvals").get(0).path("roleKey").asString()).isEqualTo("VICE_PRINCIPAL");
    }

    @Test
    void classReviewPreservesEarlierApprovalWhenPolicyOrderChanges() {
        String key = "bp_" + classId + "_" + plan.id();
        ObjectNode content = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        service.saveClassDoc(teacher, "branch", key, content, -1L, null);
        act(teacher, "branch", key, submit());
        act(leader, "branch", key, approve());
        jdbc.update(
                "UPDATE edu_settings SET workflow_policy=jsonb_set(workflow_policy,'{branch,reviewers}','[\"VICE_PRINCIPAL\",\"TEAM_LEADER\"]'::jsonb),workflow_revision=workflow_revision+1 WHERE id=1");
        entityManager.flush();
        entityManager.clear();
        ObjectNode result = act(vicePrincipal, "branch", key, approve());
        assertThat(result.path("status").asString()).isEqualTo("APPROVED");
        assertThat(result.path("approvals").size()).isEqualTo(2);
        assertThat(result.path("approvals").get(0).path("roleKey").asString()).isEqualTo("TEAM_LEADER");
        assertThat(result.path("approvals").get(1).path("roleKey").asString()).isEqualTo("VICE_PRINCIPAL");
    }

    @Test
    @DisplayName("EDU-14, EDU-18: chủ đề nhánh đủ tên mới gửi; Tổ trưởng rồi Phó HT duyệt; trả lại cần lý do")
    void branchPlanFlow() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21"))));

        assertApiError(() -> act(teacher, "branch", key, submit()), ApiCode.CONFLICT);

        ObjectNode saved = service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        assertThat(saved.path("names").path("2026-09-28").asString()).isEqualTo("Nhánh 2026-09-28");
        assertThat(act(teacher, "branch", key, submit())
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
        // Đã gửi: không sửa được, người không đến lượt không duyệt được
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "branch",
                        key,
                        branch(names()),
                        versionOf(key),
                        rootVersionOf("branch", branch(names()))),
                ApiCode.CONFLICT);
        assertApiError(
                () -> act(vicePrincipal, "branch", key, approve()),
                ApiCode.FORBIDDEN);
        assertApiError(
                () -> act(leader, "branch", key, new ClassDocActionRequest("reject", " ")),
                ApiCode.BAD_REQUEST);

        assertThat(act(leader, "branch", key, approve())
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
        ObjectNode approved = act(vicePrincipal, "branch", key, approve());
        assertThat(approved.path("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.path("approvals")).hasSize(2);
        assertThat(approved.path("history")).hasSize(3);
    }

    @Test
    @DisplayName("EDU-13, EDU-17: kế hoạch chủ đề sau chủ đề nhánh duyệt; hồ sơ gửi một lần khi đủ giáo án các nhánh")
    void themeDossierFlow() {
        String themeKey = "tp_" + classId + "_" + theme1;
        // Spec 003: kế hoạch chủ đề soạn được trước; hồ sơ chủ đề chỉ gửi khi gói học kỳ đã ban hành (FR-005)
        service.saveClassDoc(
                teacher, "theme", themeKey, theme(theme1), versionOf(themeKey), rootVersionOf("theme", theme(theme1)));
        assertErrorCode(() -> act(teacher, "theme", themeKey, submit()), ApiCode.SEMESTER_PACKAGE_NOT_RELEASED);

        approveBranch();
        service.saveClassDoc(
                teacher, "theme", themeKey, theme(theme1), versionOf(themeKey), rootVersionOf("theme", theme(theme1)));
        // Tuần nghỉ không phải tuần nhánh; giáo viên lớp khác không soạn được
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "week",
                        "wp_" + classId + "_2026-09-14",
                        week("2026-09-14"),
                        versionOf("wp_" + classId + "_2026-09-14"),
                        rootVersionOf("week", week("2026-09-14"))),
                ApiCode.BAD_REQUEST);
        assertApiError(
                () -> service.saveClassDoc(
                        otherTeacher,
                        "theme",
                        themeKey,
                        theme(theme1),
                        versionOf(themeKey),
                        rootVersionOf("theme", theme(theme1))),
                ApiCode.FORBIDDEN);

        service.saveClassDoc(
                teacher,
                "week",
                "wp_" + classId + "_2026-09-07",
                week("2026-09-07"),
                versionOf("wp_" + classId + "_2026-09-07"),
                rootVersionOf("week", week("2026-09-07")));
        assertApiError(
                () -> act(teacher, "theme", themeKey, submit()),
                ApiCode.CONFLICT);
        assertApiError(
                () -> service.actOnClassDoc(
                        teacher,
                        "week",
                        "wp_" + classId + "_2026-09-07",
                        submit(),
                        versionOf("wp_" + classId + "_2026-09-07")),
                ApiCode.BAD_REQUEST);

        service.saveClassDoc(
                teacher,
                "week",
                "wp_" + classId + "_2026-09-21",
                week("2026-09-21"),
                versionOf("wp_" + classId + "_2026-09-21"),
                rootVersionOf("week", week("2026-09-21")));
        act(teacher, "theme", themeKey, submit());
        // Giáo án tuần đi cùng trạng thái hồ sơ, không sửa được khi đã gửi
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "week",
                        "wp_" + classId + "_2026-09-07",
                        week("2026-09-07"),
                        versionOf("wp_" + classId + "_2026-09-07"),
                        rootVersionOf("week", week("2026-09-07"))),
                ApiCode.CONFLICT);
        act(leader, "theme", themeKey, new ClassDocActionRequest("reject", "Sửa mục đích tuần"));

        ClassDocsResponse docs = service.getClassDocs(teacher, schoolYear);
        assertThat(docs.weekPlans())
                .hasSize(2)
                .allMatch(w -> w.path("status").asString().equals("DRAFT"));
        assertThat(docs.dayPlans()).allMatch(d -> d.path("status").asString().equals("DRAFT"));
        assertThat(docs.dayPlans().getFirst().path("days")).hasSize(5);
        assertThat(docs.themeDossiers()
                        .getFirst()
                        .path("history")
                        .get(1)
                        .path("note")
                        .asString())
                .isEqualTo("Sửa mục đích tuần");
    }

    @Test
    @DisplayName("Khóa tài liệu phải khớp lớp / kế hoạch năm; kế hoạch năm chưa ban hành thì chưa lập được")
    void rejectsMismatchedKey() {
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "branch",
                        "bp_1_2",
                        branch(names()),
                        versionOf("bp_1_2"),
                        rootVersionOf("branch", branch(names()))),
                ApiCode.BAD_REQUEST);
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "lesson",
                        "x",
                        branch(names()),
                        versionOf("x"),
                        rootVersionOf("lesson", branch(names()))),
                ApiCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("Lịch tuần: bỏ tuần nghỉ trọn tuần, tuần học đánh số liên tục qua các chủ đề")
    void teachingWeeksSkipBreaks() {
        assertThat(service.getClassDocs(teacher, schoolYear).branchPlans()).isEmpty();
        ObjectNode doc = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(teacher, "branch", key, doc, versionOf(key), rootVersionOf("branch", doc));
        jdbc.update("DELETE FROM staff_assignments WHERE staff_role = 'AGE_GROUP_TEAM_LEADER'");
        // Không còn tổ trưởng: gửi thẳng Phó HT
        assertThat(act(teacher, "branch", key, submit())
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("EDU-18: hồ sơ chờ Tổ trưởng mà nhóm tuổi hết Tổ trưởng ⇒ chuyển bước Phó HT, không kẹt")
    void pendingLeaderStepMovesOnWhenLeaderRemoved() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        assertThat(act(teacher, "branch", key, submit())
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");

        jdbc.update("DELETE FROM staff_assignments WHERE staff_role = 'AGE_GROUP_TEAM_LEADER'");

        assertThat(service.getClassDocs(vicePrincipal, schoolYear).semesterPackages())
                .filteredOn(d -> d.path("semesterNo").asInt() == 1)
                .singleElement()
                .extracting(d -> d.path("status").asString())
                .isEqualTo("REVIEW");
        ObjectNode approved = act(vicePrincipal, "branch", key, approve());
        assertThat(approved.path("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.path("approvals")).hasSize(1);
    }

    @Test
    @DisplayName("EDU-18: đổi cấu hình chuỗi khi hồ sơ đang chờ không làm hồ sơ tự duyệt (theo chuỗi chụp lúc gửi)")
    void signerChainChangeDoesNotAutoApprove() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        act(teacher, "branch", key, submit());
        jdbc.update("UPDATE edu_settings SET signers = "
                + "'{\"year\":[\"VICE_PRINCIPAL\"],\"branch\":[\"VICE_PRINCIPAL\",\"PRINCIPAL\"],"
                + "\"week\":[\"VICE_PRINCIPAL\"]}'");

        assertThat(act(leader, "branch", key, approve())
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
        assertThat(act(vicePrincipal, "branch", key, approve())
                        .path("status")
                        .asString())
                .isEqualTo("APPROVED");

        // Hồ sơ gửi trước khi có snapshot: cấu hình bỏ bước đang chờ thì các bước còn lại vẫn phải duyệt
        String otherKey = "bp_" + otherClassId + "_" + plan.id();
        ObjectNode otherDoc = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        otherDoc.put("classId", String.valueOf(otherClassId));
        service.saveClassDoc(
                otherTeacher, "branch", otherKey, otherDoc, versionOf(otherKey), rootVersionOf("branch", otherDoc));
        jdbc.update("UPDATE edu_settings SET signers = "
                + "'{\"year\":[\"VICE_PRINCIPAL\"],\"branch\":[\"TEAM_LEADER\"],\"week\":[\"VICE_PRINCIPAL\"]}'");
        act(otherTeacher, "branch", otherKey, submit());
        jdbc.update("UPDATE edu_class_docs SET sign_chain = NULL WHERE doc_key = ?", otherKey);
        jdbc.update("UPDATE edu_settings SET signers = "
                + "'{\"year\":[\"VICE_PRINCIPAL\"],\"branch\":[\"VICE_PRINCIPAL\"],\"week\":[\"VICE_PRINCIPAL\"]}'");
        assertThat(act(leader, "branch", otherKey, approve())
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
    }

    private void approveBranch() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        act(teacher, "branch", key, submit());
        act(leader, "branch", key, approve());
        act(vicePrincipal, "branch", key, approve());
    }

    @Test
    void vicePrincipalCannotApproveClassAtAnotherCampus() {
        long otherCampus = insertId("INSERT INTO campuses(code,name) VALUES ('CS-B','Campus B') RETURNING id");
        long targetClass = insertId(
                "INSERT INTO classrooms(campus_id,school_year_id,name,age_group_id) VALUES (?,?,'B1',?) RETURNING id",
                otherCampus,
                schoolYear,
                ageGroup3);
        CurrentUser targetTeacher = user("teacher-campus-b", CareNestRole.TEACHER);
        assignTeacher(targetTeacher, targetClass);
        String key = "bp_" + targetClass + "_" + plan.id();
        ObjectNode content = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        content.put("classId", targetClass);
        service.saveClassDoc(targetTeacher, "branch", key, content, -1L, null);
        act(targetTeacher, "branch", key, submit());
        act(leader, "branch", key, approve());
        assertErrorCode(
                () -> act(vicePrincipal, "branch", key, approve()),
                ApiCode.NOT_YOUR_REVIEW_STEP);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM edu_class_docs WHERE doc_key=?", String.class, "sp_" + key.substring(3) + "_1"))
                .isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("EDU-12/18: học liệu private, retry một lần, stale binding cần gắn lại và bản khóa không sửa")
    void learningResourcesRespectScopeAndActivityIdentity() throws Exception {
        approveBranch();
        String key = "tp_" + classId + "_" + theme1;
        String goal = String.valueOf(
                jdbc.queryForObject("SELECT id FROM edu_plan_goals WHERE team_plan_id=?", Long.class, plan.id()));
        ObjectNode doc = theme(theme1);
        ((ObjectNode) doc.path("rows")).putObject(goal).put("activities", "- HĐ học:\n+ Vận động A\n+ Vận động B");
        service.saveClassDoc(teacher, "theme", key, doc, versionOf(key), rootVersionOf("theme", doc));
        String sig = com.carenest.utils.LearningResourcePolicy.signature(
                doc.path("rows").path(goal).path("activities").asString());
        var binding = new com.carenest.dto.eduplan.LearningResourceBinding(goal + "#1", sig, "Vận động B");
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "demo.pdf", "application/pdf", "%PDF-1.7\ndemo".getBytes());
        java.util.UUID request = java.util.UUID.randomUUID();
        var first = learningResources.upload(teacher, key, file, binding, request);
        assertThat(learningResources
                        .upload(teacher, key, file, binding, request)
                        .id())
                .isEqualTo(first.id());
        assertThat(learningResources
                        .content(teacher, key, first.id())
                        .resource()
                        .getInputStream()
                        .readAllBytes())
                .isEqualTo(file.getBytes());
        assertApiError(() -> learningResources.list(otherTeacher, key), ApiCode.FORBIDDEN);
        assertApiError(() -> learningResources.content(otherTeacher, key, first.id()), ApiCode.FORBIDDEN);
        assertApiError(
                () -> learningResources.upload(vicePrincipal, key, file, binding, java.util.UUID.randomUUID()),
                ApiCode.FORBIDDEN);
        assignTeacher(otherTeacher, classId);
        long docId = jdbc.queryForObject("SELECT id FROM edu_class_docs WHERE doc_key=?", Long.class, key);
        learningResources.upload(otherTeacher, key, file, binding, request);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_document_authors WHERE class_doc_id=? AND user_id=?",
                        Long.class,
                        docId,
                        otherTeacher.userId()))
                .isZero();
        learningResources.upload(otherTeacher, key, file, binding, java.util.UUID.randomUUID());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_document_authors WHERE class_doc_id=? AND user_id=?",
                        Long.class,
                        docId,
                        otherTeacher.userId()))
                .isEqualTo(1);
        ((ObjectNode) doc.path("rows").path(goal)).put("activities", "HĐ học: Vận động mới");
        service.saveClassDoc(teacher, "theme", key, doc, versionOf(key), rootVersionOf("theme", doc));
        assertThat(learningResources.list(teacher, key).getFirst().active()).isFalse();
        assertThat(learningResources.list(vicePrincipal, key)).isEmpty();
        assertApiError(() -> learningResources.content(vicePrincipal, key, first.id()), ApiCode.NOT_FOUND);
        assertApiError(
                () -> learningResources.upload(teacher, key, file, binding, java.util.UUID.randomUUID()),
                ApiCode.CONFLICT);
        var rebound = new com.carenest.dto.eduplan.LearningResourceBinding(
                goal + "#0", com.carenest.utils.LearningResourcePolicy.signature("HĐ học: Vận động mới"), "Mới");
        CurrentUser editor = user("resource-editor", CareNestRole.TEACHER);
        assignTeacher(editor, classId);
        assertThat(learningResources.bind(editor, key, first.id(), rebound).active())
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_document_authors WHERE class_doc_id=? AND user_id=?",
                        Long.class,
                        docId,
                        editor.userId()))
                .isEqualTo(1);
        CurrentUser noop = user("resource-noop", CareNestRole.TEACHER);
        assignTeacher(noop, classId);
        learningResources.bind(noop, key, first.id(), rebound);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_document_authors WHERE class_doc_id=? AND user_id=?",
                        Long.class,
                        docId,
                        noop.userId()))
                .isZero();
        CurrentUser detacher = user("resource-detacher", CareNestRole.TEACHER);
        assignTeacher(detacher, classId);
        learningResources.detach(detacher, key, first.id());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_document_authors WHERE class_doc_id=? AND user_id=?",
                        Long.class,
                        docId,
                        detacher.userId()))
                .isEqualTo(1);
        learningResources.detach(noop, key, first.id());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_document_authors WHERE class_doc_id=? AND user_id=?",
                        Long.class,
                        docId,
                        noop.userId()))
                .isZero();
        assertThat(learningResources.list(teacher, key).getFirst().activityKey())
                .isNull();
        assertThat(learningResources
                        .content(teacher, key, first.id())
                        .resource()
                        .exists())
                .isTrue();
        CurrentUser configPrincipal = user("resource-principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2026-09-01')",
                configPrincipal.userId());
        var changedPolicies =
                new java.util.HashMap<String, com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry>();
        workflow.getPolicy(configPrincipal)
                .policies()
                .forEach((type, policy) -> changedPolicies.put(
                        type,
                        new com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry(
                                policy.creators(), policy.reviewers())));
        changedPolicies.put(
                "theme",
                new com.carenest.dto.eduplan.EduWorkflowPolicyRequest.PolicyEntry(
                        List.of("TEACHER"), List.of("TEAM_LEADER")));
        workflow.savePolicy(
                configPrincipal,
                new com.carenest.dto.eduplan.EduWorkflowPolicyRequest(workflow.getPolicyRevision(), changedPolicies));
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2026-09-01')",
                editor.userId(),
                ageGroup3);
        jdbc.update("UPDATE edu_class_docs SET status='REVIEW' WHERE doc_key=?", key);
        // Native update cần xóa persistence cache trước khi kiểm lại status.
        entityManager.clear();
        assertApiError(() -> learningResources.detach(teacher, key, first.id()), ApiCode.CONFLICT);
        assertErrorCode(
                () -> act(editor, "theme", key, approve()),
                ApiCode.NOT_YOUR_REVIEW_STEP);
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("EDU-12: ZIP đổi đuôi Office không phải học liệu hợp lệ")
    void learningResourcesRejectRenamedZipAndInvalidGoal() throws Exception {
        approveBranch();
        String key = "tp_" + classId + "_" + theme1;
        String goal = String.valueOf(
                jdbc.queryForObject("SELECT id FROM edu_plan_goals WHERE team_plan_id=?", Long.class, plan.id()));
        ObjectNode doc = theme(theme1);
        ((ObjectNode) doc.path("rows")).putObject(goal).put("activities", "A");
        service.saveClassDoc(teacher, "theme", key, doc, versionOf(key), rootVersionOf("theme", doc));
        var binding = new com.carenest.dto.eduplan.LearningResourceBinding(
                goal + "#0", com.carenest.utils.LearningResourcePolicy.signature("A"), "A");
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("random.txt"));
            zip.write("x".getBytes());
            zip.closeEntry();
        }
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "fake.pptx", "application/zip", bytes.toByteArray());
        assertApiError(
                () -> learningResources.upload(teacher, key, file, binding, java.util.UUID.randomUUID()),
                ApiCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(learningResources.list(teacher, key)).isEmpty();
    }

    @Test
    @DisplayName("EDU-27: kế hoạch điều chỉnh ghi được cả khi đã duyệt, không đổi trạng thái/nội dung; chỉ GV lớp")
    void adjustmentsSaveAfterApproval() {
        approveBranch();
        String key = "wp_" + classId + "_2026-09-07";
        service.saveClassDoc(
                teacher,
                "theme",
                "tp_" + classId + "_" + theme1,
                theme(theme1),
                versionOf("tp_" + classId + "_" + theme1),
                rootVersionOf("theme", theme(theme1)));
        service.saveClassDoc(
                teacher, "week", key, week("2026-09-07"), versionOf("dd_" + key.substring(3)), rootVersionOf("week", week("2026-09-07")));
        // EDU-27 ở giáo án ngày (Q9): khóa wp_ gửi lên được chuyển sang dd_ cùng tuần
        String dayKey = "dd_" + key.substring(3);
        ObjectNode day = base();
        day.put("themeId", theme1);
        day.put("weekStart", "2026-09-07");
        day.putArray("days").addObject().put("date", "2026-09-07");
        service.saveClassDoc(teacher, "day", dayKey, day, -1L, rootVersionOf("day", day));
        jdbc.update("UPDATE edu_class_docs SET status='APPROVED' WHERE doc_key IN (?, ?)", key, dayKey);
        entityManager.clear();

        ObjectNode saved = service.saveAdjustments(
                teacher,
                key,
                java.util.Map.of("2026-09-08", "  Đổi giờ học  "),
                versionOf("dd_" + key.substring(3)),
                rootVersionOfWeek(key));
        assertThat(saved.path("status").asString()).isEqualTo("APPROVED");
        assertThat(saved.path("adjustments").path("2026-09-08").asString()).isEqualTo("Đổi giờ học");
        assertThat(saved.path("history")
                        .get(saved.path("history").size() - 1)
                        .path("action")
                        .asString())
                .isEqualTo("Cập nhật kế hoạch điều chỉnh và bổ sung");

        assertApiError(
                () -> service.saveAdjustments(
                        otherTeacher, key, java.util.Map.of("2026-09-08", "x"), versionOf("dd_" + key.substring(3)), rootVersionOfWeek(key)),
                ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.saveAdjustments(
                        teacher, key, java.util.Map.of("2026-09-14", "x"), versionOf("dd_" + key.substring(3)), rootVersionOfWeek(key)),
                ApiCode.BAD_REQUEST);
        assertApiError(
                () -> service.saveAdjustments(
                        teacher,
                        key,
                        java.util.Map.of("2026-09-08", "x".repeat(4001)),
                        versionOf("dd_" + key.substring(3)),
                        rootVersionOfWeek(key)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("EDU-25/26: phiên bản mới — bản cũ chỉ xem; bản chép có ô đỏ thì chặn gửi tới khi sửa/đánh dấu")
    void versionedClassDocsNeedFixes() {
        approveBranch();
        String oldTheme2Key = "tp_" + classId + "_" + theme2;
        String goal = String.valueOf(
                jdbc.queryForObject("SELECT id FROM edu_plan_goals WHERE team_plan_id=?", Long.class, plan.id()));
        ObjectNode oldTheme = theme(theme2);
        ((ObjectNode) oldTheme.path("rows")).putObject(goal).put("activities", "Trò chuyện: Gia đình");
        service.saveClassDoc(
                teacher, "theme", oldTheme2Key, oldTheme, versionOf(oldTheme2Key), rootVersionOf("theme", oldTheme));

        // Fixture lịch sử nội bộ tổ; API phiên bản công khai chỉ dành cho năm (EDU-62).
        java.util.UUID session = java.util.UUID.randomUUID();
        YearPlanDetailResponse v2 = internalTeamUpdate(session);
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.sourcePlanId()).isEqualTo(plan.id());
        assertThat(v2.code()).isEqualTo(plan.code() + "-V2");
        assertThat(v2.themes()).hasSize(9);
        assertThat(v2.domains().stream().flatMap(d -> d.rows().stream()).toList())
                .hasSize(2); // nhóm "Kết quả mong đợi" + 1 mục tiêu
        assertApiError(
                () -> yearPlanService.createVersion(
                        leader,
                        plan.id(),
                        new com.carenest.dto.eduplan.YearPlanEditRequest(
                                java.util.UUID.randomUUID(), plan.revision(), java.util.UUID.randomUUID())),
                ApiCode.BAD_REQUEST); // EDU-62
        YearPlanDetailResponse edited = yearPlanService.updateYearPlan(
                leader,
                v2.id(),
                requestOf(
                        v2,
                        "Trẻ thực hiện đủ động tác theo nhạc",
                        LocalDate.of(2026, 10, 26),
                        LocalDate.of(2026, 10, 30)),
                new com.carenest.dto.eduplan.YearPlanEditRequest(session, v2.revision(), java.util.UUID.randomUUID()));
        YearPlanDetailResponse submitted = yearPlanService.submitYearPlan(
                leader,
                v2.id(),
                "Gửi duyệt",
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        session, edited.revision(), java.util.UUID.randomUUID()));
        YearPlanDetailResponse issued = yearPlanService.issueYearPlan(
                vicePrincipal,
                v2.id(),
                "Ban hành",
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        null, submitted.revision(), java.util.UUID.randomUUID()));
        assertThat(issued.current()).isTrue();
        assertThat(yearPlanService.getYearPlan(teacher, plan.id()).current()).isFalse();
        assertThat(yearPlanService
                        .getYearPlan(teacher, plan.id())
                        .domains()
                        .getFirst()
                        .rows()
                        .getFirst()
                        .text())
                .isEqualTo("Trẻ thực hiện đủ động tác");

        var changes = yearPlanService.compareVersions(plan.id(), v2.id());
        assertThat(changes.goals()).singleElement().satisfies(g -> {
            assertThat(g.change()).isEqualTo(com.carenest.dto.eduplan.YearPlanChangesResponse.Change.CHANGED);
            assertThat(g.fromId()).isEqualTo(Long.valueOf(goal));
        });
        assertThat(changes.themes())
                .extracting(com.carenest.dto.eduplan.YearPlanChangesResponse.Theme::change)
                .containsExactly(
                        com.carenest.dto.eduplan.YearPlanChangesResponse.Change.UNCHANGED,
                        com.carenest.dto.eduplan.YearPlanChangesResponse.Change.CHANGED);
        // v1 chỉ còn để xem; không tạo phiên bản từ bản không mới nhất
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "theme",
                        oldTheme2Key,
                        oldTheme,
                        versionOf(oldTheme2Key),
                        rootVersionOf("theme", oldTheme)),
                ApiCode.CONFLICT);
        assertApiError(
                () -> yearPlanService.createVersion(
                        leader,
                        plan.id(),
                        new com.carenest.dto.eduplan.YearPlanEditRequest(
                                java.util.UUID.randomUUID(), plan.revision(), java.util.UUID.randomUUID())),
                ApiCode.BAD_REQUEST); // EDU-62: kế hoạch tổ không tạo phiên bản qua API công khai

        String newGoal =
                String.valueOf(issued.domains().getFirst().rows().getFirst().id());
        String newTheme2 = String.valueOf(issued.themes().get(1).id());
        String branchKey = "bp_" + classId + "_" + v2.id();
        ObjectNode branch = branch(names("2026-09-07", "2026-09-21", "2026-09-28", "2026-10-26"));
        branch.put("yearPlanId", String.valueOf(v2.id()));
        branch.put("sourceDocKey", "bp_" + classId + "_" + plan.id());
        assertThat(service.saveClassDoc(
                                teacher,
                                "branch",
                                branchKey,
                                branch,
                                versionOf(branchKey),
                                rootVersionOf("branch", branch))
                        .path("sourceDocKey")
                        .asString())
                .isEqualTo("bp_" + classId + "_" + plan.id());
        // Chủ đề 2 đổi ngày, chưa đánh dấu kiểm tra ⇒ chặn gửi
        assertErrorCode(
                () -> act(teacher, "branch", branchKey, submit()),
                ApiCode.CLASS_DOC_VERSION_FIX_REQUIRED);
        branch.putArray("versionChecks").add("theme:" + newTheme2);
        service.saveClassDoc(
                teacher, "branch", branchKey, branch, versionOf(branchKey), rootVersionOf("branch", branch));
        act(teacher, "branch", branchKey, submit());
        act(leader, "branch", branchKey, approve());
        act(vicePrincipal, "branch", branchKey, approve());

        String themeKey = "tp_" + classId + "_" + newTheme2;
        ObjectNode copy = theme(newTheme2);
        copy.put("yearPlanId", String.valueOf(v2.id()));
        copy.put("sourceDocKey", oldTheme2Key);
        ((ObjectNode) copy.path("rows")).putObject(newGoal).put("activities", "Trò chuyện: Gia đình");
        service.saveClassDoc(teacher, "theme", themeKey, copy, versionOf(themeKey), rootVersionOf("theme", copy));
        String weekKey = "wp_" + classId + "_2026-10-26_" + v2.id();
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "week",
                        "wp_" + classId + "_2026-10-26",
                        weekOf(v2.id(), newTheme2),
                        versionOf("wp_" + classId + "_2026-10-26"),
                        rootVersionOf("week", weekOf(v2.id(), newTheme2))),
                ApiCode.BAD_REQUEST);
        service.saveClassDoc(
                teacher,
                "week",
                weekKey,
                weekOf(v2.id(), newTheme2),
                versionOf(weekKey),
                rootVersionOf("week", weekOf(v2.id(), newTheme2)));
        assertThatThrownBy(() -> act(teacher, "theme", themeKey, submit()))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CLASS_DOC_VERSION_FIX_REQUIRED);
        copy.putArray("versionChecks").add("goal:" + newGoal);
        service.saveClassDoc(teacher, "theme", themeKey, copy, versionOf(themeKey), rootVersionOf("theme", copy));
        assertThat(act(teacher, "theme", themeKey, submit())
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
        // Bản v1 giữ nguyên
        assertThat(service.getClassDocs(teacher, schoolYear).classThemePlans())
                .filteredOn(d -> d.path("id").asString().equals(oldTheme2Key))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("status").asString()).isEqualTo("DRAFT");
                    assertThat(d.path("superseded").asBoolean()).isTrue();
                    assertThat(d.path("yearPlanVersion").asInt()).isEqualTo(1);
                });
    }

    private YearPlanDetailResponse internalTeamUpdate(java.util.UUID session) {
        EduPlan source = planRepository.findById(plan.id()).orElseThrow();
        Instant now = Instant.parse("2026-10-22T03:00:00Z");
        EduPlan update = EduPlan.createVersion(source, source.getCode() + "-V2", leader.userId(), "Tổ trưởng", now);
        update.initializeSave(java.util.UUID.randomUUID());
        update = planRepository.saveAndFlush(update);
        workflow.recordAuthor(update.getId(), null, leader.userId(), "CREATOR", now);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                (Object) org.springframework.test.util.AopTestUtils.getTargetObject(yearPlanService),
                "copyContent", source.getId(), update.getId());
        return yearPlanService.getYearPlan(leader, update.getId());
    }

    private YearPlanRequest requestOf(YearPlanDetailResponse detail, String goalText, LocalDate start, LocalDate end) {
        List<YearPlanRequest.Domain> domains = detail.domains().stream()
                .filter(d -> !d.rows().isEmpty())
                .map(d -> new YearPlanRequest.Domain(
                        d.domainId(),
                        d.rows().stream()
                                .map(r -> new YearPlanRequest.Row(
                                        r.id(),
                                        r.kind(),
                                        goalText,
                                        r.contents().stream()
                                                .map(c -> new YearPlanRequest.Content(
                                                        c.id(),
                                                        c.text(),
                                                        c.themeIds().stream()
                                                                .map(String::valueOf)
                                                                .toList()))
                                                .toList()))
                                .toList()))
                .toList();
        List<YearPlanRequest.Theme> themes = detail.themes().stream()
                .map(t -> new YearPlanRequest.Theme(
                        String.valueOf(t.id()),
                        t.id(),
                        t.name(),
                        t.abbr(),
                        t.id().equals(detail.themes().get(1).id()) ? start : t.startDate(),
                        t.id().equals(detail.themes().get(1).id()) ? end : t.endDate(),
                        t.festivals(),
                        t.note()))
                .toList();
        return new YearPlanRequest(
                null,
                null,
                detail.title(),
                detail.description(),
                domains,
                themes,
                detail.breaks().stream()
                        .map(b -> new YearPlanRequest.Break(b.id(), b.label(), b.startDate(), b.endDate()))
                        .toList());
    }

    private ObjectNode weekOf(Long yearPlanId, String themeId) {
        ObjectNode doc = week("2026-10-26");
        doc.put("yearPlanId", String.valueOf(yearPlanId));
        doc.put("themeId", themeId);
        return doc;
    }

    private ObjectNode names(String... weeks) {
        ObjectNode names = objectMapper.createObjectNode();
        for (String w : weeks) {
            names.put(w, "Nhánh " + w);
        }
        return names;
    }

    private ObjectNode branch(ObjectNode names) {
        ObjectNode doc = base();
        doc.set("names", names);
        doc.putObject("notes");
        // Trạng thái client gửi bị bỏ qua
        doc.put("status", "APPROVED");
        return doc;
    }

    private ObjectNode theme(String themeId) {
        ObjectNode doc = base();
        doc.put("themeId", themeId);
        doc.putObject("rows");
        doc.put("envIn", "Góc chơi");
        return doc;
    }

    private ObjectNode week(String weekStart) {
        ObjectNode doc = base();
        doc.put("themeId", theme1);
        doc.put("weekStart", weekStart);
        doc.putObject("aims").put("knowledge", "Trẻ biết tên trường");
        return doc;
    }

    private ObjectNode base() {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("classId", String.valueOf(classId));
        doc.put("yearPlanId", String.valueOf(plan.id()));
        return doc;
    }

    private static ClassDocActionRequest submit() {
        return new ClassDocActionRequest("submit", null);
    }

    private static ClassDocActionRequest approve() {
        return new ClassDocActionRequest("approve", null);
    }

    @Test
    @DisplayName(
            "T028 [US2]: người có vai trò trong creators được tạo; reviewer bị từ chối quyền tác giả (precedence); 3 trạng thái DRAFT -> REVIEW -> cấm sửa khi REVIEW")
    void classDocCreatorScopeEnforcedAndThreeStates() {
        String key = "bp_" + classId + "_" + plan.id();

        // Leader (nằm trong reviewers) gọi saveClassDoc bị từ chối
        assertApiError(
                () -> service.saveClassDoc(
                        leader,
                        "branch",
                        key,
                        branch(names("2026-09-07", "2026-09-21")),
                        versionOf(key),
                        rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21")))),
                ApiCode.FORBIDDEN);

        // Teacher tạo doc -> DRAFT
        ObjectNode saved = service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        assertThat(saved.path("status").asText()).isEqualTo("DRAFT");

        // Teacher submit -> REVIEW
        ObjectNode submitted = act(teacher, "branch", key, submit());
        assertThat(submitted.path("status").asText()).isEqualTo("REVIEW");

        // Khi ở REVIEW: teacher cố sửa content -> bị từ chối
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "branch",
                        key,
                        branch(names("2026-09-07", "2026-09-21")),
                        versionOf(key),
                        rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21")))),
                ApiCode.CONFLICT);

        // Khi ở REVIEW: reviewer cũng không được sửa content
        assertApiError(
                () -> service.saveClassDoc(
                        leader,
                        "branch",
                        key,
                        branch(names("2026-09-07", "2026-09-21")),
                        versionOf(key),
                        rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21")))),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName(
            "T028 [US2]: kiểm tra version headers X-Class-Doc-Version và X-Dossier-Version (mismatch trả 9012 CONCURRENT_UPDATE)")
    void classDocVersionHeadersPrecondition() {
        String key = "bp_" + classId + "_" + plan.id();

        // Tạo mới: expectedVersion khác -1 (ví dụ 0) khi doc chưa có -> trả CONCURRENT_UPDATE
        assertErrorCode(
                () -> service.saveClassDoc(
                        teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), 0L, null),
                ApiCode.CONCURRENT_UPDATE);

        // Tạo mới hợp lệ với expectedVersion = -1
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), -1L, null);

        // Cập nhật khi đã có: gửi -1 -> CONCURRENT_UPDATE
        assertErrorCode(
                () -> service.saveClassDoc(
                        teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), -1L, null),
                ApiCode.CONCURRENT_UPDATE);

        // Cập nhật khi đã có: gửi version sai (99) -> CONCURRENT_UPDATE
        assertErrorCode(
                () -> service.saveClassDoc(
                        teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), 99L, null),
                ApiCode.CONCURRENT_UPDATE);

        // Duyệt branch để chuyển sang test theme dossier versioning
        approveBranch();
        String themeKey = "tp_" + classId + "_" + theme1;
        service.saveClassDoc(teacher, "theme", themeKey, theme(theme1), -1L, null);

        // Week doc: nếu gửi expectedRootVersion sai lệch với version hiện tại của theme root -> CONCURRENT_UPDATE
        assertErrorCode(
                () -> service.saveClassDoc(
                        teacher, "week", "wp_" + classId + "_2026-09-07", week("2026-09-07"), -1L, 999L),
                ApiCode.CONCURRENT_UPDATE);
    }

    @Test
    @DisplayName("T028 [US2]: returnClassDoc trả về DRAFT kèm note; cấm return nếu thiếu note")
    void returnClassDocRevertsToDraftWithReason() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        act(teacher, "branch", key, submit());

        // Thiếu note khi reject bị 400 RETURN_REASON_REQUIRED
        assertErrorCode(
                () -> act(leader, "branch", key, new ClassDocActionRequest("reject", "   ")),
                ApiCode.RETURN_REASON_REQUIRED);

        // Trả lại kèm note -> status về DRAFT
        ObjectNode returned = act(leader, "branch", key, new ClassDocActionRequest("reject", "Cần điều chỉnh tuần nhánh"));
        assertThat(returned.path("status").asText()).isEqualTo("DRAFT");

        // Sau khi về DRAFT, tác giả có thể sửa lại
        ObjectNode resaved = service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        assertThat(resaved).isNotNull();
    }

    @Test
    @DisplayName("T028 [US2]: giáo án tuần không gửi duyệt riêng, chỉ duyệt qua hồ sơ chủ đề (theme dossier)")
    void weekDocCannotSubmitIndependently() {
        approveBranch();
        String themeKey = "tp_" + classId + "_" + theme1;
        service.saveClassDoc(
                teacher, "theme", themeKey, theme(theme1), versionOf(themeKey), rootVersionOf("theme", theme(theme1)));
        service.saveClassDoc(
                teacher,
                "week",
                "wp_" + classId + "_2026-09-07",
                week("2026-09-07"),
                versionOf("wp_" + classId + "_2026-09-07"),
                rootVersionOf("week", week("2026-09-07")));

        assertApiError(
                () -> service.actOnClassDoc(
                        teacher,
                        "week",
                        "wp_" + classId + "_2026-09-07",
                        submit(),
                        versionOf("wp_" + classId + "_2026-09-07")),
                ApiCode.BAD_REQUEST);
    }

    private static void assertErrorCode(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(code));
    }

    private static void assertApiError(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode().getHttpStatus())
                        .isEqualTo(code.getHttpStatus()));
    }

    private void assignTeacher(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', true)",
                user.userId(),
                classroomId);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(id, "kc-" + username, username, "Demo " + username, null, Set.of(role));
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void strictVersionsAndMetadataCannotBeForged() {
        String key = "bp_" + classId + "_" + plan.id();
        ObjectNode content = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        content.put("version", 999L);
        content.putObject("permissions").put("canApprove", true);
        content.putObject("review").put("waitingRole", "PRINCIPAL");
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "branch", key, content, null, null),
                ApiCode.REQUIRED_FIELD_MISSING);
        ObjectNode saved = service.saveClassDoc(teacher, "branch", key, content, -1L, null);
        assertThat(saved.path("version").asLong()).isZero();
        assertThat(saved.path("permissions").path("canApprove").asBoolean()).isFalse();
        assertThat(saved.path("review").path("waitingRole").isNull()).isTrue();
        String stored =
                jdbc.queryForObject("SELECT content::text FROM edu_class_docs WHERE doc_key=?", String.class, key);
        assertThat(stored).doesNotContain("permissions", "review", "version");
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "semester", "sp_" + key.substring(3) + "_1", submit(), null), ApiCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    void weekMutationInvalidatesRootAndRequiresDossierVersion() {
        approveBranch();
        String rootKey = "tp_" + classId + "_" + theme1;
        service.saveClassDoc(teacher, "theme", rootKey, theme(theme1), -1L, null);
        long before = versionOf(rootKey);
        String weekKey = "wp_" + classId + "_2026-09-07";
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "week", weekKey, week("2026-09-07"), -1L, null),
                ApiCode.REQUIRED_FIELD_MISSING);
        ObjectNode saved = service.saveClassDoc(teacher, "week", weekKey, week("2026-09-07"), -1L, before);
        assertThat(saved.path("dossierVersion").asLong()).isGreaterThan(before);
        assertErrorCode(
                () -> service.saveClassDoc(
                        teacher,
                        "week",
                        weekKey,
                        week("2026-09-07"),
                        saved.path("version").asLong(),
                        before),
                ApiCode.CONCURRENT_UPDATE);
    }

    /**
     * Spec 003: lịch nhánh gửi/duyệt trong gói học kỳ 1 (sp_), kế hoạch chủ đề trong hồ sơ chủ đề (ds_). Trước khi gửi hồ
     * sơ, chép 5 kế hoạch ngày của các tuần đã lưu sang giáo án ngày (trang riêng, Q9).
     */
    private ObjectNode act(CurrentUser user, String kind, String key, ClassDocActionRequest request) {
        boolean branch = "branch".equals(kind);
        String rootKey = branch ? "sp_" + key.substring(3) + "_1" : "ds_" + key.substring(3);
        if (!branch && "submit".equals(request.action())) {
            String[] parts = key.split("_");
            for (var row : jdbc.queryForList(
                    "SELECT doc_key, week_start::text AS ws FROM edu_class_docs w WHERE kind='week' AND classroom_id=? "
                            + "AND theme_id=? AND NOT EXISTS (SELECT 1 FROM edu_class_docs d WHERE d.doc_key='dd_' || "
                            + "substring(w.doc_key FROM 4))",
                    Long.valueOf(parts[1]),
                    Long.valueOf(parts[2]))) {
                String weekKey = (String) row.get("doc_key");
                ObjectNode day = base();
                day.put("classId", parts[1]);
                day.put("themeId", parts[2]);
                day.put("weekStart", (String) row.get("ws"));
                var days = day.putArray("days");
                for (int i = 0; i < 5; i++)
                    days.addObject().put("date", LocalDate.parse((String) row.get("ws")).plusDays(i).toString());
                try {
                    service.saveClassDoc(user, "day", "dd_" + weekKey.substring(3), day, -1L, versionOf(rootKey));
                } catch (GlobalException ignored) {
                    // người gửi không phải GVCN: để lỗi của bước gửi tự báo
                }
            }
        }
        return service.actOnClassDoc(user, branch ? "semester" : "dossier", rootKey, request, versionOf(rootKey));
    }

    // ===== Spec 003: mốc duyệt theo thời gian dạy =====

    private static ClassDocActionRequest extend(String note, String deadline) {
        return new ClassDocActionRequest("extend", note, LocalDate.parse(deadline));
    }

    private long notificationsOf(CurrentUser user, String type, String link) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id=? AND type=? AND link=?",
                Long.class,
                user.userId(),
                type,
                link);
    }

    @Test
    @DisplayName("Spec 003 FR-004/004a: quá hạn khóa gửi gói học kỳ; PHT mở hạn (lý do + hạn mới) rồi gửi được, có thông báo")
    void overdueSemesterPackageNeedsExtension() {
        jdbc.update("UPDATE classrooms SET created_at='2026-08-01' WHERE id=?", classId);
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        String sp = "sp_" + classId + "_" + plan.id() + "_1";
        String link = "/education/class/" + classId + "/semesters/1";
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp)),
                ApiCode.CLASS_DOC_DEADLINE_PASSED);
        // FR-003: Ban giám hiệu thấy gói đang Nháp kèm hạn để mở hạn, không thấy tên nhánh đang soạn
        assertThat(service.getClassDocs(vicePrincipal, schoolYear).semesterPackages())
                .filteredOn(d -> d.path("id").asString().equals(sp))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("deadlineState").asString()).isEqualTo("OVERDUE");
                    assertThat(d.path("canExtend").asBoolean()).isTrue();
                    assertThat(d.path("branchNames").size()).isZero();
                });
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "semester", sp, extend("Lý do", "2026-10-28"), versionOf(sp)),
                ApiCode.NOT_YOUR_REVIEW_STEP);
        assertErrorCode(
                () -> service.actOnClassDoc(vicePrincipal, "semester", sp, extend(" ", "2026-10-28"), versionOf(sp)),
                ApiCode.REQUIRED_FIELD_MISSING);
        assertErrorCode(
                () -> service.actOnClassDoc(vicePrincipal, "semester", sp, extend("Lý do", "2026-10-22"), versionOf(sp)),
                ApiCode.INVALID_VALUE);
        // Q8: PHT phân công ở điểm trường khác vẫn mở hạn được
        CurrentUser otherCampusVp = user("vp-other-campus", CareNestRole.VICE_PRINCIPAL);
        long otherCampus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'Campus B') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                otherCampusVp.userId(),
                otherCampus);
        ObjectNode extended = service.actOnClassDoc(
                otherCampusVp, "semester", sp, extend("Giáo viên nghỉ ốm", "2026-10-28"), versionOf(sp));
        assertThat(extended.path("extendedTo").asString()).isEqualTo("2026-10-28");
        assertThat(extended.path("deadlineState").asString()).isEqualTo("NEAR");
        assertThat(notificationsOf(teacher, "EDU_DEADLINE_EXTENDED", link)).isEqualTo(1);

        ObjectNode sent = service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp));
        assertThat(sent.path("status").asString()).isEqualTo("REVIEW");
        assertThat(notificationsOf(leader, "EDU_SUBMITTED", link)).isEqualTo(1);
    }

    @Test
    @DisplayName("Spec 003 Q2/Q4: chủ đề 'theo học kỳ' gửi kế hoạch chủ đề + tuần trong gói học kỳ; hồ sơ chủ đề chỉ còn giáo án ngày")
    void semesterModeThemeShipsInPackage() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        String sp = "sp_" + classId + "_" + plan.id() + "_1";
        String themeKey = "tp_" + classId + "_" + theme1;
        ObjectNode t = theme(theme1);
        t.put("submitMode", "SEMESTER");
        service.saveClassDoc(teacher, "theme", themeKey, t, versionOf(themeKey), null);
        for (String ws : List.of("2026-09-07", "2026-09-21"))
            service.saveClassDoc(teacher, "week", "wp_" + classId + "_" + ws, week(ws), versionOf("wp_" + classId + "_" + ws), versionOf(sp));
        service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp));
        service.actOnClassDoc(leader, "semester", sp, approve(), versionOf(sp));
        assertThat(service.actOnClassDoc(vicePrincipal, "semester", sp, approve(), versionOf(sp))
                        .path("status")
                        .asString())
                .isEqualTo("APPROVED");

        ClassDocsResponse docs = service.getClassDocs(teacher, schoolYear);
        assertThat(docs.classThemePlans())
                .filteredOn(d -> d.path("id").asString().equals(themeKey))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("status").asString()).isEqualTo("APPROVED");
                    assertThat(d.path("containerKey").asString()).isEqualTo(sp);
                });
        assertThat(docs.weekPlans()).allMatch(w -> w.path("status").asString().equals("APPROVED"));
        assertThat(docs.themeDossiers())
                .filteredOn(d -> d.path("themeId").asString().equals(theme1))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("status").asString()).isEqualTo("DRAFT");
                    assertThat(d.path("submitMode").asString()).isEqualTo("SEMESTER");
                });
        // FR-002a: đã ban hành thì không đổi cách gửi
        t.put("submitMode", "THEME");
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "theme", themeKey, t, versionOf(themeKey), null),
                ApiCode.CLASS_DOC_LOCKED);
    }

    @Test
    @DisplayName("Spec 003 FR-015: người chỉ xem thấy tên nhánh của học kỳ có gói đã ban hành, học kỳ chưa duyệt thì báo")
    void viewerSeesOnlyReleasedSemesterBranchNames() {
        CurrentUser second = user("second", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', false)",
                second.userId(),
                classId);
        approveBranch();
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28", "2026-10-05")),
                versionOf(key),
                null);

        ObjectNode viewed = service.getClassDocs(second, schoolYear).branchPlans().getFirst();
        assertThat(viewed.path("names").has("2026-09-07")).isTrue();
        assertThat(viewed.path("names").has("2026-10-05")).isFalse();
        assertThat(viewed.path("branchUnreleasedSemesters").toString()).isEqualTo("[2]");
        assertThat(service.getClassDocs(teacher, schoolYear).branchPlans().getFirst().path("names").has("2026-10-05"))
                .isTrue();
    }

    @Test
    @DisplayName("Spec 003 FR-013: hồ sơ chủ đề vào 'Gần đến hạn' thì nhắc GVCN đúng một lần")
    void nearDeadlineReminderIsSentOnce() {
        jdbc.update("UPDATE classrooms SET created_at='2026-08-01' WHERE id=?", classId);
        String near = String.valueOf(jdbc.queryForObject(
                "SELECT id FROM edu_themes WHERE team_plan_id=? AND start_date='2026-10-26'", Long.class, plan.id()));
        String themeKey = "tp_" + classId + "_" + near;
        service.saveClassDoc(teacher, "theme", themeKey, theme(near), versionOf(themeKey), null);
        String dossierKey = "ds_" + classId + "_" + near;
        ObjectNode dossier = service.getClassDocs(teacher, schoolYear).themeDossiers().stream()
                .filter(d -> d.path("id").asString().equals(dossierKey))
                .findFirst()
                .orElseThrow();
        assertThat(dossier.path("deadlineState").asString()).isEqualTo("NEAR");
        assertThat(dossier.path("daysLeft").asInt()).isEqualTo(4);

        String link = "/education/class/" + classId + "/themes/" + near;
        assertThat(service.remindNearDeadlines()).isPositive();
        service.remindNearDeadlines();
        assertThat(notificationsOf(teacher, "EDU_DEADLINE_NEAR", link)).isEqualTo(1);
        // Đổi GVCN giữa năm: lần nhắc sau tới GVCN mới (một lần), GVCN cũ không nhận thêm
        jdbc.update("UPDATE staff_assignments SET is_homeroom=false WHERE user_id=? AND classroom_id=?", teacher.userId(), classId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) VALUES (?, 'TEACHER', ?, '2026-09-01', true)",
                otherTeacher.userId(),
                classId);
        entityManager.clear();
        service.remindNearDeadlines();
        assertThat(notificationsOf(otherTeacher, "EDU_DEADLINE_NEAR", link)).isEqualTo(1);
        assertThat(notificationsOf(teacher, "EDU_DEADLINE_NEAR", link)).isEqualTo(1);
    }

    private Long versionOf(String key) {
        var versions = jdbc.queryForList("SELECT version FROM edu_class_docs WHERE doc_key=?", Long.class, key);
        return versions.isEmpty() ? -1L : versions.getFirst();
    }

    private Long rootVersionOf(String kind, ObjectNode content) {
        if (!"week".equals(kind) && !"day".equals(kind)) return null;
        return versionOf("ds_" + content.path("classId").asText() + "_"
                + content.path("themeId").asText());
    }

    private Long rootVersionOfWeek(String key) {
        return jdbc.queryForObject(
                "SELECT r.version FROM edu_class_docs w JOIN edu_class_docs r ON r.classroom_id=w.classroom_id AND r.team_plan_id=w.team_plan_id AND r.theme_id=w.theme_id AND r.kind='dossier' WHERE w.doc_key=?",
                Long.class,
                key);
    }

    /** Chủ đề phụ 1 tuần, nối tiếp sau chủ đề 2 (từ 05/10/2026). */
    private static YearPlanRequest.Theme extraTheme(int no) {
        LocalDate monday = LocalDate.of(2026, 10, 5).plusWeeks(no - 3);
        return new YearPlanRequest.Theme("t-" + no, null, "Chủ đề " + no, "CĐ" + no, monday, monday.plusDays(4), "", "");
    }

}
