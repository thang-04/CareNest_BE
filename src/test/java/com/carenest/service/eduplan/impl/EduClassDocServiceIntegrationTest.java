package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.exception.GlobalException;
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
    private CurrentUser vicePrincipal;
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

        // Chủ đề 1 có tuần nghỉ ở giữa (2 tuần học), chủ đề 2 bắt đầu Chủ nhật (1 tuần học)
        java.util.UUID editSession = java.util.UUID.randomUUID();
        YearPlanDetailResponse draft = yearPlanService.createYearPlan(
                vicePrincipal,
                new YearPlanRequest(
                        schoolYear,
                        ageGroup3,
                        "Kế hoạch MGB",
                        "",
                        List.of(new YearPlanRequest.Domain(
                                domain,
                                List.of(new YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "Trẻ thực hiện đủ động tác",
                                        List.of(new YearPlanRequest.Content(null, "Hô hấp", List.of("t-1"))))))),
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
                                        LocalDate.of(2026, 9, 27),
                                        LocalDate.of(2026, 10, 2),
                                        "",
                                        "")),
                        List.of(new YearPlanRequest.Break(
                                null, "Nghỉ", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)))),
                new com.carenest.dto.eduplan.YearPlanEditRequest(editSession, 0L, java.util.UUID.randomUUID()));
        plan = yearPlanService.issueYearPlan(
                vicePrincipal,
                draft.id(),
                null,
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        editSession, draft.revision(), java.util.UUID.randomUUID()));
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
    @DisplayName("EDU-14, EDU-18: chủ đề nhánh đủ tên mới gửi; Tổ trưởng rồi Phó HT duyệt; trả lại cần lý do")
    void branchPlanFlow() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(teacher, "branch", key, branch(names("2026-09-07", "2026-09-21")));

        assertApiError(() -> service.actOnClassDoc(teacher, "branch", key, submit()), ApiCode.CONFLICT);

        ObjectNode saved =
                service.saveClassDoc(teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")));
        assertThat(saved.path("names").path("2026-09-28").asString()).isEqualTo("Nhánh 2026-09-28");
        assertThat(service.actOnClassDoc(teacher, "branch", key, submit())
                        .path("status")
                        .asString())
                .isEqualTo("PENDING_TL");
        // Đã gửi: không sửa được, người không đến lượt không duyệt được
        assertApiError(() -> service.saveClassDoc(teacher, "branch", key, branch(names())), ApiCode.CONFLICT);
        assertApiError(() -> service.actOnClassDoc(vicePrincipal, "branch", key, approve()), ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.actOnClassDoc(leader, "branch", key, new ClassDocActionRequest("reject", " ")),
                ApiCode.BAD_REQUEST);

        assertThat(service.actOnClassDoc(leader, "branch", key, approve())
                        .path("status")
                        .asString())
                .isEqualTo("PENDING_VP");
        ObjectNode approved = service.actOnClassDoc(vicePrincipal, "branch", key, approve());
        assertThat(approved.path("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.path("approvals")).hasSize(2);
        assertThat(approved.path("history")).hasSize(3);
    }

    @Test
    @DisplayName("EDU-13, EDU-17: kế hoạch chủ đề sau chủ đề nhánh duyệt; hồ sơ gửi một lần khi đủ giáo án các nhánh")
    void themeDossierFlow() {
        String themeKey = "tp_" + classId + "_" + theme1;
        assertApiError(() -> service.saveClassDoc(teacher, "theme", themeKey, theme(theme1)), ApiCode.CONFLICT);

        approveBranch();
        service.saveClassDoc(teacher, "theme", themeKey, theme(theme1));
        // Tuần nghỉ không phải tuần nhánh; giáo viên lớp khác không soạn được
        assertApiError(
                () -> service.saveClassDoc(teacher, "week", "wp_" + classId + "_2026-09-14", week("2026-09-14")),
                ApiCode.BAD_REQUEST);
        assertApiError(() -> service.saveClassDoc(otherTeacher, "theme", themeKey, theme(theme1)), ApiCode.FORBIDDEN);

        service.saveClassDoc(teacher, "week", "wp_" + classId + "_2026-09-07", week("2026-09-07"));
        assertApiError(() -> service.actOnClassDoc(teacher, "theme", themeKey, submit()), ApiCode.CONFLICT);
        assertApiError(
                () -> service.actOnClassDoc(teacher, "week", "wp_" + classId + "_2026-09-07", submit()),
                ApiCode.BAD_REQUEST);

        service.saveClassDoc(teacher, "week", "wp_" + classId + "_2026-09-21", week("2026-09-21"));
        service.actOnClassDoc(teacher, "theme", themeKey, submit());
        // Giáo án tuần đi cùng trạng thái hồ sơ, không sửa được khi đã gửi
        assertApiError(
                () -> service.saveClassDoc(teacher, "week", "wp_" + classId + "_2026-09-07", week("2026-09-07")),
                ApiCode.CONFLICT);
        service.actOnClassDoc(leader, "theme", themeKey, new ClassDocActionRequest("reject", "Sửa mục đích tuần"));

        ClassDocsResponse docs = service.getClassDocs(teacher, schoolYear);
        assertThat(docs.weekPlans())
                .hasSize(2)
                .allMatch(w -> w.path("status").asString().equals("REJECTED"));
        assertThat(docs.weekPlans().getFirst().path("days")).hasSize(5);
        assertThat(docs.classThemePlans()
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
        assertApiError(() -> service.saveClassDoc(teacher, "branch", "bp_1_2", branch(names())), ApiCode.BAD_REQUEST);
        assertApiError(() -> service.saveClassDoc(teacher, "lesson", "x", branch(names())), ApiCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("Lịch tuần: bỏ tuần nghỉ, chủ đề bắt đầu Chủ nhật tính từ Thứ Hai kế tiếp")
    void teachingWeeksSkipBreaks() {
        assertThat(service.getClassDocs(teacher, schoolYear).branchPlans()).isEmpty();
        ObjectNode doc = branch(names("2026-09-07", "2026-09-21", "2026-09-28"));
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(teacher, "branch", key, doc);
        jdbc.update("DELETE FROM staff_assignments WHERE staff_role = 'AGE_GROUP_TEAM_LEADER'");
        // Không còn tổ trưởng: gửi thẳng Phó HT
        assertThat(service.actOnClassDoc(teacher, "branch", key, submit())
                        .path("status")
                        .asString())
                .isEqualTo("PENDING_VP");
    }

    private void approveBranch() {
        String key = "bp_" + classId + "_" + plan.id();
        service.saveClassDoc(teacher, "branch", key, branch(names("2026-09-07", "2026-09-21", "2026-09-28")));
        service.actOnClassDoc(teacher, "branch", key, submit());
        service.actOnClassDoc(leader, "branch", key, approve());
        service.actOnClassDoc(vicePrincipal, "branch", key, approve());
    }

    @Test
    @DisplayName("EDU-12/18: học liệu private, retry một lần, stale binding cần gắn lại và bản khóa không sửa")
    void learningResourcesRespectScopeAndActivityIdentity() throws Exception {
        approveBranch();
        String key = "tp_" + classId + "_" + theme1;
        String goal = String.valueOf(jdbc.queryForObject(
                "SELECT id FROM edu_goal_rows WHERE year_plan_id=? AND kind='goal'", Long.class, plan.id()));
        ObjectNode doc = theme(theme1);
        ((ObjectNode) doc.path("rows")).putObject(goal).put("activities", "- HĐ học:\n+ Vận động A\n+ Vận động B");
        service.saveClassDoc(teacher, "theme", key, doc);
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
        ((ObjectNode) doc.path("rows").path(goal)).put("activities", "HĐ học: Vận động mới");
        service.saveClassDoc(teacher, "theme", key, doc);
        assertThat(learningResources.list(teacher, key).getFirst().active()).isFalse();
        assertThat(learningResources.list(vicePrincipal, key)).isEmpty();
        assertApiError(() -> learningResources.content(vicePrincipal, key, first.id()), ApiCode.NOT_FOUND);
        assertApiError(
                () -> learningResources.upload(teacher, key, file, binding, java.util.UUID.randomUUID()),
                ApiCode.CONFLICT);
        var rebound = new com.carenest.dto.eduplan.LearningResourceBinding(
                goal + "#0", com.carenest.utils.LearningResourcePolicy.signature("HĐ học: Vận động mới"), "Mới");
        assertThat(learningResources.bind(teacher, key, first.id(), rebound).active())
                .isTrue();
        learningResources.detach(teacher, key, first.id());
        assertThat(learningResources.list(teacher, key).getFirst().activityKey())
                .isNull();
        assertThat(learningResources
                        .content(teacher, key, first.id())
                        .resource()
                        .exists())
                .isTrue();
        jdbc.update("UPDATE edu_class_docs SET status='PENDING_VP' WHERE doc_key=?", key);
        // Native update cần xóa persistence cache trước khi kiểm lại status.
        entityManager.clear();
        assertApiError(() -> learningResources.detach(teacher, key, first.id()), ApiCode.CONFLICT);
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("EDU-12: ZIP đổi đuôi Office không phải học liệu hợp lệ")
    void learningResourcesRejectRenamedZipAndInvalidGoal() throws Exception {
        approveBranch();
        String key = "tp_" + classId + "_" + theme1;
        String goal = String.valueOf(jdbc.queryForObject(
                "SELECT id FROM edu_goal_rows WHERE year_plan_id=? AND kind='goal'", Long.class, plan.id()));
        ObjectNode doc = theme(theme1);
        ((ObjectNode) doc.path("rows")).putObject(goal).put("activities", "A");
        service.saveClassDoc(teacher, "theme", key, doc);
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
        service.saveClassDoc(teacher, "theme", "tp_" + classId + "_" + theme1, theme(theme1));
        service.saveClassDoc(teacher, "week", key, week("2026-09-07"));
        jdbc.update("UPDATE edu_class_docs SET status='APPROVED' WHERE doc_key=?", key);
        entityManager.clear();

        ObjectNode saved = service.saveAdjustments(teacher, key, java.util.Map.of("2026-09-08", "  Đổi giờ học  "));
        assertThat(saved.path("status").asString()).isEqualTo("APPROVED");
        assertThat(saved.path("adjustments").path("2026-09-08").asString()).isEqualTo("Đổi giờ học");
        assertThat(saved.path("aims").path("knowledge").asString()).isEqualTo("Trẻ biết tên trường");
        assertThat(saved.path("history")
                        .get(saved.path("history").size() - 1)
                        .path("action")
                        .asString())
                .isEqualTo("Cập nhật kế hoạch điều chỉnh và bổ sung");

        assertApiError(
                () -> service.saveAdjustments(otherTeacher, key, java.util.Map.of("2026-09-08", "x")),
                ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.saveAdjustments(teacher, key, java.util.Map.of("2026-09-14", "x")), ApiCode.BAD_REQUEST);
        assertApiError(
                () -> service.saveAdjustments(teacher, key, java.util.Map.of("2026-09-08", "x".repeat(4001))),
                ApiCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("EDU-25/26: phiên bản mới — bản cũ chỉ xem; bản chép có ô đỏ thì chặn gửi tới khi sửa/đánh dấu")
    void versionedClassDocsNeedFixes() {
        approveBranch();
        String oldTheme2Key = "tp_" + classId + "_" + theme2;
        String goal = String.valueOf(jdbc.queryForObject(
                "SELECT id FROM edu_goal_rows WHERE year_plan_id=? AND kind='goal'", Long.class, plan.id()));
        ObjectNode oldTheme = theme(theme2);
        ((ObjectNode) oldTheme.path("rows")).putObject(goal).put("activities", "Trò chuyện: Gia đình");
        service.saveClassDoc(teacher, "theme", oldTheme2Key, oldTheme);

        // Tổ trưởng không tạo được khi còn bản mở; PHT tạo v2, đổi câu MT và ngày chủ đề 2, ban hành
        java.util.UUID session = java.util.UUID.randomUUID();
        YearPlanDetailResponse v2 = yearPlanService.createVersion(
                vicePrincipal,
                plan.id(),
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        session, plan.revision(), java.util.UUID.randomUUID()));
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.sourcePlanId()).isEqualTo(plan.id());
        assertThat(v2.code()).isEqualTo(plan.code() + "-V2");
        assertThat(v2.themes()).hasSize(2);
        assertThat(v2.domains().stream().flatMap(d -> d.rows().stream()).toList())
                .hasSize(1);
        assertApiError(
                () -> yearPlanService.createVersion(
                        leader,
                        plan.id(),
                        new com.carenest.dto.eduplan.YearPlanEditRequest(
                                java.util.UUID.randomUUID(), plan.revision(), java.util.UUID.randomUUID())),
                ApiCode.CONFLICT);
        YearPlanDetailResponse edited = yearPlanService.updateYearPlan(
                vicePrincipal,
                v2.id(),
                requestOf(
                        v2,
                        "Trẻ thực hiện đủ động tác theo nhạc",
                        LocalDate.of(2026, 10, 26),
                        LocalDate.of(2026, 10, 30)),
                new com.carenest.dto.eduplan.YearPlanEditRequest(session, v2.revision(), java.util.UUID.randomUUID()));
        YearPlanDetailResponse issued = yearPlanService.issueYearPlan(
                vicePrincipal,
                v2.id(),
                null,
                new com.carenest.dto.eduplan.YearPlanEditRequest(
                        session, edited.revision(), java.util.UUID.randomUUID()));
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

        var changes = yearPlanService.getChanges(teacher, v2.id(), null);
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
        assertApiError(() -> service.saveClassDoc(teacher, "theme", oldTheme2Key, oldTheme), ApiCode.CONFLICT);
        assertApiError(
                () -> yearPlanService.createVersion(
                        vicePrincipal,
                        plan.id(),
                        new com.carenest.dto.eduplan.YearPlanEditRequest(
                                java.util.UUID.randomUUID(), plan.revision(), java.util.UUID.randomUUID())),
                ApiCode.CONFLICT);

        String newGoal =
                String.valueOf(issued.domains().getFirst().rows().getFirst().id());
        String newTheme2 = String.valueOf(issued.themes().get(1).id());
        String branchKey = "bp_" + classId + "_" + v2.id();
        ObjectNode branch = branch(names("2026-09-07", "2026-09-21", "2026-09-28", "2026-10-26"));
        branch.put("yearPlanId", String.valueOf(v2.id()));
        branch.put("sourceDocKey", "bp_" + classId + "_" + plan.id());
        assertThat(service.saveClassDoc(teacher, "branch", branchKey, branch)
                        .path("sourceDocKey")
                        .asString())
                .isEqualTo("bp_" + classId + "_" + plan.id());
        // Chủ đề 2 đổi ngày, chưa đánh dấu kiểm tra ⇒ chặn gửi
        assertApiError(() -> service.actOnClassDoc(teacher, "branch", branchKey, submit()), ApiCode.CONFLICT);
        branch.putArray("versionChecks").add("theme:" + newTheme2);
        service.saveClassDoc(teacher, "branch", branchKey, branch);
        service.actOnClassDoc(teacher, "branch", branchKey, submit());
        service.actOnClassDoc(leader, "branch", branchKey, approve());
        service.actOnClassDoc(vicePrincipal, "branch", branchKey, approve());

        String themeKey = "tp_" + classId + "_" + newTheme2;
        ObjectNode copy = theme(newTheme2);
        copy.put("yearPlanId", String.valueOf(v2.id()));
        copy.put("sourceDocKey", oldTheme2Key);
        ((ObjectNode) copy.path("rows")).putObject(newGoal).put("activities", "Trò chuyện: Gia đình");
        service.saveClassDoc(teacher, "theme", themeKey, copy);
        String weekKey = "wp_" + classId + "_2026-10-26_" + v2.id();
        assertApiError(
                () -> service.saveClassDoc(
                        teacher, "week", "wp_" + classId + "_2026-10-26", weekOf(v2.id(), newTheme2)),
                ApiCode.BAD_REQUEST);
        service.saveClassDoc(teacher, "week", weekKey, weekOf(v2.id(), newTheme2));
        assertThatThrownBy(() -> service.actOnClassDoc(teacher, "theme", themeKey, submit()))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CLASS_DOC_VERSION_FIX_REQUIRED);
        copy.putArray("versionChecks").add("goal:" + newGoal);
        service.saveClassDoc(teacher, "theme", themeKey, copy);
        assertThat(service.actOnClassDoc(teacher, "theme", themeKey, submit())
                        .path("status")
                        .asString())
                .isEqualTo("PENDING_TL");
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
        var days = doc.putArray("days");
        for (int i = 0; i < 5; i++) {
            days.addObject().put("date", LocalDate.parse(weekStart).plusDays(i).toString());
        }
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

    private static void assertApiError(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode().getHttpStatus())
                        .isEqualTo(code.getHttpStatus()));
    }

    private void assignTeacher(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01')",
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
}
