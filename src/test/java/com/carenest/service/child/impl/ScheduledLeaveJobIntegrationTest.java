package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.WithdrawChildRequest;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildRecordService;
import com.carenest.support.MovableClock;
import com.carenest.utils.enums.CareNestRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Áp lịch nghỉ học (G94-180/CHD-03, CHD-04, user chốt 2026-11-25): trẻ vẫn Đang học tới ngày nghỉ; job 00:05 ngày đó và
 * lần chạy lúc khởi động cho trẻ nghỉ, khóa phụ huynh không còn con đang học. Không có transaction của test: job mở
 * transaction riêng cho từng trẻ và listener khóa tài khoản chỉ chạy sau commit nên dữ liệu được commit thật (container
 * riêng của lớp). Mỗi test dùng một tuần riêng; lịch của test đều được áp hoặc dọn trong test nên không lọt sang test
 * khác. Keycloak giả lập. Dữ liệu giả.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ScheduledLeaveJobIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @MockitoBean
    private IdentityProviderClient identityProviderClient;

    @Autowired
    private MovableClock clock;

    @Autowired
    private ChildRecordService recordService;

    @Autowired
    private ScheduledLeaveJob scheduledLeaveJob;

    @Autowired
    private com.carenest.service.child.ChildIntakeService intakeService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long classroom;
    private long otherClassroom;

    @BeforeEach
    void setUp() {
        // Dữ liệu commit thật, chỉ một năm học ACTIVE: trường, lớp, HT tạo ở test chạy đầu, các test sau dùng lại
        if (jdbc.queryForObject("SELECT count(*) FROM classrooms WHERE name = 'Lớp SL1'", Integer.class) == 0) {
            createSchool();
        }
        classroom = jdbc.queryForObject("SELECT id FROM classrooms WHERE name = 'Lớp SL1'", Long.class);
        otherClassroom = jdbc.queryForObject("SELECT id FROM classrooms WHERE name = 'Lớp SL2'", Long.class);
        long principalId = jdbc.queryForObject("SELECT id FROM user_accounts WHERE username = 'ht.sl'", Long.class);
        principal = new CurrentUser(
                principalId,
                "kc-ht-sl",
                "ht.sl",
                "HT giả",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                TestPermissions.defaultsOf(CareNestRole.PRINCIPAL));
    }

    @Test
    @DisplayName("G94-180/CHD-03, CHD-04: lịch nghỉ thứ 4 ⇒ job thứ 3 chưa làm gì; job 00:05 thứ 4 cho nghỉ từ ngày hẹn"
            + " (lớp tới hết thứ 3, lịch chuyển lớp thứ 6 bị hủy, xóa lịch, lịch sử WITHDRAWN của người đặt lịch), khóa"
            + " phụ huynh; chạy lại trong ngày không áp lần 2")
    void jobAppliesLeaveOnLeaveDayOnlyOnce() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalDate wednesday = monday.plusDays(2);
        Pupil pupil = pupilWithSoleParent(1);
        scheduleTransfer(pupil.id(), monday.plusDays(4));
        scheduleLeave(pupil, monday, wednesday);

        clock.set(monday.plusDays(1), LocalTime.of(0, 5));
        scheduledLeaveJob.applyDueScheduledLeaves();
        assertThat(childState(pupil.id())).isEqualTo("ACTIVE||" + wednesday + "|Chuyển trường");
        assertThat(accountStatus(pupil.parentId())).isEqualTo("ACTIVE");
        verify(identityProviderClient, never()).setUserEnabled(eq(pupil.keycloakId()), anyBoolean());

        clock.set(wednesday, LocalTime.of(0, 5));
        assertThat(recordService.applyDueScheduledLeaves(wednesday)).contains(pupil.id());

        assertThat(childState(pupil.id())).isEqualTo("LEFT|" + wednesday + "||Chuyển trường");
        assertThat(jdbc.queryForObject(
                        "SELECT valid_to FROM enrollments WHERE child_id = ? AND classroom_id = ?",
                        LocalDate.class,
                        pupil.id(),
                        classroom))
                .isEqualTo(wednesday.minusDays(1));
        assertThat(count(
                        "SELECT count(*) FROM enrollments WHERE child_id = ? AND classroom_id = ?",
                        pupil.id(),
                        otherClassroom))
                .isZero();
        assertThat(count(
                        "SELECT count(*) FROM child_placements WHERE child_id = ? AND to_classroom_id = ?"
                                + " AND cancelled_at IS NOT NULL",
                        pupil.id(),
                        otherClassroom))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM child_history WHERE child_id = ? AND action = 'WITHDRAWN' AND user_id = ?"
                                + " AND note = 'Chuyển trường'",
                        pupil.id(),
                        principal.userId()))
                .isEqualTo(1);
        assertLockedBySystem(pupil);

        // Khởi động lại BE trong ngày: lịch đã xóa ⇒ không áp lại, không ghi lịch sử lần 2
        clock.set(wednesday, LocalTime.of(7, 30));
        assertThat(recordService.applyDueScheduledLeaves(wednesday)).doesNotContain(pupil.id());
        scheduledLeaveJob.catchUpOnStartup();
        assertThat(count("SELECT count(*) FROM child_history WHERE child_id = ? AND action = 'WITHDRAWN'", pupil.id()))
                .isEqualTo(1);
        assertThat(childState(pupil.id())).isEqualTo("LEFT|" + wednesday + "||Chuyển trường");
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03: lịch chuyển lớp thứ 4 trước ngày nghỉ thứ 6 ⇒ thứ 4 trẻ sang lớp mới, thứ 6 nghỉ ở lớp"
                    + " mới (lớp mới tới hết thứ 5)")
    void transferBeforeLeaveDayEndsInNewClass() {
        LocalDate monday = LocalDate.of(2026, 11, 30);
        LocalDate wednesday = monday.plusDays(2);
        LocalDate friday = monday.plusDays(4);
        Pupil pupil = pupilWithSoleParent(2);
        scheduleTransfer(pupil.id(), wednesday);
        scheduleLeave(pupil, monday, friday);

        clock.set(wednesday, LocalTime.of(0, 5));
        scheduledLeaveJob.applyDueScheduledLeaves();
        assertThat(childState(pupil.id())).startsWith("ACTIVE|");

        clock.set(friday, LocalTime.of(0, 5));
        scheduledLeaveJob.applyDueScheduledLeaves();

        assertThat(childState(pupil.id())).isEqualTo("LEFT|" + friday + "||Chuyển trường");
        assertThat(validTo(pupil.id(), classroom)).isEqualTo(wednesday.minusDays(1));
        assertThat(validTo(pupil.id(), otherClassroom)).isEqualTo(friday.minusDays(1));
        assertLockedBySystem(pupil);
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03, CHD-04: BE tắt qua 00:05 ngày nghỉ, khởi động lại 10 ngày sau ⇒ lần chạy lúc khởi động"
                    + " vẫn áp lịch từ đúng ngày hẹn và khóa phụ huynh (không giới hạn số ngày lỡ)")
    void startupRunAppliesMissedLeave() {
        LocalDate monday = LocalDate.of(2027, 1, 4);
        LocalDate tuesday = monday.plusDays(1);
        Pupil pupil = pupilWithSoleParent(3);
        scheduleLeave(pupil, monday, tuesday);

        clock.set(tuesday.plusDays(10), LocalTime.of(7, 30));
        scheduledLeaveJob.catchUpOnStartup();

        assertThat(childState(pupil.id())).isEqualTo("LEFT|" + tuesday + "||Chuyển trường");
        assertThat(validTo(pupil.id(), classroom)).isEqualTo(monday);
        assertLockedBySystem(pupil);
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03: một trẻ lỗi khi áp lịch (dữ liệu hỏng) ⇒ trẻ đó giữ nguyên lịch và trạng thái, trẻ khác"
                    + " cùng ngày vẫn được áp")
    void failingChildDoesNotBlockOthers() {
        LocalDate monday = LocalDate.of(2027, 2, 1);
        LocalDate leaveDay = monday.plusDays(7);
        // Lịch nghỉ trước ngày tiếp nhận, đặt thẳng bằng SQL: áp lịch vi phạm ck_children_left_on (left_on ≥
        // enrolled_on). Tạo trước để job gặp trẻ lỗi trước trẻ đúng (job đi theo id)
        long broken = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on,"
                + " scheduled_leave_on, scheduled_leave_reason, scheduled_leave_by) VALUES ('SL0099', 'Trẻ giả hỏng',"
                + " '2022-03-01', 'MALE', 'ACTIVE', '" + leaveDay.plusDays(2) + "', '" + leaveDay + "', 'Hỏng', "
                + principal.userId() + ") RETURNING id");
        try {
            Pupil pupil = pupilWithSoleParent(4);
            scheduleLeave(pupil, monday, leaveDay);

            clock.set(leaveDay, LocalTime.of(0, 5));
            assertThat(recordService.applyDueScheduledLeaves(leaveDay))
                    .contains(pupil.id())
                    .doesNotContain(broken);

            assertThat(childState(pupil.id())).isEqualTo("LEFT|" + leaveDay + "||Chuyển trường");
            assertLockedBySystem(pupil);
            assertThat(childState(broken)).isEqualTo("ACTIVE||" + leaveDay + "|Hỏng");
            assertThat(count("SELECT count(*) FROM child_history WHERE child_id = ?", broken))
                    .isZero();
        } finally {
            // Dọn lịch hỏng (kể cả khi assert lỗi) để job ở test khác không gặp lại
            jdbc.update(
                    "UPDATE children SET scheduled_leave_on = NULL, scheduled_leave_reason = NULL,"
                            + " scheduled_leave_by = NULL WHERE id = ?",
                    broken);
        }
    }

    @Test
    @DisplayName("G94-180/CHD-03, CHD-11: trẻ đã đăng ký năm sau ⇒ đặt lịch nghỉ vẫn giữ đăng ký (và ghi Nghỉ học ở"
            + " bảng năm sau); tới ngày nghỉ job hủy đăng ký")
    void plannedAdmissionKeptAtSchedulingAndCancelledAtApply() {
        LocalDate monday = LocalDate.of(2027, 3, 1);
        LocalDate leaveDay = monday.plusDays(9);
        long plannedYear = ensurePlannedYear();
        Pupil pupil = pupilWithSoleParent(6);
        long admission = insertId("INSERT INTO planned_admissions (child_id, school_year_id, campus_id, start_date)"
                + " VALUES (" + pupil.id() + ", " + plannedYear
                + ", (SELECT id FROM campuses WHERE code = 'SL-A'), '2027-09-05') RETURNING id");

        scheduleLeave(pupil, monday, leaveDay);

        assertThat(admissionStatus(admission)).isEqualTo("PENDING");
        assertThat(count(
                        "SELECT count(*) FROM class_placements WHERE child_id = ? AND school_year_id = ?"
                                + " AND action = 'LEAVE'",
                        pupil.id(),
                        plannedYear))
                .isEqualTo(1);

        clock.set(leaveDay, LocalTime.of(0, 5));
        assertThat(recordService.applyDueScheduledLeaves(leaveDay)).contains(pupil.id());

        assertThat(admissionStatus(admission)).isEqualTo("CANCELLED");
        assertThat(childState(pupil.id())).isEqualTo("LEFT|" + leaveDay + "||Chuyển trường");
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): hủy lịch nghỉ ⇒ trẻ vẫn Đang học, cột lịch null, dòng Nghỉ học"
            + " ở bảng năm sau bị xóa, lịch sử LEAVE_SCHEDULED rồi LEAVE_CANCELLED, tài khoản phụ huynh còn mở; job"
            + " ngày hẹn không còn gì để áp; hủy lần 2 lỗi; hồ sơ trả về scheduledLeaveOn")
    void cancelScheduledLeaveRestoresChild() {
        LocalDate monday = LocalDate.of(2027, 3, 15);
        LocalDate leaveDay = monday.plusDays(9);
        long plannedYear = ensurePlannedYear();
        Pupil pupil = pupilWithSoleParent(7);
        scheduleLeave(pupil, monday, leaveDay);
        var scheduledView = intakeService.getChildView(pupil.id());
        assertThat(scheduledView.status()).isEqualTo(com.carenest.utils.enums.ChildStatus.ACTIVE);
        assertThat(scheduledView.scheduledLeaveOn()).isEqualTo(leaveDay);
        assertThat(scheduledView.scheduledLeaveReason()).isEqualTo("Chuyển trường");

        var view = recordService.cancelScheduledLeave(principal, pupil.id());

        assertThat(view.status()).isEqualTo(com.carenest.utils.enums.ChildStatus.ACTIVE);
        assertThat(view.scheduledLeaveOn()).isNull();
        assertThat(view.scheduledLeaveReason()).isNull();
        assertThat(childState(pupil.id())).isEqualTo("ACTIVE|||");
        assertThat(count(
                        "SELECT count(*) FROM class_placements WHERE child_id = ? AND school_year_id = ?",
                        pupil.id(),
                        plannedYear))
                .isZero();
        assertThat(count(
                        "SELECT count(*) FROM child_history WHERE child_id = ? AND action = 'LEAVE_SCHEDULED'",
                        pupil.id()))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM child_history WHERE child_id = ? AND action = 'LEAVE_CANCELLED'"
                                + " AND user_id = ?",
                        pupil.id(),
                        principal.userId()))
                .isEqualTo(1);
        assertThat(accountStatus(pupil.parentId())).isEqualTo("ACTIVE");

        clock.set(leaveDay, LocalTime.of(0, 5));
        assertThat(recordService.applyDueScheduledLeaves(leaveDay)).doesNotContain(pupil.id());
        assertThat(childState(pupil.id())).isEqualTo("ACTIVE|||");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> recordService.cancelScheduledLeave(principal, pupil.id()))
                .isInstanceOfSatisfying(
                        com.carenest.exception.GlobalException.class,
                        ex -> assertThat(ex.getApiCode())
                                .isEqualTo(com.carenest.utils.ApiCode.CHILD_NO_SCHEDULED_LEAVE));
    }

    private record Pupil(long id, long parentId, String keycloakId) {}

    // Trẻ học lớp SL1 từ đầu năm, phụ huynh có tài khoản và chỉ có trẻ này
    private Pupil pupilWithSoleParent(int n) {
        long child = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on)"
                + " VALUES ('SL000" + n + "', 'Trẻ giả " + n + "', '2022-03-01', 'MALE', 'ACTIVE', '2026-09-01')"
                + " RETURNING id");
        jdbc.update(
                "INSERT INTO child_profiles (child_id, premature, gestation_weeks, ethnicity, beneficiary,"
                        + " payment_source, address_province, address_ward, address_street)"
                        + " VALUES (?, 'NO', 39, 'Kinh', 'Không', 'BHYT', 'Hà Nội', 'Cầu Giấy', '1 Láng')",
                child);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child,
                classroom);
        String phone = "090400000" + n;
        String keycloakId = "kc-parent-sl" + n;
        long parent = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('" + keycloakId + "', '" + phone + "', 'Phụ huynh giả " + n + "', 'PARENT') RETURNING id");
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id,"
                        + " linked_at, account_recipient) VALUES (?, 0, 'Mẹ giả', 'MOTHER', ?, ?, ?, now(), TRUE)",
                child,
                phone,
                "00119000030" + n,
                parent);
        return new Pupil(child, parent, keycloakId);
    }

    // Lịch chuyển lớp SL1 → SL2 từ ngày {@code from}, ghi như place(): lớp cũ tới hôm trước, lớp mới từ ngày đó, dòng
    // lịch sử xếp lớp
    private void scheduleTransfer(long childId, LocalDate from) {
        jdbc.update(
                "UPDATE enrollments SET valid_to = ? WHERE child_id = ? AND classroom_id = ?",
                from.minusDays(1),
                childId,
                classroom);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, ?)",
                childId,
                otherClassroom,
                from);
        jdbc.update(
                "INSERT INTO child_placements (child_id, from_classroom_id, to_classroom_id, placed_by, reason,"
                        + " effective_from) VALUES (?, ?, ?, ?, 'Đổi lớp', ?)",
                childId,
                classroom,
                otherClassroom,
                principal.userId(),
                from);
    }

    // HT đặt lịch nghỉ lúc 10:00 ngày {@code on}: trẻ vẫn Đang học, tài khoản phụ huynh còn mở
    private void scheduleLeave(Pupil pupil, LocalDate on, LocalDate leaveDay) {
        clock.set(on, LocalTime.of(10, 0));
        recordService.withdraw(principal, pupil.id(), new WithdrawChildRequest(leaveDay.toString(), "Chuyển trường"));
        assertThat(childState(pupil.id())).isEqualTo("ACTIVE||" + leaveDay + "|Chuyển trường");
        assertThat(accountStatus(pupil.parentId())).isEqualTo("ACTIVE");
    }

    // status|left_on|scheduled_leave_on|lý do (left_reason khi đã nghỉ, scheduled_leave_reason khi có lịch)
    private String childState(long childId) {
        return jdbc.queryForObject(
                "SELECT status || '|' || coalesce(left_on::text, '') || '|' || coalesce(scheduled_leave_on::text, '')"
                        + " || '|' || coalesce(left_reason, scheduled_leave_reason, '') FROM children WHERE id = ?",
                String.class,
                childId);
    }

    private LocalDate validTo(long childId, long classroomId) {
        return jdbc.queryForObject(
                "SELECT valid_to FROM enrollments WHERE child_id = ? AND classroom_id = ?",
                LocalDate.class,
                childId,
                classroomId);
    }

    private void assertLockedBySystem(Pupil pupil) {
        assertThat(accountStatus(pupil.parentId())).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT locked_by_system FROM user_accounts WHERE id = ?", Boolean.class, pupil.parentId()))
                .isTrue();
        verify(identityProviderClient).setUserEnabled(pupil.keycloakId(), false);
        verify(identityProviderClient).logoutUser(pupil.keycloakId());
    }

    private void createSchool() {
        long campus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('SL-A', 'Cơ sở A', TRUE) RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long ageGroup = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        for (String code : new String[] {"SL1", "SL2"}) {
            long room = insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campus
                    + ", '" + code + "', 'Phòng " + code + "', 'CLASS', 30) RETURNING id");
            insertId(
                    "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) VALUES ("
                            + campus + ", " + year + ", 'Lớp " + code + "', " + ageGroup + ", " + room
                            + ", 25) RETURNING id");
        }
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('kc-ht-sl', 'ht.sl', 'HT giả', 'PRINCIPAL') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
    }

    // Năm học sắp tới (PLANNED) của test này, tạo một lần: scheduleLeave ghi Nghỉ học vào bảng xếp lớp của năm này
    private long ensurePlannedYear() {
        Long existing = jdbc.query(
                "SELECT id FROM school_years WHERE status = 'PLANNED' AND name = '2027-2028'",
                rs -> rs.next() ? rs.getLong(1) : null);
        return existing != null
                ? existing
                : insertId("INSERT INTO school_years (name, start_date, end_date, status)"
                        + " VALUES ('2027-2028', '2027-09-01', '2028-05-31', 'PLANNED') RETURNING id");
    }

    private String admissionStatus(long admissionId) {
        return jdbc.queryForObject("SELECT status FROM planned_admissions WHERE id = ?", String.class, admissionId);
    }

    private String accountStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM user_accounts WHERE id = ?", String.class, userId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
