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
                                null, "Nghỉ", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)))));
        plan = yearPlanService.issueYearPlan(vicePrincipal, draft.id(), null);
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
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(code));
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
