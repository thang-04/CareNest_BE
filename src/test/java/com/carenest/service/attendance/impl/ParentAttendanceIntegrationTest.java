package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceDay;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceResponse;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentDailyHealthResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.ParentAttendanceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.ParentAttendanceStatus;
import com.carenest.utils.enums.Permission;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** API phụ huynh xem điểm danh / sức khỏe đầu ngày của con (A2–A4). Hôm nay = thứ 4, 21/10/2026. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ParentAttendanceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z"); // 10:00 giờ trường

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
        FixedClock fixedClock() {
            return new FixedClock();
        }
    }

    @Autowired
    private ParentAttendanceService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser parent;
    private CurrentUser otherParent;
    private CurrentUser teacher;
    private long year;
    private long classA;
    private long classB;
    private long oldClass;
    private long child;

    @BeforeEach
    void setUp() {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, year);
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, closedYear);
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) VALUES (?, '2026-10-16', '2026-10-16', 'Nghỉ')",
                year);
        classA = classroom(campus, year, "Lớp A", ag3);
        classB = classroom(campus, year, "Lớp B", ag3);
        oldClass = classroom(campus, closedYear, "Lớp cũ", ag3);
        child = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES"
                + " ('IT' || substr(md5(random()::text), 1, 12), 'An', '2022-01-01', 'MALE', 'ACTIVE', '2025-09-01')"
                + " RETURNING id");
        enroll(child, oldClass, "2025-09-01", "2026-05-31");
        enroll(child, classA, "2026-09-01", "2026-10-19");
        enroll(child, classB, "2026-10-20", null);
        parent = user("parent", CareNestRole.PARENT);
        otherParent = user("parent2", CareNestRole.PARENT);
        teacher = user("teacher", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                parent.userId(),
                child);
    }

    @Test
    @DisplayName("Phụ huynh khác hoặc nhân viên ⇒ 4108")
    void onlyLinkedParentReads() {
        expectCode(() -> service.getToday(otherParent, child), ApiCode.PARENT_CHILD_FORBIDDEN);
        expectCode(() -> service.getToday(teacher, child), ApiCode.PARENT_CHILD_FORBIDDEN);
        expectCode(() -> service.getAttendance(otherParent, child, TODAY, TODAY), ApiCode.PARENT_CHILD_FORBIDDEN);
        expectCode(() -> service.getDailyHealth(otherParent, child, TODAY, TODAY), ApiCode.PARENT_CHILD_FORBIDDEN);
    }

    @Test
    void attendanceIncludesOnlySuccessfulPickupWithNameRelationshipAndTime() {
        record(classB, TODAY, "PRESENT", "LUNCH", null);
        long campus = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, classB);
        jdbc.update(
                "INSERT INTO pickup_records (child_id, classroom_id, campus_id, date, outcome, verification,"
                        + " guardian_position, pickup_person_name, pickup_person_relationship, pickup_person_phone, handed_over_at, note,"
                        + " recorded_by, recorded_at) VALUES (?, ?, ?, ?, 'HANDED_OVER', 'PHOTO_MATCH',"
                        + " 0, 'Mẹ kiểm thử', 'MOTHER', '', ?, '', ?, ?)",
                child,
                classB,
                campus,
                TODAY,
                java.sql.Timestamp.from(NOW),
                teacher.userId(),
                java.sql.Timestamp.from(NOW));
        ParentAttendanceDay day =
                service.getAttendance(parent, child, TODAY, TODAY).days().getFirst();
        assertThat(day.pickedUpByName()).isEqualTo("Mẹ kiểm thử (Mẹ)");
        assertThat(day.pickedUpAt()).isEqualTo(NOW);
    }

    @Test
    void attendanceDoesNotExposeUnsuccessfulPickup() {
        record(classB, TODAY, "PRESENT", "LUNCH", null);
        long campus = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, classB);
        jdbc.update(
                "INSERT INTO pickup_records (child_id, classroom_id, campus_id, date, outcome, verification,"
                        + " pickup_person_name, pickup_person_phone, note, recorded_by, recorded_at)"
                        + " VALUES (?, ?, ?, ?, 'NOT_HANDED_OVER', 'PHONE_DECLINED', 'Người gọi', '', '', ?, ?)",
                child,
                classB,
                campus,
                TODAY,
                teacher.userId(),
                java.sql.Timestamp.from(NOW));
        ParentAttendanceDay day =
                service.getAttendance(parent, child, TODAY, TODAY).days().getFirst();
        assertThat(day.pickedUpByName()).isNull();
        assertThat(day.pickedUpAt()).isNull();
    }

    @Test
    void attendanceUsesPhoneConfirmationDescriptionWhenPickupNameIsBlank() {
        long campus = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, classB);
        jdbc.update(
                "INSERT INTO pickup_records (child_id, classroom_id, campus_id, date, outcome, verification,"
                        + " pickup_person_name, pickup_person_phone, handed_over_at, note, recorded_by, recorded_at)"
                        + " VALUES (?, ?, ?, ?, 'HANDED_OVER', 'PHONE_CONFIRMED', '', '', ?, '', ?, ?)",
                child,
                classB,
                campus,
                TODAY,
                java.sql.Timestamp.from(NOW),
                teacher.userId(),
                java.sql.Timestamp.from(NOW));
        ParentAttendanceDay day =
                service.getAttendance(parent, child, TODAY, TODAY).days().getFirst();
        assertThat(day.pickedUpByName()).isEqualTo("Người đón đã được phụ huynh xác nhận qua điện thoại");
        assertThat(day.pickedUpAt()).isEqualTo(NOW);
        assertThat(day.status()).isEqualTo(ParentAttendanceStatus.NOT_RECORDED);
    }

    @Test
    @DisplayName("Trẻ chuyển lớp: mỗi ngày mang lớp của ngày đó; nghỉ lễ, cuối tuần là NO_SCHOOL; tổng nửa buổi 0,5")
    void transferredChildShowsClassPerDay() {
        record(classA, LocalDate.of(2026, 10, 15), "PRESENT", "LUNCH,AFTERNOON", null);
        record(classA, LocalDate.of(2026, 10, 19), "EXCUSED", "", null);
        record(classB, LocalDate.of(2026, 10, 20), "PRESENT", "LUNCH", "AFTERNOON");

        ParentAttendanceResponse month = service.getAttendance(parent, child, LocalDate.of(2026, 10, 15), TODAY);

        assertThat(month.days())
                .extracting(ParentAttendanceDay::date, ParentAttendanceDay::status, ParentAttendanceDay::className)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 15), ParentAttendanceStatus.PRESENT, "Lớp A"),
                        tuple(LocalDate.of(2026, 10, 16), ParentAttendanceStatus.NO_SCHOOL, "Lớp A"),
                        tuple(LocalDate.of(2026, 10, 17), ParentAttendanceStatus.NO_SCHOOL, "Lớp A"),
                        tuple(LocalDate.of(2026, 10, 18), ParentAttendanceStatus.NO_SCHOOL, "Lớp A"),
                        tuple(LocalDate.of(2026, 10, 19), ParentAttendanceStatus.ABSENT_EXCUSED, "Lớp A"),
                        tuple(LocalDate.of(2026, 10, 20), ParentAttendanceStatus.PRESENT, "Lớp B"),
                        tuple(TODAY, ParentAttendanceStatus.NOT_RECORDED, "Lớp B"));
        ParentAttendanceDay halfDay = month.days().get(5);
        assertThat(halfDay.pickedUpByName()).isNull();
        assertThat(halfDay.pickedUpAt()).isNull();
        assertThat(halfDay.mealsEaten()).containsExactly(MealSession.LUNCH);
        assertThat(halfDay.cancelledMeals()).containsExactly(MealSession.AFTERNOON);
        assertThat(halfDay.arrivedAt()).isNotNull();
        assertThat(month.days().get(4).cancelledMeals()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(month.totals().schoolDays()).isEqualTo(4);
        assertThat(month.totals().present()).isEqualTo(1.5);
        assertThat(month.totals().excused()).isEqualTo(1.5);
        assertThat(month.totals().absent()).isZero();

        var today = service.getToday(parent, child);
        assertThat(today.status()).isEqualTo(ParentAttendanceStatus.NOT_RECORDED);
        assertThat(today.className()).isEqualTo("Lớp B");
    }

    @Test
    @DisplayName("CHD-04 (26/11/2026): trẻ đã thôi học thì phụ huynh mất quyền xem điểm danh")
    void withdrawnChildIsForbiddenForParent() {
        jdbc.update("DELETE FROM enrollments WHERE child_id = ? AND classroom_id = ?", child, classB);
        record(classA, LocalDate.of(2026, 10, 19), "PRESENT", "LUNCH", null);

        expectCode(
                () -> service.getAttendance(parent, child, LocalDate.of(2026, 10, 19), TODAY),
                ApiCode.PARENT_CHILD_FORBIDDEN);
        expectCode(() -> service.getToday(parent, child), ApiCode.PARENT_CHILD_FORBIDDEN);
    }

    @Test
    @DisplayName("Năm học đã đóng vẫn xem được; ngày trước khi vào lớp là NOT_ENROLLED")
    void closedYearAndDaysBeforeEnrollment() {
        record(oldClass, LocalDate.of(2026, 5, 29), "PRESENT", "LUNCH", null);
        ParentAttendanceResponse old =
                service.getAttendance(parent, child, LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 29));
        assertThat(old.days().getLast().status()).isEqualTo(ParentAttendanceStatus.PRESENT);
        assertThat(old.days().getLast().className()).isEqualTo("Lớp cũ");

        long late = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES"
                + " ('IT' || substr(md5(random()::text), 1, 12), 'Bình', '2022-01-01', 'MALE', 'ACTIVE', '2026-10-14')"
                + " RETURNING id");
        enroll(late, classA, "2026-10-14", null);
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                parent.userId(),
                late);
        assertThat(service.getAttendance(parent, late, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14))
                        .days())
                .extracting(ParentAttendanceDay::status)
                .containsExactly(
                        ParentAttendanceStatus.NOT_ENROLLED,
                        ParentAttendanceStatus.NOT_ENROLLED,
                        ParentAttendanceStatus.NOT_RECORDED);
    }

    @Test
    @DisplayName("Khoảng quá 62 ngày hoặc vượt năm học ⇒ 4109")
    void rangeLimits() {
        expectCode(
                () -> service.getAttendance(parent, child, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 2)),
                ApiCode.PARENT_RANGE_INVALID);
        expectCode(
                () -> service.getAttendance(parent, child, LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 5)),
                ApiCode.PARENT_RANGE_INVALID);
        expectCode(
                () -> service.getAttendance(parent, child, LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 4)),
                ApiCode.PARENT_RANGE_INVALID);
        expectCode(() -> service.getAttendance(parent, child, TODAY, TODAY.minusDays(1)), ApiCode.PARENT_RANGE_INVALID);
        expectCode(
                () -> service.getDailyHealth(parent, child, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 2)),
                ApiCode.PARENT_RANGE_INVALID);
        assertThat(service.getAttendance(parent, child, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 1))
                        .days())
                .hasSize(62);
    }

    @Test
    @DisplayName("Ngày đang được mở lại: phụ huynh thấy số trước khi mở lại, không thấy số GV đang sửa")
    void reopenedDayShowsStateBeforeReopening() {
        LocalDate day = LocalDate.of(2026, 10, 20);
        long recordId = record(classB, day, "PRESENT", "LUNCH", null);
        history(recordId, day, "RECORDED", "PRESENT", null);
        long unlock = insertId("INSERT INTO attendance_day_unlocks (classroom_id, campus_id, date, valid_until, reason,"
                + " unlocked_by, unlocked_at) SELECT id, campus_id, '2026-10-20', '2026-10-21T10:00:00Z', 'Sửa', "
                + teacher.userId() + ", '2026-10-21T02:00:00Z' FROM classrooms WHERE id = " + classB + " RETURNING id");
        jdbc.update("UPDATE attendance_records SET status = 'UNEXCUSED', meals = '' WHERE id = ?", recordId);
        history(recordId, day, "UPDATED", "UNEXCUSED", unlock);

        assertThat(service.getAttendance(parent, child, day, day)
                        .days()
                        .getFirst()
                        .status())
                .isEqualTo(ParentAttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("Sức khỏe đầu ngày: thân nhiệt, triệu chứng, ghi chú, người ghi, lớp của ngày đó")
    void dailyHealthForParent() {
        jdbc.update(
                "INSERT INTO daily_health_entries (request_id, child_id, classroom_id, campus_id, date, temperature,"
                        + " observation, symptoms, note, recorded_by, recorded_at) SELECT 'r1', ?, id, campus_id,"
                        + " '2026-10-19', 37.8, 'NOTED', 'COUGH,FEVER', 'Bé ho nhẹ', ?, now() FROM classrooms WHERE id = ?",
                child,
                teacher.userId(),
                classA);
        jdbc.update(
                "INSERT INTO daily_health_entries (request_id, child_id, classroom_id, campus_id, date, temperature,"
                        + " observation, recorded_by, recorded_at) SELECT 'r2', ?, id, campus_id, '2026-10-20', 36.6,"
                        + " 'NO_CONCERN', ?, now() FROM classrooms WHERE id = ?",
                child,
                teacher.userId(),
                classB);

        var entries = service.getDailyHealth(parent, child, LocalDate.of(2026, 10, 19), TODAY);

        assertThat(entries)
                .extracting(ParentDailyHealthResponse::date, ParentDailyHealthResponse::className)
                .containsExactlyInAnyOrder(
                        tuple(LocalDate.of(2026, 10, 19), "Lớp A"), tuple(LocalDate.of(2026, 10, 20), "Lớp B"));
        ParentDailyHealthResponse noted = entries.stream()
                .filter(e -> e.date().equals(LocalDate.of(2026, 10, 19)))
                .findFirst()
                .orElseThrow();
        assertThat(noted.temperature()).isEqualByComparingTo(new BigDecimal("37.8"));
        assertThat(noted.symptoms()).containsExactlyInAnyOrder(HealthSymptom.COUGH, HealthSymptom.FEVER);
        assertThat(noted.note()).isEqualTo("Bé ho nhẹ");
        assertThat(noted.recordedByName()).isEqualTo("teacher");
    }

    private long record(long classId, LocalDate date, String status, String meals, String absentPart) {
        return insertId("INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals,"
                + " absent_part, recorded_by, recorded_at) SELECT " + child + ", id, campus_id, '" + date + "', '"
                + status + "', '" + meals + "', " + (absentPart == null ? "NULL" : "'" + absentPart + "'") + ", "
                + teacher.userId() + ", '2026-10-21T00:30:00Z' FROM classrooms WHERE id = " + classId
                + " RETURNING id");
    }

    private void history(long recordId, LocalDate date, String action, String status, Long unlockId) {
        jdbc.update(
                "INSERT INTO attendance_record_history (record_id, child_id, classroom_id, campus_id, date, action,"
                        + " status, meals, user_id, at, unlock_id) SELECT r.id, r.child_id, r.classroom_id, r.campus_id,"
                        + " ?, ?, ?, '', ?, now(), ? FROM attendance_records r WHERE r.id = ?",
                date,
                action,
                status,
                teacher.userId(),
                unlockId,
                recordId);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions = role == CareNestRole.TEACHER
                ? Set.of(Permission.ATTENDANCE_VIEW)
                : Set.of(Permission.CHILD_PROFILE_VIEW);
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusId
                + ", " + yearId + ", '" + name + "', " + ageGroupId + ") RETURNING id");
    }

    private void enroll(long childId, long classroomId, String from, String to) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, ?::date, ?::date)",
                childId,
                classroomId,
                from,
                to);
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
