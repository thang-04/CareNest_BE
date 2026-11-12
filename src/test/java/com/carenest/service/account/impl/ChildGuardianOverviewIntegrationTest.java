package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.child.ChildGuardianOverviewResponse;
import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.GuardianAccountService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Màn Tài khoản phụ huynh đọc một lần cho cả trang ({@code GET /children/guardian-overview}): mỗi trẻ kèm tài khoản của
 * phụ huynh nhận tài khoản (như {@code GET /children/{id}/guardians}), số truy vấn không tăng theo số trẻ. Dữ liệu giả.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildGuardianOverviewIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private GuardianAccountService guardianService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private CurrentUser principal;
    private CurrentUser vicePrincipalA;
    private long campusA;
    private long campusB;
    private long classA;
    private long classB;
    private long activated;
    private long notActivated;
    private long noGuardian;
    private long otherCampus;
    private long parentId;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        long principalId = staff("ht.go" + n, "PRINCIPAL");
        long vpAId = staff("pht.go" + n, "VICE_PRINCIPAL");
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('GOA" + n + "', 'Cơ sở A', TRUE)"
                + " RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('GOB" + n + "', 'Cơ sở B') RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = classroom(campusA, year, "Chồi A");
        classB = classroom(campusB, year, "Chồi B");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2025-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2025-01-01')",
                vpAId,
                campusA);

        activated = enrolledChild("Nguyễn An", classA);
        parentId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('kc-ph" + n + "', '0900" + String.format("%06d", n) + "', 'Phụ huynh An', 'PARENT')"
                + " RETURNING id");
        guardian(activated, 0, "Phụ huynh An", "0900" + String.format("%06d", n), parentId, true);
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
                parentId,
                activated);

        notActivated = enrolledChild("Trần Bình", classA);
        guardian(notActivated, 0, "Phụ huynh Bình", "0911" + String.format("%06d", n), null, true);

        noGuardian = enrolledChild("Lê Cường", classA);
        otherCampus = enrolledChild("Phạm Dung", classB);

        principal = user(principalId, CareNestRole.PRINCIPAL);
        vicePrincipalA = user(vpAId, CareNestRole.VICE_PRINCIPAL);
        entityManager.flush();
    }

    @Test
    @DisplayName("Trẻ đã kích hoạt kèm tài khoản người nhận; chưa kích hoạt, chưa có phụ huynh ⇒ account null")
    void returnsRecipientAccountPerChild() {
        PageResponse<ChildGuardianOverviewResponse> page = guardianService.guardianOverview(principal, filter(null));

        assertThat(page.items())
                .extracting(item -> item.child().id())
                .containsExactlyInAnyOrder(activated, notActivated, noGuardian, otherCampus);
        ChildGuardianOverviewResponse an = item(page, activated);
        assertThat(an.account()).isNotNull();
        assertThat(an.account().userId()).isEqualTo(parentId);
        assertThat(an.account().fullName()).isEqualTo("Phụ huynh An");
        assertThat(an.account().relationship()).isEqualTo(GuardianRelationship.MOTHER);
        assertThat(an.account().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(an.child().guardians())
                .singleElement()
                .satisfies(g -> assertThat(g.accountRecipient()).isTrue());
        assertThat(item(page, notActivated).account()).isNull();
        assertThat(item(page, notActivated).child().guardians()).hasSize(1);
        assertThat(item(page, noGuardian).account()).isNull();
        assertThat(item(page, noGuardian).child().guardians()).isEmpty();
    }

    @Test
    @DisplayName("PHT chỉ thấy trẻ điểm trường được phân công, kèm tài khoản phụ huynh")
    void vicePrincipalSeesOwnCampusOnly() {
        PageResponse<ChildGuardianOverviewResponse> page =
                guardianService.guardianOverview(vicePrincipalA, filter(null));

        assertThat(page.items())
                .extracting(item -> item.child().id())
                .containsExactlyInAnyOrder(activated, notActivated, noGuardian);
        assertThat(item(page, activated).account().userId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("Số truy vấn của cả trang không tăng theo số trẻ")
    void queryCountDoesNotGrowWithChildren() {
        activatedChild(classB);
        long oneActivated = statementsFor(campusB);
        for (int i = 0; i < 5; i++) {
            activatedChild(classB);
        }

        assertThat(statementsFor(campusB)).isEqualTo(oneActivated);
    }

    // ---------- dữ liệu ----------

    private void activatedChild(long classroomId) {
        long child = enrolledChild("Trẻ thêm", classroomId);
        long account = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('kc-x" + SEQ.incrementAndGet() + "', '09" + String.format("%08d", SEQ.get())
                + "', 'Phụ huynh thêm', 'PARENT') RETURNING id");
        guardian(child, 0, "Phụ huynh thêm", "08" + String.format("%08d", SEQ.incrementAndGet()), account, true);
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'FATHER')",
                account,
                child);
        entityManager.flush();
    }

    private long statementsFor(long campusId) {
        entityManager.clear();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        guardianService.guardianOverview(principal, filter(campusId));
        return stats.getPrepareStatementCount();
    }

    private static ChildListFilter filter(Long campusId) {
        return new ChildListFilter(null, campusId, null, null, null, 0, 100);
    }

    private static ChildGuardianOverviewResponse item(PageResponse<ChildGuardianOverviewResponse> page, long childId) {
        return page.items().stream()
                .filter(item -> item.child().id() == childId)
                .findFirst()
                .orElseThrow();
    }

    private long enrolledChild(String name, long classroomId) {
        long id = jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on)"
                        + " VALUES (?, ?, '2022-03-01', 'FEMALE', 'ACTIVE', '2026-09-01') RETURNING id",
                Long.class,
                "GO" + SEQ.incrementAndGet(),
                name);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                id,
                classroomId);
        return id;
    }

    private void guardian(
            long childId, int position, String name, String phone, Long userId, boolean accountRecipient) {
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id,"
                        + " account_recipient) VALUES (?, ?, ?, 'MOTHER', ?, ?, ?, ?)",
                childId,
                position,
                name,
                phone,
                "999" + String.format("%09d", SEQ.incrementAndGet()),
                userId,
                accountRecipient);
    }

    private long classroom(long campusId, long yearId, String name) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusId + ", " + yearId
                + ", '" + name + "') RETURNING id");
    }

    private long staff(String username, String role) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role) VALUES ('kc-"
                + username + "', '" + username + "', '" + username + "', '" + role + "') RETURNING id");
    }

    private static CurrentUser user(long id, CareNestRole role) {
        return new CurrentUser(
                id, "kc-" + id, "user" + id, "User " + id, null, Set.of(role), TestPermissions.defaultsOf(role));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
