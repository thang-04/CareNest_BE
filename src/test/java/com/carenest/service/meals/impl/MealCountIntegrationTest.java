package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest.Entry;
import com.carenest.dto.meals.ClassMealCountResponse;
import com.carenest.dto.meals.MealAdjustRequest;
import com.carenest.dto.meals.MealCountDayResponse;
import com.carenest.dto.meals.MealCountResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.meals.MealCountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
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

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class MealCountIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant BEFORE_CUTOFF = Instant.parse("2026-10-21T01:00:00Z"); // 08:00 giờ trường
    private static final Instant AFTER_CUTOFF = Instant.parse("2026-10-21T02:00:00Z"); // 09:00 giờ trường

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

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
    private MealCountService service;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceDayUnlockService unlockService;

    @Autowired
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser kitchenA;
    private long campusA;
    private long classA;
    private long child1;
    private long child2;

    @Test
    void activeYearAfterItsEndDoesNotCreateMealBooks() {
        clock.now = Instant.parse("2027-06-01T03:00:00Z");
        long year = jdbc.queryForObject("SELECT school_year_id FROM classrooms WHERE id = ?", Long.class, classA);
        assertThat(service.getMealCounts(principal, LocalDate.of(2027, 6, 1), year)
                        .counts())
                .isEmpty();
        service.aggregateLockedDay();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_counts WHERE date = '2027-06-01'", Integer.class))
                .isZero();
    }

    @Test
    void selectedClosedYearUsesOwnCutoffAndClassMealHistory() {
        long oldYear = insertId(
                "INSERT INTO school_years (name, start_date, end_date, status) VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        long group = jdbc.queryForObject("SELECT age_group_id FROM classrooms WHERE id = ?", Long.class, classA);
        long oldClass = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campusA + ", " + oldYear + ", 'Lớp cũ', " + group + ") RETURNING id");
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '07:45', '2025-09-01')",
                oldYear);
        var day = LocalDate.of(2026, 5, 20);
        assertThat(service.getMealCounts(principal, day, oldYear).cutoff()).isEqualTo("07:45");
        assertThat(service.getClassMealCount(principal, oldClass, day).className())
                .isEqualTo("Lớp cũ");
    }

    @BeforeEach
    void setUp() {
        clock.now = BEFORE_CUTOFF;
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, year);
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        child1 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'An', '2023-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        child2 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Bình', '2023-02-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        enroll(child1);
        enroll(child2);

        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("vpa", CareNestRole.VICE_PRINCIPAL);
        vpB = user("vpb", CareNestRole.VICE_PRINCIPAL);
        kitchenA = user("kitchena", CareNestRole.KITCHEN_STAFF);
        campusRole(vpA, "VICE_PRINCIPAL", campusA);
        campusRole(vpB, "VICE_PRINCIPAL", campusB);
        campusRole(kitchenA, "KITCHEN_STAFF", campusA);
        teacherA = user("teachera", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacherA.userId(),
                classA);
    }

    @Test
    @DisplayName("CFG-YR-05: năm kích hoạt sớm, ngày trước ngày bắt đầu chưa có sổ suất ăn (Cần chốt 17/11/2026)")
    void earlyActivatedYearHasNoMealCountBeforeStartDate() {
        jdbc.update("UPDATE school_years SET start_date = '2026-10-26'");
        clock.now = AFTER_CUTOFF;

        assertThat(service.getMealCounts(vpA, TODAY, null).counts()).isEmpty();
        service.aggregateLockedDay();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_counts", Integer.class))
                .isZero();
    }

    @Test
    void aggregatesAtCutoffAndKitchenSeesOnlyConfirmedCounts() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        // Trước giờ chốt: PHT xem số tạm tính, bếp chưa thấy gì
        MealCountResponse preview = lunch(service.getMealCounts(vpA, TODAY, null));
        assertThat(preview.id()).isNull();
        assertThat(preview.status()).isEqualTo("OPEN");
        assertThat(preview.totals().normal()).isEqualTo(1);
        assertThat(service.getMealCounts(kitchenA, TODAY, null).counts()).isEmpty();

        clock.now = AFTER_CUTOFF;
        MealCountDayResponse day = service.getMealCounts(vpA, TODAY, null);
        assertThat(day.counts())
                .extracting(MealCountResponse::session)
                .containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        MealCountResponse lunch = lunch(day);
        assertThat(lunch.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(lunch.canConfirm()).isTrue();
        assertThat(lunch.classes().getFirst())
                .extracting(
                        MealCountResponse.Line::present, MealCountResponse.Line::absent, MealCountResponse.Line::normal)
                .containsExactly(1, 1, 1);
        assertThat(service.getMealCounts(kitchenA, TODAY, null).counts()).isEmpty();
        // Phân quyền động (Cần chốt 10/11/2026): PHT cơ sở khác mặc định không thấy sổ cơ sở A; HT cấp ATTENDANCE_VIEW
        // toàn trường thì xem được nhưng không xác nhận/điều chỉnh. HT xác nhận và điều chỉnh suất thay thế được
        assertThat(service.getMealCounts(vpB, TODAY, null).counts())
                .noneMatch(c -> c.id() != null && c.id().equals(lunch.id()));
        CurrentUser vpBViewsSchool = withPermissions(
                vpB,
                Set.of(Permission.ATTENDANCE_VIEW, Permission.MEAL_COUNT_CONFIRM),
                Set.of(Permission.ATTENDANCE_VIEW));
        assertThat(lunch(service.getMealCounts(vpBViewsSchool, TODAY, null)))
                .extracting(MealCountResponse::id, MealCountResponse::canConfirm, MealCountResponse::canAdjust)
                .containsExactly(lunch.id(), false, false);
        expectError(() -> service.confirm(vpB, lunch.id()), ApiCode.FORBIDDEN);
        assertThat(lunch(service.getMealCounts(principal, TODAY, null)))
                .extracting(MealCountResponse::canConfirm, MealCountResponse::canAdjust)
                .containsExactly(true, true);

        MealCountResponse confirmed = service.confirm(vpA, lunch.id());
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedByName()).isEqualTo("vpa");
        assertThat(confirmed.confirmedByRole()).isEqualTo(StaffRole.VICE_PRINCIPAL);
        assertThat(confirmed.classes().getFirst().ageGroupId()).isNotNull();
        // NUT-02: xác nhận theo cơ sở–ngày ⇒ mọi bữa của ngày cùng được xác nhận, bếp thấy đủ
        assertThat(service.getMealCounts(kitchenA, TODAY, null).counts())
                .extracting(MealCountResponse::session, MealCountResponse::status)
                .containsExactly(tuple(MealSession.LUNCH, "CONFIRMED"), tuple(MealSession.AFTERNOON, "CONFIRMED"));
        // Gọi lại cho bữa khác (client xác nhận lần lượt từng bữa) ⇒ trả trạng thái, không ghi thêm lịch sử
        long afternoonId =
                service.getMealCounts(vpA, TODAY, null).counts().getLast().id();
        assertThat(service.confirm(vpA, afternoonId).status()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM meal_count_history WHERE action = 'CONFIRMED'", Long.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Cần chốt 14/11/2026: PHT xem lại sổ suất ăn năm đã đóng của điểm trường mình, chỉ xem")
    void vicePrincipalReadsClosedYearMealBookOfOwnCampus() {
        long closed = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long oldClass = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campusA + ", " + closed + ", 'Lớp cũ', " + ag3 + ") RETURNING id");
        LocalDate oldDay = LocalDate.of(2026, 3, 4);
        long countId = insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status,"
                + " aggregated_at, confirmed_at) VALUES (" + campusA + ", " + closed + ", '" + oldDay
                + "', 'LUNCH', 'CONFIRMED', now(), now()) RETURNING id");
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, 3, 0, 0, 3, 0)",
                countId,
                oldClass);

        MealCountResponse old = lunch(service.getMealCounts(vpA, oldDay, closed));
        assertThat(old.id()).isEqualTo(countId);
        assertThat(old.canConfirm()).isFalse();
        assertThat(old.canAdjust()).isFalse();
        assertThat(service.getClassMealCount(vpA, oldClass, oldDay).meals()).anySatisfy(meal -> {
            assertThat(meal.session()).isEqualTo(MealSession.LUNCH);
            assertThat(meal.normal()).isEqualTo(3);
        });
        assertThat(service.getMealCounts(vpB, oldDay, closed).counts()).isEmpty();
    }

    @Test
    @DisplayName("NUT-02, ATT-02: giáo viên xem suất ăn lớp mình theo bữa; lớp không được phân công ⇒ 403")
    void teacherSeesClassMealCountOfOwnClassOnly() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        // Trước giờ chốt: tạm tính từ điểm danh, không lập sổ
        ClassMealCountResponse open = service.getClassMealCount(teacherA, classA, TODAY);
        assertThat(open.className()).isEqualTo("Lớp A");
        assertThat(open.meals())
                .extracting(
                        ClassMealCountResponse.Meal::session,
                        ClassMealCountResponse.Meal::normal,
                        ClassMealCountResponse.Meal::status)
                .containsExactly(tuple(MealSession.LUNCH, 1, "OPEN"), tuple(MealSession.AFTERNOON, 1, "OPEN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_counts", Long.class))
                .isZero();

        // Qua giờ chốt, PHT mở sổ rồi xác nhận ⇒ giáo viên thấy trạng thái và giờ xác nhận
        clock.now = AFTER_CUTOFF;
        MealCountResponse lunch = lunch(service.getMealCounts(vpA, TODAY, null));
        assertThat(service.getClassMealCount(teacherA, classA, TODAY)
                        .meals()
                        .getFirst()
                        .status())
                .isEqualTo("PENDING_CONFIRMATION");
        service.confirm(vpA, lunch.id());
        ClassMealCountResponse.Meal confirmed =
                service.getClassMealCount(teacherA, classA, TODAY).meals().getFirst();
        assertThat(confirmed)
                .extracting(
                        ClassMealCountResponse.Meal::normal,
                        ClassMealCountResponse.Meal::status,
                        ClassMealCountResponse.Meal::needsReconfirmation)
                .containsExactly(1, "CONFIRMED", false);
        assertThat(confirmed.confirmedAt()).isNotNull();

        CurrentUser otherTeacher = user("teacherb", CareNestRole.TEACHER);
        expectCode(() -> service.getClassMealCount(otherTeacher, classA, TODAY), ApiCode.CLASS_ACCESS_DENIED);
    }

    @Test
    @DisplayName("NUT-02: chặn xác nhận khi còn lớp có trẻ đang học chưa được điểm danh, báo tên lớp")
    void confirmIsBlockedWhileChildrenAreNotMarked() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1)));
        clock.now = AFTER_CUTOFF;
        MealCountResponse lunch = lunch(service.getMealCounts(vpA, TODAY, null));
        assertThat(lunch.totals().missing()).isEqualTo(1);

        assertThatThrownBy(() -> service.confirm(vpA, lunch.id()))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("Lớp A")
                .extracting("apiCode")
                .isEqualTo(ApiCode.MEAL_COUNT_ATTENDANCE_INCOMPLETE);
        assertThat(service.getMealCounts(kitchenA, TODAY, null).counts()).isEmpty();

        // PHT mở lại, GV điểm danh nốt ⇒ xác nhận được
        unlockService.unlockDay(
                vpA, new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Điểm danh nốt"));
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        service.markAttendanceChanged(TODAY, campusA, classA);
        assertThat(service.confirm(vpA, lunch.id()).status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName(
            "AUTH-20: PHT được giao dinh dưỡng cả trường xác nhận số suất cơ sở khác; bị thu hồi thì không xác nhận được")
    void mealCountConfirmFollowsNutritionScope() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        MealCountResponse lunch = lunch(service.getMealCounts(vpA, TODAY, null));
        // Thu hồi MEAL_COUNT_CONFIRM, còn ATTENDANCE_VIEW cơ sở mình: vẫn xem sổ, không xác nhận được
        CurrentUser vpARevoked = withPermissions(vpA, Set.of(Permission.ATTENDANCE_VIEW), Set.of());
        CurrentUser vpBSchoolWide =
                withPermissions(vpB, Set.of(Permission.MEAL_COUNT_CONFIRM), Set.of(Permission.MEAL_COUNT_CONFIRM));

        assertThat(lunch(service.getMealCounts(vpARevoked, TODAY, null)))
                .extracting(MealCountResponse::canConfirm, MealCountResponse::canAdjust)
                .containsExactly(false, false);
        expectError(() -> service.confirm(vpARevoked, lunch.id()), ApiCode.FORBIDDEN);
        assertThat(lunch(service.getMealCounts(vpBSchoolWide, TODAY, null)))
                .extracting(MealCountResponse::canConfirm, MealCountResponse::canAdjust)
                .containsExactly(true, true);
        assertThat(service.confirm(vpBSchoolWide, lunch.id()).confirmedByName()).isEqualTo("vpb");
    }

    @Test
    void lateChildAfterConfirmationIsRejectedAndCountStaysConfirmed() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();
        service.confirm(vpA, lunchId);

        // ATT-07: sổ đã xác nhận khóa cứng; PHT mở lại điểm danh nhưng GV không lưu đổi được
        reopenDay();
        expectCode(
                () -> attendanceService.saveClassAttendance(
                        teacherA, classA, request(present(child1), present(child2))),
                ApiCode.MEAL_COUNT_ALREADY_CONFIRMED);

        MealCountResponse review = lunch(service.getMealCounts(vpA, TODAY, null));
        assertThat(review.status()).isEqualTo("CONFIRMED");
        assertThat(review.needsReconfirmation()).isFalse();
        assertThat(review.totals().normal()).isEqualTo(1);
    }

    @Test
    void principalConfirmsAnyCampusAndKitchenSeesConfirmedCount() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(principal, TODAY, null)).id();

        MealCountResponse confirmed = service.confirm(principal, lunchId);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedByName()).isEqualTo("principal");
        assertThat(confirmed.confirmedByRole()).isEqualTo(StaffRole.PRINCIPAL);

        // ATT-07: đổi điểm danh sau xác nhận bị từ chối, bếp vẫn nhận số đã xác nhận
        reopenDay();
        expectCode(
                () -> attendanceService.saveClassAttendance(
                        teacherA, classA, request(present(child1), present(child2))),
                ApiCode.MEAL_COUNT_ALREADY_CONFIRMED);
        assertThat(lunch(service.getMealCounts(principal, TODAY, null)))
                .extracting(MealCountResponse::needsReconfirmation, MealCountResponse::canConfirm)
                .containsExactly(false, false);
        assertThat(lunch(service.getMealCounts(kitchenA, TODAY, null)).totals().normal())
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "NUT-02: không còn điều chỉnh suất thay thế; người ngoài điểm trường vẫn bị từ chối (Cần chốt 25/11/2026)")
    void adjustmentsNoLongerChangeServings() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();
        service.confirm(vpA, lunchId);

        MealAdjustRequest adjust =
                new MealAdjustRequest(campusA, TODAY, MealSession.LUNCH, classA, null, 1, "Trẻ dị ứng sữa");
        expectError(() -> service.adjust(vpB, adjust), ApiCode.FORBIDDEN);
        expectCode(() -> service.adjust(vpA, adjust), ApiCode.MEAL_COUNT_UNCHANGED);
        expectCode(() -> service.adjust(principal, adjust), ApiCode.MEAL_COUNT_UNCHANGED);
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null))).satisfies(c -> {
            assertThat(c.needsReconfirmation()).isFalse();
            assertThat(c.totals().normal()).isEqualTo(2);
            assertThat(c.totals().substitute()).isZero();
        });
    }

    @Test
    @DisplayName("ATT-07: đổi điểm danh sau khi xác nhận bị từ chối, sổ suất ăn giữ nguyên, không có lịch sử đổi")
    void attendanceChangeAfterConfirmIsRejected() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();
        service.confirm(vpA, lunchId);

        reopenDay();
        expectCode(
                () -> attendanceService.saveClassAttendance(
                        teacherA, classA, request(present(child1), present(child2))),
                ApiCode.MEAL_COUNT_ALREADY_CONFIRMED);

        MealCountResponse review = lunch(service.getMealCounts(vpA, TODAY, null));
        assertThat(review.needsReconfirmation()).isFalse();
        assertThat(review.totals())
                .extracting(MealCountResponse.Totals::normal, MealCountResponse.Totals::substitute)
                .containsExactly(1, 0);
        assertThat(jdbc.queryForList(
                        "SELECT action FROM meal_count_history WHERE meal_count_id = ? ORDER BY id",
                        String.class,
                        lunchId))
                .containsExactly("AGGREGATED", "CONFIRMED");
        assertThat(jdbc.queryForList(
                        "SELECT user_id FROM notifications WHERE type = 'MEAL_COUNT_RECONFIRM'", Long.class))
                .isEmpty();

        // ATT-08: mở lại (trả lại GV kiểm tra) vẫn báo giáo viên lớp
        assertThat(jdbc.queryForList(
                        "SELECT user_id FROM notifications WHERE type = 'ATTENDANCE_DAY_UNLOCKED'", Long.class))
                .contains(teacherA.userId());
    }

    @Test
    @DisplayName("Tổng hợp điểm danh không có suất thay thế, kể cả khi trẻ có khai dị ứng")
    void attendanceSummaryHasNoSubstitute() {
        declareAllergy(child1);
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        service.getMealCounts(vpA, TODAY, null);

        var summary = attendanceService.getClassSummary(vpA, classA, TODAY, TODAY);
        assertThat(summary.days())
                .singleElement()
                .satisfies(d -> assertThat(d.substitute()).isZero());
    }

    @Test
    void pastDayInitialConfirmationRemainsBlocked() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        var count = service.getMealCounts(vpA, TODAY, null).counts().getFirst();
        clock.now = AFTER_CUTOFF.plus(Duration.ofDays(1));
        expectCode(() -> service.confirm(vpA, count.id()), ApiCode.MEAL_COUNT_PAST_DAY);
    }

    @Test
    @DisplayName("NUT-02, NUT-23: trẻ có khai dị ứng vẫn tính suất thường, không sinh suất thay thế")
    void allergicChildrenInProfileStillGetNormalServings() {
        declareAllergy(child1);
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        // Trước giờ chốt (tạm tính) và sau giờ chốt (sổ) đều là 2 suất thường
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null)).totals())
                .extracting(MealCountResponse.Totals::normal, MealCountResponse.Totals::substitute)
                .containsExactly(2, 0);
        clock.now = AFTER_CUTOFF;
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null)).totals())
                .extracting(MealCountResponse.Totals::normal, MealCountResponse.Totals::substitute)
                .containsExactly(2, 0);
    }

    @Test
    void confirmRejectsFiguresTheReviewerHasNotSeen() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        var counts = service.getMealCounts(vpA, TODAY, null).counts();
        int seen = servings(counts);
        // Điểm danh đổi sau lúc PHT mở sổ: số đang thấy đã cũ
        reopenAndMarkBothPresent();
        expectCode(
                () -> service.confirm(vpA, counts.getFirst().id(), seen, null), ApiCode.MEAL_COUNT_CHANGED_SINCE_VIEW);
        var fresh = service.getMealCounts(vpA, TODAY, null).counts();
        assertThat(service.confirm(vpA, counts.getFirst().id(), servings(fresh), null)
                        .status())
                .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName(
            "INS-03: M2 hoặc M3 của ngày đã ký ⇒ không điều chỉnh, không xác nhận suất (6106); Sửa lại biên bản thì được")
    void signedProcessOrServeSheetLocksServings() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();

        signSheet("PROCESS");
        expectCode(() -> service.confirm(vpA, lunchId), ApiCode.MEAL_COUNT_INSPECTION_SIGNED);
        unsignSheet("PROCESS");
        assertThat(service.confirm(vpA, lunchId).status()).isEqualTo("CONFIRMED");

        signSheet("SERVE");
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null))).satisfies(c -> {
            assertThat(c.inspectionSigned()).isTrue();
            assertThat(c.canAdjust()).isFalse();
            assertThat(c.canConfirm()).isFalse();
        });
    }

    private void signSheet(String tab) {
        jdbc.update(
                "INSERT INTO inspection_sheets (campus_id, sheet_date, tab, status, signature) VALUES (?, ?, ?, 'SIGNED',"
                        + " '{\"signedBy\":1,\"signedByName\":\"PHT\",\"signedAt\":\"2026-10-21T03:00:00Z\","
                        + "\"documentVersion\":1,\"imageKey\":\"sig.png\",\"contentType\":\"image/png\","
                        + "\"sizeBytes\":10}'::jsonb)",
                campusA,
                TODAY,
                tab);
    }

    private void unsignSheet(String tab) {
        jdbc.update(
                "DELETE FROM inspection_sheets WHERE campus_id = ? AND sheet_date = ? AND tab = ?",
                campusA,
                TODAY,
                tab);
    }

    private static int servings(List<MealCountResponse> counts) {
        return counts.stream()
                .mapToInt(c -> c.totals().normal() + c.totals().substitute())
                .sum();
    }

    // Mở lại điểm danh (trả lại GV kiểm tra) rồi cả hai trẻ có mặt: số của lớp đổi khi sổ chưa xác nhận
    private void reopenAndMarkBothPresent() {
        reopenDay();
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        service.markAttendanceChanged(TODAY, campusA, classA);
    }

    // Mở lại điểm danh (trả lại GV kiểm tra) sau giờ chốt
    private void reopenDay() {
        unlockService.unlockDay(
                vpA, new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Trẻ đến muộn"));
    }

    private void declareAllergy(long childId) {
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by)"
                        + " VALUES (?, 'REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', ?)",
                childId,
                vpA.userId());
        jdbc.update("INSERT INTO child_allergies (child_id, name) VALUES (?, 'Sữa bò')", childId);
    }

    @Test
    @DisplayName("ATT/NUT: bữa đã ghi vẫn hiện trước và sau chốt khi bỏ bữa khỏi cấu hình")
    void recordedMealSurvivesConfigurationChangeBeforeLedgerCreation() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        jdbc.update(
                "UPDATE school_year_age_group_members SET meal_sessions='AFTERNOON'"
                        + " WHERE school_year_id=(SELECT school_year_id FROM classrooms WHERE id=?)",
                classA);
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null)).totals().normal())
                .isEqualTo(2);
        assertThat(service.getClassMealCount(teacherA, classA, TODAY).meals()).anySatisfy(m -> {
            assertThat(m.session()).isEqualTo(MealSession.LUNCH);
            assertThat(m.normal()).isEqualTo(2);
        });
        clock.now = AFTER_CUTOFF;
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null)).totals().normal())
                .isEqualTo(2);
        jdbc.update("UPDATE attendance_records SET meals='' WHERE classroom_id=? AND date=?", classA, TODAY);
        entityManager.clear();
        service.markAttendanceChanged(TODAY, campusA, classA);
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null)).totals().normal())
                .isZero();
    }

    @Test
    void endOfDayJobRecordsLedgerNobodyOpened() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        service.aggregateLockedDay();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM meal_counts WHERE campus_id = ? AND date = ?",
                        Integer.class,
                        campusA,
                        TODAY))
                .isPositive();
        // Chạy lại không tạo trùng
        int before = jdbc.queryForObject("SELECT count(*) FROM meal_counts", Integer.class);
        service.aggregateLockedDay();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_counts", Integer.class))
                .isEqualTo(before);
    }

    private static MealCountResponse lunch(MealCountDayResponse day) {
        return day.counts().stream()
                .filter(c -> c.session() == MealSession.LUNCH)
                .findFirst()
                .orElseThrow();
    }

    private static SaveAttendanceRequest request(Entry... entries) {
        return new SaveAttendanceRequest(TODAY, List.of(entries));
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
        // Quyền mặc định = seed ma trận (NUT-02: PHT có MEAL_COUNT_CONFIRM ở cơ sở mình)
        return new CurrentUser(
                id, "kc-" + username, username, username, null, Set.of(role), TestPermissions.defaultsOf(role));
    }

    private static CurrentUser withPermissions(
            CurrentUser user, Set<Permission> permissions, Set<Permission> schoolWide) {
        return new CurrentUser(
                user.userId(),
                user.keycloakUserId(),
                user.username(),
                user.fullName(),
                user.email(),
                user.roles(),
                permissions,
                schoolWide);
    }

    private void campusRole(CurrentUser user, String staffRole, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, ?, ?, '2026-01-01')",
                user.userId(),
                staffRole,
                campusId);
    }

    private void enroll(long childId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classA);
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

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
