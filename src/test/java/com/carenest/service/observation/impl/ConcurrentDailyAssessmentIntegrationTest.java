package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.observation.AssessmentDayUnlockRequest;
import com.carenest.dto.observation.SaveDailyGeneralRequest;
import com.carenest.dto.observation.SaveDailySkillsRequest;
import com.carenest.dto.observation.SaveDailySkillsRequest.Activity;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.observation.AssessmentDayUnlockService;
import com.carenest.service.observation.ClassAssessmentOverviewService;
import com.carenest.service.observation.DailyAssessmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.Participation;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
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

/** Đánh giá trẻ trên PostgreSQL thật (V41). Hôm nay = thứ 4, 28/10/2026, 08:00 giờ trường (trước giờ chốt điểm danh). */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentDailyAssessmentIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static final class FixedClock extends Clock {
        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return NOW;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return new FixedClock();
        }
    }

    @Autowired
    private DailyAssessmentService service;

    @Autowired
    private EvaluationSources sources;

    @Autowired
    private com.carenest.service.organization.ClassroomService classrooms;

    @Autowired
    private com.carenest.service.observation.ChildEvaluationService evaluations;

    @Autowired
    private AssessmentDayUnlockService unlockService;

    @Autowired
    private ClassAssessmentOverviewService overviewService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private com.carenest.service.attendance.LeaveRequestService leaveService;

    @Autowired
    private DailyHealthService healthService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private long campusA;
    private long classA;
    private long classB;
    private long oldClass;
    private long child1;
    private long child2;
    private long child3;
    private long goal1;
    private long goal2;
    private long goal3;
    private long weekDoc;

    @BeforeEach
    void setUp() {
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = classroom(campusA, year, "Lớp A", ag3);
        classB = classroom(campusB, year, "Lớp B", ag3);
        oldClass = classroom(campusA, closedYear, "Lớp cũ", ag3);
        child1 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'An', '2023-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        child2 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Bình', '2023-02-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        child3 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Chi', '2023-03-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        enroll(child1, classA);
        enroll(child2, classA);
        enroll(child3, classB);

        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("vpa", CareNestRole.VICE_PRINCIPAL);
        vpB = user("vpb", CareNestRole.VICE_PRINCIPAL);
        campusRole(vpA, campusA);
        campusRole(vpB, campusB);
        teacherA = user("teachera", CareNestRole.TEACHER);
        teacherB = user("teacherb", CareNestRole.TEACHER);
        teach(teacherA, classA);
        teach(teacherA, oldClass);
        teach(teacherB, classB);

        // Giáo án tuần 26–30/10 của lớp A đã duyệt: Hoạt động học thứ 3 có MT1, thứ 4 có MT1 + MT2
        long plan = insertId(
                "INSERT INTO edu_year_plans (legacy_team_without_source, code, school_year_id, age_group_id, title, status, owner_id) "
                        + "VALUES (true, 'KH-T', " + year + ", " + ag3 + ", 'KH', 'APPROVED', " + principal.userId()
                        + ") RETURNING id");
        long domain = jdbc.queryForObject("SELECT id FROM edu_domains ORDER BY sort_order LIMIT 1", Long.class);
        goal1 = goal(plan, domain, 1, "Trẻ nhận biết màu");
        goal2 = goal(plan, domain, 2, "Trẻ đếm đến 5");
        goal3 = goal(plan, domain, 3, "Trẻ nhảy bật");
        long theme = insertId("INSERT INTO edu_themes (year_plan_id, sort_order, name, abbr) VALUES (" + plan
                + ", 1, 'Bản thân', 'BT') RETURNING id");
        String content = "{\"grid\":{\"hoc\":{\"days\":[\"\",\"Toán\",\"Tạo hình\",\"\",\"\"]}},\"picks\":{"
                + "\"hoc.1\":[{\"id\":\"p1\",\"goalId\":\"" + goal1 + "\"}],"
                + "\"hoc.2\":[{\"id\":\"p2\",\"goalId\":\"" + goal1 + "\"},{\"id\":\"p3\",\"goalId\":\"" + goal2
                + "\"}]}}";
        weekDoc = insertId("INSERT INTO edu_class_docs (doc_key, kind, classroom_id, year_plan_id, theme_id, "
                + "week_start, status, content, created_by, updated_by) VALUES ('w-a', 'week', " + classA + ", "
                + plan + ", " + theme + ", '2026-10-26', 'APPROVED', '" + content + "'::jsonb, "
                + principal.userId() + ", " + principal.userId() + ") RETURNING id");
    }

    @Test
    void simultaneousSavesSerializeFirstInsertAndMergeIndependentViews() throws Exception {
        attendance(child1, classA, TODAY);
        race(
                () -> service.saveGeneral(
                        teacherA,
                        classA,
                        new SaveDailyGeneralRequest(
                                TODAY, general(TODAY, child1, "First").entries(), "Class note")),
                () -> service.saveGeneral(
                        teacherA,
                        classA,
                        new SaveDailyGeneralRequest(
                                TODAY, general(TODAY, child1, "Second").entries(), "Class note")));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM daily_observations WHERE child_id = ?", Integer.class, child1))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM class_daily_comments WHERE classroom_id = ?", Integer.class, classA))
                .isEqualTo(1);
        race(
                () -> service.saveGeneral(teacherA, classA, general(TODAY, child1, "General saved")),
                () -> service.saveSkills(
                        teacherA,
                        classA,
                        skills(TODAY, child1, null, activity("hoc", List.of(goal1), List.of(goal2)))));
        var sheet = service.getDailySheet(teacherA, classA, TODAY);
        assertThat(sheet.rows().getFirst().activities()).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM daily_activity_results WHERE child_id = ?", Integer.class, child1))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT comment FROM daily_observations WHERE child_id = ?", String.class, child1))
                .isEqualTo("General saved");

        // Thứ tự tên ngược ID: đơn nghỉ đi qua hai lớp trong khi danh sách đánh giá cũng khóa cả hai.
        jdbc.update("UPDATE classrooms SET name = 'Z' WHERE id = ?", classA);
        jdbc.update("UPDATE classrooms SET campus_id = ?, name = 'A' WHERE id = ?", campusA, classB);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) VALUES (?, 'TEACHER', ?, '2026-09-01', false)",
                teacherA.userId(),
                classB);
        jdbc.update(
                "UPDATE enrollments SET valid_to = ? WHERE child_id = ? AND classroom_id = ?", TODAY, child1, classA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, ?)",
                child1,
                classB,
                TODAY.plusDays(1));
        var parent = user("parent-lock", CareNestRole.PARENT);
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
                parent.userId(),
                child1);
        race(
                () -> evaluations.list(teacherA, com.carenest.utils.enums.EvaluationPeriodType.YEAR, null, null, null),
                () -> leaveService.createLeaveRequest(
                        parent,
                        new com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest(
                                child1, TODAY, TODAY.plusDays(1), "Demo leave")));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM attendance_records WHERE child_id = ? AND status = 'EXCUSED'",
                        Integer.class,
                        child1))
                .isEqualTo(2);
    }

    private void race(Runnable first, Runnable second) throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                await(start);
                first.run();
            });
            var b = pool.submit(() -> {
                await(start);
                second.run();
            });
            start.countDown();
            a.get(30, java.util.concurrent.TimeUnit.SECONDS);
            b.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static SaveDailySkillsRequest skills(LocalDate date, long childId, String comment, Activity... activities) {
        return new SaveDailySkillsRequest(
                date, List.of(new SaveDailySkillsRequest.Entry(childId, comment, List.of(activities))));
    }

    private static Activity activity(String key, List<Long> met, List<Long> notMet) {
        return new Activity(
                key, met.isEmpty() && notMet.isEmpty() ? null : Participation.TICH_CUC, met, List.of(), notMet);
    }

    private static SaveDailyGeneralRequest general(LocalDate date, long childId, String comment) {
        return new SaveDailyGeneralRequest(
                date,
                List.of(new SaveDailyGeneralRequest.Entry(
                        childId,
                        false,
                        null,
                        null,
                        null,
                        null,
                        Emotion.CALM,
                        Participation.BINH_THUONG,
                        null,
                        null,
                        null,
                        comment)));
    }

    private AssessmentDayUnlockRequest unlock(LocalDate date, Instant until) {
        return new AssessmentDayUnlockRequest(classA, date, until, "Cô quên lưu");
    }

    private void attendance(long childId, long classroomId, LocalDate date) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, recorded_by, recorded_at) "
                        + "SELECT ?, id, campus_id, ?, 'PRESENT', ?, now() FROM classrooms WHERE id = ?",
                childId,
                date,
                principal.userId(),
                classroomId);
    }

    private long goal(long plan, long domain, int no, String text) {
        return insertId("INSERT INTO edu_plan_goals (year_plan_id, domain_id, sort_order, text, goal_no) VALUES ("
                + plan + ", " + domain + ", " + no + ", '" + text + "', " + no + ") RETURNING id");
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                user.userId(),
                campusId);
    }

    private void teach(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                user.userId(),
                classroomId);
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusId
                + ", " + yearId + ", '" + name + "', " + ageGroupId + ") RETURNING id");
    }

    private void enroll(long childId, long classroomId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classroomId);
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
