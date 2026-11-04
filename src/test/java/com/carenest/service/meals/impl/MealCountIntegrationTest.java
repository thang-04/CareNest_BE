package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
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
        // Người duyệt thấy số đã chốt cũ cạnh số mới của lớp thay đổi (NUT-03)
        assertThat(review.classes())
                .filteredOn(l -> l.classId().equals(classA))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.normal()).isEqualTo(2);
                    assertThat(l.confirmedNormal()).isEqualTo(1);
                });
        MealCountResponse kitchen = lunch(service.getMealCounts(kitchenA, TODAY, null));
        assertThat(kitchen.classes())
                .allSatisfy(l -> assertThat(l.confirmedNormal()).isNull());
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
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(principal, TODAY, null)).id();

        MealCountResponse confirmed = service.confirm(principal, lunchId);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedByName()).isEqualTo("principal");
        assertThat(confirmed.confirmedByRole()).isEqualTo(StaffRole.PRINCIPAL);

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
    @DisplayName("NUT-02: PHT chỉ điều chỉnh suất thay thế (có lý do, chờ xác nhận lại); sửa suất thường bị từ chối")
    void leadersAdjustOnlySubstituteServings() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();
        service.confirm(vpA, lunchId);

        MealAdjustRequest adjust =
                new MealAdjustRequest(campusA, TODAY, MealSession.LUNCH, classA, null, 1, " Trẻ dị ứng sữa ");
        expectError(() -> service.adjust(vpB, adjust), ApiCode.FORBIDDEN);
        expectError(() -> service.adjust(principal, adjust), ApiCode.FORBIDDEN);
        expectCode(
                () -> service.adjust(
                        vpA, new MealAdjustRequest(campusA, TODAY, MealSession.LUNCH, classA, 3, 1, "Thêm suất")),
                ApiCode.MEAL_COUNT_NORMAL_NOT_EDITABLE);
        List<MealCountResponse> adjusted = service.adjust(vpA, adjust);
        assertThat(adjusted).singleElement().satisfies(c -> {
            assertThat(c.needsReconfirmation()).isTrue();
            assertThat(c.totals().normal()).isEqualTo(2);
            assertThat(c.totals().substitute()).isEqualTo(1);
            assertThat(c.adjustments())
                    .singleElement()
                    .extracting(
                            MealCountResponse.Adjustment::normal,
                            MealCountResponse.Adjustment::substitute,
                            MealCountResponse.Adjustment::note)
                    .containsExactly(0, 1, "Trẻ dị ứng sữa");
        });
        // normal bằng số hiện tại = giữ nguyên (client cũ gửi lại số đang thấy)
        expectError(
                () -> service.adjust(vpA, new MealAdjustRequest(campusA, TODAY, MealSession.LUNCH, classA, 2, 1, "x")),
                ApiCode.BAD_REQUEST);
        assertThat(lunch(service.getMealCounts(kitchenA, TODAY, null)).totals().substitute())
                .isZero();

        // Cả ngày (không chọn bữa): áp cho mọi bữa của lớp
        service.adjust(vpA, new MealAdjustRequest(campusA, TODAY, null, classA, null, 2, "Thêm trẻ dị ứng"));
        assertThat(service.getMealCounts(vpA, TODAY, null).counts())
                .allSatisfy(c -> assertThat(c.totals().substitute()).isEqualTo(2));
    }

    @Test
    @DisplayName("NUT-02: đổi điểm danh sau khi điều chỉnh không xóa suất thay thế, lịch sử ghi đủ")
    void attendanceChangeKeepsAdjustedSubstitute() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), absent(child2)));
        clock.now = AFTER_CUTOFF;
        long lunchId = lunch(service.getMealCounts(vpA, TODAY, null)).id();
        service.adjust(vpA, new MealAdjustRequest(campusA, TODAY, MealSession.LUNCH, classA, null, 1, "Dị ứng"));
        service.confirm(vpA, lunchId);

        unlockService.unlockDay(
                vpA, new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Trẻ đến muộn"));
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        service.markAttendanceChanged(TODAY, campusA, classA);

        MealCountResponse review = lunch(service.getMealCounts(vpA, TODAY, null));
        assertThat(review.needsReconfirmation()).isTrue();
        assertThat(review.totals())
                .extracting(MealCountResponse.Totals::normal, MealCountResponse.Totals::substitute)
                .containsExactly(2, 1);
        assertThat(jdbc.queryForList(
                        "SELECT action FROM meal_count_history WHERE meal_count_id = ? ORDER BY id",
                        String.class,
                        lunchId))
                .containsExactly("AGGREGATED", "ADJUSTED", "CONFIRMED", "ATTENDANCE_CHANGED");
    }

    @Test
    void pastDayReconfirmationIsIdempotentAcrossSessions() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        var counts = service.getMealCounts(vpA, TODAY, null).counts();
        service.confirm(vpA, counts.getFirst().id());
        service.adjust(vpA, new MealAdjustRequest(campusA, TODAY, null, classA, null, 1, "Test substitute"));
        clock.now = AFTER_CUTOFF.plus(Duration.ofDays(1));
        for (var count : counts) {
            assertThat(service.confirm(vpA, count.id()).needsReconfirmation()).isFalse();
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM meal_count_history WHERE action='CONFIRMED'", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void pastDayInitialConfirmationRemainsBlocked() {
        attendanceService.saveClassAttendance(teacherA, classA, request(present(child1), present(child2)));
        clock.now = AFTER_CUTOFF;
        var count = service.getMealCounts(vpA, TODAY, null).counts().getFirst();
        clock.now = AFTER_CUTOFF.plus(Duration.ofDays(1));
        expectCode(() -> service.confirm(vpA, count.id()), ApiCode.MEAL_COUNT_PAST_DAY);
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
