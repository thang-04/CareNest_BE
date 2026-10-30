package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.observation.AssessmentDayUnlockRequest;
import com.carenest.dto.observation.AssessmentDayUnlockResponse;
import com.carenest.dto.observation.ClassAssessmentDetailResponse;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse;
import com.carenest.dto.observation.DailySheetResponse;
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
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.Participation;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Đánh giá trẻ trên PostgreSQL thật (V39). Hôm nay = thứ 4, 28/10/2026, 08:00 giờ trường (trước giờ chốt điểm danh). */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DailyAssessmentIntegrationTest {

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
    private AssessmentDayUnlockService unlockService;

    @Autowired
    private ClassAssessmentOverviewService overviewService;

    @Autowired
    private AttendanceService attendanceService;

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
        child1 = insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('An', '2023-01-01') RETURNING id");
        child2 = insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('Bình', '2023-02-01') RETURNING id");
        child3 = insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('Chi', '2023-03-01') RETURNING id");
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
        long plan = insertId("INSERT INTO edu_year_plans (code, school_year_id, age_group_id, title, status, owner_id) "
                + "VALUES ('KH-T', " + year + ", " + ag3 + ", 'KH', 'APPROVED', " + principal.userId()
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
    void teacherAssessesOnlyPresentChildrenAgainstApprovedLesson() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(
                                new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true)),
                                new SaveAttendanceRequest.Entry(child2, AttendanceStatus.UNEXCUSED, Map.of()))));
        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "h1",
                        classA,
                        child1,
                        TODAY,
                        null,
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.COUGH),
                        "",
                        List.of(),
                        null));

        DailySheetResponse sheet = service.getDailySheet(teacherA, classA, null);
        assertThat(sheet.date()).isEqualTo(TODAY);
        assertThat(sheet.canRecord()).isTrue();
        assertThat(sheet.locked()).isFalse();
        assertThat(sheet.attendanceTaken()).isTrue();
        // Chỉ trẻ có mặt; sức khỏe điền sẵn từ sức khỏe trong ngày
        assertThat(sheet.rows()).extracting(DailySheetResponse.Row::childId).containsExactly(child1);
        assertThat(sheet.rows().getFirst().health().signs()).containsExactly(HealthSymptom.COUGH);
        assertThat(sheet.rows().getFirst().recorded()).isFalse();
        assertThat(sheet.lesson().classDocId()).isEqualTo(weekDoc);
        assertThat(sheet.lesson().activities()).singleElement().satisfies(a -> {
            assertThat(a.key()).isEqualTo("hoc");
            assertThat(a.goals()).extracting(g -> g.id()).containsExactly(goal1, goal2);
        });

        service.saveGeneral(
                teacherA,
                classA,
                new SaveDailyGeneralRequest(
                        TODAY,
                        List.of(new SaveDailyGeneralRequest.Entry(
                                child1,
                                false,
                                null,
                                null,
                                Map.of(MealSession.LUNCH, EatingLevel.AN_HET, MealSession.MORNING, EatingLevel.AN_IT),
                                NapLevel.NGU_TOT,
                                Emotion.HAPPY,
                                Participation.TICH_CUC,
                                null,
                                null,
                                true,
                                " Bé ngoan "))));
        // Kiến thức kỹ năng: chỉ chấm MT1, comment null = giữ nhận xét chung
        service.saveSkills(teacherA, classA, skills(TODAY, child1, null, activity("hoc", List.of(goal1), List.of())));

        DailySheetResponse.Row row =
                service.getDailySheet(teacherA, classA, TODAY).rows().getFirst();
        assertThat(row.recorded()).isTrue();
        assertThat(row.comment()).isEqualTo("Bé ngoan");
        assertThat(row.flag()).isTrue();
        // Tham gia cả ngày lưu ở bản ghi ngày, không tạo kết quả theo hoạt động
        assertThat(row.participation()).isEqualTo(Participation.TICH_CUC);
        assertThat(row.meals()).containsOnlyKeys(MealSession.LUNCH);
        assertThat(row.activities()).singleElement().satisfies(a -> {
            assertThat(a.goalsMet()).containsExactly(goal1);
            // MT2 chưa chấm thì không tự ghi Đạt
            assertThat(a.goalsEffort()).isEmpty();
            assertThat(a.goalsNotMet()).isEmpty();
        });

        // Nhận xét sửa ở Kiến thức kỹ năng hiện ở Hoạt động chung (1 bản / trẻ / ngày)
        service.saveSkills(
                teacherA, classA, skills(TODAY, child1, "Bé hăng hái", activity("hoc", List.of(), List.of())));
        DailySheetResponse.Row after =
                service.getDailySheet(vpA, classA, TODAY).rows().getFirst();
        assertThat(after.comment()).isEqualTo("Bé hăng hái");
        assertThat(after.activities()).isEmpty();

        expectCode(
                () -> service.saveSkills(teacherA, classA, skills(TODAY, child2, "x")),
                ApiCode.ASSESSMENT_CHILD_NOT_PRESENT);
        expectCode(
                () -> service.saveSkills(
                        teacherA, classA, skills(TODAY, child1, null, activity("hoc", List.of(goal3), List.of()))),
                ApiCode.ASSESSMENT_GOAL_NOT_IN_ACTIVITY);
        expectCode(
                () -> service.saveSkills(
                        teacherA, classA, skills(TODAY, child1, null, activity("hoc", List.of(goal1), List.of(goal1)))),
                ApiCode.ASSESSMENT_GOAL_TWO_RESULTS);
        expectCode(
                () -> service.saveSkills(
                        teacherA, classA, skills(TODAY, child1, null, activity("goc", List.of(goal1), List.of()))),
                ApiCode.ASSESSMENT_ACTIVITY_NOT_IN_LESSON);
        expectCode(
                () -> service.saveSkills(teacherA, classA, skills(TODAY, child1, null)),
                ApiCode.ASSESSMENT_NOTHING_TO_SAVE);
        expectCode(
                () -> service.saveSkills(teacherA, classA, skills(TODAY, child1, "Nghi tự kỷ")),
                ApiCode.ASSESSMENT_DIAGNOSTIC_TERM);
        expectCode(() -> service.getDailySheet(teacherB, classA, TODAY), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(() -> service.getDailySheet(vpB, classA, TODAY), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(() -> service.saveSkills(vpA, classA, skills(TODAY, child1, "x")), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(() -> service.getDailySheet(teacherA, classA, TODAY.plusDays(1)), ApiCode.ASSESSMENT_FUTURE_DATE);
        expectCode(
                () -> service.getDailySheet(teacherA, classA, LocalDate.of(2026, 10, 25)),
                ApiCode.ASSESSMENT_NOT_SCHOOL_DAY);
        expectCode(
                () -> service.getDailySheet(teacherA, classA, LocalDate.of(2026, 8, 31)),
                ApiCode.ASSESSMENT_DATE_OUT_OF_YEAR);
    }

    @Test
    void skillsLockedWithoutApprovedLessonAndEmptyBeforeAttendance() {
        // Lớp chưa điểm danh: danh sách rỗng kèm cờ
        DailySheetResponse empty = service.getDailySheet(teacherA, classA, TODAY);
        assertThat(empty.attendanceTaken()).isFalse();
        assertThat(empty.rows()).isEmpty();

        attendance(child3, classB, TODAY);
        DailySheetResponse sheetB = service.getDailySheet(teacherB, classB, TODAY);
        assertThat(sheetB.lesson()).isNull();
        expectCode(
                () -> service.saveSkills(
                        teacherB, classB, skills(TODAY, child3, null, activity("hoc", List.of(goal1), List.of()))),
                ApiCode.ASSESSMENT_NO_APPROVED_LESSON);
        // Hoạt động chung (kể cả Tham gia cả ngày) vẫn ghi được khi chưa có giáo án duyệt
        assertThat(service.saveGeneral(teacherB, classB, general(TODAY, child3, "Bé vui"))
                        .saved())
                .isEqualTo(1);
        DailySheetResponse.Row rowB =
                service.getDailySheet(teacherB, classB, TODAY).rows().getFirst();
        assertThat(rowB.participation()).isEqualTo(Participation.BINH_THUONG);
        assertThat(rowB.activities()).isEmpty();

        // Giáo án chưa duyệt không dùng để chấm
        jdbc.update("UPDATE edu_class_docs SET status = 'PENDING_VP' WHERE id = ?", weekDoc);
        entityManager.clear();
        attendance(child1, classA, TODAY);
        assertThat(service.getDailySheet(teacherA, classA, TODAY).lesson()).isNull();
        expectCode(
                () -> service.saveSkills(
                        teacherA, classA, skills(TODAY, child1, null, activity("hoc", List.of(goal1), List.of()))),
                ApiCode.ASSESSMENT_NO_APPROVED_LESSON);
    }

    @Test
    void pastDayLocksUntilLeaderReopensAndEarlyEndRelocks() {
        attendance(child1, classA, YESTERDAY);
        DailySheetResponse locked = service.getDailySheet(teacherA, classA, YESTERDAY);
        assertThat(locked.locked()).isTrue();
        assertThat(locked.lesson().activities().getFirst().goals())
                .extracting(g -> g.id())
                .containsExactly(goal1);
        expectCode(
                () -> service.saveGeneral(teacherA, classA, general(YESTERDAY, child1, "Muộn")),
                ApiCode.ASSESSMENT_DAY_LOCKED);

        Instant until = NOW.plus(Duration.ofHours(1));
        expectCode(() -> unlockService.unlockDay(vpB, unlock(YESTERDAY, until)), ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
        expectCode(
                () -> unlockService.unlockDay(teacherA, unlock(YESTERDAY, until)), ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
        expectCode(() -> unlockService.unlockDay(vpA, unlock(TODAY, until)), ApiCode.ASSESSMENT_DAY_NOT_LOCKED_YET);
        expectCode(
                () -> unlockService.unlockDay(vpA, unlock(LocalDate.of(2026, 10, 24), until)),
                ApiCode.ASSESSMENT_UNLOCK_DATE_OUT_OF_RANGE);
        expectCode(
                () -> unlockService.unlockDay(vpA, unlock(LocalDate.of(2026, 8, 31), until)),
                ApiCode.ASSESSMENT_UNLOCK_DATE_OUT_OF_RANGE);
        expectCode(
                () -> unlockService.unlockDay(vpA, unlock(YESTERDAY, NOW.minusSeconds(60))),
                ApiCode.ASSESSMENT_UNLOCK_DEADLINE_INVALID);
        expectCode(
                () -> unlockService.unlockDay(vpA, unlock(YESTERDAY, Instant.parse("2027-06-01T00:00:00Z"))),
                ApiCode.ASSESSMENT_UNLOCK_DEADLINE_INVALID);

        AssessmentDayUnlockResponse opened = unlockService.unlockDay(vpA, unlock(YESTERDAY, until));
        assertThat(opened.active()).isTrue();
        assertThat(opened.unlockedByName()).isEqualTo("vpa");
        expectCode(
                () -> unlockService.unlockDay(principal, unlock(YESTERDAY, until)),
                ApiCode.ASSESSMENT_UNLOCK_ALREADY_ACTIVE);

        DailySheetResponse reopened = service.getDailySheet(teacherA, classA, YESTERDAY);
        assertThat(reopened.locked()).isFalse();
        assertThat(reopened.unlock().id()).isEqualTo(opened.id());
        service.saveGeneral(teacherA, classA, general(YESTERDAY, child1, "Bổ sung"));

        assertThat(unlockService.getUnlocks(vpB, null, null, null, null, null)).isEmpty();
        assertThat(unlockService.getUnlocks(principal, campusA, classA, true, YESTERDAY, YESTERDAY))
                .extracting(AssessmentDayUnlockResponse::id)
                .containsExactly(opened.id());
        expectCode(() -> unlockService.revokeUnlock(vpB, opened.id()), ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);

        AssessmentDayUnlockResponse ended = unlockService.revokeUnlock(principal, opened.id());
        assertThat(ended.active()).isFalse();
        assertThat(ended.revokedByName()).isEqualTo("principal");
        expectCode(() -> unlockService.revokeUnlock(principal, opened.id()), ApiCode.ASSESSMENT_UNLOCK_ALREADY_ENDED);
        expectCode(() -> unlockService.revokeUnlock(principal, -1L), ApiCode.ASSESSMENT_UNLOCK_NOT_FOUND);
        expectCode(
                () -> service.saveGeneral(teacherA, classA, general(YESTERDAY, child1, "Lần nữa")),
                ApiCode.ASSESSMENT_DAY_LOCKED);
        assertThat(unlockService.getUnlocks(principal, null, null, false, null, null))
                .extracting(AssessmentDayUnlockResponse::id)
                .containsExactly(opened.id());
        expectCode(
                () -> unlockService.getUnlocks(principal, null, null, null, TODAY, YESTERDAY),
                ApiCode.INVALID_DATE_RANGE);
    }

    @Test
    void closedYearIsReadOnly() {
        LocalDate oldDay = LocalDate.of(2026, 5, 20);
        DailySheetResponse sheet = service.getDailySheet(teacherA, oldClass, oldDay);
        assertThat(sheet.yearReadOnly()).isTrue();
        assertThat(sheet.canRecord()).isFalse();
        expectCode(
                () -> service.saveGeneral(teacherA, oldClass, general(oldDay, child1, "x")),
                ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        expectCode(
                () -> unlockService.unlockDay(
                        principal, new AssessmentDayUnlockRequest(oldClass, oldDay, NOW.plusSeconds(3600), "Bổ sung")),
                ApiCode.ASSESSMENT_UNLOCK_YEAR_NOT_ACTIVE);
    }

    @Test
    void leadersSeeClassOverviewInTheirScope() {
        attendance(child1, classA, YESTERDAY);
        attendance(child1, classA, TODAY);
        attendance(child3, classB, TODAY);
        service.saveSkills(
                teacherA, classA, skills(TODAY, child1, "Bé tiến bộ", activity("hoc", List.of(goal1), List.of(goal2))));

        ClassAssessmentOverviewResponse day = overviewService.getOverview(principal, "day", null, null, null, null);
        assertThat(day.schoolDays()).isEqualTo(1);
        assertThat(day.rows())
                .extracting(ClassAssessmentOverviewResponse.ClassRow::classId)
                .containsExactly(classA, classB);
        ClassAssessmentOverviewResponse.ClassRow rowA = day.rows().getFirst();
        assertThat(rowA.size()).isEqualTo(2);
        assertThat(rowA.present()).isEqualTo(1);
        assertThat(rowA.assessed()).isEqualTo(1);
        assertThat(rowA.dat()).isEqualTo(1);
        assertThat(rowA.chuaDat()).isEqualTo(1);
        assertThat(day.totals().datRate()).isEqualTo(50);
        assertThat(day.totals().complete()).isEqualTo(1);

        ClassAssessmentOverviewResponse week = overviewService.getOverview(vpA, "WEEK", null, null, null, null);
        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(week.schoolDays()).isEqualTo(3);
        assertThat(week.rows()).singleElement().satisfies(r -> {
            assertThat(r.classId()).isEqualTo(classA);
            assertThat(r.present()).isEqualTo(2);
            assertThat(r.assessed()).isEqualTo(1);
            assertThat(r.assessedDays()).isEqualTo(1);
            // Thứ 2, thứ 3 đã qua và chưa mở lại
            assertThat(r.lockedDays()).isEqualTo(2);
        });
        assertThat(overviewService
                        .getOverview(principal, "MONTH", null, null, null, null)
                        .start())
                .isEqualTo(LocalDate.of(2026, 10, 1));

        ClassAssessmentDetailResponse detail = overviewService.getClassDetail(principal, classA, "WEEK");
        assertThat(detail.children())
                .extracting(ClassAssessmentDetailResponse.ChildRow::childId)
                .containsExactly(child1, child2);
        assertThat(detail.children().getFirst().comments())
                .extracting(ClassAssessmentDetailResponse.Comment::text)
                .containsExactly("Bé tiến bộ");

        expectCode(
                () -> overviewService.getOverview(teacherA, "DAY", null, null, null, null),
                ApiCode.ASSESSMENT_OVERVIEW_FORBIDDEN);
        expectCode(() -> overviewService.getClassDetail(vpB, classA, "DAY"), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(() -> overviewService.getOverview(principal, "YEAR", null, null, null, null), ApiCode.INVALID_VALUE);
    }

    @Test
    void classCommentSavesIndependentlyAndAppearsOnlyForScopedLeaders() {
        service.saveGeneral(teacherA, classA, new SaveDailyGeneralRequest(TODAY, List.of(), "Class note"));
        assertThat(service.getDailySheet(teacherA, classA, TODAY).classComment().text())
                .isEqualTo("Class note");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM class_daily_comments WHERE classroom_id = ?", Long.class, classA))
                .isEqualTo(1L);
        service.saveGeneral(teacherA, classA, new SaveDailyGeneralRequest(TODAY, List.of(), "Updated class note"));
        assertThat(overviewService.getClassDetail(vpA, classA, "DAY").classComments())
                .hasSize(1);
        assertThat(overviewService
                        .getClassDetail(principal, classA, "WEEK")
                        .classComments()
                        .getFirst()
                        .text())
                .isEqualTo("Updated class note");
        assertThatThrownBy(() -> overviewService.getClassDetail(vpB, classA, "DAY"))
                .isInstanceOf(GlobalException.class);
        attendance(child1, classA, TODAY);
        service.saveGeneral(teacherA, classA, general(TODAY, child1, "Child note"));
        assertThat(service.getDailySheet(teacherA, classA, TODAY).classComment().text())
                .isEqualTo("Updated class note");
        service.saveGeneral(teacherA, classA, new SaveDailyGeneralRequest(TODAY, List.of(), ""));
        assertThat(overviewService.getClassDetail(principal, classA, "MONTH").classComments())
                .isEmpty();
    }

    @Test
    void approvedNewVersionProjectsSavedGradesByLineageWithoutRewritingOnRead() {
        attendance(child1, classA, TODAY);
        service.saveSkills(
                teacherA, classA, skills(TODAY, child1, null, activity("hoc", List.of(goal1), List.of(goal2))));
        long nextPlan = insertId("INSERT INTO edu_year_plans "
                + "(code, school_year_id, age_group_id, title, status, owner_id, version_no, source_plan_id) "
                + "SELECT 'KH-T-V2', school_year_id, age_group_id, title, status, owner_id, 2, id "
                + "FROM edu_year_plans WHERE id = (SELECT year_plan_id FROM edu_class_docs WHERE id = "
                + weekDoc + ") RETURNING id");
        long domain = jdbc.queryForObject("SELECT domain_id FROM edu_plan_goals WHERE id = ?", Long.class, goal1);
        long copiedGoal = goal(nextPlan, domain, 1, "Trẻ nhận biết màu");
        long addedGoal = goal(nextPlan, domain, 2, "Trẻ nhảy bật");
        jdbc.update("UPDATE edu_plan_goals SET lineage_id = ? WHERE id = ?", goal1, copiedGoal);
        jdbc.update("UPDATE edu_plan_goals SET lineage_id = ? WHERE id = ?", goal3, addedGoal);
        long nextTheme = insertId("INSERT INTO edu_themes (year_plan_id, sort_order, name, abbr, lineage_id) "
                + "SELECT " + nextPlan + ", 1, name, abbr, id FROM edu_themes "
                + "WHERE id = (SELECT theme_id FROM edu_class_docs WHERE id = " + weekDoc + ") RETURNING id");
        String content =
                "{\"picks\":{\"hoc.2\":[{\"goalId\":\"" + copiedGoal + "\"},{\"goalId\":\"" + addedGoal + "\"}]}}";
        long nextDoc = insertId("INSERT INTO edu_class_docs "
                + "(doc_key, kind, classroom_id, year_plan_id, theme_id, week_start, status, content, created_by, updated_by, source_doc_id) "
                + "SELECT 'w-a-v2', kind, classroom_id, " + nextPlan + ", " + nextTheme
                + ", week_start, status, '" + content + "'::jsonb, created_by, updated_by, id "
                + "FROM edu_class_docs WHERE id = " + weekDoc + " RETURNING id");
        entityManager.clear();

        DailySheetResponse sheet = service.getDailySheet(teacherA, classA, TODAY);
        assertThat(sheet.lesson().classDocId()).isEqualTo(nextDoc);
        DailySheetResponse.ActivityResult result =
                sheet.rows().getFirst().activities().getFirst();
        assertThat(result.goalsMet()).containsExactly(copiedGoal);
        assertThat(result.goalsNotMet()).isEmpty();
        assertThat(result.goalsEffort()).isEmpty();
        assertThat(result.participation()).isEqualTo(Participation.TICH_CUC);
        ClassAssessmentDetailResponse detail = overviewService.getClassDetail(principal, classA, "DAY");
        assertThat(detail.children().getFirst().dat()).isEqualTo(1);
        assertThat(detail.children().getFirst().chuaDat()).isZero();
        ClassAssessmentOverviewResponse overview =
                overviewService.getOverview(principal, "DAY", null, null, null, classA);
        assertThat(overview.rows().getFirst().dat()).isEqualTo(1);
        assertThat(overview.rows().getFirst().chuaDat()).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT class_doc_id FROM daily_activity_results WHERE child_id = ?", Long.class, child1))
                .isEqualTo(weekDoc);
        service.saveSkills(
                teacherA,
                classA,
                skills(TODAY, child1, null, activity("hoc", result.goalsMet(), result.goalsNotMet())));
        assertThat(jdbc.queryForObject(
                        "SELECT class_doc_id FROM daily_activity_results WHERE child_id = ?", Long.class, child1))
                .isEqualTo(nextDoc);
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
