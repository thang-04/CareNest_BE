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
@SpringBootTest(properties = "carenest.storage.bucket=test-bucket")
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

    // Kho thật trên bucket giả trong bộ nhớ: học liệu đi qua đủ kiểm định dạng/giới hạn
    @org.springframework.test.context.bean.override.mockito.MockitoBean(
            answers = org.mockito.Answers.CALLS_REAL_METHODS)
    software.amazon.awssdk.services.s3.S3Client s3;

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
    // MT duy nhất của kế hoạch tổ (tích cho chủ đề 1); EDU-80 cần MT được xếp vào ngày dạy
    private long goal1;
    private java.util.function.Predicate<String> pickGoal = ws -> true;

    @BeforeEach
    void setUp() {
        com.carenest.integration.storage.InMemoryS3.install(s3);
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-A', 'Campus A') RETURNING id");
        schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        // Spec 003: HK1 gồm chủ đề 1–2; lớp mở sau ngày bắt đầu nên hạn đã qua không tính quá hạn (C2)
        jdbc.update(
                "UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2026-10-04', "
                        + "semester2_start='2026-10-05', semester2_end='2027-05-31' WHERE id=?",
                schoolYear);
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
                        List.of(
                                new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()),
                                new YearPlanRequest.Row(
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
                        extraTheme(3),
                        extraTheme(4),
                        extraTheme(5),
                        extraTheme(6),
                        extraTheme(7),
                        extraTheme(8),
                        extraTheme(9)),
                List.of(new YearPlanRequest.Break(null, "Nghỉ", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18))),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of(new YearPlanRequest.BranchTemplate(
                        "tmpl-1", null, "t-1", "Dự án mẫu", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11))));

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
        goal1 = jdbc.queryForObject("SELECT goal_id FROM edu_goal_contents WHERE id = ?", Long.class, contentId);
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
                () -> service.previewBranchFile(teacher, classId, annualPlanId, file),
                ApiCode.YEAR_PLAN_CLASS_MISMATCH);
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
                    assertThat(d.path("permissions").path("canEdit").asBoolean())
                            .isTrue();
                });
        assertThat(service.previewBranchFile(
                                teacher, classId, plan.id(), branchFile("Chủ đề,Chủ đề nhánh\nTrường mầm non,Tên\n"))
                        .matches())
                .isNotEmpty();
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

        assertThat(act(leader, "branch", key, approve()).path("status").asString())
                .isEqualTo("REVIEW");
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

        // Spec 004 FR-003: gói học kỳ thiếu tên nhánh / kế hoạch chủ đề / kế hoạch tuần thì không gửi được
        String sp = "sp_" + classId + "_" + plan.id() + "_1";
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp)),
                ApiCode.SEMESTER_PACKAGE_INCOMPLETE);

        ObjectNode saved = service.saveClassDoc(
                teacher,
                "branch",
                key,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(key),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        assertThat(saved.path("names").path("2026-09-28").asString()).isEqualTo("Nhánh 2026-09-28");
        assertThat(act(teacher, "branch", key, submit()).path("status").asString())
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
        assertApiError(() -> act(vicePrincipal, "branch", key, approve()), ApiCode.FORBIDDEN);
        assertApiError(() -> act(leader, "branch", key, new ClassDocActionRequest("reject", " ")), ApiCode.BAD_REQUEST);

        assertThat(act(leader, "branch", key, approve()).path("status").asString())
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
        // Spec 004: hồ sơ chủ đề chỉ chứa giáo án ngày; đang Chờ duyệt thì giáo án ngày khóa
        String dayKey = "dd_" + classId + "_2026-09-07";
        ObjectNode lockedDay = base();
        lockedDay.put("themeId", theme1);
        lockedDay.put("weekStart", "2026-09-07");
        assertApiError(
                () -> service.saveClassDoc(
                        teacher,
                        "day",
                        dayKey,
                        lockedDay,
                        versionOf(dayKey),
                        versionOf("ds_" + classId + "_" + theme1)),
                ApiCode.CONFLICT);
        act(leader, "theme", themeKey, new ClassDocActionRequest("reject", "Sửa mục đích tuần"));

        ClassDocsResponse docs = service.getClassDocs(teacher, schoolYear);
        // Spec 004: gói HK1 chứa kế hoạch tuần của cả hai chủ đề (3 tuần học), đang ở phiên bản điều chỉnh
        assertThat(docs.weekPlans())
                .hasSize(3)
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
        assertThat(act(teacher, "branch", key, submit()).path("status").asString())
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
        assertThat(act(teacher, "branch", key, submit()).path("status").asString())
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

        assertThat(act(leader, "branch", key, approve()).path("status").asString())
                .isEqualTo("REVIEW");
        assertThat(act(vicePrincipal, "branch", key, approve()).path("status").asString())
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
        assertThat(act(leader, "branch", otherKey, approve()).path("status").asString())
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
        // Spec 004: gói học kỳ chứa kế hoạch chủ đề/tuần; mở phiên bản điều chỉnh để các test tiếp tục soạn
        act(teacher, "branch", key, new ClassDocActionRequest("revise", null));
    }

    /** Spec 004 FR-003: soạn đủ phần còn thiếu của gói học kỳ (kế hoạch chủ đề + kế hoạch tuần mọi chủ đề trong kỳ). */
    private void fillSemesterPackage(CurrentUser user, String cls, int no) {
        String packageKey = "sp_" + cls + "_" + plan.id() + "_" + no;
        ObjectNode pkg = service.getClassDocs(user, schoolYear).semesterPackages().stream()
                .filter(p -> packageKey.equals(p.path("id").asString("")))
                .findFirst()
                .orElseThrow();
        String branchKey = "bp_" + cls + "_" + plan.id();
        ObjectNode names = (ObjectNode) objectMapper
                .readTree(jdbc.queryForObject(
                        "SELECT content::text FROM edu_class_docs WHERE doc_key=?", String.class, branchKey))
                .path("names");
        boolean renamed = false;
        for (var part : pkg.path("missing"))
            if ("branch".equals(part.path("kind").asString(""))) {
                names.put(
                        part.path("weekStart").asString(""),
                        "Nhánh " + part.path("weekStart").asString(""));
                renamed = true;
            }
        if (renamed) {
            ObjectNode content = branch(names);
            content.put("classId", cls);
            service.saveClassDoc(user, "branch", branchKey, content, versionOf(branchKey), null);
        }
        for (var part : pkg.path("missing")) {
            String themeId = part.path("themeId").asString("");
            if ("theme".equals(part.path("kind").asString(""))) {
                String themeKey = "tp_" + cls + "_" + themeId;
                ObjectNode theme = theme(themeId);
                theme.put("classId", cls);
                service.saveClassDoc(user, "theme", themeKey, theme, versionOf(themeKey), null);
            } else if ("week".equals(part.path("kind").asString(""))) {
                String ws = part.path("weekStart").asString("");
                ObjectNode week = week(ws);
                week.put("themeId", themeId);
                week.put("classId", cls);
                service.saveClassDoc(
                        user,
                        "week",
                        "wp_" + cls + "_" + ws,
                        week,
                        versionOf("wp_" + cls + "_" + ws),
                        rootVersionOf("week", week));
            }
        }
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
        assertErrorCode(() -> act(vicePrincipal, "branch", key, approve()), ApiCode.NOT_YOUR_REVIEW_STEP);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM edu_class_docs WHERE doc_key=?",
                        String.class,
                        "sp_" + key.substring(3) + "_1"))
                .isEqualTo("REVIEW");
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("EDU-81: học liệu chỉ ở kế hoạch giáo dục ngày, gắn ngày + mục; ZIP đổi đuôi Office bị từ chối")
    void learningResourcesBelongToDayPlanSections() throws Exception {
        approveBranch();
        String dayKey = "dd_" + classId + "_2026-09-07";
        ObjectNode day = base();
        day.put("themeId", theme1);
        day.put("weekStart", "2026-09-07");
        day.putArray("days").addObject().put("date", "2026-09-07");
        service.saveClassDoc(teacher, "day", dayKey, day, -1L, rootVersionOf("day", day));
        var binding = new com.carenest.dto.eduplan.LearningResourceBinding("2026-09-07#hoc", "Hoạt động học");

        var pdf = new org.springframework.mock.web.MockMultipartFile(
                "file", "bai.pdf", "application/pdf", "%PDF-1.7 test".getBytes());
        var saved = learningResources.upload(teacher, dayKey, pdf, binding, java.util.UUID.randomUUID());
        assertThat(saved.active()).isTrue();
        // EDU-82: tệp thành học liệu trong kho, giáo án chỉ giữ dòng gắn
        assertThat(jdbc.queryForObject(
                        "SELECT storage_key FROM edu_resource_items WHERE id=?", String.class, saved.itemId()))
                .startsWith("hoclieu/hoclieu_");

        // Ngày ngoài tuần của kế hoạch ngày, hoặc mục không có ⇒ không gắn được
        assertApiError(
                () -> learningResources.addLink(
                        teacher,
                        dayKey,
                        new com.carenest.dto.eduplan.ResourceLinkRequest(
                                "https://youtu.be/dQw4w9WgXcQ", null, "2026-09-14#hoc", "x", null)),
                ApiCode.ACTIVITY_CHANGED);
        // Dùng lại học liệu ở mục khác của tuần: không tạo học liệu mới; gỡ ở mục đầu thì mục sau vẫn còn (EDU-85)
        Long itemsBefore = jdbc.queryForObject("SELECT count(*) FROM edu_resource_items", Long.class);
        var again = learningResources.attach(
                teacher,
                dayKey,
                new com.carenest.dto.eduplan.ResourceAttachRequest(
                        java.util.List.of(saved.itemId()), "2026-09-08#chieu", "Hoạt động chiều"));
        assertThat(again).singleElement().satisfies(r -> assertThat(r.itemId()).isEqualTo(saved.itemId()));
        assertApiError(
                () -> learningResources.attach(
                        teacher,
                        dayKey,
                        new com.carenest.dto.eduplan.ResourceAttachRequest(
                                java.util.List.of(saved.itemId()), "2026-09-08#chieu", "Hoạt động chiều")),
                ApiCode.RESOURCE_ALREADY_ATTACHED);
        learningResources.detach(teacher, dayKey, saved.id());
        assertThat(learningResources.list(teacher, dayKey))
                .extracting(r -> r.activityKey())
                .containsExactly("2026-09-08#chieu");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_resource_items", Long.class)).isEqualTo(itemsBefore);
        // EDU-82: học liệu gắn sau đứng sau; người sửa đổi được thứ tự, danh sách cũ (thiếu dòng) bị từ chối
        var video = learningResources.addLink(
                teacher,
                dayKey,
                new com.carenest.dto.eduplan.ResourceLinkRequest(
                        "https://youtu.be/dQw4w9WgXcQ", null, "2026-09-08#chieu", "Hoạt động chiều", null));
        assertThat(learningResources.list(teacher, dayKey))
                .extracting(r -> r.id())
                .containsExactly(again.get(0).id(), video.id());
        assertApiError(
                () -> learningResources.reorder(
                        teacher,
                        dayKey,
                        new com.carenest.dto.eduplan.ResourceOrderRequest("2026-09-08#chieu", java.util.List.of(video.id()))),
                ApiCode.RESOURCE_ORDER_STALE);
        learningResources.reorder(
                teacher,
                dayKey,
                new com.carenest.dto.eduplan.ResourceOrderRequest(
                        "2026-09-08#chieu", java.util.List.of(video.id(), again.get(0).id())));
        assertThat(learningResources.list(teacher, dayKey))
                .extracting(r -> r.id())
                .containsExactly(video.id(), again.get(0).id());
        // Kế hoạch chủ đề không còn nhận học liệu
        String themeKey = "tp_" + classId + "_" + theme1;
        service.saveClassDoc(
                teacher, "theme", themeKey, theme(theme1), versionOf(themeKey), rootVersionOf("theme", theme(theme1)));
        assertApiError(() -> learningResources.list(teacher, themeKey), ApiCode.RESOURCE_DAY_ONLY);

        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("random.txt"));
            zip.write("x".getBytes());
            zip.closeEntry();
        }
        var fake = new org.springframework.mock.web.MockMultipartFile(
                "file", "fake.pptx", "application/zip", bytes.toByteArray());
        assertApiError(
                () -> learningResources.upload(teacher, dayKey, fake, binding, java.util.UUID.randomUUID()),
                ApiCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(learningResources.list(teacher, dayKey)).hasSize(2);
    }

    @Test
    @DisplayName("Spec 900 FR-018, SC-002: chuyển học liệu cũ sang kho — số học liệu = số dòng cũ, dòng gắn = dòng đang gắn")
    void legacyLearningResourcesMigrateToLibrary() throws Exception {
        approveBranch();
        String dayKey = "dd_" + classId + "_2026-09-07";
        ObjectNode day = base();
        day.put("themeId", theme1);
        day.put("weekStart", "2026-09-07");
        day.putArray("days").addObject().put("date", "2026-09-07");
        service.saveClassDoc(teacher, "day", dayKey, day, -1L, rootVersionOf("day", day));
        Long docId = jdbc.queryForObject("SELECT id FROM edu_class_docs WHERE doc_key=?", Long.class, dayKey);
        // Hai dòng cũ: một dòng đang gắn mục, một dòng đã gỡ
        java.util.UUID request = java.util.UUID.randomUUID();
        String insert = "INSERT INTO edu_learning_resources (class_doc_id, activity_key, activity_label, original_name,"
                + " content_type, byte_size, storage_key, upload_request_id, uploaded_by) VALUES (?,?,?,?,?,?,?,?,?)";
        jdbc.update(insert, docId, "2026-09-07#hoc", "Hoạt động học", "bai.hat.pdf", "application/pdf", 10L,
                "hoclieu/legacy-1", request, teacher.userId());
        jdbc.update(insert, docId, null, "Hoạt động học", "anh.png", "image/png", 5L, "hoclieu/legacy-2",
                java.util.UUID.randomUUID(), teacher.userId());
        String script = new String(
                getClass().getResourceAsStream("/db/migration/V125__edu_resource_library.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        String data = script.substring(script.indexOf("ALTER TABLE edu_resource_items ADD COLUMN legacy_id"));
        for (String sql : data.split(";\\s*\\n")) if (!sql.isBlank()) jdbc.execute(sql);
        assertThat(jdbc.queryForList(
                        "SELECT title FROM edu_resource_items WHERE storage_key LIKE 'hoclieu/legacy-%' ORDER BY id",
                        String.class))
                .containsExactlyInAnyOrder("bai.hat", "anh");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_resource_attachments a JOIN edu_resource_items i ON i.id = a.item_id"
                                + " WHERE i.storage_key LIKE 'hoclieu/legacy-%'",
                        Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(upload_request_id) FROM edu_resource_items WHERE storage_key LIKE 'hoclieu/legacy-%'",
                        Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                        "SELECT sort_order FROM edu_resource_attachments WHERE class_doc_id=?", Integer.class, docId))
                .isZero();
        assertThat(learningResources.list(teacher, dayKey))
                .singleElement()
                .satisfies(r -> assertThat(r.fileName()).isEqualTo("bai.hat"));
    }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Spec 005 FR-012: bỏ hẳn 'Kế hoạch điều chỉnh và bổ sung' — không còn endpoint, trường, cờ quyền")
    void adjustmentsRemoved() {
        assertThat(handlerMapping.getHandlerMethods().keySet())
                .flatExtracting(info -> List.copyOf(info.getPatternValues()))
                .noneMatch(path -> path.contains("class-docs") && path.endsWith("/adjustments"));
        approveBranch();
        String dayKey = "dd_" + classId + "_2026-09-07";
        ObjectNode day = base();
        day.put("themeId", theme1);
        day.put("weekStart", "2026-09-07");
        day.putArray("days").addObject().put("date", "2026-09-07");
        ObjectNode saved = service.saveClassDoc(teacher, "day", dayKey, day, -1L, rootVersionOf("day", day));
        assertThat(saved.has("adjustments")).isFalse();
        assertThat(saved.path("permissions").has("canAdjust")).isFalse();
        assertThat(saved.path("permissions").has("canEditAdjustments")).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE table_name='edu_class_docs' "
                                + "AND column_name='adjustments'",
                        Long.class))
                .isZero();
    }

    /** Ngày theo mẫu mới: 4 mục đủ nội dung (spec 005). */
    private ObjectNode fullDay(String date) {
        ObjectNode day = objectMapper.createObjectNode();
        day.put("date", date);
        ObjectNode sections = day.putObject("sections");
        for (String key : List.of("hoc", "ngoai_troi", "goc", "chieu")) {
            ObjectNode section = sections.putObject(key);
            section.put("aims", "");
            section.put("prep", "");
            section.putArray("rows")
                    .addObject()
                    .put("id", key + "-1")
                    .put("title", "")
                    .put("teacher", "Cô hướng dẫn " + key)
                    .put("child", "")
                    .put("note", "");
        }
        return day;
    }

    private void saveDays(String weekStart, java.util.function.Consumer<tools.jackson.databind.node.ArrayNode> edit) {
        String dayKey = "dd_" + classId + "_" + weekStart;
        ObjectNode doc = base();
        doc.put("themeId", theme1);
        doc.put("weekStart", weekStart);
        var days = doc.putArray("days");
        for (int i = 0; i < 5; i++)
            days.add(fullDay(LocalDate.parse(weekStart).plusDays(i).toString()));
        edit.accept(days);
        service.saveClassDoc(teacher, "day", dayKey, doc, versionOf(dayKey), rootVersionOf("day", doc));
    }

    @Test
    @DisplayName(
            "Spec 005 FR-007a: gửi hồ sơ chủ đề chặn mục trống của ngày dạy (tên dòng không tính), bỏ qua ngày nghỉ")
    void dossierSubmitRequiresEveryDaySection() {
        approveBranch();
        // Nghỉ lễ một ngày giữa tuần học: ngày đó để trống không bị chặn
        jdbc.update(
                "INSERT INTO edu_breaks (team_plan_id, sort_order, label, start_date, end_date) "
                        + "VALUES (?, 9, 'Nghỉ lễ', '2026-09-23', '2026-09-23')",
                plan.id());
        entityManager.clear();
        // Thứ 4 09/09: mục góc chỉ có tên dòng gợi ý ⇒ vẫn là trống
        saveDays(
                "2026-09-07",
                days -> ((ObjectNode) days.get(2)
                                .path("sections")
                                .path("goc")
                                .path("rows")
                                .get(0))
                        .put("teacher", "")
                        .put("title", "Góc xây dựng"));
        saveDays("2026-09-21", days -> ((ObjectNode) days.get(2)).putObject("sections"));
        String ds = "ds_" + classId + "_" + theme1;
        assertThatThrownBy(() -> service.actOnClassDoc(teacher, "dossier", ds, submit(), versionOf(ds)))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.DAY_PLAN_SECTION_EMPTY);
                    assertThat(ex.getMessage())
                            .contains("Thứ 4 09/09: Hoạt động góc")
                            .doesNotContain("23/09");
                    tools.jackson.databind.JsonNode details =
                            objectMapper.valueToTree(ex.getData()).path("details");
                    assertThat(details).hasSize(1);
                    assertThat(details.get(0).path("date").asString()).isEqualTo("2026-09-09");
                    assertThat(details.get(0).path("section").asString()).isEqualTo("goc");
                });
        saveDays("2026-09-07", days -> {});
        assertThat(service.actOnClassDoc(teacher, "dossier", ds, submit(), versionOf(ds))
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
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
                "copyContent",
                source.getId(),
                update.getId());
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
        if (pickGoal.test(weekStart)) doc.set("picks", picksOf(goal1));
        return doc;
    }

    /** Ô Hoạt động học Thứ 2 xếp một thẻ gắn MT (EDU-78); goal null = hoạt động gõ tay, không MT. */
    private ObjectNode picksOf(Long goal) {
        ObjectNode picks = objectMapper.createObjectNode();
        ObjectNode pick = picks.putArray("hoc.0").addObject().put("text", "Trò chuyện về trường");
        if (goal != null) pick.put("cardId", "c-mt").putArray("goalIds").add(String.valueOf(goal));
        return picks;
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
        service.saveClassDoc(teacher, "theme", themeKey, theme(theme1), versionOf(themeKey), null);

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
        ObjectNode returned =
                act(leader, "branch", key, new ClassDocActionRequest("reject", "Cần điều chỉnh tuần nhánh"));
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
                () -> service.actOnClassDoc(teacher, "semester", "sp_" + key.substring(3) + "_1", submit(), null),
                ApiCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    void weekMutationInvalidatesRootAndRequiresDossierVersion() {
        approveBranch();
        String weekKey = "wp_" + classId + "_2026-09-07";
        // Spec 004: gốc của kế hoạch tuần là gói học kỳ
        long before = rootVersionOf("week", week("2026-09-07"));
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "week", weekKey, week("2026-09-07"), versionOf(weekKey), null),
                ApiCode.REQUIRED_FIELD_MISSING);
        ObjectNode saved = service.saveClassDoc(
                teacher, "week", weekKey, week("2026-09-07").put("prep", "Mới"), versionOf(weekKey), before);
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
        // Spec 004: gói học kỳ chỉ gửi khi đủ kế hoạch chủ đề/tuần ⇒ test điền phần thiếu trước khi gửi
        if (branch && "submit".equals(request.action())) fillSemesterPackage(user, key.split("_")[1], 1);
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
                    days.addObject()
                            .put(
                                    "date",
                                    LocalDate.parse((String) row.get("ws"))
                                            .plusDays(i)
                                            .toString());
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
    @DisplayName(
            "Spec 005 FR-021: quá hạn khóa gửi gói học kỳ; chỉ HT mở hạn (lý do + hạn mới) rồi gửi được, có thông báo")
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
        // FR-003 + spec 005 FR-021: HT thấy gói Nháp quá hạn để mở hạn (không thấy tên nhánh đang soạn); PHT không mở
        // được
        assertThat(service.getClassDocs(principal, schoolYear).semesterPackages())
                .filteredOn(d -> d.path("id").asString().equals(sp))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("deadlineState").asString()).isEqualTo("OVERDUE");
                    assertThat(d.path("canExtend").asBoolean()).isTrue();
                    assertThat(d.path("branchNames").size()).isZero();
                });
        // Tiền 11/11/2026: PHT không soạn/duyệt chỉ thấy khung học kỳ (số học kỳ, chủ đề), không trạng thái, hạn, nút
        assertThat(service.getClassDocs(vicePrincipal, schoolYear).semesterPackages())
                .filteredOn(d -> d.path("id").asString().equals(sp))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("unreleased").asBoolean()).isTrue();
                    assertThat(d.path("semesterNo").asInt()).isEqualTo(1);
                    assertThat(d.path("themeIds").isArray()).isTrue();
                    assertThat(d.has("status")).isFalse();
                    assertThat(d.has("deadlineState")).isFalse();
                    assertThat(d.has("branchNames")).isFalse();
                    assertThat(d.has("history")).isFalse();
                    assertThat(d.path("permissions").size()).isZero();
                });
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "semester", sp, extend("Lý do", "2026-10-28"), versionOf(sp)),
                ApiCode.NOT_YOUR_REVIEW_STEP);
        assertErrorCode(
                () -> service.actOnClassDoc(
                        vicePrincipal, "semester", sp, extend("Lý do", "2026-10-28"), versionOf(sp)),
                ApiCode.NOT_YOUR_REVIEW_STEP);
        assertErrorCode(
                () -> service.actOnClassDoc(principal, "semester", sp, extend(" ", "2026-10-28"), versionOf(sp)),
                ApiCode.REQUIRED_FIELD_MISSING);
        assertErrorCode(
                () -> service.actOnClassDoc(principal, "semester", sp, extend("Lý do", "2026-10-22"), versionOf(sp)),
                ApiCode.INVALID_VALUE);
        ObjectNode extended = service.actOnClassDoc(
                principal, "semester", sp, extend("Giáo viên nghỉ ốm", "2026-10-28"), versionOf(sp));
        assertThat(extended.path("history")
                        .get(extended.path("history").size() - 1)
                        .path("role")
                        .asString())
                .isEqualTo("Hiệu trưởng");
        assertThat(extended.path("extendedTo").asString()).isEqualTo("2026-10-28");
        assertThat(extended.path("deadlineState").asString()).isEqualTo("NEAR");
        assertThat(notificationsOf(teacher, "EDU_DEADLINE_EXTENDED", link)).isEqualTo(1);

        // Spec 004: sau mở khóa soạn đủ kế hoạch chủ đề/tuần của kỳ rồi gửi
        fillSemesterPackage(teacher, String.valueOf(classId), 1);
        ObjectNode sent = service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp));
        assertThat(sent.path("status").asString()).isEqualTo("REVIEW");
        assertThat(notificationsOf(leader, "EDU_SUBMITTED", link)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Spec 004: kế hoạch chủ đề + tuần luôn trong gói học kỳ; hồ sơ chủ đề chỉ giáo án ngày; sửa bản đã ban hành phải tạo phiên bản")
    void semesterPackageAlwaysHoldsThemeAndWeekPlans() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        String sp = "sp_" + classId + "_" + plan.id() + "_1";
        String themeKey = "tp_" + classId + "_" + theme1;
        ObjectNode t = theme(theme1);
        t.put("submitMode", "THEME"); // Web cũ còn gửi: bỏ qua
        service.saveClassDoc(teacher, "theme", themeKey, t, versionOf(themeKey), null);
        fillSemesterPackage(teacher, String.valueOf(classId), 1);
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
                    assertThat(d.has("submitMode")).isFalse();
                });
        assertThat(docs.weekPlans()).allMatch(w -> w.path("status").asString().equals("APPROVED"));
        assertThat(docs.semesterPackages())
                .filteredOn(d -> d.path("id").asString().equals(sp))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("missing").size()).isZero();
                    assertThat(d.path("canRevise").asBoolean()).isTrue();
                });
        // Q2: bản đã ban hành không sửa thẳng; tạo phiên bản điều chỉnh rồi mới sửa
        assertErrorCode(
                () -> service.saveClassDoc(
                        teacher, "theme", themeKey, theme(theme1).put("envIn", "Đổi"), versionOf(themeKey), null),
                ApiCode.CLASS_DOC_LOCKED);
        ObjectNode revised = service.actOnClassDoc(
                teacher, "semester", sp, new ClassDocActionRequest("revise", null), versionOf(sp));
        assertThat(revised.path("status").asString()).isEqualTo("DRAFT");
        assertThat(revised.path("releases").size()).isEqualTo(1);
        assertErrorCode(
                () -> service.actOnClassDoc(
                        teacher, "semester", sp, new ClassDocActionRequest("revise", null), versionOf(sp)),
                ApiCode.CLASS_DOC_LOCKED);
        assertThat(service.saveClassDoc(
                                teacher,
                                "theme",
                                themeKey,
                                theme(theme1).put("envIn", "Đổi"),
                                versionOf(themeKey),
                                null)
                        .path("envIn")
                        .asString())
                .isEqualTo("Đổi");
    }

    @Test
    @DisplayName(
            "Spec 004 Q1, FR-010a: giáo án ngày soạn/lưu được khi gói học kỳ chưa ban hành; hồ sơ chủ đề chưa gửi được")
    void dayPlanSavesBeforeSemesterRelease() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        fillSemesterPackage(teacher, String.valueOf(classId), 1);
        String dayKey = "dd_" + classId + "_2026-09-07";
        ObjectNode day = base();
        day.put("themeId", theme1);
        day.put("weekStart", "2026-09-07");
        var days = day.putArray("days");
        for (int i = 0; i < 5; i++)
            days.addObject().put("date", LocalDate.of(2026, 9, 7).plusDays(i).toString());
        ObjectNode saved =
                service.saveClassDoc(teacher, "day", dayKey, day, -1L, versionOf("ds_" + classId + "_" + theme1));
        assertThat(saved.path("status").asString()).isEqualTo("DRAFT");
        assertThat(saved.path("containerKey").asString()).isEqualTo("ds_" + classId + "_" + theme1);
        String ds = "ds_" + classId + "_" + theme1;
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "dossier", ds, submit(), versionOf(ds)),
                ApiCode.SEMESTER_PACKAGE_NOT_RELEASED);
    }

    @Test
    @DisplayName(
            "Spec 004 Q2, Q5 + spec 005 FR-021: gói đã ban hành qua hạn thì khóa tạo phiên bản/sửa, không mở khóa nữa")
    void releasedPackageLocksAfterSemesterStartUntilUnlocked() {
        jdbc.update("UPDATE classrooms SET created_at='2026-08-01' WHERE id=?", classId);
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        String sp = "sp_" + classId + "_" + plan.id() + "_1";
        service.actOnClassDoc(principal, "semester", sp, extend("Lớp mới nhận", "2026-10-28"), versionOf(sp));
        fillSemesterPackage(teacher, String.valueOf(classId), 1);
        service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp));
        service.actOnClassDoc(leader, "semester", sp, approve(), versionOf(sp));
        service.actOnClassDoc(vicePrincipal, "semester", sp, approve(), versionOf(sp));
        // Hạn mở khóa đã qua (giả lập ngày hôm qua) ⇒ gói đã ban hành bị khóa theo thời gian
        jdbc.update(
                "UPDATE edu_class_docs SET history = jsonb_set(history, '{0,deadline}', '\"2026-10-21\"') WHERE doc_key=?",
                sp);
        entityManager.clear();
        assertThat(service.getClassDocs(teacher, schoolYear).semesterPackages())
                .filteredOn(d -> d.path("id").asString().equals(sp))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.path("timeLocked").asBoolean()).isTrue();
                    assertThat(d.path("canRevise").asBoolean()).isFalse();
                });
        assertErrorCode(
                () -> service.actOnClassDoc(
                        teacher, "semester", sp, new ClassDocActionRequest("revise", null), versionOf(sp)),
                ApiCode.CLASS_DOC_TIME_LOCKED);
        assertThat(service.getClassDocs(principal, schoolYear).semesterPackages())
                .filteredOn(d -> d.path("id").asString().equals(sp))
                .singleElement()
                .satisfies(d -> assertThat(d.path("canExtend").asBoolean()).isFalse());
        assertThatThrownBy(() -> service.actOnClassDoc(
                        principal, "semester", sp, extend("Sửa sai sót", "2026-10-30"), versionOf(sp)))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_VALUE);
                    assertThat(ex.getMessage()).contains("đã ban hành");
                });
    }

    @Test
    @DisplayName(
            "Spec 004 Q7 + spec 005 FR-017a: phiên bản mới đổi bảng góc ⇒ giáo án ngày tuần đó về Nháp; chỉ đổi chuẩn bị thì giữ")
    void newPackageVersionResetsOnlyChangedWeekDays() {
        approveBranch(); // gói HK1 đã ban hành một lần, đang ở phiên bản điều chỉnh
        String sp = "sp_" + classId + "_" + plan.id() + "_1";
        String key = "bp_" + classId + "_" + plan.id();
        act(teacher, "branch", key, submit());
        act(leader, "branch", key, approve());
        act(vicePrincipal, "branch", key, approve());
        String themeKey = "tp_" + classId + "_" + theme1;
        act(teacher, "theme", themeKey, submit());
        act(leader, "theme", themeKey, approve());
        act(vicePrincipal, "theme", themeKey, approve());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_class_docs WHERE kind='day' AND status='APPROVED' AND classroom_id=?",
                        Long.class,
                        classId))
                .isEqualTo(2L);
        service.actOnClassDoc(teacher, "semester", sp, new ClassDocActionRequest("revise", null), versionOf(sp));
        String changed = "wp_" + classId + "_2026-09-07";
        ObjectNode withCorners = week("2026-09-07");
        withCorners.putArray("corners").addObject().put("id", "c1").put("name", "Góc xây dựng");
        service.saveClassDoc(
                teacher, "week", changed, withCorners, versionOf(changed), rootVersionOf("week", week("2026-09-07")));
        String prepOnly = "wp_" + classId + "_2026-09-21";
        ObjectNode prep = week("2026-09-21");
        prep.putObject("prep").put("teacher", "Đổi chuẩn bị").put("child", "").put("parent", "");
        service.saveClassDoc(
                teacher, "week", prepOnly, prep, versionOf(prepOnly), rootVersionOf("week", week("2026-09-21")));
        service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp));
        service.actOnClassDoc(leader, "semester", sp, approve(), versionOf(sp));
        service.actOnClassDoc(vicePrincipal, "semester", sp, approve(), versionOf(sp));
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM edu_class_docs WHERE doc_key=?",
                        String.class,
                        "dd_" + classId + "_2026-09-07"))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM edu_class_docs WHERE doc_key=?",
                        String.class,
                        "dd_" + classId + "_2026-09-21"))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM edu_class_docs WHERE doc_key=?",
                        String.class,
                        "ds_" + classId + "_" + theme1))
                .isEqualTo("DRAFT");
        assertThat(notificationsOf(teacher, "EDU_RETURNED", "/education/class/" + classId + "/themes/" + theme1))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Dự kiến chủ đề nhánh: người chỉ xem trong phạm vi lớp thấy đủ tên nhánh, kể cả học kỳ chưa ban hành")
    void viewerSeesAllBranchNamesOfClassInScope() {
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

        ObjectNode viewed =
                service.getClassDocs(second, schoolYear).branchPlans().getFirst();
        assertThat(viewed.path("names").has("2026-09-07")).isTrue();
        assertThat(viewed.path("names").has("2026-10-05")).isTrue();
        assertThat(viewed.has("branchUnreleasedSemesters")).isFalse();
        assertThat(viewed.path("permissions").path("canEdit").asBoolean()).isFalse();
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
        jdbc.update(
                "UPDATE staff_assignments SET is_homeroom=false WHERE user_id=? AND classroom_id=?",
                teacher.userId(),
                classId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) VALUES (?, 'TEACHER', ?, '2026-09-01', true)",
                otherTeacher.userId(),
                classId);
        entityManager.clear();
        service.remindNearDeadlines();
        assertThat(notificationsOf(otherTeacher, "EDU_DEADLINE_NEAR", link)).isEqualTo(1);
        assertThat(notificationsOf(teacher, "EDU_DEADLINE_NEAR", link)).isEqualTo(1);
    }

    // ===== Spec 006: thẻ hoạt động (EDU-78), kế hoạch tuần theo hồ sơ chủ đề (EDU-79), kiểm đủ MT (EDU-80) =====

    private ObjectNode card(String id, String slot, String name, Long... goals) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("id", id).put("slot", slot).put("name", name);
        var ids = card.putArray("goalIds");
        for (Long g : goals) ids.add(String.valueOf(g));
        return card;
    }

    private ObjectNode dayWith(String weekStart, tools.jackson.databind.JsonNode week) {
        ObjectNode day = base();
        day.put("themeId", theme1);
        day.put("weekStart", weekStart);
        var days = day.putArray("days");
        for (int i = 0; i < 5; i++)
            days.add(fullDay(LocalDate.parse(weekStart).plusDays(i).toString()));
        if (week != null) day.set("week", week);
        return day;
    }

    private ObjectNode saveDay(String weekStart, tools.jackson.databind.JsonNode week) {
        String dayKey = "dd_" + classId + "_" + weekStart;
        return service.saveClassDoc(
                teacher,
                "day",
                dayKey,
                dayWith(weekStart, week),
                versionOf(dayKey),
                versionOf("ds_" + classId + "_" + theme1));
    }

    @Test
    @DisplayName(
            "EDU-78 FR-005, FR-007: thẻ hợp lệ lưu được; thiếu tên, MT ngoài chủ đề, thẻ mới không loại giờ bị server từ chối")
    void themeCardsAreValidatedOnServer() {
        String themeKey = "tp_" + classId + "_" + theme1;
        ObjectNode doc = theme(theme1);
        doc.putArray("activities")
                .add(card("c-1", "hoc", "Trò chuyện về ATGT", goal1))
                .add(card(goal1 + "#0", "", "Bò chui", goal1).put("legacy", true));
        ObjectNode saved = service.saveClassDoc(teacher, "theme", themeKey, doc, versionOf(themeKey), null);
        assertThat(saved.path("activities")).hasSize(2);
        assertThat(saved.path("activities").get(0).path("goalIds").get(0).asString())
                .isEqualTo(String.valueOf(goal1));

        ObjectNode noName = theme(theme1);
        noName.putArray("activities").add(card("c-1", "hoc", " ", goal1));
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "theme", themeKey, noName, versionOf(themeKey), null),
                ApiCode.INVALID_VALUE);
        ObjectNode foreignGoal = theme(theme1);
        foreignGoal.putArray("activities").add(card("c-1", "hoc", "X", 999_999L));
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "theme", themeKey, foreignGoal, versionOf(themeKey), null),
                ApiCode.INVALID_VALUE);
        ObjectNode newBlankSlot = theme(theme1);
        newBlankSlot.putArray("activities").add(card("c-2", "", "Không loại giờ", goal1));
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "theme", themeKey, newBlankSlot, versionOf(themeKey), null),
                ApiCode.INVALID_VALUE);
        // FR-007: giáo viên không chủ nhiệm lớp không lưu được thẻ
        assertApiError(
                () -> service.saveClassDoc(otherTeacher, "theme", themeKey, doc, versionOf(themeKey), null),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("EDU-78 R2: kế hoạch chủ đề cũ (ô chữ) lưu lại thành thẻ id 'goalId#i', bỏ ô chữ")
    void legacyActivitiesBecomeCards() {
        String themeKey = "tp_" + classId + "_" + theme1;
        ObjectNode doc = theme(theme1);
        ((ObjectNode) doc.path("rows"))
                .putObject(String.valueOf(goal1))
                .put("activities", "Hoạt động học:\n+ Đi bước lùi");
        ObjectNode saved = service.saveClassDoc(teacher, "theme", themeKey, doc, versionOf(themeKey), null);
        assertThat(saved.path("activities").get(0).path("id").asString()).isEqualTo(goal1 + "#0");
        assertThat(saved.path("activities").get(0).path("slot").asString()).isEqualTo("hoc");
        assertThat(saved.path("rows").path(String.valueOf(goal1)).has("activities"))
                .isFalse();
    }

    @Test
    @DisplayName(
            "EDU-79 FR-015, FR-018: day.week lưu khi gói học kỳ đã ban hành; khóa lạ bị từ chối; hồ sơ ban hành ⇒ bản hiệu lực")
    void dossierWeekSavesAfterSemesterReleaseAndBecomesEffective() {
        String themeKey = "tp_" + classId + "_" + theme1;
        service.saveClassDoc(teacher, "theme", themeKey, theme(theme1), versionOf(themeKey), null);
        ObjectNode edited = objectMapper.createObjectNode();
        edited.set("picks", picksOf(goal1));
        edited.putObject("base");
        String dayKey = "dd_" + classId + "_2026-09-07";
        String ds = "ds_" + classId + "_" + theme1;
        // EDU-14 (13/11/2026): tuần chưa có tên nhánh thì chưa tạo được kế hoạch tuần/ngày
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "day", dayKey, dayWith("2026-09-07", edited), -1L, versionOf(ds)),
                ApiCode.BRANCH_WEEKS_UNNAMED);
        String branchKey = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher,
                "branch",
                branchKey,
                branch(names("2026-09-07", "2026-09-21", "2026-09-28")),
                versionOf(branchKey),
                rootVersionOf("branch", branch(names("2026-09-07", "2026-09-21", "2026-09-28"))));
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "day", dayKey, dayWith("2026-09-07", edited), -1L, versionOf(ds)),
                ApiCode.SEMESTER_PACKAGE_NOT_RELEASED);

        approveBranch();
        ObjectNode saved = saveDay("2026-09-07", edited);
        assertThat(saved.path("week").path("picks").has("hoc.0")).isTrue();
        assertThat(saved.path("effectiveWeek").asString()).isEqualTo("semester");
        ObjectNode unknown = dayWith("2026-09-07", edited);
        unknown.put("foo", 1);
        assertErrorCode(
                () -> service.saveClassDoc(teacher, "day", dayKey, unknown, versionOf(dayKey), versionOf(ds)),
                ApiCode.INVALID_VALUE);

        act(teacher, "theme", themeKey, submit());
        act(leader, "theme", themeKey, approve());
        act(vicePrincipal, "theme", themeKey, approve());
        assertThat(service.getClassDocs(teacher, schoolYear).dayPlans())
                .filteredOn(d -> d.path("id").asString().equals(dayKey))
                .singleElement()
                .satisfies(d -> assertThat(d.path("effectiveWeek").asString()).isEqualTo("dossier"));
    }

    @Test
    @DisplayName("EDU-79 + EDU-77: gói học kỳ ra bản mới đổi tuần ⇒ ngày về Nháp, bản tuần sửa theo hồ sơ vẫn giữ")
    void newSemesterVersionKeepsDossierWeek() {
        approveBranch();
        String themeKey = "tp_" + classId + "_" + theme1;
        String sp = "sp_" + classId + "_" + plan.id() + "_1";
        String key = "bp_" + classId + "_" + plan.id();
        act(teacher, "branch", key, submit());
        act(leader, "branch", key, approve());
        act(vicePrincipal, "branch", key, approve());
        ObjectNode edited = objectMapper.createObjectNode();
        edited.set("picks", picksOf(goal1));
        saveDay("2026-09-07", edited);
        act(teacher, "theme", themeKey, submit());
        act(leader, "theme", themeKey, approve());
        act(vicePrincipal, "theme", themeKey, approve());
        service.actOnClassDoc(teacher, "semester", sp, new ClassDocActionRequest("revise", null), versionOf(sp));
        String changed = "wp_" + classId + "_2026-09-07";
        ObjectNode withCorners = week("2026-09-07");
        withCorners.putArray("corners").addObject().put("id", "c1").put("name", "Góc xây dựng");
        service.saveClassDoc(
                teacher, "week", changed, withCorners, versionOf(changed), rootVersionOf("week", withCorners));
        service.actOnClassDoc(teacher, "semester", sp, submit(), versionOf(sp));
        service.actOnClassDoc(leader, "semester", sp, approve(), versionOf(sp));
        service.actOnClassDoc(vicePrincipal, "semester", sp, approve(), versionOf(sp));
        String dayKey = "dd_" + classId + "_2026-09-07";
        assertThat(jdbc.queryForObject("SELECT status FROM edu_class_docs WHERE doc_key=?", String.class, dayKey))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                        "SELECT content->'week' IS NOT NULL FROM edu_class_docs WHERE doc_key=?",
                        Boolean.class,
                        dayKey))
                .isTrue();
    }

    @Test
    @DisplayName("EDU-80 FR-030: hồ sơ chủ đề thiếu MT ⇒ 7396 kèm details; 7390 kiểm trước; gõ tay không tính MT")
    void dossierSubmitRequiresThemeGoalsTaught() {
        approveBranch();
        String ds = "ds_" + classId + "_" + theme1;
        ObjectNode typedOnly = objectMapper.createObjectNode();
        typedOnly.set("picks", picksOf(null));
        // Mục trống (7390) báo trước thiếu MT
        ObjectNode emptySection = dayWith("2026-09-07", typedOnly);
        ((ObjectNode) emptySection.path("days").get(0)).putObject("sections");
        String dayKey = "dd_" + classId + "_2026-09-07";
        service.saveClassDoc(teacher, "day", dayKey, emptySection, versionOf(dayKey), versionOf(ds));
        saveDay("2026-09-21", typedOnly);
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "dossier", ds, submit(), versionOf(ds)),
                ApiCode.DAY_PLAN_SECTION_EMPTY);
        // Bản tuần sửa trong hồ sơ bỏ mất MT đã có ở bản theo kỳ ⇒ chặn
        saveDay("2026-09-07", typedOnly);
        assertThatThrownBy(() -> service.actOnClassDoc(teacher, "dossier", ds, submit(), versionOf(ds)))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.THEME_GOALS_NOT_COVERED);
                    tools.jackson.databind.JsonNode details =
                            objectMapper.valueToTree(ex.getData()).path("details");
                    assertThat(details).hasSize(1);
                    assertThat(details.get(0).path("goalId").asLong()).isEqualTo(goal1);
                    assertThat(ex.getMessage())
                            .contains("MT" + details.get(0).path("goalNo").asInt());
                });
        // Bỏ bản tuần sửa ⇒ dùng bản theo kỳ (có MT) ⇒ gửi được
        saveDay("2026-09-07", null);
        saveDay("2026-09-21", null);
        assertThat(service.actOnClassDoc(teacher, "dossier", ds, submit(), versionOf(ds))
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("EDU-80 FR-031: gói HK1 thiếu MT vẫn gửi được; gói HK2 (cuối năm) thiếu MT năm ⇒ 7397")
    void yearGoalsCheckedOnlyAtLastSemester() {
        pickGoal = ws -> false;
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        fillSemesterPackage(teacher, String.valueOf(classId), 1);
        fillSemesterPackage(teacher, String.valueOf(classId), 2);
        String sp1 = "sp_" + classId + "_" + plan.id() + "_1";
        assertThat(service.actOnClassDoc(teacher, "semester", sp1, submit(), versionOf(sp1))
                        .path("status")
                        .asString())
                .isEqualTo("REVIEW");
        String sp2 = "sp_" + classId + "_" + plan.id() + "_2";
        assertThatThrownBy(() -> service.actOnClassDoc(teacher, "semester", sp2, submit(), versionOf(sp2)))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.YEAR_GOALS_NOT_COVERED);
                    assertThat(objectMapper
                                    .valueToTree(ex.getData())
                                    .path("details")
                                    .get(0)
                                    .path("goalId")
                                    .asLong())
                            .isEqualTo(goal1);
                });
    }

    @Test
    @DisplayName("EDU-80 FR-031: hồ sơ của chủ đề cuối năm, bản tuần sửa bỏ MT duy nhất của năm ⇒ 7397")
    void lastThemeDossierChecksYearGoals() {
        String lastWeek = "2026-11-16";
        pickGoal = lastWeek::equals;
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(
                teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")), versionOf(key), null);
        fillSemesterPackage(teacher, String.valueOf(classId), 2);
        String sp2 = "sp_" + classId + "_" + plan.id() + "_2";
        service.actOnClassDoc(teacher, "semester", sp2, submit(), versionOf(sp2));
        service.actOnClassDoc(leader, "semester", sp2, approve(), versionOf(sp2));
        service.actOnClassDoc(vicePrincipal, "semester", sp2, approve(), versionOf(sp2));
        String lastTheme = String.valueOf(jdbc.queryForObject(
                "SELECT id FROM edu_themes WHERE team_plan_id=? ORDER BY start_date DESC LIMIT 1",
                Long.class,
                plan.id()));
        ObjectNode edited = objectMapper.createObjectNode();
        edited.set("picks", picksOf(null));
        ObjectNode day = dayWith(lastWeek, edited);
        day.put("themeId", lastTheme);
        String dayKey = "dd_" + classId + "_" + lastWeek;
        String ds = "ds_" + classId + "_" + lastTheme;
        service.saveClassDoc(teacher, "day", dayKey, day, versionOf(dayKey), versionOf(ds));
        assertErrorCode(
                () -> service.actOnClassDoc(teacher, "dossier", ds, submit(), versionOf(ds)),
                ApiCode.YEAR_GOALS_NOT_COVERED);
    }

    private Long versionOf(String key) {
        var versions = jdbc.queryForList("SELECT version FROM edu_class_docs WHERE doc_key=?", Long.class, key);
        return versions.isEmpty() ? -1L : versions.getFirst();
    }

    private Long rootVersionOf(String kind, ObjectNode content) {
        if (!"week".equals(kind) && !"day".equals(kind)) return null;
        if ("week".equals(kind)) {
            // Spec 004: kế hoạch tuần thuộc gói học kỳ của chủ đề (HK1 tới 04/10/2026)
            LocalDate start = jdbc.queryForObject(
                    "SELECT start_date FROM edu_themes WHERE id=?",
                    LocalDate.class,
                    Long.valueOf(content.path("themeId").asText()));
            int no = start.isAfter(LocalDate.of(2026, 10, 4)) ? 2 : 1;
            return versionOf("sp_" + content.path("classId").asText() + "_" + plan.id() + "_" + no);
        }
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
        return new YearPlanRequest.Theme(
                "t-" + no, null, "Chủ đề " + no, "CĐ" + no, monday, monday.plusDays(4), "", "");
    }
}
