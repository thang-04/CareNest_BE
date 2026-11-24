package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.integration.ai.AiRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.observation.AssessmentDayUnlockService;
import com.carenest.service.observation.ClassAssessmentOverviewService;
import com.carenest.service.observation.DailyAssessmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationDraftSource;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.Participation;
import com.carenest.utils.enums.Permission;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** Đánh giá trẻ trên PostgreSQL thật (V41). Hôm nay = thứ 4, 28/10/2026, 08:00 giờ trường (trước giờ chốt điểm danh). */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DailyAssessmentIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

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

    // Hạn / khóa kỳ đánh giá kiểm riêng ở EvaluationDeadlineIntegrationTest; dữ liệu ở đây có kỳ đã qua hạn từ lâu
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.carenest.service.observation.EvaluationDeadlineService evaluationDeadlineService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private AiGateway aiGateway;

    @Autowired
    private DailyAssessmentService service;

    @Autowired
    private EvaluationSources sources;

    @Autowired
    private com.carenest.service.observation.TransferredAssessmentHistoryService transferredHistory;

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
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, year);
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = classroom(campusA, year, "Lớp A", ag3);
        classB = classroom(campusB, year, "Lớp B", ag3);
        oldClass = classroom(campusA, closedYear, "Lớp cũ", ag3);
        child1 = child("An", "2023-01-01");
        child2 = child("Bình", "2023-02-01");
        child3 = child("Chi", "2023-03-01");
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
        jdbc.update(
                "UPDATE staff_assignments SET valid_from='2025-09-01',valid_to='2026-05-31' WHERE user_id=? AND classroom_id=?",
                teacherA.userId(),
                oldClass);
        teach(teacherB, classB);

        // Giáo án tuần 26–30/10 của lớp A đã duyệt: Hoạt động học thứ 3 có MT1, thứ 4 có MT1 + MT2
        long plan = insertId(
                "INSERT INTO edu_team_plans (legacy_team_without_source, code, school_year_id, age_group_id, title, status, owner_id) "
                        + "VALUES (true, 'KH-T', " + year + ", " + ag3 + ", 'KH', 'APPROVED', " + principal.userId()
                        + ") RETURNING id");
        long domain = jdbc.queryForObject("SELECT id FROM edu_domains ORDER BY sort_order LIMIT 1", Long.class);
        goal1 = goal(plan, domain, 1, "Trẻ nhận biết màu");
        goal2 = goal(plan, domain, 2, "Trẻ đếm đến 5");
        goal3 = goal(plan, domain, 3, "Trẻ nhảy bật");
        long theme = insertId("INSERT INTO edu_themes (team_plan_id, sort_order, name, abbr) VALUES (" + plan
                + ", 1, 'Bản thân', 'BT') RETURNING id");
        String content = "{\"grid\":{\"hoc\":{\"days\":[\"\",\"Toán\",\"Tạo hình\",\"\",\"\"]}},\"picks\":{"
                + "\"hoc.1\":[{\"id\":\"p1\",\"goalId\":\"" + goal1 + "\"}],"
                + "\"hoc.2\":[{\"id\":\"p2\",\"goalId\":\"" + goal1 + "\"},{\"id\":\"p3\",\"goalId\":\"" + goal2
                + "\"}]}}";
        weekDoc = insertId("INSERT INTO edu_class_docs (doc_key, kind, classroom_id, team_plan_id, theme_id, "
                + "week_start, status, content, created_by, updated_by) VALUES ('w-a', 'week', " + classA + ", "
                + plan + ", " + theme + ", '2026-10-26', 'APPROVED', '" + content + "'::jsonb, "
                + principal.userId() + ", " + principal.userId() + ") RETURNING id");
        // Spec 003 FR-008: chỉ chấm theo tuần có cả giáo án ngày đã ban hành (kế hoạch giáo dục ngày)
        jdbc.update(
                "INSERT INTO edu_class_docs (doc_key, kind, classroom_id, team_plan_id, theme_id, week_start, status, content, "
                        + "created_by, updated_by) SELECT 'd-a', 'day', classroom_id, team_plan_id, theme_id, week_start, status, '{}'::jsonb, "
                        + "created_by, updated_by FROM edu_class_docs WHERE id = " + weekDoc);
    }

    @Test
    void historicalDailyRowsDistinguishTransferAndLeavingSchool() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(
                                new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()),
                                new SaveAttendanceRequest.Entry(child2, AttendanceStatus.PRESENT, Map.of()))));
        jdbc.update("UPDATE enrollments SET valid_to='2026-10-27' WHERE child_id IN (?,?)", child1, child2);
        jdbc.update(
                "INSERT INTO enrollments(child_id,classroom_id,valid_from) VALUES (?,?,'2026-10-28')", child1, classB);
        jdbc.update(
                "UPDATE children SET status='LEFT',left_reason='Kiểm thử nghỉ học',left_on='2026-10-28' WHERE id=?",
                child2);
        jdbc.update("UPDATE attendance_records SET date='2026-10-27' WHERE child_id IN (?,?)", child1, child2);
        var sheet = service.getDailySheet(teacherA, classA, YESTERDAY);
        assertThat(sheet.rows())
                .extracting(DailySheetResponse.Row::historyStatus)
                .containsExactlyInAnyOrder("TRANSFERRED", "LEFT_SCHOOL");
        assertThat(attendanceService
                        .getClassAttendance(teacherA, classA, YESTERDAY)
                        .children())
                .extracting(r -> r.child().historyStatus())
                .containsExactlyInAnyOrder("TRANSFERRED", "LEFT_SCHOOL");
        assertThat(healthService
                        .getClassChildHealth(teacherA, classA, child1, YESTERDAY)
                        .historyStatus())
                .isEqualTo("TRANSFERRED");
        assertThat(healthService
                        .getClassChildHealth(teacherA, classA, child2, YESTERDAY)
                        .historyStatus())
                .isEqualTo("LEFT_SCHOOL");
    }

    @Test
    @DisplayName("ATT-02a: đánh giá ngày giữ Không ăn theo điểm danh, kể cả request từ tab cũ")
    void dailyMealsFollowAttendanceParticipation() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(new SaveAttendanceRequest.Entry(
                                child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, false)))));
        DailySheetResponse.Row initial =
                service.getDailySheet(teacherA, classA, TODAY).rows().getFirst();
        assertThat(initial.skippedMeals()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(initial.generalRecorded()).isFalse();
        SaveDailyGeneralRequest request = new SaveDailyGeneralRequest(
                TODAY,
                List.of(new SaveDailyGeneralRequest.Entry(
                        child1,
                        false,
                        null,
                        null,
                        Map.of(MealSession.LUNCH, EatingLevel.AN_HET),
                        NapLevel.NGU_TOT,
                        Emotion.HAPPY,
                        Participation.BINH_THUONG,
                        null,
                        null,
                        false,
                        "")));
        service.saveGeneral(teacherA, classA, request);
        assertThat(service.getDailySheet(teacherA, classA, TODAY)
                        .rows()
                        .getFirst()
                        .meals())
                .containsEntry(MealSession.LUNCH, EatingLevel.KHONG_AN);
        assertThat(jdbc.queryForObject(
                        "SELECT meals ->> 'LUNCH' FROM daily_observations WHERE child_id = ? AND date = ?",
                        String.class,
                        child1,
                        TODAY))
                .isEqualTo("KHONG_AN");
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(new SaveAttendanceRequest.Entry(
                                child1,
                                AttendanceStatus.PRESENT,
                                Map.of(MealSession.LUNCH, true, MealSession.AFTERNOON, true)))));
        assertThat(service.getDailySheet(teacherA, classA, TODAY)
                        .rows()
                        .getFirst()
                        .skippedMeals())
                .isEmpty();
        service.saveGeneral(teacherA, classA, request);
        assertThat(service.getDailySheet(teacherA, classA, TODAY)
                        .rows()
                        .getFirst()
                        .meals())
                .containsEntry(MealSession.LUNCH, EatingLevel.AN_HET);
    }

    @Test
    @DisplayName("OBS-01: trẻ vắng thì mục tiêu của giáo án ngày đó tính Chưa đạt ở tổng quan lớp")
    void absentChildCountsDayGoalsAsNotMetInClassDetail() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(
                                new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true)),
                                new SaveAttendanceRequest.Entry(child2, AttendanceStatus.UNEXCUSED, Map.of()))));
        ClassAssessmentDetailResponse detail = overviewService.getClassDetail(principal, classA, "DAY");
        // Giáo án thứ 4 (hôm nay) có 2 mục tiêu ở Hoạt động học
        assertThat(detail.children())
                .filteredOn(r -> r.childId().equals(child2))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.chuaDat()).isEqualTo(2);
                    assertThat(r.dat()).isZero();
                });
        // Trẻ có mặt chưa đánh giá: không có lượt nào
        assertThat(detail.children())
                .filteredOn(r -> r.childId().equals(child1))
                .singleElement()
                .satisfies(r -> assertThat(r.chuaDat()).isZero());
    }

    @Test
    @DisplayName("Hồ sơ phát triển trả ngày trẻ đã điểm danh vắng, trẻ có mặt thì không")
    void developmentListsAbsentDays() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(
                                new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true)),
                                new SaveAttendanceRequest.Entry(child2, AttendanceStatus.UNEXCUSED, Map.of()))));
        assertThat(evaluations.development(principal, child2, TODAY, TODAY).absentDays())
                .containsExactly(TODAY);
        assertThat(evaluations.development(principal, child1, TODAY, TODAY).absentDays())
                .isEmpty();
    }

    @Test
    void sheetWithoutDateOpensLatestSchoolDaySkippingConfiguredHoliday() {
        // Hôm nay (28/10) là ngày nghỉ trong cấu hình năm học: mở màn không chọn ngày thì ra ngày học gần nhất (27/10)
        long year = jdbc.queryForObject("SELECT school_year_id FROM classrooms WHERE id = ?", Long.class, classA);
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) VALUES (?, ?, ?, 'Nghỉ thử')",
                year,
                TODAY,
                TODAY);
        assertThat(service.getDailySheet(teacherA, classA, null).date()).isEqualTo(YESTERDAY);
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
        // Trẻ vắng vẫn hiện riêng, không có dòng đánh giá
        assertThat(sheet.absentChildren()).singleElement().satisfies(a -> {
            assertThat(a.childId()).isEqualTo(child2);
            assertThat(a.attendanceStatus()).isEqualTo(AttendanceStatus.UNEXCUSED);
        });
        assertThat(sheet.rows().getFirst().health().signs()).containsExactly(HealthSymptom.COUGH);
        assertThat(sheet.rows().getFirst().recorded()).isFalse();
        assertThat(sheet.rows().getFirst().generalRecorded()).isFalse();
        assertThat(sheet.lesson().classDocId()).isEqualTo(weekDoc);
        assertThat(sheet.lesson().activities()).singleElement().satisfies(a -> {
            assertThat(a.key()).isEqualTo("hoc");
            assertThat(a.goals()).extracting(g -> g.id()).containsExactly(goal1, goal2);
        });
        // Chấm KTKN trước: trẻ đã có ghi nhận nhưng Hoạt động chung (ăn, ngủ, cảm xúc) vẫn chưa lưu
        service.saveSkills(teacherA, classA, skills(TODAY, child1, null, activity("hoc", List.of(), List.of(goal2))));
        DailySheetResponse.Row skillsOnly =
                service.getDailySheet(teacherA, classA, TODAY).rows().getFirst();
        assertThat(skillsOnly.recorded()).isTrue();
        assertThat(skillsOnly.skillsRecorded()).isTrue();
        assertThat(skillsOnly.generalRecorded()).isFalse();

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
        assertThat(row.generalRecorded()).isTrue();
        assertThat(row.skillsRecorded()).isTrue();
        assertThat(row.comment()).isEqualTo("Bé ngoan");
        assertThat(row.flag()).isTrue();
        // Tham gia cả ngày lưu ở bản ghi ngày, không tạo kết quả theo hoạt động
        assertThat(row.participation()).isEqualTo(Participation.TICH_CUC);
        assertThat(row.meals())
                .containsEntry(MealSession.LUNCH, EatingLevel.AN_HET)
                .containsEntry(MealSession.AFTERNOON, EatingLevel.KHONG_AN)
                .doesNotContainKey(MealSession.MORNING);
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
        // AUTH-20: PHT điểm trường khác xem được (chỉ xem), không ghi
        assertThat(service.getDailySheet(withSchoolWide(vpB, Permission.ASSESSMENT_VIEW), classA, TODAY))
                .isNotNull();
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
        jdbc.update("UPDATE edu_class_docs SET status = 'REVIEW' WHERE id = ?", weekDoc);
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

        // PHT điểm trường khác chỉ xem lịch sử khi ASSESSMENT_VIEW được cấp scope SCHOOL; không mở/thu hồi được.
        assertThat(unlockService.getUnlocks(
                        withSchoolWide(vpB, Permission.ASSESSMENT_VIEW), null, null, null, null, null))
                .extracting(AssessmentDayUnlockResponse::id)
                .containsExactly(opened.id());
        // PHT có quyền Mở lại ở cơ sở mình nhưng bị thu hồi quyền Xem: vẫn thấy lần mở lại của cơ sở mình
        CurrentUser unlockOnly = new CurrentUser(
                vpA.userId(),
                vpA.keycloakUserId(),
                vpA.username(),
                vpA.fullName(),
                null,
                vpA.roles(),
                Set.of(Permission.ASSESSMENT_UNLOCK));
        assertThat(unlockService.getUnlocks(unlockOnly, null, null, null, null, null))
                .extracting(AssessmentDayUnlockResponse::id)
                .containsExactly(opened.id());
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
    void receivingTeacherCanReadPreviousMonthHistoryWhileOldTeacherCannotReadNewClassDays() {
        attendance(child1, classA, TODAY);
        service.saveGeneral(teacherA, classA, general(TODAY, child1, "Bé vui vẻ tại lớp cũ"));
        jdbc.update("UPDATE daily_observations SET date=? WHERE child_id=?", YESTERDAY, child1);
        jdbc.update("UPDATE attendance_records SET date=? WHERE child_id=?", YESTERDAY, child1);
        jdbc.update("UPDATE enrollments SET valid_to=? WHERE child_id=? AND classroom_id=?", YESTERDAY, child1, classA);
        jdbc.update("INSERT INTO enrollments(child_id,classroom_id,valid_from) VALUES (?,?,?)", child1, classB, TODAY);
        entityManager.clear();
        attendance(child1, classB, TODAY);
        service.saveGeneral(teacherB, classB, general(TODAY, child1, "Bé vui vẻ tại lớp mới"));
        var receiving = evaluations.development(teacherB, child1, YESTERDAY, TODAY);
        assertThat(receiving.days()).extracting(d -> d.classId()).containsExactlyInAnyOrder(classA, classB);
        var withoutView = new CurrentUser(
                teacherB.userId(),
                teacherB.keycloakUserId(),
                teacherB.username(),
                teacherB.fullName(),
                null,
                teacherB.roles(),
                Set.of(Permission.ATTENDANCE_VIEW));
        expectCode(() -> evaluations.development(withoutView, child1, YESTERDAY, TODAY), ApiCode.CHILD_ACCESS_DENIED);
        var pastOnly = evaluations.development(teacherB, child1, YESTERDAY, YESTERDAY);
        assertThat(pastOnly.days()).extracting(d -> d.classId()).containsExactly(classA);
        var old = evaluations.development(teacherA, child1, YESTERDAY, TODAY);
        assertThat(old.days()).extracting(d -> d.classId()).containsExactly(classA);
        assertThat(old.child().historyStatus()).isEqualTo("TRANSFERRED");
        expectCode(() -> evaluations.development(teacherA, child1, TODAY, TODAY), ApiCode.CHILD_ACCESS_DENIED);
    }

    @Test
    @DisplayName("OBS-35: cô lớp mới xem đánh giá ngày, nhận xét tuần đã gửi ở lớp cũ của trẻ chuyển đến, chỉ xem")
    void receivingTeacherSeesTransferredChildOldDaysAndSentWeeklyCommentsOnly() {
        attendance(child1, classA, TODAY);
        attendance(child2, classA, TODAY);
        service.saveGeneral(teacherA, classA, general(TODAY, child1, "Bé vui ở lớp cũ"));
        service.saveGeneral(teacherA, classA, general(TODAY, child2, "Bạn cùng lớp cũ"));
        jdbc.update("UPDATE daily_observations SET date=?", YESTERDAY);
        jdbc.update("UPDATE attendance_records SET date=?", YESTERDAY);
        jdbc.update("UPDATE enrollments SET valid_to=? WHERE child_id=? AND classroom_id=?", YESTERDAY, child1, classA);
        jdbc.update("INSERT INTO enrollments(child_id,classroom_id,valid_from) VALUES (?,?,?)", child1, classB, TODAY);
        jdbc.update(
                "INSERT INTO weekly_comments (child_id, classroom_id, campus_id, school_year_id, week_start, content, status, "
                        + "sent_at, sent_by, created_by, created_at, updated_at) SELECT ?, id, campus_id, school_year_id, "
                        + "'2026-10-19', 'Tuần trước bé ngoan', 'SENT', now(), ?, ?, now(), now() FROM classrooms WHERE id=?",
                child1,
                teacherA.userId(),
                teacherA.userId(),
                classA);
        jdbc.update(
                "INSERT INTO weekly_comments (child_id, classroom_id, campus_id, school_year_id, week_start, content, status, "
                        + "created_by, created_at, updated_at) SELECT ?, id, campus_id, school_year_id, '2026-10-26', "
                        + "'Nháp chưa gửi', 'DRAFT', ?, now(), now() FROM classrooms WHERE id=?",
                child1,
                teacherA.userId(),
                classA);
        jdbc.update(
                "INSERT INTO daily_health_entries (request_id, child_id, classroom_id, campus_id, date, temperature, "
                        + "observation, symptoms, note, recorded_by, recorded_at) SELECT 'h1', ?, id, campus_id, ?, 37.5, "
                        + "'NOTED', 'COUGH', 'Ho nhẹ', ?, now() FROM classrooms WHERE id=?",
                child1,
                YESTERDAY,
                teacherA.userId(),
                classA);
        // Sức khỏe của bạn cùng lớp cũ không lộ sang lớp mới
        jdbc.update(
                "INSERT INTO daily_health_entries (request_id, child_id, classroom_id, campus_id, date, observation, "
                        + "recorded_by, recorded_at) SELECT 'h2', ?, id, campus_id, ?, 'NO_CONCERN', ?, now() "
                        + "FROM classrooms WHERE id=?",
                child2,
                YESTERDAY,
                teacherA.userId(),
                classA);
        String snapshot = "{\"classId\":" + classA + ",\"className\":\"Lớp A\",\"themeId\":901,"
                + "\"themeName\":\"Gia đình\",\"startDate\":\"2026-10-12\",\"endDate\":\"2026-10-23\","
                + "\"status\":\"CONFIRMED\",\"goals\":[{\"goalId\":51,\"goalNo\":5,\"text\":\"Kể tên người thân\",\"metChildren\":1,"
                + "\"assessedChildren\":2}],"
                + "\"rows\":[{\"childId\":" + child1
                + ",\"fullName\":\"x\",\"cells\":[{\"goalId\":51,\"met\":true,\"manual\":false}],"
                + "\"met\":1,\"notMet\":0,\"rate\":100,\"passed\":true},{\"childId\":" + child2
                + ",\"fullName\":\"y\",\"cells\":[{\"goalId\":51,\"met\":false,\"manual\":false}],\"met\":0,\"notMet\":1}],"
                + "\"assessedChildren\":2,\"passedChildren\":1,\"notPassedChildren\":1,\"passPercent\":70,"
                + "\"canEdit\":false,\"canConfirm\":false,\"confirmedByName\":\"teachera\",\"confirmedAt\":\"2026-10-24T03:00:00Z\"}";
        jdbc.update(
                "INSERT INTO theme_evaluations (classroom_id, campus_id, school_year_id, theme_id, theme_name, start_date, "
                        + "end_date, cells, confirmed_by, confirmed_at, updated_at, presentation_snapshot) SELECT id, "
                        + "campus_id, school_year_id, 901, 'Gia đình', '2026-10-12', '2026-10-23', '[]', ?, now(), now(), "
                        + "CAST(? AS jsonb) FROM classrooms WHERE id=?",
                teacherA.userId(),
                snapshot,
                classA);
        // Bảng chủ đề chưa xác nhận (nháp) không trả về
        jdbc.update(
                "INSERT INTO theme_evaluations (classroom_id, campus_id, school_year_id, theme_id, theme_name, start_date, "
                        + "end_date, cells, updated_at) SELECT id, campus_id, school_year_id, 902, 'Nháp', '2026-10-19', "
                        + "'2026-10-30', '[]', now() FROM classrooms WHERE id=?",
                classA);
        entityManager.clear();
        int observations = jdbc.queryForObject("SELECT count(*) FROM daily_observations", Integer.class);

        var history = transferredHistory.findHistory(teacherB, classB, LocalDate.of(2026, 10, 1), TODAY);

        assertThat(history.children()).extracting(c -> c.childId()).containsExactly(child1);
        var child = history.children().getFirst();
        assertThat(child.stays()).singleElement().satisfies(s -> {
            assertThat(s.classId()).isEqualTo(classA);
            assertThat(s.className()).isEqualTo("Lớp A");
            assertThat(s.to()).isEqualTo(YESTERDAY);
        });
        assertThat(child.days()).singleElement().satisfies(d -> {
            assertThat(d.day().date()).isEqualTo(YESTERDAY);
            assertThat(d.day().classId()).isEqualTo(classA);
            assertThat(d.day().comment()).isEqualTo("Bé vui ở lớp cũ");
            assertThat(d.recordedByName()).isEqualTo("teachera");
        });
        assertThat(child.weeklyComments()).singleElement().satisfies(c -> {
            assertThat(c.content()).isEqualTo("Tuần trước bé ngoan");
            assertThat(c.className()).isEqualTo("Lớp A");
            assertThat(c.sentByName()).isEqualTo("teachera");
        });
        assertThat(child.attendance()).singleElement().satisfies(a -> {
            assertThat(a.date()).isEqualTo(YESTERDAY);
            assertThat(a.status()).isEqualTo(com.carenest.utils.enums.AttendanceStatus.PRESENT);
            assertThat(a.className()).isEqualTo("Lớp A");
            assertThat(a.recordedById()).isEqualTo(principal.userId());
            assertThat(a.recordedByName()).isNotBlank();
        });
        assertThat(child.health()).singleElement().satisfies(h -> {
            assertThat(h.date()).isEqualTo(YESTERDAY);
            assertThat(h.temperature()).isEqualByComparingTo("37.5");
            assertThat(h.symptoms()).containsExactly(com.carenest.utils.enums.HealthSymptom.COUGH);
            assertThat(h.className()).isEqualTo("Lớp A");
            assertThat(h.recordedByName()).isEqualTo("teachera");
        });
        assertThat(child.themeResults()).singleElement().satisfies(t -> {
            assertThat(t.themeName()).isEqualTo("Gia đình");
            assertThat(t.className()).isEqualTo("Lớp A");
            assertThat(t.passed()).isTrue();
            assertThat(t.goals())
                    .singleElement()
                    .satisfies(g -> assertThat(g.met()).isTrue());
            assertThat(t.confirmedById()).isEqualTo(teacherA.userId());
            assertThat(t.confirmedByName()).isEqualTo("teachera");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_observations", Integer.class))
                .isEqualTo(observations);
        // Lớp cũ không tự có quyền xem trẻ của lớp mới; khoảng ngày sai bị từ chối
        expectCode(
                () -> transferredHistory.findHistory(teacherA, classB, YESTERDAY, TODAY), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(
                () -> transferredHistory.findHistory(teacherB, classB, TODAY, YESTERDAY), ApiCode.INVALID_DATE_RANGE);
        expectCode(
                () -> transferredHistory.findHistory(teacherB, classB, TODAY.minusDays(62), TODAY),
                ApiCode.DATE_RANGE_TOO_LONG);
        // Lớp A không có trẻ chuyển đến
        assertThat(transferredHistory
                        .findHistory(teacherA, classA, YESTERDAY, TODAY)
                        .children())
                .isEmpty();
    }

    @Test
    void assessmentClassListDoesNotRequireAttendancePermissions() {
        var user = new CurrentUser(
                teacherA.userId(),
                teacherA.keycloakUserId(),
                teacherA.username(),
                teacherA.fullName(),
                null,
                teacherA.roles(),
                Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_RECORD));
        var cls = service.listClasses(user, null).stream()
                .filter(c -> c.id().equals(classA))
                .findFirst()
                .orElseThrow();
        assertThat(cls.teaches()).isTrue();
        assertThat(cls.canRecord()).isTrue();
        assertThat(cls.historicalReadOnly()).isFalse();
        assertThat(service.listClasses(user, null)).extracting(c -> c.id()).doesNotContain(classB);
        assertThat(service.getDailySheet(user, classA, TODAY).canRecord()).isTrue();
    }

    @Test
    void assessmentOnlyTeacherIsNotMislabeledAsHistoricalAttendanceClass() {
        var user = new CurrentUser(
                teacherA.userId(),
                teacherA.keycloakUserId(),
                teacherA.username(),
                teacherA.fullName(),
                null,
                teacherA.roles(),
                Set.of(Permission.ATTENDANCE_VIEW, Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_RECORD));
        var cls = attendanceService.getClasses(user, null).stream()
                .filter(c -> c.id().equals(classA))
                .findFirst()
                .orElseThrow();
        assertThat(cls.teaches()).isTrue();
        assertThat(cls.canRecord()).isFalse();
        assertThat(cls.historicalReadOnly()).isFalse();
        assertThat(service.getDailySheet(user, classA, TODAY).canRecord()).isTrue();
    }

    @Test
    void formerTeacherReadsOnlyAssignedDaysInActiveAndClosedYears() {
        jdbc.update(
                "UPDATE staff_assignments SET valid_to='2026-10-27' WHERE user_id=? AND classroom_id=?",
                teacherA.userId(),
                classA);
        assertThat(service.getDailySheet(teacherA, classA, YESTERDAY).canRecord())
                .isFalse();
        expectCode(() -> service.getDailySheet(teacherA, classA, TODAY), ApiCode.CLASS_ACCESS_DENIED);
        var historyClass = attendanceService.getClasses(teacherA, null).stream()
                .filter(c -> c.id().equals(classA))
                .findFirst()
                .orElseThrow();
        assertThat(historyClass.historicalReadOnly()).isTrue();
        assertThat(historyClass.lastReadableDate()).isEqualTo(YESTERDAY);
        assertThat(evaluations.developmentChildren(teacherA, null, classA))
                .extracting(c -> c.id())
                .contains(child1);
        assertThat(evaluations
                        .development(teacherA, child1, YESTERDAY, YESTERDAY)
                        .child()
                        .id())
                .isEqualTo(child1);
        expectCode(() -> evaluations.development(teacherA, child1, TODAY, TODAY), ApiCode.CHILD_ACCESS_DENIED);
        jdbc.update(
                "UPDATE staff_assignments SET valid_from='2026-05-01',valid_to='2026-05-31' WHERE user_id=? AND classroom_id=?",
                teacherA.userId(),
                oldClass);
        entityManager.clear();
        assertThat(service.getDailySheet(teacherA, oldClass, LocalDate.of(2026, 5, 20))
                        .yearReadOnly())
                .isTrue();
        expectCode(
                () -> service.getDailySheet(teacherA, oldClass, LocalDate.of(2025, 9, 2)), ApiCode.CLASS_ACCESS_DENIED);
    }

    @Test
    void closedYearIsReadOnly() {
        LocalDate oldDay = LocalDate.of(2026, 5, 20);
        DailySheetResponse sheet = service.getDailySheet(teacherA, oldClass, oldDay);
        assertThat(sheet.yearReadOnly()).isTrue();
        assertThat(sheet.canRecord()).isFalse();
        expectCode(
                () -> service.saveGeneral(teacherA, oldClass, general(oldDay, child1, "x")),
                ApiCode.CLASS_ACCESS_DENIED);
        expectCode(
                () -> unlockService.unlockDay(
                        principal, new AssessmentDayUnlockRequest(oldClass, oldDay, NOW.plusSeconds(3600), "Bổ sung")),
                ApiCode.ASSESSMENT_UNLOCK_YEAR_NOT_ACTIVE);
    }

    @Test
    void closedYearHistoricalReadCannotBypassTheGrantedCampus() {
        CurrentUser oldVp = user("vp.historical", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2025-09-01', '2026-05-31')",
                oldVp.userId(),
                campusA);
        LocalDate oldDay = LocalDate.of(2026, 5, 20);
        Long otherCampus = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, classB);
        CurrentUser pinnedHistoric = new CurrentUser(
                oldVp.userId(),
                oldVp.keycloakUserId(),
                oldVp.username(),
                oldVp.fullName(),
                oldVp.email(),
                oldVp.roles(),
                oldVp.permissions(),
                oldVp.schoolWidePermissions(),
                Map.of(Permission.ASSESSMENT_VIEW, campusA));
        CurrentUser pinnedOther = new CurrentUser(
                oldVp.userId(),
                oldVp.keycloakUserId(),
                oldVp.username(),
                oldVp.fullName(),
                oldVp.email(),
                oldVp.roles(),
                oldVp.permissions(),
                oldVp.schoolWidePermissions(),
                Map.of(Permission.ASSESSMENT_VIEW, otherCampus));
        assertThat(service.getDailySheet(oldVp, oldClass, oldDay).yearReadOnly())
                .isTrue();
        assertThat(service.getDailySheet(pinnedHistoric, oldClass, oldDay).yearReadOnly())
                .isTrue();
        expectCode(() -> service.getDailySheet(pinnedOther, oldClass, oldDay), ApiCode.CLASS_ACCESS_DENIED);
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
        // Mặc định PHT chỉ thấy campus A.
        assertThat(week.rows()).extracting(r -> r.classId()).containsExactly(classA);
        assertThat(overviewService
                        .getOverview(withSchoolWide(vpA, Permission.ASSESSMENT_VIEW), "WEEK", null, null, null, null)
                        .rows())
                .extracting(r -> r.classId())
                .contains(classA, classB);
        assertThat(week.rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> {
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
        // PHT điểm trường khác cần ASSESSMENT_VIEW scope SCHOOL; giáo viên không phải lớp mình vẫn bị chặn.
        assertThat(overviewService
                        .getClassDetail(withSchoolWide(vpB, Permission.ASSESSMENT_VIEW), classA, "DAY")
                        .classId())
                .isEqualTo(classA);
        expectCode(() -> overviewService.getClassDetail(teacherA, classA, "DAY"), ApiCode.CLASS_ACCESS_DENIED);
        // Tổ trưởng nhóm tuổi xem tổng quan và chi tiết các lớp cùng nhóm tuổi (lớp A, B)
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from) "
                        + "SELECT ?, 'AGE_GROUP_TEAM_LEADER', age_group_id, school_year_id, '2026-01-01' FROM classrooms WHERE id = ?",
                teacherB.userId(),
                classA);
        assertThat(overviewService
                        .getOverview(teacherB, "DAY", null, null, null, null)
                        .rows())
                .extracting(r -> r.classId())
                .containsExactlyInAnyOrder(classA, classB);
        assertThat(overviewService.getClassDetail(teacherB, classA, "DAY").classId())
                .isEqualTo(classA);
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
        assertThat(overviewService
                        .getClassDetail(withSchoolWide(vpB, Permission.ASSESSMENT_VIEW), classA, "DAY")
                        .classId())
                .isEqualTo(classA);
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
        long nextPlan = insertId("INSERT INTO edu_team_plans "
                + "(code, school_year_id, age_group_id, title, status, owner_id, version_no, source_plan_id, legacy_team_without_source) "
                + "SELECT 'KH-T-V2', school_year_id, age_group_id, title, status, owner_id, 2, id, legacy_team_without_source "
                + "FROM edu_team_plans WHERE id = (SELECT team_plan_id FROM edu_class_docs WHERE id = "
                + weekDoc + ") RETURNING id");
        long domain = jdbc.queryForObject("SELECT domain_id FROM edu_plan_goals WHERE id = ?", Long.class, goal1);
        long copiedGoal = goal(nextPlan, domain, 1, "Trẻ nhận biết màu");
        long addedGoal = goal(nextPlan, domain, 2, "Trẻ nhảy bật");
        jdbc.update("UPDATE edu_plan_goals SET lineage_id = ? WHERE id = ?", goal1, copiedGoal);
        jdbc.update("UPDATE edu_plan_goals SET lineage_id = ? WHERE id = ?", goal3, addedGoal);
        long nextTheme = insertId("INSERT INTO edu_themes (team_plan_id, sort_order, name, abbr, lineage_id) "
                + "SELECT " + nextPlan + ", 1, name, abbr, id FROM edu_themes "
                + "WHERE id = (SELECT theme_id FROM edu_class_docs WHERE id = " + weekDoc + ") RETURNING id");
        String content =
                "{\"picks\":{\"hoc.2\":[{\"goalId\":\"" + copiedGoal + "\"},{\"goalId\":\"" + addedGoal + "\"}]}}";
        long nextDoc = insertId("INSERT INTO edu_class_docs "
                + "(doc_key, kind, classroom_id, team_plan_id, theme_id, week_start, status, content, created_by, updated_by, source_doc_id) "
                + "SELECT 'w-a-v2', kind, classroom_id, " + nextPlan + ", " + nextTheme
                + ", week_start, status, '" + content + "'::jsonb, created_by, updated_by, id "
                + "FROM edu_class_docs WHERE id = " + weekDoc + " RETURNING id");
        jdbc.update(
                "INSERT INTO edu_class_docs (doc_key, kind, classroom_id, team_plan_id, theme_id, week_start, status, content, "
                        + "created_by, updated_by) SELECT 'd-a-v2', 'day', classroom_id, team_plan_id, theme_id, week_start, status, '{}'::jsonb, "
                        + "created_by, updated_by FROM edu_class_docs WHERE id = " + nextDoc);
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
        var data = sources.load(classrooms.findClassroomInfo(classA).orElseThrow(), List.of(child1), TODAY, TODAY, true)
                .get(child1);
        assertThat(data.days()).singleElement().satisfies(day -> {
            assertThat(day.met())
                    .extracting(com.carenest.dto.observation.EvaluationDtos.GoalRef::id)
                    .containsExactly(copiedGoal);
            assertThat(day.notMet()).isEmpty();
        });
        assertThat(data.structured().goals().byGoal().values()).singleElement().satisfies(count -> {
            assertThat(count.dat()).isEqualTo(1);
            assertThat(count.chuaDat()).isZero();
            assertThat(count.canCoGang()).isZero();
        });
        service.saveSkills(
                teacherA,
                classA,
                skills(TODAY, child1, null, activity("hoc", result.goalsMet(), result.goalsNotMet())));
        assertThat(jdbc.queryForObject(
                        "SELECT class_doc_id FROM daily_activity_results WHERE child_id = ?", Long.class, child1))
                .isEqualTo(nextDoc);
    }

    @Test
    void monthlyDraftSurvivesTransferWithoutDuplicateOrLostName() {
        var month = com.carenest.utils.enums.EvaluationPeriodType.MONTH;
        attendance(child1, classA, LocalDate.of(2026, 9, 15));
        var old = evaluations
                .list(teacherA, month, null, classA, LocalDate.of(2026, 9, 1))
                .getFirst();
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-09-19' WHERE child_id = ? AND classroom_id = ?",
                child1,
                classA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-20')",
                child1,
                classB);
        attendance(child1, classB, LocalDate.of(2026, 9, 21));
        entityManager.clear();
        var next = evaluations
                .list(teacherB, month, null, classB, LocalDate.of(2026, 9, 1))
                .getFirst();
        assertThat(next.id()).isNotEqualTo(old.id());
        assertThat(evaluations.list(teacherB, month, null, classB, LocalDate.of(2026, 9, 1)))
                .hasSize(1);
        assertThat(evaluations.get(teacherA, old.id()).child().fullName()).isEqualTo("An");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM child_evaluations WHERE child_id = ? AND period_type = 'MONTH'",
                        Integer.class,
                        child1))
                .isEqualTo(2);
        expectCode(() -> evaluations.get(teacherB, old.id()), ApiCode.EVALUATION_VIEW_FORBIDDEN);
    }

    private static final String AI_TEXT = "{TEN} đi học 1 ngày trong tháng 09/2026, hay giúp bạn xếp đồ chơi. "
            + "Cô và gia đình tiếp tục khuyến khích con tự tin hơn khi tham gia hoạt động ở lớp.";

    // Tháng 9: 1 ngày đi học có nhận xét của cô (chứa ngày sinh, số điện thoại) và 1 nhận xét tuần đã gửi
    private long monthWithTeacherNotes() {
        attendance(child1, classA, LocalDate.of(2026, 9, 15));
        jdbc.update(
                "INSERT INTO daily_observations (child_id, classroom_id, campus_id, date, meals, behaviors, flag, comment,"
                        + " recorded_by, recorded_at) SELECT ?, c.id, c.campus_id, '2026-09-15', '{}', '', true, ?, ?, now()"
                        + " FROM classrooms c WHERE c.id = ?",
                child1,
                "An hay giúp bạn xếp đồ chơi, sinh ngày 01/01/2023, gọi 0912345678.",
                principal.userId(),
                classA);
        jdbc.update(
                "INSERT INTO weekly_comments (child_id, classroom_id, campus_id, school_year_id, week_start, content, status,"
                        + " draft_source, sent_at, sent_by, created_by, created_at, updated_by, updated_at)"
                        + " SELECT ?, c.id, c.campus_id, c.school_year_id, '2026-09-14', 'Tuần này An đi học đều.', 'SENT',"
                        + " 'MANUAL', now(), ?, ?, now(), ?, now() FROM classrooms c WHERE c.id = ?",
                child1,
                principal.userId(),
                principal.userId(),
                principal.userId(),
                classA);
        entityManager.clear();
        return evaluations
                .list(
                        teacherA,
                        com.carenest.utils.enums.EvaluationPeriodType.MONTH,
                        null,
                        classA,
                        LocalDate.of(2026, 9, 1))
                .getFirst()
                .id();
    }

    private static AiRawResult aiResult(String content) {
        return new AiRawResult(content, "groq", "fake-model", "observation/period_comment_v2", 100, 50, 10);
    }

    @Test
    @DisplayName("OBS-34: GV bấm AI viết nháp ⇒ nháp AI kèm model, prompt version; prompt không có danh tính trẻ")
    void redraftWithAiStoresSourceAndSendsNoIdentity() {
        long id = monthWithTeacherNotes();
        when(aiGateway.isAvailable(AiTask.PERIOD_COMMENT)).thenReturn(true);
        when(aiGateway.complete(any())).thenReturn(aiResult(AI_TEXT));

        var row = evaluations.redraft(teacherA, id, true);

        assertThat(row.aiEnabled()).isTrue();
        assertThat(row.aiDraft().source()).isEqualTo(EvaluationDraftSource.AI);
        assertThat(row.aiDraft().content()).startsWith("An đi học 1 ngày trong tháng 09/2026");
        assertThat(row.status()).isEqualTo(com.carenest.utils.enums.EvaluationStatus.AWAITING_REVIEW);
        assertThat(row.history()).anyMatch(h -> h.action() == com.carenest.utils.enums.EvaluationAction.AI_REDRAFTED);
        assertThat(jdbc.queryForMap(
                        "SELECT draft_source, draft_model, draft_prompt_version FROM child_evaluations WHERE id = ?",
                        id))
                .containsEntry("draft_source", "AI")
                .containsEntry("draft_model", "fake-model")
                .containsEntry("draft_prompt_version", "observation/period_comment_v2");
        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiGateway).complete(request.capture());
        assertThat(request.getValue().task()).isEqualTo(AiTask.PERIOD_COMMENT);
        assertThat(request.getValue().userPrompt())
                .contains("{TEN} hay giúp bạn xếp đồ chơi", "Tuần này {TEN} đi học đều.", "- Đi học 1 ngày.")
                .doesNotContain("0912345678", "01/01/2023", "2023", "An hay", "An đi");
    }

    @Test
    @DisplayName("OBS-34, AI-03: AI tắt ⇒ xin AI vẫn ra nháp mẫu câu, không gọi AI, aiEnabled = false")
    void redraftWithAiDisabledKeepsTemplate() {
        long id = monthWithTeacherNotes();

        var row = evaluations.redraft(teacherA, id, true);

        assertThat(row.aiEnabled()).isFalse();
        assertThat(row.aiDraft().source()).isEqualTo(EvaluationDraftSource.TEMPLATE);
        assertThat(row.aiDraft().content()).contains("tháng 9/2026").doesNotContain("MT");
        verify(aiGateway, never()).complete(any());
    }

    @Test
    @DisplayName("OBS-34, AI-03: không xin AI (thiếu cờ) ⇒ mẫu câu như trước, không gọi AI dù AI đang bật")
    void redraftWithoutAiFlagDoesNotCallAi() {
        long id = monthWithTeacherNotes();
        when(aiGateway.isAvailable(AiTask.PERIOD_COMMENT)).thenReturn(true);

        var row = evaluations.redraft(teacherA, id, false);

        assertThat(row.aiEnabled()).isTrue();
        assertThat(row.aiDraft().source()).isEqualTo(EvaluationDraftSource.TEMPLATE);
        verify(aiGateway, never()).complete(any());
    }

    @Test
    @DisplayName("OBS-34, AI-03: AI lỗi, hết giờ hoặc đầu ra bịa số ⇒ rơi về mẫu câu, không lưu model")
    void redraftFallsBackToTemplateOnFailure() {
        long id = monthWithTeacherNotes();
        when(aiGateway.isAvailable(AiTask.PERIOD_COMMENT)).thenReturn(true);
        when(aiGateway.complete(any())).thenThrow(new AiClientException(AiErrorReason.TIMEOUT, "hết giờ", null));

        var timedOut = evaluations.redraft(teacherA, id, true);
        assertThat(timedOut.aiDraft().source()).isEqualTo(EvaluationDraftSource.TEMPLATE);

        org.mockito.Mockito.doReturn(aiResult(AI_TEXT.replace("1 ngày", "27 ngày")))
                .when(aiGateway)
                .complete(any());
        var invented = evaluations.redraft(teacherA, id, true);
        assertThat(invented.aiDraft().source()).isEqualTo(EvaluationDraftSource.TEMPLATE);
        assertThat(invented.aiDraft().content()).doesNotContain("27 ngày");
        assertThat(jdbc.queryForMap(
                        "SELECT draft_source, draft_model, draft_prompt_version FROM child_evaluations WHERE id = ?",
                        id))
                .containsEntry("draft_source", "TEMPLATE")
                .containsEntry("draft_model", null)
                .containsEntry("draft_prompt_version", null);
    }

    @Test
    @DisplayName("OBS-32, OBS-34: bản đã xác nhận hoặc GV lớp khác không được tạo lại nháp bằng AI, không gọi AI")
    void redraftWithAiKeepsGuards() {
        long id = monthWithTeacherNotes();
        when(aiGateway.isAvailable(AiTask.PERIOD_COMMENT)).thenReturn(true);
        when(aiGateway.complete(any())).thenReturn(aiResult(AI_TEXT));

        expectCode(() -> evaluations.redraft(teacherB, id, true), ApiCode.EVALUATION_EDIT_FORBIDDEN);
        evaluations.confirm(teacherA, id, "Nhận xét tháng 9 đã được giáo viên kiểm tra đầy đủ dữ liệu.");
        expectCode(() -> evaluations.redraft(teacherA, id, true), ApiCode.EVALUATION_ALREADY_CONFIRMED);
        verify(aiGateway, never()).complete(any());
    }

    @Test
    @DisplayName("OBS-34: nháp học kỳ đọc thêm nhận xét tháng đã xác nhận của kỳ")
    void semesterPromptIncludesConfirmedMonthComments() {
        jdbc.update("UPDATE school_years SET semester1_start = start_date, semester1_end = '2026-09-30', "
                + "semester2_start = '2026-10-01', semester2_end = end_date WHERE status = 'ACTIVE'");
        long month = monthWithTeacherNotes();
        evaluations.confirm(teacherA, month, "Tháng 9 con thích nghi tốt, tự xếp đồ chơi gọn gàng sau giờ chơi.");
        entityManager.clear();
        var semester = evaluations
                .list(teacherA, com.carenest.utils.enums.EvaluationPeriodType.SEMESTER, null, classA, null)
                .getFirst();
        when(aiGateway.isAvailable(AiTask.PERIOD_COMMENT)).thenReturn(true);
        when(aiGateway.complete(any())).thenReturn(aiResult(AI_TEXT.replace("tháng 09/2026", "học kỳ")));

        var row = evaluations.redraft(teacherA, semester.id(), true);

        assertThat(row.aiDraft().source()).isEqualTo(EvaluationDraftSource.AI);
        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiGateway).complete(request.capture());
        assertThat(request.getValue().userPrompt())
                .contains("Kỳ nhận xét: học kỳ từ", "Nhận xét kỳ nhỏ hơn cô đã xác nhận:")
                .contains("- Nhận xét tháng 09/2026: Tháng 9 con thích nghi tốt")
                .contains("Nhận xét tuần cô đã gửi phụ huynh:", "Nhận xét hằng ngày của cô:");
    }

    @Autowired
    private com.carenest.service.observation.ClassRatingService ratings;

    @Test
    void classRatingIsBlockedForUpcomingYear() {
        // CFG-YR-01: năm sắp tới chưa hoạt động ⇒ không xếp loại lớp, chỉ xem
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long planned = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-09-06', '2028-05-31', 'PLANNED') RETURNING id");
        long plannedClass = classroom(campusA, planned, "Lớp năm sau", ag3);
        var tot = com.carenest.utils.enums.ClassRatingLevel.TOT;
        var levels = new com.carenest.dto.observation.ClassRatingDtos.Levels(tot, tot, tot, tot, tot, tot, tot, tot);
        var request = new com.carenest.dto.observation.ClassRatingDtos.ClassRatingSaveRequest(
                com.carenest.utils.enums.ClassRatingPeriod.SEMESTER_1,
                List.of(new com.carenest.dto.observation.ClassRatingDtos.ClassRatingItem(plannedClass, levels, null)));

        expectCode(() -> ratings.save(principal, request), ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM class_ratings", Integer.class))
                .isZero();
    }

    @Test
    void classRatingsAreScoredByLeadersOfTheCampusAndReadByTheClassTeacher() {
        var period = com.carenest.utils.enums.ClassRatingPeriod.SEMESTER_1;
        var tot = com.carenest.utils.enums.ClassRatingLevel.TOT;
        var kha = com.carenest.utils.enums.ClassRatingLevel.KHA;
        var levels = new com.carenest.dto.observation.ClassRatingDtos.Levels(tot, tot, kha, tot, tot, tot, kha, tot);
        var item = new com.carenest.dto.observation.ClassRatingDtos.ClassRatingItem(classA, levels, " Lớp làm tốt ");
        var request = new com.carenest.dto.observation.ClassRatingDtos.ClassRatingSaveRequest(period, List.of(item));

        // ASSESSMENT_VIEW scope SCHOOL cho phép xem lớp A, nhưng không tự cấp quyền chấm.
        assertThat(ratings.list(withSchoolWide(vpB, Permission.ASSESSMENT_VIEW), null, period, null)
                        .rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> assertThat(r.canEdit()).isFalse());
        expectCode(() -> ratings.save(vpB, request), ApiCode.CLASS_RATING_FORBIDDEN);

        // Trường chỉ có 1 HT: tên HT điền sẵn chỗ ký bản in (Cần chốt 10/11/2026)
        jdbc.update("UPDATE user_accounts SET primary_role = 'PRINCIPAL' WHERE id = ?", principal.userId());
        assertThat(ratings.list(vpA, null, period, null).principalName()).isEqualTo("principal");
        var saved = ratings.save(vpA, request);
        assertThat(saved.rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.levels()).isEqualTo(levels);
                    assertThat(r.note()).isEqualTo("Lớp làm tốt");
                    assertThat(r.canEdit()).isTrue();
                });
        // Lưu lại cùng kỳ là cập nhật, không thêm dòng
        ratings.save(principal, request);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM class_ratings", Integer.class))
                .isEqualTo(1);

        // GV chỉ thấy lớp mình, chỉ xem; kỳ khác chưa chấm
        assertThat(ratings.list(teacherA, null, period, null).rows())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.classId()).isEqualTo(classA);
                    assertThat(r.levels().overall()).isEqualTo(tot);
                    assertThat(r.canEdit()).isFalse();
                });
        // Tổ trưởng nhóm tuổi xem thêm các lớp cùng nhóm tuổi (lớp B), chỉ xem
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from) "
                        + "SELECT ?, 'AGE_GROUP_TEAM_LEADER', age_group_id, school_year_id, '2026-01-01' FROM classrooms WHERE id = ?",
                teacherA.userId(),
                classA);
        assertThat(ratings.list(teacherA, null, period, null).rows())
                .extracting(r -> r.classId())
                .containsExactlyInAnyOrder(classA, classB);
        assertThat(ratings.list(teacherA, null, period, null).rows()).noneMatch(r -> r.canEdit());

        // Cuối năm chỉ chấm từ tháng cuối năm học; đã chấm tiêu chí thì phải chọn xếp loại chung
        var yearRequest = new com.carenest.dto.observation.ClassRatingDtos.ClassRatingSaveRequest(
                com.carenest.utils.enums.ClassRatingPeriod.YEAR, List.of(item));
        expectCode(() -> ratings.save(principal, yearRequest), ApiCode.CLASS_RATING_NOT_OPEN);
        var noOverall = new com.carenest.dto.observation.ClassRatingDtos.ClassRatingItem(
                classA,
                new com.carenest.dto.observation.ClassRatingDtos.Levels(tot, null, null, null, null, null, null, null),
                null);
        expectCode(
                () -> ratings.save(
                        principal,
                        new com.carenest.dto.observation.ClassRatingDtos.ClassRatingSaveRequest(
                                period, List.of(noOverall))),
                ApiCode.CLASS_RATING_OVERALL_REQUIRED);
        assertThat(ratings.list(principal, null, com.carenest.utils.enums.ClassRatingPeriod.YEAR, null)
                        .rows())
                .allSatisfy(r -> assertThat(r.canEdit()).isFalse());
        assertThat(ratings.list(teacherA, null, com.carenest.utils.enums.ClassRatingPeriod.YEAR, null)
                        .rows()
                        .getFirst()
                        .levels()
                        .overall())
                .isNull();
    }

    @Test
    @DisplayName("GV chỉ có quyền xem mở danh sách đánh giá tháng không tạo bản nháp (không ghi DB)")
    void viewOnlyTeacherListingMonthEvaluationsCreatesNoDrafts() {
        attendance(child1, classA, LocalDate.of(2026, 9, 15));
        var viewOnly = new CurrentUser(
                teacherA.userId(),
                teacherA.keycloakUserId(),
                teacherA.username(),
                teacherA.fullName(),
                null,
                teacherA.roles(),
                Set.of(Permission.ASSESSMENT_VIEW));
        var month = com.carenest.utils.enums.EvaluationPeriodType.MONTH;
        assertThat(evaluations.list(viewOnly, month, null, classA, LocalDate.of(2026, 9, 1)))
                .isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM child_evaluations", Integer.class))
                .isZero();
        assertThat(evaluations.list(teacherA, month, null, classA, LocalDate.of(2026, 9, 1)))
                .isNotEmpty();
    }

    @Test
    void semesterDraftsFollowSemesterDatesOfSchoolYear() {
        var semester = com.carenest.utils.enums.EvaluationPeriodType.SEMESTER;
        // Chưa đặt ngày học kỳ ở Cấu hình thời gian ⇒ chưa có đánh giá học kỳ
        attendance(child1, classA, LocalDate.of(2026, 9, 15));
        assertThat(evaluations.list(teacherA, semester, null, classA, null)).isEmpty();

        jdbc.update("UPDATE school_years SET semester1_start = start_date, semester1_end = '2026-09-30', "
                + "semester2_start = '2026-10-01', semester2_end = end_date WHERE status = 'ACTIVE'");
        var september = evaluations
                .list(
                        teacherA,
                        com.carenest.utils.enums.EvaluationPeriodType.MONTH,
                        null,
                        classA,
                        LocalDate.of(2026, 9, 1))
                .getFirst();
        evaluations.confirm(teacherA, september.id(), "Nhận xét tháng 9 đã được giáo viên kiểm tra đầy đủ dữ liệu.");
        entityManager.clear();
        var rows = evaluations.list(teacherA, semester, null, classA, null);
        // Học kỳ II chưa kết thúc ⇒ chỉ có bản nháp học kỳ I, cho trẻ có đi học trong kỳ
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.childId()).isEqualTo(child1);
            assertThat(r.periodType()).isEqualTo(semester);
            assertThat(r.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        });
        var detail = evaluations.get(teacherA, rows.getFirst().id());
        assertThat(detail.evaluation().aiDraft().content()).contains("học kỳ vừa qua");
        assertThat(detail.monthlyEvaluations())
                .singleElement()
                .satisfies(m -> assertThat(m.id()).isEqualTo(september.id()));
        assertThat(detail.evaluation().aiDraft().content()).doesNotContain("đánh giá tháng", "MT");
        evaluations.confirm(teacherA, rows.getFirst().id(), "Nhận xét học kỳ I đã được giáo viên kiểm tra đầy đủ.");
        assertThat(evaluations.list(teacherA, semester, null, classA, null)).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM child_evaluations WHERE period_type = 'SEMESTER'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AI-03: bản nháp hệ thống tạo từ mẫu câu ghi vết TEMPLATE_DRAFTED, không ghi là AI")
    void templateDraftIsLoggedAsTemplate() {
        jdbc.update("UPDATE school_years SET end_date = '2026-10-31', semester1_start = start_date,"
                + " semester1_end = '2026-09-30', semester2_start = '2026-10-01', semester2_end = '2026-10-27'"
                + " WHERE status = 'ACTIVE'");
        entityManager.clear();
        long id = evaluations
                .list(teacherA, com.carenest.utils.enums.EvaluationPeriodType.YEAR, null, classA, null)
                .getFirst()
                .id();

        assertThat(jdbc.queryForList(
                        "SELECT action FROM child_evaluation_events WHERE evaluation_id = ?", String.class, id))
                .containsExactly("TEMPLATE_DRAFTED");
    }

    @Test
    void yearDraftSurvivesTransferAndProgramReportCountsCurrentRosterOnce() {
        var year = com.carenest.utils.enums.EvaluationPeriodType.YEAR;
        // OBS-06 (Cần chốt 10/11/2026): cuối năm mở khi học kỳ II kết thúc (27/10, trước hôm nay 28/10)
        jdbc.update("UPDATE school_years SET end_date = '2026-10-31', semester1_start = start_date,"
                + " semester1_end = '2026-09-30', semester2_start = '2026-10-01', semester2_end = '2026-10-27'"
                + " WHERE status = 'ACTIVE'");
        jdbc.update(
                "UPDATE classrooms SET age_group_id = (SELECT id FROM age_groups WHERE code = 'ag-5' LIMIT 1) WHERE id IN (?, ?)",
                classA,
                classB);
        entityManager.clear();
        var old = evaluations.list(teacherA, year, null, classA, null).stream()
                .filter(e -> e.childId().equals(child1))
                .findFirst()
                .orElseThrow();
        evaluations.markProgram(teacherA, old.id(), true);
        evaluations.confirm(teacherA, old.id(), "Nhận xét cuối năm đã được giáo viên kiểm tra đầy đủ dữ liệu.");
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-10-27' WHERE child_id = ? AND classroom_id = ?",
                child1,
                classA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-10-28')",
                child1,
                classB);
        entityManager.clear();
        var next = evaluations.list(teacherB, year, null, classB, null).stream()
                .filter(e -> e.childId().equals(child1))
                .findFirst()
                .orElseThrow();
        assertThat(next.id()).isNotEqualTo(old.id());
        evaluations.markProgram(teacherB, next.id(), true);
        evaluations.confirm(teacherB, next.id(), "Nhận xét cuối năm đã được giáo viên lớp mới kiểm tra đầy đủ.");
        evaluations.list(teacherB, year, null, classB, null);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM child_evaluations WHERE child_id = ? AND period_type = 'YEAR'",
                        Integer.class,
                        child1))
                .isEqualTo(2);
        assertThat(evaluations.get(teacherA, old.id()).child().fullName()).isEqualTo("An");
        var report = evaluations.programReport(principal, null, null);
        assertThat(report.totals().children()).isEqualTo(3);
        assertThat(report.totals().completed()).isEqualTo(1);
        assertThat(report.rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> assertThat(r.completed()).isZero());
    }

    @Autowired
    private com.carenest.service.organization.StaffAssignmentService staffAssignments;

    @Test
    @DisplayName(
            "CFG-YR-01: qua ngày kết thúc mà HT chưa đóng năm, GV vẫn xem lớp cũ và xác nhận cuối năm, không điểm danh")
    void teacherFinishesYearEndAfterYearEndUntilYearCloses() {
        var yearType = com.carenest.utils.enums.EvaluationPeriodType.YEAR;
        // Năm 2026–2027 kết thúc 27/10 (trước hôm nay 28/10), vẫn ACTIVE; phân công hết ở ngày kết thúc năm
        jdbc.update("UPDATE school_years SET end_date = '2026-10-27', semester1_start = start_date,"
                + " semester1_end = '2026-09-30', semester2_start = '2026-10-01', semester2_end = '2026-10-20'"
                + " WHERE status = 'ACTIVE'");
        jdbc.update("UPDATE staff_assignments SET valid_to = '2026-10-27' WHERE classroom_id = ?", classA);
        long yearId = jdbc.queryForObject("SELECT id FROM school_years WHERE status = 'ACTIVE'", Long.class);
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from, valid_to)"
                        + " VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2026-09-01', '2026-10-27')",
                teacherB.userId(),
                ag3,
                yearId);
        entityManager.clear();

        // Xem lại lớp cũ ở điểm danh; tổ trưởng năm đó vẫn giữ nhóm tuổi
        assertThat(attendanceService.getClasses(teacherA, yearId))
                .extracting(c -> c.id())
                .contains(classA);
        assertThat(staffAssignments.findScopedTeamLeaderAgeGroupIds(teacherB.userId(), yearId, TODAY))
                .containsExactly(ag3);

        // Hoàn tất đánh giá cuối năm
        var row = evaluations.list(teacherA, yearType, null, classA, null).stream()
                .filter(e -> e.childId().equals(child1))
                .findFirst()
                .orElseThrow();
        evaluations.confirm(
                teacherA, row.id(), "Nhận xét cuối năm hoàn tất sau ngày kết thúc năm học, trước khi đóng.");
        assertThat(jdbc.queryForObject("SELECT status FROM child_evaluations WHERE id = ?", String.class, row.id()))
                .isEqualTo("CONFIRMED");

        // Điểm danh sau ngày kết thúc vẫn bị chặn
        assertThatThrownBy(() -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true))))))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
    }

    @Autowired
    private com.carenest.service.observation.QualityReportService qualityReports;

    @Test
    void firstMonthlyListingIncludesChildWhoTransferredBeforeMonthEnd() {
        attendance(child1, classA, LocalDate.of(2026, 9, 15));
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-09-19' WHERE child_id = ? AND classroom_id = ?",
                child1,
                classA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-20')",
                child1,
                classB);
        attendance(child1, classB, LocalDate.of(2026, 9, 21));
        entityManager.clear();
        var month = com.carenest.utils.enums.EvaluationPeriodType.MONTH;
        var old = evaluations.list(teacherA, month, null, classA, LocalDate.of(2026, 9, 1));
        assertThat(old)
                .extracting(com.carenest.dto.observation.EvaluationDtos.EvaluationRow::childId)
                .containsExactly(child1);
        assertThat(old.getFirst().childName()).isEqualTo("An");
        assertThat(evaluations.list(teacherB, month, null, classB, LocalDate.of(2026, 9, 1)))
                .extracting(com.carenest.dto.observation.EvaluationDtos.EvaluationRow::childId)
                .contains(child1);
        assertThat(evaluations.list(teacherA, month, null, classA, LocalDate.of(2026, 9, 1)))
                .hasSize(1);
        expectCode(() -> evaluations.get(teacherB, old.getFirst().id()), ApiCode.EVALUATION_VIEW_FORBIDDEN);
    }

    @Test
    @DisplayName("OBS-23: hôm nay lớp chưa điểm danh thì chưa tính là ngày phải đi (không hạ bé chăm, chuyên cần)")
    void qualityReportSkipsTodayUntilClassRecordsAttendance() {
        // Hôm nay 28/10/2026; 1–27/10 có 19 ngày học thứ 2–6
        attendance(child1, classA, LocalDate.of(2026, 10, 1));
        entityManager.clear();
        var before = qualityReports.getReport(principal, null, java.time.YearMonth.of(2026, 10), null);
        assertThat(before.rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> assertThat(r.schoolDays()).isEqualTo(19));

        attendance(child1, classA, TODAY);
        entityManager.clear();
        var after = qualityReports.getReport(principal, null, java.time.YearMonth.of(2026, 10), null);
        assertThat(after.rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> assertThat(r.schoolDays()).isEqualTo(20));
    }

    @Test
    void monthlyQualityCountsChildrenWhoLeftAndJoinedDuringMonth() {
        attendance(child1, classA, LocalDate.of(2026, 9, 15));
        attendance(child2, classA, LocalDate.of(2026, 9, 21));
        jdbc.update("UPDATE enrollments SET valid_to = '2026-09-19' WHERE child_id = ?", child1);
        jdbc.update("UPDATE enrollments SET valid_from = '2026-09-20' WHERE child_id = ?", child2);
        entityManager.clear();
        var report = qualityReports.getReport(principal, null, java.time.YearMonth.of(2026, 9), null);
        assertThat(report.rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.children()).isEqualTo(2);
                    assertThat(r.presentDays()).isEqualTo(2);
                    // Ngày học theo lịch (22 ngày thứ 2–6 của tháng 9/2026), ngày chưa điểm danh vẫn tính và được báo
                    assertThat(r.schoolDays()).isEqualTo(22);
                    assertThat(r.avgPresent()).isZero();
                    assertThat(r.unrecordedDays()).hasSize(20).doesNotContain(LocalDate.of(2026, 9, 15));
                    // Trẻ ra lớp: An thôi học 19/09 trên sĩ số 2
                    assertThat(r.leftChildren()).isEqualTo(1);
                    assertThat(r.leftRate()).isEqualTo(50);
                });
        assertThat(qualityReports
                        .getReport(vpA, null, java.time.YearMonth.of(2026, 9), campusA)
                        .rows())
                .allMatch(r -> r.campusId().equals(campusA));
    }

    @Test
    void schoolTotalCountsOnlyChildrenWhoQuitWhileClassRowCountsTransfers() {
        // Cần chốt 10/11/2026: lớp đếm cả trẻ chuyển đi; dòng toàn trường chỉ đếm trẻ nghỉ hẳn
        jdbc.update("UPDATE enrollments SET valid_to = '2026-09-19' WHERE child_id = ?", child1);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-20')",
                child1,
                classB);
        // Trẻ đi đủ mọi ngày học ở cả lớp A (1–19/9) lẫn lớp B (20–30/9)
        for (LocalDate d = LocalDate.of(2026, 9, 1); !d.isAfter(LocalDate.of(2026, 9, 30)); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                attendance(child1, d.isBefore(LocalDate.of(2026, 9, 20)) ? classA : classB, d);
            }
        }
        entityManager.clear();
        var transferred = qualityReports.getReport(principal, null, java.time.YearMonth.of(2026, 9), null);
        // Lớp A: trẻ chuyển đi tính vào trẻ ra lớp, không xét bé chăm
        assertThat(transferred.rows())
                .filteredOn(r -> r.classId().equals(classA))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.leftChildren()).isEqualTo(1);
                    assertThat(r.diligentChildren()).isZero();
                });
        // Lớp B: trẻ vào 20/9 đi đủ thời gian ở lớp = 100% ⇒ bé chăm; vẫn tính vào sĩ số tháng 9
        assertThat(transferred.rows())
                .filteredOn(r -> r.classId().equals(classB))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.children()).isEqualTo(2);
                    assertThat(r.diligentChildren()).isEqualTo(1);
                });
        assertThat(transferred.totals())
                .filteredOn(t -> t.level().equals("TOAN_TRUONG"))
                .singleElement()
                .satisfies(t -> assertThat(t.leftChildren()).isZero());

        // Thôi học ở lớp B sau đó ⇒ nghỉ hẳn, dòng toàn trường đếm 1
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-09-25' WHERE child_id = ? AND classroom_id = ?",
                child1,
                classB);
        entityManager.clear();
        var quit = qualityReports.getReport(principal, null, java.time.YearMonth.of(2026, 9), null);
        // Nghỉ hẳn ở lớp B ⇒ trẻ ra lớp của B, không còn là bé chăm
        assertThat(quit.rows())
                .filteredOn(r -> r.classId().equals(classB))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.leftChildren()).isEqualTo(1);
                    assertThat(r.diligentChildren()).isZero();
                });
        assertThat(quit.totals())
                .filteredOn(t -> t.level().equals("TOAN_TRUONG"))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.leftChildren()).isEqualTo(1);
                    assertThat(t.leftRate()).isNotNull();
                });
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
        return insertId("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no) VALUES ("
                + plan + ", " + domain + ", " + no + ", '" + text + "', " + no + ") RETURNING id");
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL ->
                        Set.of(
                                Permission.ATTENDANCE_VIEW,
                                Permission.ATTENDANCE_UNLOCK,
                                Permission.ASSESSMENT_VIEW,
                                Permission.ASSESSMENT_UNLOCK,
                                Permission.ASSESSMENT_CLASS_REVIEW);
                    case VICE_PRINCIPAL ->
                        Set.of(
                                Permission.ATTENDANCE_VIEW,
                                Permission.ATTENDANCE_UNLOCK,
                                Permission.ASSESSMENT_VIEW,
                                Permission.ASSESSMENT_UNLOCK,
                                Permission.ASSESSMENT_CLASS_REVIEW,
                                Permission.REWARD_REVIEW);
                    case TEACHER, TEAM_LEADER ->
                        Set.of(
                                Permission.ATTENDANCE_VIEW,
                                Permission.ATTENDANCE_RECORD,
                                Permission.DAILY_HEALTH_RECORD,
                                Permission.ASSESSMENT_VIEW,
                                Permission.ASSESSMENT_RECORD,
                                Permission.ASSESSMENT_CONFIRM);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private static CurrentUser withSchoolWide(CurrentUser user, Permission... permissions) {
        return new CurrentUser(
                user.userId(),
                user.keycloakUserId(),
                user.username(),
                user.fullName(),
                user.email(),
                user.roles(),
                user.permissions(),
                Set.of(permissions));
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
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

    // Trẻ đủ thông tin bắt buộc của hồ sơ (mã, giới tính, trạng thái — tiếp nhận trẻ G94-180)
    private long child(String name, String dateOfBirth) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'DA' || substr(md5(random()::text), 1, 12), '" + name + "', '" + dateOfBirth
                + "', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
