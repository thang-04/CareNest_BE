package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.utils.enums.CareNestRole;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * Trẻ đã nghỉ học tính tới một ngày (CHD-03, CHD-04): cho nghỉ theo {@code children.status}, ra trường theo lớp đã đóng;
 * chờ xếp lớp (mới tiếp nhận hoặc mở lại hồ sơ) không tính là nghỉ. Kết quả quyết định phụ huynh xem trẻ, danh sách con ở
 * {@code GET /accounts/me} và khóa tài khoản. Hôm nay = 11/11/2026. Dữ liệu giả.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildLeftSchoolIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private ChildAccessPolicy childAccessPolicy;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long campus;
    private long classA1;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        campus = insertId(
                "INSERT INTO campuses (code, name, is_main) VALUES ('LS" + n + "', 'Cơ sở', TRUE) RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA1 = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campus + ", " + year
                + ", 'Lớp A1') RETURNING id");
    }

    @Test
    @DisplayName("A: đang học ⇒ chưa nghỉ")
    void studyingChildHasNotLeft() {
        long child = child("ACTIVE", "2026-09-01", null, null);
        enrollment(child, "2026-09-01", null);

        assertThat(leftToday(child)).isFalse();
    }

    @Test
    @DisplayName("B: mới tiếp nhận, chờ xếp lớp ⇒ chưa nghỉ")
    void newChildAwaitingPlacementHasNotLeft() {
        long child = child("PENDING_PLACEMENT", "2026-11-05", null, null);

        assertThat(leftToday(child)).isFalse();
    }

    @Test
    @DisplayName("C: đang học, cho nghỉ từ 05/11 (lớp kết thúc 04/11) ⇒ đã nghỉ")
    void withdrawnAfterStudyingHasLeft() {
        long child = child("LEFT", "2026-09-01", "2026-11-05", null);
        enrollment(child, "2026-09-01", "2026-11-04");

        assertThat(leftToday(child)).isTrue();
    }

    @Test
    @DisplayName("D, E: cho nghỉ khi chưa có lớp nào (chờ xếp lớp, hoặc nghỉ ngay ngày vào lớp ⇒ lớp bị xóa) ⇒ đã nghỉ")
    void withdrawnWithoutAnyClassHasLeft() {
        long child = child("LEFT", "2026-11-01", "2026-11-05", null);

        assertThat(leftToday(child)).isTrue();
    }

    @Test
    @DisplayName("F: mở lại hồ sơ, chờ xếp lớp, lớp cũ đã đóng ⇒ chưa nghỉ")
    void readmittedChildAwaitingPlacementHasNotLeft() {
        long child = child("PENDING_PLACEMENT", "2026-09-01", null, "2026-11-10");
        enrollment(child, "2026-09-01", "2026-10-31");

        assertThat(leftToday(child)).isFalse();
    }

    @Test
    @DisplayName("G: ra trường khi chuyển năm học (lớp đóng, trạng thái vẫn Đang học) ⇒ đã nghỉ")
    void graduatedChildHasLeft() {
        long child = child("ACTIVE", "2025-09-01", null, null);
        enrollment(child, "2025-09-01", "2026-08-31");

        assertThat(leftToday(child)).isTrue();
    }

    @Test
    @DisplayName(
            "H: lớp kết thúc trong tương lai (kích hoạt năm học sớm) ⇒ chưa nghỉ tới hết ngày đó, nghỉ từ ngày sau")
    void classEndingLaterLeavesOnlyAfterItsLastDay() {
        long child = child("ACTIVE", "2026-09-01", null, null);
        enrollment(child, "2026-09-01", "2026-11-30");

        assertThat(enrollmentService.findChildIdsLeftSchool(List.of(child), TODAY))
                .isEmpty();
        assertThat(enrollmentService.findChildIdsLeftSchool(List.of(child), LocalDate.of(2026, 12, 1)))
                .containsExactly(child);
    }

    @Test
    @DisplayName("Một lần gọi cho nhiều trẻ trả đúng các trẻ đã nghỉ")
    void batchReturnsOnlyChildrenWhoLeft() {
        long studying = child("ACTIVE", "2026-09-01", null, null);
        enrollment(studying, "2026-09-01", null);
        long withdrawnPending = child("LEFT", "2026-11-01", "2026-11-05", null);
        long readmitted = child("PENDING_PLACEMENT", "2026-09-01", null, "2026-11-10");
        enrollment(readmitted, "2026-09-01", "2026-10-31");
        long graduated = child("ACTIVE", "2025-09-01", null, null);
        enrollment(graduated, "2025-09-01", "2026-08-31");

        assertThat(enrollmentService.findChildIdsLeftSchool(
                        List.of(studying, withdrawnPending, readmitted, graduated), TODAY))
                .containsExactlyInAnyOrder(withdrawnPending, graduated);
    }

    @Test
    @DisplayName("Phụ huynh trẻ chờ xếp lớp bị cho nghỉ: không xem được trẻ, trẻ không còn ở GET /accounts/me")
    void parentOfWithdrawnPendingChildLosesAccess() {
        long child = child("LEFT", "2026-11-01", "2026-11-05", null);
        CurrentUser parent = parentOf(child);

        assertThat(childAccessPolicy.canViewChild(parent, child)).isFalse();
        assertThat(accountService.getMyAccount(parent).childIds()).doesNotContain(child);
    }

    @Test
    @DisplayName(
            "Phụ huynh trẻ mở lại hồ sơ, chờ xếp lớp: xem được trẻ như trẻ mới tiếp nhận, trẻ có ở GET /accounts/me")
    void parentOfReadmittedChildKeepsAccess() {
        long child = child("PENDING_PLACEMENT", "2026-09-01", null, "2026-11-10");
        enrollment(child, "2026-09-01", "2026-10-31");
        CurrentUser parent = parentOf(child);

        assertThat(childAccessPolicy.canViewChild(parent, child)).isTrue();
        assertThat(accountService.getMyAccount(parent).childIds()).contains(child);
    }

    // ---------- dữ liệu ----------

    private boolean leftToday(long childId) {
        return enrollmentService.findChildIdsLeftSchool(List.of(childId), TODAY).contains(childId);
    }

    private long child(String status, String enrolledOn, String leftOn, String readmittedOn) {
        long id = jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, left_on,"
                        + " left_reason, readmitted_on, intake_campus_id) VALUES (?, 'Trẻ demo', '2022-03-01', 'FEMALE',"
                        + " ?, CAST(? AS DATE), CAST(? AS DATE), ?, CAST(? AS DATE), ?) RETURNING id",
                Long.class,
                "LS" + SEQ.incrementAndGet(),
                status,
                enrolledOn,
                leftOn,
                leftOn == null ? null : "Chuyển trường",
                readmittedOn,
                campus);
        entityManager.flush();
        return id;
    }

    private void enrollment(long childId, String validFrom, String validTo) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to)"
                        + " VALUES (?, ?, CAST(? AS DATE), CAST(? AS DATE))",
                childId,
                classA1,
                validFrom,
                validTo);
    }

    private CurrentUser parentOf(long childId) {
        int n = SEQ.incrementAndGet();
        String phone = "06" + String.format("%08d", n);
        long parentId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('kc-ls" + n + "', '" + phone + "', 'Phụ huynh demo', 'PARENT') RETURNING id");
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
                parentId,
                childId);
        return new CurrentUser(
                parentId,
                "kc-ls" + n,
                phone,
                "Phụ huynh demo",
                null,
                Set.of(CareNestRole.PARENT),
                TestPermissions.defaultsOf(CareNestRole.PARENT));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
