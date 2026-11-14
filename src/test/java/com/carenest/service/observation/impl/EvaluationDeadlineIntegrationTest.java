package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.carenest.dto.observation.AssessmentDayUnlockRequest;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationPeriod;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockRequest;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockResponse;
import com.carenest.dto.observation.EvaluationDeadlineDtos.OverdueClassItem;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.eduplan.EduPlanCalendarQueryService;
import com.carenest.service.eduplan.EduPlanCalendarQueryService.EffectiveTheme;
import com.carenest.service.observation.AssessmentDayUnlockService;
import com.carenest.service.observation.AssessmentReminderService;
import com.carenest.service.observation.EvaluationDeadlineService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EvaluationLockKind;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Hạn, khóa, mở lại đánh giá cuối chủ đề / học kỳ / cuối năm và nhắc việc (OBS-06, OBS-27, OBS-28, OBS-30). Học kỳ I
 * kết thúc thứ 6, 23/10/2026; chủ đề "Gia đình" kết thúc thứ 6, 16/10/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EvaluationDeadlineIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final MovableClock CLOCK = new MovableClock();
    private static final long THEME_LINEAGE = 900L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }
    }

    static final class MovableClock extends Clock {
        private Instant now = at(LocalDate.of(2026, 10, 30), 10);

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @MockitoBean
    private EduPlanCalendarQueryService calendarQueryService;

    @MockitoBean
    private EduLessonQueryService lessonQueryService;

    @Autowired
    private EvaluationDeadlineService service;

    @Autowired
    private AssessmentReminderService reminderService;

    @Autowired
    private AssessmentDayUnlockService dayUnlockService;

    @Autowired
    private ClassroomService classroomService;

    @Autowired
    private ObservationDayRules rules;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private long year;
    private long classA;
    private long child1;

    @BeforeEach
    void setUp() {
        CLOCK.set(at(LocalDate.of(2026, 10, 30), 10));
        long campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status, semester1_start, semester1_end,"
                + " semester2_start, semester2_end) VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE',"
                + " '2026-09-01', '2026-10-23', '2026-10-26', '2027-05-28') RETURNING id");
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusB + ", "
                + year + ", 'Lớp B', " + ag3 + ") RETURNING id");
        child1 = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'ED' || substr(md5(random()::text), 1, 12), 'An', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01')"
                + " RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child1,
                classA);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("vpa", CareNestRole.VICE_PRINCIPAL);
        vpB = user("vpb", CareNestRole.VICE_PRINCIPAL);
        campusRole(vpA, campusA);
        campusRole(vpB, campusB);
        teacherA = user("teachera", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', '2027-05-31', TRUE)",
                teacherA.userId(),
                classA);
        when(calendarQueryService.findApprovedThemes(anyLong()))
                .thenReturn(List.of(new EffectiveTheme(
                        1L, THEME_LINEAGE, 1L, "Gia đình", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 16))));
        when(lessonQueryService.findApprovedDayLessons(
                        anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
    }

    @Test
    @DisplayName("OBS-06: học kỳ I mở từ ngày kết thúc, hạn mặc định 5 ngày học; quá 23:59 ngày hạn thì khóa")
    void semesterLocksAfterFifthSchoolDay() {
        assertThat(period(EvaluationLockKind.THEME, String.valueOf(THEME_LINEAGE))
                        .deadline())
                .isEqualTo(LocalDate.of(2026, 10, 21));
        EvaluationPeriod s1 = period(EvaluationLockKind.SEMESTER, "1");
        assertThat(s1.openFrom()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(s1.deadline()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(s1.locked()).isFalse();
        service.checkWritable(cls(), EvaluationLockKind.SEMESTER, "1");

        CLOCK.set(at(LocalDate.of(2026, 10, 31), 0));
        assertThat(period(EvaluationLockKind.SEMESTER, "1").locked()).isTrue();
        expectCode(() -> service.checkWritable(cls(), EvaluationLockKind.SEMESTER, "1"), ApiCode.EVALUATION_LOCKED);
    }

    @Test
    @DisplayName("OBS-27: chủ đề kết thúc 16/10, hạn 3 ngày học (21/10); nghỉ 20/10 không tính ⇒ hạn 22/10")
    void themeDeadlineSkipsHolidays() {
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) VALUES (?, ?, ?, 'Nghỉ')",
                year,
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 20));
        assertThat(period(EvaluationLockKind.THEME, String.valueOf(THEME_LINEAGE))
                        .deadline())
                .isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    @DisplayName("Hạn cố định: chủ đề 3, học kỳ 5, cuối năm 5 ngày học sau ngày kết thúc kỳ")
    void deadlinesAreFixed() {
        assertThat(AssessmentDeadlines.THEME).isEqualTo(3);
        assertThat(AssessmentDeadlines.SEMESTER).isEqualTo(5);
        assertThat(AssessmentDeadlines.YEAR_END).isEqualTo(5);
        assertThat(AssessmentDeadlines.WEEKLY_COMMENT).isEqualTo(1);
    }

    @Test
    @DisplayName("OBS-28: hạn cuối năm tính sau ngày kết thúc HK II, có thể sau ngày kết thúc năm học")
    void yearEndDeadlineMayPassYearEnd() {
        EvaluationPeriod y = period(EvaluationLockKind.YEAR, "Y");
        assertThat(y.openFrom()).isEqualTo(LocalDate.of(2027, 5, 28));
        assertThat(y.deadline()).isEqualTo(LocalDate.of(2027, 6, 4));
    }

    @Test
    @DisplayName("Mở lại lớp – kỳ: PHT điểm trường mở, GV được báo; kết thúc sớm thì khóa lại; quyền và lỗi")
    void reopenByClassAndPeriod() {
        Instant until = at(LocalDate.of(2026, 11, 3), 17);
        expectCode(
                () -> service.unlock(vpA, request(EvaluationLockKind.SEMESTER, "1", until)),
                ApiCode.EVALUATION_NOT_LOCKED_YET);

        CLOCK.set(at(LocalDate.of(2026, 11, 2), 8));
        expectCode(
                () -> service.unlock(vpB, request(EvaluationLockKind.SEMESTER, "1", until)),
                ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
        expectCode(
                () -> service.unlock(teacherA, request(EvaluationLockKind.SEMESTER, "1", until)),
                ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
        expectCode(
                () -> service.unlock(vpA, request(EvaluationLockKind.SEMESTER, "9", until)),
                ApiCode.EVALUATION_PERIOD_NOT_FOUND);
        expectCode(
                () -> service.unlock(vpA, request(EvaluationLockKind.SEMESTER, "1", at(LocalDate.of(2026, 11, 1), 8))),
                ApiCode.ASSESSMENT_UNLOCK_DEADLINE_INVALID);

        EvaluationUnlockResponse unlock = service.unlock(vpA, request(EvaluationLockKind.SEMESTER, "1", until));
        assertThat(unlock.periodLabel()).isEqualTo("Đánh giá học kỳ I");
        assertThat(unlock.active()).isTrue();
        service.checkWritable(cls(), EvaluationLockKind.SEMESTER, "1");
        assertThat(notifications(teacherA, "ASSESSMENT_REOPENED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT link FROM notifications WHERE user_id = ? AND type = 'ASSESSMENT_REOPENED'",
                        String.class,
                        teacherA.userId()))
                .isEqualTo("/assessment/semester?classId=" + classA + "&period=1");
        assertThat(period(EvaluationLockKind.THEME, String.valueOf(THEME_LINEAGE))
                        .pageKey())
                .isEqualTo("1");
        expectCode(
                () -> service.unlock(principal, request(EvaluationLockKind.SEMESTER, "1", until)),
                ApiCode.EVALUATION_UNLOCK_ALREADY_ACTIVE);
        assertThat(service.getUnlocks(principal, null, classA, EvaluationLockKind.SEMESTER, true))
                .extracting(EvaluationUnlockResponse::id)
                .containsExactly(unlock.id());

        service.revokeUnlock(vpA, unlock.id());
        expectCode(() -> service.checkWritable(cls(), EvaluationLockKind.SEMESTER, "1"), ApiCode.EVALUATION_LOCKED);
        expectCode(() -> service.revokeUnlock(vpA, unlock.id()), ApiCode.ASSESSMENT_UNLOCK_ALREADY_ENDED);
    }

    @Test
    @DisplayName("OBS-30: nhắc GV khi mở, trước hạn 1 ngày học, khi khóa — mỗi lời nhắc một lần")
    void remindsTeachersOncePerStep() {
        CLOCK.set(at(LocalDate.of(2026, 10, 29), 7));
        reminderService.remindEvaluationDeadlines();
        reminderService.remindEvaluationDeadlines();
        assertThat(titles(teacherA)).contains("Đánh giá học kỳ I đã mở", "Đánh giá học kỳ I sắp hết hạn");
        assertThat(notifications(teacherA, "ASSESSMENT_REMINDER"))
                .isEqualTo(titles(teacherA).size());
        long afterTwoRuns = notifications(teacherA, "ASSESSMENT_REMINDER");

        CLOCK.set(at(LocalDate.of(2026, 11, 2), 7));
        reminderService.remindEvaluationDeadlines();
        reminderService.remindEvaluationDeadlines();
        assertThat(titles(teacherA)).contains("Đánh giá học kỳ I đã khóa");
        assertThat(notifications(teacherA, "ASSESSMENT_REMINDER")).isGreaterThan(afterTwoRuns);
        assertThat(titles(teacherA).stream().filter("Đánh giá học kỳ I đã khóa"::equals))
                .hasSize(1);
    }

    @Test
    @DisplayName("OBS-30: BGH nhận tóm tắt lớp quá hạn chưa xong; GV 20:00 nhận nhắc khi còn trẻ chưa đánh giá")
    void remindsLeadersAndDailyAssessment() {
        CLOCK.set(at(LocalDate.of(2026, 11, 2), 7));
        List<OverdueClassItem> overdue = service.getOverdue(vpA, null);
        assertThat(overdue)
                .extracting(OverdueClassItem::kind, OverdueClassItem::periodKey)
                .contains(org.assertj.core.groups.Tuple.tuple("SEMESTER", "1"));
        reminderService.remindLeadersOverdue();
        assertThat(notifications(vpA, "LEADER_REMINDER")).isEqualTo(1);
        assertThat(notifications(principal, "LEADER_REMINDER")).isEqualTo(1);
        // Lớp B (điểm trường B) cũng quá hạn chủ đề ⇒ PHT B nhận bản của điểm trường mình
        assertThat(notifications(vpB, "LEADER_REMINDER")).isEqualTo(1);

        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, recorded_by, recorded_at) "
                        + "SELECT ?, id, campus_id, ?, 'PRESENT', ?, now() FROM classrooms WHERE id = ?",
                child1,
                LocalDate.of(2026, 11, 2),
                principal.userId(),
                classA);
        CLOCK.set(at(LocalDate.of(2026, 11, 2), 20));
        reminderService.remindDailyAssessment();
        reminderService.remindDailyAssessment();
        assertThat(titles(teacherA).stream().filter("Chưa xong đánh giá hôm nay"::equals))
                .hasSize(1);
    }

    @Test
    @DisplayName(
            "Sau ngày kết thúc năm (chưa đóng): mở lại ngày đánh giá được hạn sau ngày kết thúc, GV năm đó ghi được")
    void dayUnlockAfterYearEndBeforeClose() {
        CLOCK.set(at(LocalDate.of(2027, 6, 2), 9));
        var unlock = dayUnlockService.unlockDay(
                vpA,
                new AssessmentDayUnlockRequest(
                        classA, LocalDate.of(2027, 5, 31), at(LocalDate.of(2027, 6, 5), 17), "Bổ sung ngày cuối"));
        assertThat(unlock.id()).isNotNull();
        assertThat(notifications(teacherA, "ASSESSMENT_REOPENED")).isEqualTo(1);
    }

    @Test
    @DisplayName("Kỳ cuối: sau ngày kết thúc năm, GV năm đó vẫn ghi được tới khi HT đóng năm; đóng năm thì hết")
    void teacherFinishesLastPeriodUntilYearCloses() {
        CLOCK.set(at(LocalDate.of(2027, 6, 2), 9));
        assertThat(rules.canRecord(teacherA, cls())).isTrue();
        assertThat(period(EvaluationLockKind.YEAR, "Y").locked()).isFalse();

        jdbc.update("UPDATE school_years SET status = 'CLOSED' WHERE id = ?", year);
        entityManager.clear();
        assertThat(rules.canRecord(teacherA, cls())).isFalse();
    }

    private EvaluationPeriod period(EvaluationLockKind kind, String key) {
        return service.periodsOf(cls()).stream()
                .filter(p -> p.kind() == kind && p.periodKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private ClassroomInfoResponse cls() {
        return classroomService.findClassroomInfo(classA).orElseThrow();
    }

    private EvaluationUnlockRequest request(EvaluationLockKind kind, String key, Instant until) {
        return new EvaluationUnlockRequest(kind, classA, key, until, "Bổ sung");
    }

    private long notifications(CurrentUser user, String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?", Long.class, user.userId(), type);
    }

    private List<String> titles(CurrentUser user) {
        return jdbc.queryForList(
                "SELECT title FROM notifications WHERE user_id = ? AND type = 'ASSESSMENT_REMINDER' ORDER BY id",
                String.class,
                user.userId());
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL, VICE_PRINCIPAL -> Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_UNLOCK);
                    case TEACHER ->
                        Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_RECORD, Permission.ASSESSMENT_CONFIRM);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                user.userId(),
                campusId);
    }

    private static Instant at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZONE).toInstant();
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
