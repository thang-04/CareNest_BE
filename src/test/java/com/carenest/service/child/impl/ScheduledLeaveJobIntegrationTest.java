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
 * Khóa tài khoản phụ huynh khi trẻ nghỉ từ ngày học kế tiếp (G94-180/CHD-03, CHD-04, user chốt 2026-11-24): job 00:05
 * và lần chạy lúc khởi động xét trẻ có ngày nghỉ trong 7 ngày gần nhất. Không có transaction của test: listener khóa tài
 * khoản chỉ chạy sau commit nên dữ liệu được commit thật (container riêng của lớp). Mỗi test cho nghỉ ở một tuần riêng
 * để khoảng 7 ngày của test này không gặp trẻ của test khác. Keycloak giả lập. Dữ liệu giả.
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
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long classroom;

    @BeforeEach
    void setUp() {
        // Dữ liệu commit thật, chỉ một năm học ACTIVE: trường, lớp, HT tạo ở test chạy đầu, các test sau dùng lại
        if (jdbc.queryForObject("SELECT count(*) FROM classrooms WHERE name = 'Lớp SL1'", Integer.class) == 0) {
            createSchool();
        }
        classroom = jdbc.queryForObject("SELECT id FROM classrooms WHERE name = 'Lớp SL1'", Long.class);
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
    @DisplayName("G94-180/CHD-03, CHD-04: nghỉ từ ngày học kế tiếp ⇒ hôm nay tài khoản phụ huynh còn mở; job 00:05 ngày"
            + " nghỉ khóa tài khoản chỉ có trẻ này")
    void scheduledLeaveJobLocksParentOnLeaveDay() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        Parent parent = parentOfChildLeavingNextDay(1, monday);

        // Chạy trong ngày thứ 2: chưa trẻ nào nghỉ từ hôm nay ⇒ không khóa sớm
        scheduledLeaveJob.lockParentsOfChildrenWhoLeft();
        assertThat(accountStatus(parent.id())).isEqualTo("ACTIVE");
        verify(identityProviderClient, never()).setUserEnabled(eq(parent.keycloakId()), anyBoolean());

        clock.set(monday.plusDays(1), LocalTime.of(0, 5));
        scheduledLeaveJob.lockParentsOfChildrenWhoLeft();

        assertLockedBySystem(parent);
    }

    @Test
    @DisplayName("G94-180/CHD-03, CHD-04: BE tắt lúc 00:05 ngày nghỉ ⇒ lần chạy 00:05 ba ngày sau vẫn khóa (bù trong 7"
            + " ngày)")
    void missedRunIsCaughtUpWithinSevenDays() {
        LocalDate monday = LocalDate.of(2026, 11, 30);
        Parent parent = parentOfChildLeavingNextDay(2, monday);

        // Nghỉ từ thứ 3 01/12, job chạy lại thứ 6 04/12
        clock.set(monday.plusDays(4), LocalTime.of(0, 5));
        scheduledLeaveJob.lockParentsOfChildrenWhoLeft();

        assertLockedBySystem(parent);
    }

    @Test
    @DisplayName("G94-180/CHD-03: ngày nghỉ đã qua 8 ngày ⇒ job không xét nữa, tài khoản giữ nguyên")
    void leaveOlderThanSevenDaysIsLeftAlone() {
        LocalDate monday = LocalDate.of(2026, 12, 14);
        Parent parent = parentOfChildLeavingNextDay(3, monday);

        // Nghỉ từ thứ 3 15/12, job chạy thứ 4 23/12
        clock.set(monday.plusDays(9), LocalTime.of(0, 5));
        scheduledLeaveJob.lockParentsOfChildrenWhoLeft();

        assertThat(accountStatus(parent.id())).isEqualTo("ACTIVE");
        verify(identityProviderClient, never()).setUserEnabled(eq(parent.keycloakId()), anyBoolean());
    }

    @Test
    @DisplayName("G94-180/CHD-03, CHD-04: BE tắt qua 00:05 ngày nghỉ, khởi động lại buổi sáng ⇒ lần chạy lúc khởi động"
            + " khóa bù")
    void startupRunLocksParentWhenMidnightRunWasMissed() {
        LocalDate monday = LocalDate.of(2027, 1, 4);
        Parent parent = parentOfChildLeavingNextDay(4, monday);

        clock.set(monday.plusDays(1), LocalTime.of(7, 30));
        scheduledLeaveJob.catchUpOnStartup();

        assertLockedBySystem(parent);
    }

    private record Parent(long id, String keycloakId) {}

    // Trẻ học lớp SL1, phụ huynh chỉ có trẻ này; thứ 2 lúc 10:00 HT cho nghỉ từ thứ 3 (ngày học kế tiếp)
    private Parent parentOfChildLeavingNextDay(int n, LocalDate monday) {
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

        clock.set(monday, LocalTime.of(10, 0));
        recordService.withdraw(
                principal, child, new WithdrawChildRequest(monday.plusDays(1).toString(), "Chuyển trường"));
        assertThat(accountStatus(parent)).isEqualTo("ACTIVE");
        return new Parent(parent, keycloakId);
    }

    private void assertLockedBySystem(Parent parent) {
        assertThat(accountStatus(parent.id())).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT locked_by_system FROM user_accounts WHERE id = ?", Boolean.class, parent.id()))
                .isTrue();
        verify(identityProviderClient).setUserEnabled(parent.keycloakId(), false);
        verify(identityProviderClient).logoutUser(parent.keycloakId());
    }

    private void createSchool() {
        long campus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('SL-A', 'Cơ sở A', TRUE) RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long ageGroup = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long room = insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campus
                + ", 'SL1', 'Phòng SL1', 'CLASS', 30) RETURNING id");
        insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) VALUES ("
                + campus + ", " + year + ", 'Lớp SL1', " + ageGroup + ", " + room + ", 25) RETURNING id");
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('kc-ht-sl', 'ht.sl', 'HT giả', 'PRINCIPAL') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
    }

    private String accountStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM user_accounts WHERE id = ?", String.class, userId);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
