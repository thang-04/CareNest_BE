package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.DailyHealthResponse;
import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.DayUnlockResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest.Entry;
import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.child.ChildListService;
import com.carenest.service.meals.MealCountService;
import com.carenest.service.observation.DailyAssessmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthAction;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.TemperatureLevel;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

/** Điểm danh trên PostgreSQL thật (V21). Hôm nay = thứ 4, 21/10/2026; giờ chốt năm học 08:30. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AttendanceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant BEFORE_CUTOFF = Instant.parse("2026-10-21T01:00:00Z"); // 08:00 giờ trường
    private static final Instant AFTER_CUTOFF = Instant.parse("2026-10-21T02:00:00Z"); // 09:00 giờ trường

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    /** Đồng hồ đổi được giữa các bước test (trước/sau giờ chốt). */
    static final class MovableClock extends Clock {
        private Instant now = BEFORE_CUTOFF;

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
            return now;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private AttendanceService service;

    @Autowired
    private AttendanceDayUnlockService unlockService;

    @Autowired
    private DailyHealthService healthService;

    @Autowired
    private MealCountService mealCountService;

    @Autowired
    private DailyAssessmentService dailyAssessmentService;

    @Autowired
    private ChildListService childListService;

    @Autowired
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private long classA;
    private long classB;
    private long oldClass;
    private long child1;
    private long child2;

    @BeforeEach
    void setUp() {
        clock.now = BEFORE_CUTOFF;
        long campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
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
        long child3 = insertId(
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
    }

    @Test
    @DisplayName("AUTH-03: GV hết phân công xem lớp cũ đúng các đợt đã dạy; ngoài khoảng mọi API đọc trả 403")
    void pastClassReadsStayInsideTaughtRanges() {
        CurrentUser sub = new CurrentUser(
                user("subpast", CareNestRole.TEACHER).userId(),
                "kc-subpast",
                "subpast",
                "subpast",
                null,
                Set.of(CareNestRole.TEACHER),
                Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_RECORD, Permission.ASSESSMENT_VIEW));
        // Hai đợt rời nhau: 05–06/10 và 12–16/10; 07–09/10 là khoảng cấm giữa hai đợt
        for (String[] stint : new String[][] {{"2026-10-05", "2026-10-06"}, {"2026-10-12", "2026-10-16"}}) {
            jdbc.update(
                    "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_substitute) "
                            + "VALUES (?, 'TEACHER', ?, ?::date, ?::date, TRUE)",
                    sub.userId(),
                    classA,
                    stint[0],
                    stint[1]);
        }
        CurrentUser stranger = user("neverthere", CareNestRole.TEACHER);
        LocalDate inside = LocalDate.of(2026, 10, 15);
        LocalDate gap = LocalDate.of(2026, 10, 8);
        LocalDate after = LocalDate.of(2026, 10, 19);

        // Danh sách lớp: lớp cũ chỉ xem, kèm từng đợt (không gộp)
        AttendanceClassResponse past = service.getClasses(sub, null).stream()
                .filter(c -> c.id() == classA)
                .findFirst()
                .orElseThrow();
        assertThat(past.teaches()).isFalse();
        assertThat(past.canRecord()).isFalse();
        assertThat(past.readRanges())
                .extracting(AttendanceClassResponse.ReadRange::start, AttendanceClassResponse.ReadRange::end)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)),
                        tuple(LocalDate.of(2026, 10, 12), inside.plusDays(1)));
        assertThat(service.getClasses(stranger, null))
                .extracting(AttendanceClassResponse::id)
                .doesNotContain(classA);

        // Theo ngày: điểm danh, giờ chốt/suất, sức khỏe, đánh giá ngày
        assertThat(service.getClassAttendance(sub, classA, inside)).isNotNull();
        assertThat(mealCountService.getClassMealCount(sub, classA, inside)).isNotNull();
        for (LocalDate outside : List.of(gap, after)) {
            expectCode(() -> service.getClassAttendance(sub, classA, outside), ApiCode.CLASS_ACCESS_DENIED);
            expectCode(() -> service.getDayInfo(sub, outside, classA), ApiCode.CLASS_ACCESS_DENIED);
            expectCode(() -> mealCountService.getClassMealCount(sub, classA, outside), ApiCode.CLASS_ACCESS_DENIED);
            expectCode(
                    () -> healthService.getClassChildHealth(sub, classA, child1, outside), ApiCode.CLASS_ACCESS_DENIED);
            expectCode(() -> dailyAssessmentService.getDailySheet(sub, classA, outside), ApiCode.CLASS_ACCESS_DENIED);
        }
        expectNotDenied(() -> dailyAssessmentService.getDailySheet(sub, classA, inside));
        expectCode(() -> service.getClassAttendance(stranger, classA, inside), ApiCode.CLASS_ACCESS_DENIED);

        // Tổng hợp: cắt về đợt gần nhất có giao, không gộp min–max; chỉ giao khoảng cấm ⇒ 403
        AttendanceSummaryResponse.ClassSummary month =
                service.getClassSummary(sub, classA, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(month.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(month.actualFrom()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(month.actualTo()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(service.getClassSummary(sub, classA, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9))
                        .actualTo())
                .isEqualTo(LocalDate.of(2026, 10, 6));
        expectCode(
                () -> service.getClassSummary(sub, classA, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9)),
                ApiCode.CLASS_ACCESS_DENIED);
        expectCode(() -> service.getClassSummary(sub, classA, after, TODAY), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(() -> service.getClassSummary(stranger, classA, inside, inside), ApiCode.CLASS_ACCESS_DENIED);

        // Danh sách trẻ lọc theo lớp cũ (Q4): trẻ học lớp trong đợt đã dạy; GV chưa từng dạy ⇒ 403
        var filter = new ChildListFilter(null, null, classA, null, null, 0, 50);
        assertThat(childListService.searchChildren(sub, filter).items())
                .extracting(ChildListItemResponse::id)
                .containsExactlyInAnyOrder(child1, child2);
        expectCode(() -> childListService.searchChildren(stranger, filter), ApiCode.CLASS_ACCESS_DENIED);

        // Ghi vẫn bị chặn (ngày đã qua chưa được mở lại)
        assertThatThrownBy(() -> service.saveClassAttendance(
                        sub, classA, request(inside, new Entry(child1, AttendanceStatus.PRESENT, Map.of()))))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("Năm đã kết thúc: PHT năm đó xem lại lớp điểm trường mình, dạy thay chỉ xem đúng ngày mình dạy")
    void closedYearReadsFollowThatYearsAssignments() {
        // PHT điểm trường A năm 2025-2026, năm nay không còn phân công
        CurrentUser oldVp = user("vpold", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2025-09-01', '2026-05-31')",
                oldVp.userId(),
                jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, oldClass));
        LocalDate oldDay = LocalDate.of(2026, 3, 4);
        assertThat(service.getClassAttendance(oldVp, oldClass, oldDay)).isNotNull();
        expectCode(() -> service.getClassAttendance(oldVp, classA, TODAY), ApiCode.CLASS_ACCESS_DENIED);
        // PHT điểm trường B năm nay không có phân công năm 2025-2026 ⇒ không xem lớp năm đó
        expectCode(() -> service.getClassAttendance(vpB, oldClass, oldDay), ApiCode.CLASS_ACCESS_DENIED);

        // Cô dạy thay lớp cũ 02/03–06/03/2026: chỉ xem lại đúng những ngày đó
        CurrentUser sub = user("subold", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_substitute) "
                        + "VALUES (?, 'TEACHER', ?, '2026-03-02', '2026-03-06', TRUE)",
                sub.userId(),
                oldClass);
        assertThat(service.getClassAttendance(sub, oldClass, oldDay)).isNotNull();
        expectCode(
                () -> service.getClassAttendance(sub, oldClass, LocalDate.of(2026, 3, 10)),
                ApiCode.CLASS_ACCESS_DENIED);
    }

    @Test
    @DisplayName("HT thu hồi quyền Xem điểm danh: GV có quyền ghi vẫn thấy và ghi lớp đang dạy, không thấy lớp khác")
    void teacherWithoutViewPermissionStillRecordsOwnClass() {
        CurrentUser recordOnly = new CurrentUser(
                teacherA.userId(),
                teacherA.keycloakUserId(),
                teacherA.username(),
                teacherA.fullName(),
                null,
                teacherA.roles(),
                Set.of(Permission.ATTENDANCE_RECORD, Permission.DAILY_HEALTH_RECORD));
        assertThat(service.getClasses(recordOnly, null))
                .extracting(AttendanceClassResponse::id)
                .containsExactly(classA);
        assertThat(service.getClassAttendance(recordOnly, classA, TODAY)).isNotNull();
        service.saveClassAttendance(recordOnly, classA, request(TODAY, present(child1)));

        CurrentUser nothing = new CurrentUser(
                teacherA.userId(),
                teacherA.keycloakUserId(),
                teacherA.username(),
                teacherA.fullName(),
                null,
                teacherA.roles(),
                Set.of());
        assertThat(service.getClasses(nothing, null)).isEmpty();
    }

    @Test
    void teacherSeesOwnClassesAndRecordsTodayBeforeCutoff() {
        List<AttendanceClassResponse> classes = service.getClasses(teacherA, null);
        assertThat(classes).extracting(AttendanceClassResponse::id).containsExactly(classA);
        assertThat(classes.getFirst().canRecord()).isTrue();
        assertThat(classes.getFirst().canUnlock()).isFalse();
        // Scope SCHOOL: PHT thấy lớp mọi cơ sở nhưng quyền mở lại vẫn theo ATTENDANCE_UNLOCK riêng.
        assertThat(service.getClasses(withSchoolWide(vpA, Permission.ATTENDANCE_VIEW), null))
                .extracting(AttendanceClassResponse::id, AttendanceClassResponse::canUnlock)
                .containsExactly(tuple(classA, true), tuple(classB, false));
        assertThat(service.getClasses(principal, null)).hasSize(2);
        // Năm chọn ở header là năm đã kết thúc ⇒ lớp năm đó, chỉ xem
        long closedYear = jdbc.queryForObject("SELECT id FROM school_years WHERE name = '2025-2026'", Long.class);
        assertThat(service.getClasses(teacherA, closedYear))
                .extracting(
                        AttendanceClassResponse::id,
                        AttendanceClassResponse::teaches,
                        AttendanceClassResponse::canRecord)
                .containsExactly(tuple(oldClass, true, false));

        ClassAttendanceResponse empty = service.getClassAttendance(teacherA, classA, TODAY);
        assertThat(empty.canRecord()).isTrue();
        assertThat(empty.day().locked()).isFalse();
        assertThat(empty.mealSessions()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(empty.children()).extracting(r -> r.child().fullName()).containsExactly("An", "Bình");

        var saved = service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));
        assertThat(saved.changed()).isEqualTo(2);

        ClassAttendanceResponse sheet = service.getClassAttendance(teacherA, classA, TODAY);
        assertThat(sheet.children().getFirst().record().status()).isEqualTo(AttendanceStatus.PRESENT);
        // ATT-02: Có mặt mặc định bật đủ bữa của nhóm tuổi.
        assertThat(sheet.children().getFirst().record().meals())
                .containsEntry(MealSession.LUNCH, true)
                .containsEntry(MealSession.AFTERNOON, true);
        assertThat(sheet.children().getLast().record().meals()).doesNotContainValue(true);
        expectError(
                () -> service.saveClassAttendance(teacherB, classA, request(TODAY, present(child1))),
                ApiCode.FORBIDDEN);
        expectError(() -> service.getClassAttendance(teacherB, classA, TODAY), ApiCode.FORBIDDEN);
    }

    @Test
    void teacherViewsOwnClassOfClosedYearReadOnly() {
        // GV B từng dạy lớp năm cũ (phân công đã kết thúc) và từng dạy lớp A năm nay nhưng đã bị gỡ
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2025-09-01', '2026-05-31', TRUE)",
                teacherB.userId(),
                oldClass);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', '2026-10-01', FALSE)",
                teacherB.userId(),
                classA);
        long closedYear = jdbc.queryForObject("SELECT id FROM school_years WHERE name = '2025-2026'", Long.class);

        assertThat(service.getClasses(teacherB, closedYear))
                .extracting(
                        AttendanceClassResponse::id,
                        AttendanceClassResponse::teaches,
                        AttendanceClassResponse::canRecord)
                .containsExactly(tuple(oldClass, true, false));
        assertThat(service.getClassAttendance(teacherB, oldClass, LocalDate.of(2026, 5, 29))
                        .canRecord())
                .isFalse();
        expectError(
                () -> service.saveClassAttendance(teacherB, oldClass, request(TODAY, present(child1))),
                ApiCode.FORBIDDEN);
        // Năm đang hoạt động (AUTH-03, chốt 15/11/2026): lớp bị gỡ giữa năm vẫn trong danh sách, chỉ xem đúng đợt đã
        // dạy
        expectError(() -> service.getClassAttendance(teacherB, classA, TODAY), ApiCode.FORBIDDEN);
        assertThat(service.getClasses(teacherB, null))
                .extracting(
                        AttendanceClassResponse::id,
                        AttendanceClassResponse::teaches,
                        c -> c.readRanges().size())
                .containsExactlyInAnyOrder(tuple(classA, false, 1), tuple(classB, true, 0));
    }

    @Test
    void savingTwiceKeepsOneRecordAndAddsHistory() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        var same = service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        assertThat(same.changed()).isZero();
        service.saveClassAttendance(teacherA, classA, request(TODAY, absent(child1)));

        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ?", child1))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT h.action FROM attendance_record_history h JOIN attendance_records r ON r.id = h.record_id"
                                + " WHERE r.child_id = ? ORDER BY h.id",
                        String.class,
                        child1))
                .containsExactly("RECORDED", "UPDATED");
    }

    @Test
    void rejectsInvalidEntries() {
        expectCode(
                () -> service.saveClassAttendance(
                        teacherA,
                        classA,
                        request(TODAY, new Entry(child2, AttendanceStatus.EXCUSED, Map.of(MealSession.LUNCH, true)))),
                ApiCode.ATTENDANCE_EXCUSED_NOT_ALLOWED);
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), present(child1))),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY.minusDays(1), present(child1))),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> service.saveClassAttendance(
                        teacherA, classA, request(LocalDate.of(2026, 10, 24), present(child1))),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> service.saveClassAttendance(teacherA, oldClass, request(TODAY, present(child1))),
                ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("ATT-02: Có mặt mặc định ăn đủ bữa nhưng giáo viên có thể tắt tham gia ăn")
    void presentDefaultsToMealsAndTeacherCanTurnMealsOff() {
        service.saveClassAttendance(
                teacherA,
                classA,
                request(
                        TODAY,
                        new Entry(
                                child1,
                                AttendanceStatus.PRESENT,
                                Map.of(MealSession.LUNCH, false, MealSession.AFTERNOON, false)),
                        new Entry(child2, AttendanceStatus.PRESENT, null)));

        var rows = service.getClassAttendance(teacherA, classA, TODAY).children();
        assertThat(rows.getFirst().record().meals()).doesNotContainValue(true);
        assertThat(rows.getLast().record().meals())
                .containsEntry(MealSession.LUNCH, true)
                .containsEntry(MealSession.AFTERNOON, true);
        AttendanceService.ClassMealTally tally =
                service.tallyMeals(TODAY, classCampus(classA), classYear(classA)).stream()
                        .filter(t -> t.classroomId() == classA)
                        .findFirst()
                        .orElseThrow();
        assertThat(tally.meals()).containsEntry(MealSession.LUNCH, 1).containsEntry(MealSession.AFTERNOON, 1);

        var resend = service.saveClassAttendance(
                teacherA,
                classA,
                request(
                        TODAY,
                        new Entry(
                                child1,
                                AttendanceStatus.PRESENT,
                                Map.of(MealSession.LUNCH, true, MealSession.AFTERNOON, true))));
        assertThat(resend.changed()).isEqualTo(1);

        expectCode(
                () -> service.saveClassAttendance(
                        teacherA,
                        classA,
                        request(TODAY, new Entry(child1, AttendanceStatus.UNEXCUSED, Map.of(MealSession.LUNCH, true)))),
                ApiCode.ABSENT_CHILD_NO_MEAL);
        // Bữa không thuộc nhóm tuổi của lớp bị chặn
        expectCode(
                () -> service.saveClassAttendance(
                        teacherA,
                        classA,
                        request(TODAY, new Entry(child1, AttendanceStatus.PRESENT, Map.of(MealSession.MORNING, true)))),
                ApiCode.MEAL_SESSION_NOT_SERVED);
    }

    @Test
    @DisplayName("ATT-02a/CHD-13: trẻ không báo ăn không có suất")
    void childWithoutMealsNeverGetsMeals() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), present(child2)));
        entityManager.flush();
        entityManager.clear();
        AttendanceService.ClassMealTally before = tallyOf(classA);

        long noMeal = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, meal_registered) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Dũng', '2023-04-01', 'MALE', 'ACTIVE', CURRENT_DATE, FALSE) RETURNING id");
        enroll(noMeal, classA);
        // Trẻ không báo ăn nhưng còn dị ứng đã lưu (CHD-13): không được tính suất thay thế
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by)"
                        + " VALUES (?, 'REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', ?)",
                noMeal,
                principal.userId());
        jdbc.update("INSERT INTO child_allergies (child_id, name) VALUES (?, 'Tôm')", noMeal);

        // meals null: mặc định ăn đủ bữa nhưng trẻ không báo ăn thì không có bữa nào
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(noMeal)));
        assertThat(mealsOfChild(noMeal)).doesNotContainValue(true);

        // Gửi mọi bữa true: bị bỏ qua, không lỗi
        service.saveClassAttendance(
                teacherA,
                classA,
                request(
                        TODAY,
                        new Entry(
                                noMeal,
                                AttendanceStatus.PRESENT,
                                Map.of(MealSession.LUNCH, true, MealSession.AFTERNOON, true))));
        assertThat(mealsOfChild(noMeal)).doesNotContainValue(true);

        AttendanceService.ClassMealTally after = tallyOf(classA);
        assertThat(after.present()).isEqualTo(before.present() + 1);
        assertThat(after.meals()).isEqualTo(before.meals());
        assertThat(after.substitutes()).isEqualTo(before.substitutes());

        var sheet = service.getClassAttendance(teacherA, classA, TODAY).children();
        assertThat(sheet)
                .filteredOn(r -> r.child().id() == noMeal)
                .singleElement()
                .satisfies(r -> assertThat(r.child().mealRegistered()).isFalse());
        assertThat(sheet)
                .filteredOn(r -> r.child().id() == child1)
                .singleElement()
                .satisfies(r -> assertThat(r.child().mealRegistered()).isTrue());
    }

    private AttendanceService.ClassMealTally tallyOf(long classId) {
        return service.tallyMeals(TODAY, classCampus(classId), classYear(classId)).stream()
                .filter(t -> t.classroomId() == classId)
                .findFirst()
                .orElseThrow();
    }

    private Map<MealSession, Boolean> mealsOfChild(long childId) {
        entityManager.flush();
        entityManager.clear();
        return service.getClassAttendance(teacherA, classA, TODAY).children().stream()
                .filter(r -> r.child().id() == childId)
                .findFirst()
                .orElseThrow()
                .record()
                .meals();
    }

    @Test
    @DisplayName(
            "ATT-09: GV không tự chọn Vắng có phép; trạng thái có phép đã có (từ đơn nghỉ) vẫn đọc và gửi lại được")
    void teacherCannotSetExcusedButKeepsExistingExcused() {
        expectCode(
                () -> service.saveClassAttendance(
                        teacherA,
                        classA,
                        request(TODAY, present(child1), new Entry(child2, AttendanceStatus.EXCUSED, null))),
                ApiCode.ATTENDANCE_EXCUSED_NOT_ALLOWED);
        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ?", child1))
                .isZero();

        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));
        // Giả lập đơn nghỉ đã đặt có phép (module đơn nghỉ chưa có)
        entityManager.flush();
        jdbc.update("UPDATE attendance_records SET status = 'EXCUSED' WHERE child_id = ?", child2);
        entityManager.clear();
        var resend = service.saveClassAttendance(
                teacherA, classA, request(TODAY, present(child1), new Entry(child2, AttendanceStatus.EXCUSED, null)));
        assertThat(resend.changed()).isZero();
        assertThat(service.getClassAttendance(teacherA, classA, TODAY)
                        .children()
                        .getLast()
                        .record()
                        .status())
                .isEqualTo(AttendanceStatus.EXCUSED);
    }

    @Test
    void lockedAfterCutoffUntilUnlockedAndAfterRevoke() {
        clock.now = AFTER_CUTOFF;
        assertThat(service.getDayInfo(teacherA, TODAY, classA).locked()).isTrue();
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1))), ApiCode.CONFLICT);

        DayUnlockRequest unlockRequest =
                new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "  GV quên lưu  ");
        expectError(() -> unlockService.unlockDay(vpB, unlockRequest), ApiCode.FORBIDDEN);
        // HT cấp ATTENDANCE_VIEW phạm vi SCHOOL nhưng ATTENDANCE_UNLOCK vẫn chỉ ở campus B.
        ClassAttendanceResponse otherCampus =
                service.getClassAttendance(withSchoolWide(vpB, Permission.ATTENDANCE_VIEW), classA, TODAY);
        assertThat(otherCampus.classId()).isEqualTo(classA);
        assertThat(otherCampus.canRecord()).isFalse();
        DayUnlockResponse unlock = unlockService.unlockDay(vpA, unlockRequest);
        assertThat(unlock.reason()).isEqualTo("GV quên lưu");
        assertThat(unlock.unlockedByName()).isEqualTo("vpa");
        expectError(() -> unlockService.unlockDay(principal, unlockRequest), ApiCode.CONFLICT);

        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        assertThat(count("SELECT count(*) FROM attendance_record_history WHERE unlock_id = ?", unlock.id()))
                .isEqualTo(1);
        assertThat(unlockService.getUnlocks(vpA)).hasSize(1);
        assertThat(unlockService.getUnlocks(vpB)).isEmpty();

        unlockService.revokeUnlock(principal, unlock.id());
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY, absent(child1))), ApiCode.CONFLICT);
        expectError(() -> unlockService.revokeUnlock(principal, unlock.id()), ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("Cần chốt 14/11/2026: dạy thay ghi điểm danh theo ngày được ghi, kể cả khi đợt dạy thay đã hết")
    void substituteRecordsReopenedDaysOfOwnStintOnly() {
        CurrentUser sub = user("subnow", CareNestRole.TEACHER);
        // Dạy thay lớp A 15/10–20/10 (đã hết đợt vào hôm nay 21/10)
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_substitute) "
                        + "VALUES (?, 'TEACHER', ?, '2026-10-15', '2026-10-20', TRUE)",
                sub.userId(),
                classA);
        LocalDate inStint = LocalDate.of(2026, 10, 19);
        LocalDate outside = LocalDate.of(2026, 10, 14);
        Instant until = BEFORE_CUTOFF.plus(Duration.ofHours(4));
        unlockService.unlockDay(vpA, new DayUnlockRequest(classA, inStint, until, "Bổ sung"));
        unlockService.unlockDay(vpA, new DayUnlockRequest(classA, outside, until, "Bổ sung"));

        // Luồng thật: lớp vẫn có trong danh sách, mở được thông tin ngày, sổ, sức khỏe trẻ của ngày trong đợt
        assertThat(service.getClasses(sub, null))
                .extracting(AttendanceClassResponse::id)
                .containsExactly(classA);
        assertThat(service.getDayInfo(sub, inStint, classA).unlock()).isNotNull();
        expectCode(() -> service.getDayInfo(sub, outside, classA), ApiCode.CLASS_ACCESS_DENIED);
        assertThat(service.getClassAttendance(sub, classA, inStint).canRecord()).isTrue();
        service.saveClassAttendance(sub, classA, request(inStint, present(child1)));
        assertThat(jdbc.queryForList(
                        "SELECT user_id FROM notifications WHERE type = 'ATTENDANCE_DAY_UNLOCKED' AND body LIKE ?",
                        Long.class,
                        "%19/10/2026%"))
                .containsExactlyInAnyOrder(teacherA.userId(), sub.userId());
        // Ngày ngoài đợt: không ghi, không được báo
        expectCode(
                () -> service.saveClassAttendance(sub, classA, request(outside, present(child1))),
                ApiCode.CLASS_ACCESS_DENIED);
        assertThat(jdbc.queryForList(
                        "SELECT user_id FROM notifications WHERE type = 'ATTENDANCE_DAY_UNLOCKED' AND body LIKE ?",
                        Long.class,
                        "%14/10/2026%"))
                .containsExactly(teacherA.userId());
        assertThat(healthService.getClassChildHealth(sub, classA, child1, inStint))
                .isNotNull();
        expectCode(() -> healthService.getClassChildHealth(sub, classA, child1, outside), ApiCode.CLASS_ACCESS_DENIED);
        // GV dạy chính ghi được mọi ngày được mở lại
        service.saveClassAttendance(teacherA, classA, request(outside, present(child1)));
    }

    @Test
    void unlockNeedsLockedDayAndValidDeadline() {
        expectError(
                () -> unlockService.unlockDay(
                        vpA, new DayUnlockRequest(classA, TODAY, BEFORE_CUTOFF.plusSeconds(3600), "x")),
                ApiCode.CONFLICT);
        expectError(
                () -> unlockService.unlockDay(
                        vpA, new DayUnlockRequest(classA, TODAY.minusDays(1), BEFORE_CUTOFF.minusSeconds(60), "x")),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> unlockService.unlockDay(
                        vpA,
                        new DayUnlockRequest(
                                classA, LocalDate.of(2027, 6, 1), Instant.parse("2027-06-02T00:00:00Z"), "x")),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> unlockService.unlockDay(
                        vpA,
                        new DayUnlockRequest(classA, TODAY.minusDays(1), Instant.parse("2027-06-01T00:00:00Z"), "x")),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void summaryCountsDaysChildrenAndMeals() {
        // Trẻ có khai dị ứng: lượt ăn tính là suất thay thế (giống Sổ báo ăn)
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by)"
                        + " VALUES (?, 'REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', ?)",
                child1,
                principal.userId());
        jdbc.update("INSERT INTO child_allergies (child_id, name) VALUES (?, 'Tôm')", child1);
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));

        AttendanceSummaryResponse.ClassSummary summary =
                service.getClassSummary(teacherA, classA, TODAY.minusDays(2), TODAY);
        assertThat(summary.days()).hasSize(3);
        var today = summary.days().getLast();
        assertThat(today.present()).isEqualTo(1);
        assertThat(today.unexcused()).isEqualTo(1);
        assertThat(today.mealTotal()).isEqualTo(1);
        assertThat(today.substitute()).isEqualTo(1);
        assertThat(today.meals()).containsEntry(MealSession.LUNCH, 1);
        assertThat(summary.days().getFirst().missing()).isEqualTo(2);

        AttendanceSummaryResponse.MultiSummary all = service.getSummary(principal, null, null, TODAY, TODAY);
        assertThat(all.size()).isEqualTo(3);
        assertThat(all.days().getFirst().missing()).isEqualTo(1);
        // PHT chỉ xem toàn trường khi ATTENDANCE_VIEW được cấp scope SCHOOL.
        assertThat(service.getSummary(withSchoolWide(vpB, Permission.ATTENDANCE_VIEW), null, null, TODAY, TODAY)
                        .classes())
                .extracting(AttendanceSummaryResponse.ClassTotal::classId)
                .containsExactlyElementsOf(all.classes().stream()
                        .map(AttendanceSummaryResponse.ClassTotal::classId)
                        .toList());
        expectError(() -> service.getSummary(teacherA, null, null, TODAY, TODAY), ApiCode.FORBIDDEN);
        expectError(() -> service.getClassSummary(teacherA, classA, TODAY, TODAY.plusDays(1)), ApiCode.BAD_REQUEST);
    }

    @Test
    void teacherRecordsHealthOnlyForPresentChildToday() {
        expectError(
                () -> healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN)),
                ApiCode.CONFLICT);
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));

        var first = healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN));
        var again = healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(first.temperature()).isEqualByComparingTo("36.8");
        assertThat(first.recordedByName()).isEqualTo("teachera");
        expectError(
                () -> healthService.addEntry(teacherA, health("r2", child2, HealthObservation.NO_CONCERN)),
                ApiCode.CONFLICT);
        expectError(
                () -> healthService.addEntry(teacherB, health("r3", child1, HealthObservation.NO_CONCERN)),
                ApiCode.FORBIDDEN);
        // Có dấu hiệu mà không có biểu hiện / ghi chú; xử trí khi không có dấu hiệu; chuyển viện thiếu nơi/giờ
        expectError(
                () -> healthService.addEntry(teacherA, health("r4", child1, HealthObservation.NOTED)),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> healthService.addEntry(
                        teacherA,
                        new DailyHealthRequest(
                                "r5",
                                classA,
                                child1,
                                TODAY,
                                null,
                                HealthObservation.NO_CONCERN,
                                List.of(),
                                "",
                                List.of(HealthAction.MONITOR),
                                null)),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> healthService.addEntry(
                        teacherA,
                        new DailyHealthRequest(
                                "r6",
                                classA,
                                child1,
                                TODAY,
                                null,
                                HealthObservation.NOTED,
                                List.of(HealthSymptom.FEVER),
                                "",
                                List.of(HealthAction.REFERRAL),
                                null)),
                ApiCode.BAD_REQUEST);

        long parent = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name)"
                + " VALUES ('kc-parent1', 'parent1', 'parent1') RETURNING id");
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id,"
                        + " linked_at) VALUES (?, 0, 'Mẹ', 'MOTHER', '0918888101', '999900000581', ?, now())",
                child1,
                parent);
        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "r7",
                        classA,
                        child1,
                        TODAY,
                        new BigDecimal("38.2"),
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.FEVER),
                        "Sốt",
                        List.of(HealthAction.MEDICAL_ROOM),
                        null));
        DailyHealthResponse.ChildDay day = healthService.getClassChildHealth(teacherA, classA, child1, TODAY);
        assertThat(day.canRecord()).isTrue();
        assertThat(day.entries()).hasSize(2);
        // Cần chốt 14/11/2026: "cần lưu ý" báo phụ huynh đã liên kết, một lần mỗi trẻ mỗi ngày
        assertThat(jdbc.queryForList(
                        "SELECT user_id FROM notifications WHERE type = 'DAILY_HEALTH_NOTED' AND child_id = ?",
                        Long.class,
                        child1))
                .containsExactly(parent);
        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "r8",
                        classA,
                        child1,
                        TODAY,
                        new BigDecimal("38.0"),
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.FEVER),
                        "",
                        List.of(),
                        null));
        assertThat(count(
                        "SELECT count(*) FROM notifications WHERE type = 'DAILY_HEALTH_NOTED' AND child_id = ?",
                        child1))
                .isEqualTo(1);
        assertThat(day.entries().getFirst().observation()).isEqualTo(HealthObservation.NOTED);
        assertThat(healthService
                        .getClassChildHealth(teacherA, classA, child2, TODAY)
                        .canRecord())
                .isFalse();
        assertThat(healthService
                        .getOverview(vpA, TODAY, null, null, null, classA)
                        .attention())
                .extracting(DailyHealthResponse.Attention::childId)
                .containsExactly(child1);
        // Sức khỏe chỉ đo khi có mặt: đổi sang vắng thì không còn trong danh sách cần chú ý
        service.saveClassAttendance(teacherA, classA, request(TODAY, absent(child1), absent(child2)));
        assertThat(healthService
                        .getOverview(vpA, TODAY, null, null, null, classA)
                        .attention())
                .isEmpty();
    }

    @Test
    void healthStaysOpenAfterCutoffAndLocksWhenTheDayEnds() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        // HLT-15: sau giờ chốt điểm danh vẫn ghi thêm sức khỏe; sang ngày hôm sau thì ngày cũ đã khóa
        clock.now = AFTER_CUTOFF;
        assertThat(healthService
                        .addEntry(teacherA, health("r-late", child1, HealthObservation.NO_CONCERN))
                        .recordedAt())
                .isEqualTo(AFTER_CUTOFF);
        clock.now = AFTER_CUTOFF.plus(Duration.ofDays(1));
        expectError(
                () -> healthService.addEntry(teacherA, health("r-next", child1, HealthObservation.NO_CONCERN)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void leadersSeeHealthOverviewInScope() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), present(child2)));
        healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN));
        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "r2",
                        classA,
                        child2,
                        TODAY,
                        null,
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.COUGH),
                        "",
                        List.of(),
                        null));

        DailyHealthResponse.Overview all = healthService.getOverview(principal, TODAY, null, null, null, null);
        assertThat(all.totals().present()).isEqualTo(2);
        assertThat(all.totals().checked()).isEqualTo(2);
        assertThat(all.totals().noted()).isEqualTo(1);
        assertThat(all.attention())
                .extracting(DailyHealthResponse.Attention::childName)
                .containsExactly("Bình");
        assertThat(all.classes()).hasSize(2);
        CurrentUser schoolViewer = withSchoolWide(vpB, Permission.ATTENDANCE_VIEW);
        // PHT cơ sở B xem sức khỏe toàn trường khi ATTENDANCE_VIEW được cấp scope SCHOOL.
        assertThat(healthService
                        .getOverview(schoolViewer, TODAY, null, null, null, null)
                        .classes())
                .extracting(DailyHealthResponse.ClassRow::classId)
                .containsExactlyElementsOf(all.classes().stream()
                        .map(DailyHealthResponse.ClassRow::classId)
                        .toList());
        assertThat(healthService
                        .getOverview(
                                schoolViewer,
                                TODAY,
                                null,
                                all.classes().getFirst().campusId(),
                                null,
                                null)
                        .classes())
                .isNotEmpty();
        expectError(() -> healthService.getOverview(teacherA, TODAY, null, null, null, null), ApiCode.FORBIDDEN);

        assertThat(healthService.getChildHistory(vpA, child2, TODAY).entries()).hasSize(1);
        assertThat(healthService.getChildHistory(schoolViewer, child2, TODAY).entries())
                .hasSize(1);
    }

    @Test
    void attendanceSheetShowsLatestHealthCheckPerChild() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));
        ClassAttendanceResponse before = service.getClassAttendance(teacherA, classA, TODAY);
        assertThat(before.children()).allSatisfy(row -> {
            assertThat(row.health().checked()).isFalse();
            assertThat(row.health().temperatureLevel()).isNull();
        });

        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "r-fever",
                        classA,
                        child1,
                        TODAY,
                        new BigDecimal("39.0"),
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.FEVER),
                        "",
                        List.of(HealthAction.MONITOR),
                        null));
        ClassAttendanceResponse.HealthSummary fever = service.getClassAttendance(teacherA, classA, TODAY)
                .children()
                .getFirst()
                .health();
        assertThat(fever.checked()).isTrue();
        assertThat(fever.observation()).isEqualTo(HealthObservation.NOTED);
        assertThat(fever.temperature()).isEqualByComparingTo("39.0");
        assertThat(fever.temperatureLevel()).isEqualTo(TemperatureLevel.HIGH);
        assertThat(fever.symptoms()).containsExactly(HealthSymptom.FEVER);

        // Khám lại trong ngày: nhãn theo lần mới nhất
        clock.now = clock.now.plus(Duration.ofMinutes(30));
        healthService.addEntry(teacherA, health("r-ok", child1, HealthObservation.NO_CONCERN));
        ClassAttendanceResponse.HealthSummary latest = service.getClassAttendance(teacherA, classA, TODAY)
                .children()
                .getFirst()
                .health();
        assertThat(latest.entryCount()).isEqualTo(2);
        assertThat(latest.observation()).isEqualTo(HealthObservation.NO_CONCERN);
        assertThat(latest.temperatureLevel()).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(latest.symptoms()).isEmpty();
    }

    @Test
    void healthCheckJustAfterMidnightSchoolTimeCountsForThatDay() {
        // 00:30 giờ trường ngày 21/10 = 17:30 UTC ngày 20/10
        clock.now = Instant.parse("2026-10-20T17:30:00Z");
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        healthService.addEntry(teacherA, health("r-midnight", child1, HealthObservation.NO_CONCERN));
        ClassAttendanceResponse.HealthSummary health = service.getClassAttendance(teacherA, classA, TODAY)
                .children()
                .getFirst()
                .health();
        assertThat(health.checked()).isTrue();
        assertThat(health.temperatureLevel()).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(service.getClassAttendance(teacherA, classA, TODAY.minusDays(1))
                        .children())
                .allSatisfy(row -> assertThat(row.health().checked()).isFalse());
    }

    @Test
    void temperatureLevelsFollowAgreedThresholds() {
        // Bộ Y tế: sốt khi nách ≥ 37,5 °C; nhẹ / vừa / cao / rất cao tách ở 38 / 39 / 40 °C
        assertThat(TemperatureLevel.of(null)).isNull();
        assertThat(TemperatureLevel.of(new BigDecimal("35.4"))).isEqualTo(TemperatureLevel.LOW);
        assertThat(TemperatureLevel.of(new BigDecimal("35.5"))).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(TemperatureLevel.of(new BigDecimal("37.4"))).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(TemperatureLevel.of(new BigDecimal("37.5"))).isEqualTo(TemperatureLevel.MILD);
        assertThat(TemperatureLevel.of(new BigDecimal("37.9"))).isEqualTo(TemperatureLevel.MILD);
        assertThat(TemperatureLevel.of(new BigDecimal("38.0"))).isEqualTo(TemperatureLevel.MODERATE);
        assertThat(TemperatureLevel.of(new BigDecimal("38.9"))).isEqualTo(TemperatureLevel.MODERATE);
        assertThat(TemperatureLevel.of(new BigDecimal("39.0"))).isEqualTo(TemperatureLevel.HIGH);
        assertThat(TemperatureLevel.of(new BigDecimal("39.9"))).isEqualTo(TemperatureLevel.HIGH);
        assertThat(TemperatureLevel.of(new BigDecimal("40.0"))).isEqualTo(TemperatureLevel.VERY_HIGH);
    }

    private DailyHealthRequest health(String requestId, long childId, HealthObservation observation) {
        return new DailyHealthRequest(
                requestId, classA, childId, TODAY, new BigDecimal("36.8"), observation, List.of(), "", List.of(), null);
    }

    private static SaveAttendanceRequest request(LocalDate date, Entry... entries) {
        return new SaveAttendanceRequest(date, List.of(entries));
    }

    private static Entry present(long childId) {
        return new Entry(
                childId, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true, MealSession.AFTERNOON, true));
    }

    private static Entry absent(long childId) {
        return new Entry(childId, AttendanceStatus.UNEXCUSED, Map.of());
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL, VICE_PRINCIPAL -> Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_UNLOCK);
                    case TEACHER, TEAM_LEADER ->
                        Set.of(
                                Permission.ATTENDANCE_VIEW,
                                Permission.ATTENDANCE_RECORD,
                                Permission.DAILY_HEALTH_RECORD,
                                Permission.PICKUP_RECORD);
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

    private long count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Long.class, arg);
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(apiCode.getHttpStatus());
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }

    // Trong khoảng đọc: không bị từ chối quyền (lỗi nghiệp vụ khác như thiếu cấu hình không thuộc phạm vi test này)
    private static void expectNotDenied(ThrowingCallable call) {
        try {
            call.call();
        } catch (GlobalException ex) {
            assertThat(ex.getApiCode()).isNotEqualTo(ApiCode.CLASS_ACCESS_DENIED);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    private long classCampus(long classroomId) {
        return count("SELECT campus_id FROM classrooms WHERE id = ?", classroomId);
    }

    private long classYear(long classroomId) {
        return count("SELECT school_year_id FROM classrooms WHERE id = ?", classroomId);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
