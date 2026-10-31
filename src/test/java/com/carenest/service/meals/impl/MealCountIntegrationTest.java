package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest.Entry;
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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

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

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser kitchenA;
    private long campusA;
    private long classA;
    private long child1;
    private long child2;

    @BeforeEach
    void setUp() {
        clock.now = BEFORE_CUTOFF;
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        child1 = insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('An', '2023-01-01') RETURNING id");
        child2 = insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('Bình', '2023-02-01') RETURNING id");
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
        // AUTH-20: PHT điểm trường khác xem được nhưng không xác nhận/điều chỉnh; HT xác nhận được, không điều chỉnh
        // (Cần chốt 27/10)
        assertThat(lunch(service.getMealCounts(vpB, TODAY, null)))
                .extracting(MealCountResponse::id, MealCountResponse::canConfirm, MealCountResponse::canAdjust)
                .containsExactly(lunch.id(), false, false);
        expectError(() -> service.confirm(vpB, lunch.id()), ApiCode.FORBIDDEN);
        assertThat(lunch(service.getMealCounts(principal, TODAY, null)))
                .extracting(MealCountResponse::canConfirm, MealCountResponse::canAdjust)
                .containsExactly(true, false);

        MealCountResponse confirmed = service.confirm(vpA, lunch.id());
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedByName()).isEqualTo("vpa");
        assertThat(confirmed.confirmedByRole()).isEqualTo(StaffRole.VICE_PRINCIPAL);
        assertThat(confirmed.classes().getFirst().ageGroupId()).isNotNull();
        expectError(() -> service.confirm(vpA, lunch.id()), ApiCode.CONFLICT);
        assertThat(service.getMealCounts(kitchenA, TODAY, null).counts())
                .extracting(MealCountResponse::session)
                .containsExactly(MealSession.LUNCH);
    }

    @Test
    @DisplayName(
            "AUTH-20: PHT được giao dinh dưỡng cả trường xác nhận số suất cơ sở khác; bị thu hồi thì không xác nhận được")
    void mealCountConfirmFollowsNutritionScope() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        MealCountResponse lunch = lunch(service.getMealCounts(vpA, TODAY, null));
        CurrentUser vpARevoked = withPermissions(vpA, Set.of(), Set.of());
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
    void lateChildAfterConfirmationNeedsReconfirmationWhileKitchenKeepsOldCount() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();
        service.confirm(vpA, lunchId);

        // Trẻ đến muộn: PHT mở lại, GV ghi có mặt + ăn trưa (ATT-07)
        unlockService.unlockDay(
                vpA, new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Trẻ đến muộn"));
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        service.markAttendanceChanged(TODAY, campusA, classA);

        MealCountResponse review = lunch(service.getMealCounts(vpA, TODAY, null));
        assertThat(review.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(review.needsReconfirmation()).isTrue();
        assertThat(review.totals().normal()).isEqualTo(2);
        MealCountResponse kitchen = lunch(service.getMealCounts(kitchenA, TODAY, null));
        assertThat(kitchen.status()).isEqualTo("CONFIRMED");
        assertThat(kitchen.needsReconfirmation()).isFalse();
        assertThat(kitchen.totals().normal()).isEqualTo(1);

        service.confirm(vpA, lunchId);
        assertThat(lunch(service.getMealCounts(kitchenA, TODAY, null)).totals().normal())
                .isEqualTo(2);
        assertThat(lunch(service.getMealCounts(vpA, TODAY, null)).needsReconfirmation())
                .isFalse();
    }

    @Test
    void principalConfirmsAnyCampusAndReconfirmsAfterChange() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(principal, TODAY, null)).id();

        MealCountResponse confirmed = service.confirm(principal, lunchId);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedByName()).isEqualTo("principal");
        assertThat(confirmed.confirmedByRole()).isEqualTo(StaffRole.PRINCIPAL);
        expectError(() -> service.confirm(principal, lunchId), ApiCode.CONFLICT);

        // ATT-07: đổi điểm danh sau xác nhận ⇒ chờ xác nhận lại; HT xác nhận lại, bếp nhận số mới
        unlockService.unlockDay(
                vpA, new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Trẻ đến muộn"));
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        service.markAttendanceChanged(TODAY, campusA, classA);
        assertThat(lunch(service.getMealCounts(principal, TODAY, null)))
                .extracting(MealCountResponse::needsReconfirmation, MealCountResponse::canConfirm)
                .containsExactly(true, true);
        service.confirm(principal, lunchId);
        assertThat(lunch(service.getMealCounts(kitchenA, TODAY, null)).totals().normal())
                .isEqualTo(2);
    }

    @Test
    void leadersAdjustWithReasonAndConfirmedCountWaitsForReconfirmation() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();
        service.confirm(vpA, lunchId);

        MealAdjustRequest adjust =
                new MealAdjustRequest(campusA, TODAY, MealSession.LUNCH, classA, 3, 0, " Cô báo thêm suất ");
        expectError(() -> service.adjust(vpB, adjust), ApiCode.FORBIDDEN);
        expectError(() -> service.adjust(principal, adjust), ApiCode.FORBIDDEN);
        List<MealCountResponse> adjusted = service.adjust(vpA, adjust);
        assertThat(adjusted).singleElement().satisfies(c -> {
            assertThat(c.needsReconfirmation()).isTrue();
            assertThat(c.totals().normal()).isEqualTo(3);
            assertThat(c.adjustments())
                    .singleElement()
                    .extracting(MealCountResponse.Adjustment::normal, MealCountResponse.Adjustment::note)
                    .containsExactly(1, "Cô báo thêm suất");
        });
        expectError(() -> service.adjust(vpA, adjust), ApiCode.BAD_REQUEST);
        assertThat(lunch(service.getMealCounts(kitchenA, TODAY, null)).totals().normal())
                .isEqualTo(2);

        // Cả ngày (không chọn bữa): áp cho mọi bữa của lớp
        service.adjust(vpA, new MealAdjustRequest(campusA, TODAY, null, classA, 1, 0, "Trẻ về sớm"));
        assertThat(service.getMealCounts(vpA, TODAY, null).counts())
                .allSatisfy(c -> assertThat(c.totals().normal()).isEqualTo(1));
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

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
