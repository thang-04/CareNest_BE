package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.child.ParentAccountRowResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildListService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianAccountStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
 * Bảng tổng quan PHT ({@code GET /children/parent-accounts}, đặc tả FE mục 4.8): mỗi phụ huynh trên hồ sơ trẻ chưa nghỉ
 * trong phạm vi quản lý tài khoản phụ huynh, kèm trạng thái và tên đăng nhập; số truy vấn không tăng theo số trẻ. Dữ liệu
 * giả.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ParentAccountListIntegrationTest {
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
    private ChildListService childListService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private CurrentUser principal;
    private CurrentUser vicePrincipalA;
    private long campusA;
    private long classA;
    private long classB;
    private long activated;
    private long notActivated;
    private long otherCampus;
    private String parentPhone;
    private String fatherPhone;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        long principalId = staff("ht.pa" + n, "PRINCIPAL");
        long vpAId = staff("pht.pa" + n, "VICE_PRINCIPAL");
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('PAA" + n + "', 'Cơ sở A', TRUE)"
                + " RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name) VALUES ('PAB" + n + "', 'Cơ sở B') RETURNING id");
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
        parentPhone = phone();
        long parentId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('kc-pa" + n + "', '" + parentPhone + "', 'Phụ huynh An', 'PARENT') RETURNING id");
        guardian(activated, 0, "Phụ huynh An", parentPhone, parentId, true);
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
                parentId,
                activated);
        fatherPhone = phone();
        guardian(activated, 1, "Bố An", fatherPhone, null, false);

        notActivated = enrolledChild("Trần Bình", classA);
        guardian(notActivated, 0, "Phụ huynh Bình", phone(), null, true);

        long left = leftChild("Lê Cường", classA);
        guardian(left, 0, "Phụ huynh Cường", phone(), null, true);

        otherCampus = enrolledChild("Phạm Dung", classB);
        guardian(otherCampus, 0, "Mẹ Dung", phone(), null, true);

        principal = user(principalId, CareNestRole.PRINCIPAL);
        vicePrincipalA = user(vpAId, CareNestRole.VICE_PRINCIPAL);
        entityManager.flush();
    }

    @Test
    @DisplayName("HT: mọi phụ huynh trên hồ sơ trẻ chưa nghỉ, kèm trạng thái tài khoản và tên đăng nhập")
    void principalListsEveryGuardianOfChildrenStillStudying() {
        List<ParentAccountRowResponse> rows = childListService.listParentAccounts(principal, null, null);

        assertThat(rows)
                .extracting(ParentAccountRowResponse::childId, ParentAccountRowResponse::guardianIndex)
                .containsExactlyInAnyOrder(
                        tuple(activated, 0), tuple(activated, 1), tuple(notActivated, 0), tuple(otherCampus, 0));
        ParentAccountRowResponse mother = row(rows, activated, 0);
        assertThat(mother.childName()).isEqualTo("Nguyễn An");
        assertThat(mother.childCode()).startsWith("PA");
        assertThat(mother.campusId()).isEqualTo(campusA);
        assertThat(mother.className()).isEqualTo("Chồi A");
        assertThat(mother.guardian().fullName()).isEqualTo("Phụ huynh An");
        assertThat(mother.guardian().accountStatus()).isEqualTo(GuardianAccountStatus.ACTIVE);
        assertThat(mother.guardian().idNumber()).isNull();
        assertThat(mother.username()).isEqualTo(parentPhone);
        ParentAccountRowResponse father = row(rows, activated, 1);
        assertThat(father.guardian().accountStatus()).isEqualTo(GuardianAccountStatus.NOT_ACTIVATED);
        assertThat(father.username()).isNull();
    }

    @Test
    @DisplayName("Lọc trạng thái: chỉ phụ huynh chưa kích hoạt (ô của bảng tổng quan PHT)")
    void statusFilterKeepsMatchingAccountsOnly() {
        List<ParentAccountRowResponse> rows =
                childListService.listParentAccounts(principal, GuardianAccountStatus.NOT_ACTIVATED, null);

        assertThat(rows)
                .extracting(ParentAccountRowResponse::childId, ParentAccountRowResponse::guardianIndex)
                .containsExactlyInAnyOrder(tuple(activated, 1), tuple(notActivated, 0), tuple(otherCampus, 0));
    }

    @Test
    @DisplayName("PHT chỉ thấy phụ huynh của trẻ điểm trường được phân công")
    void vicePrincipalSeesOwnCampusOnly() {
        List<ParentAccountRowResponse> rows = childListService.listParentAccounts(vicePrincipalA, null, null);

        assertThat(rows)
                .extracting(ParentAccountRowResponse::childId)
                .containsOnly(activated, notActivated)
                .hasSize(3);
    }

    @Test
    @DisplayName("Từ khóa khớp họ tên trẻ/phụ huynh, SĐT; không phân biệt hoa thường, dấu")
    void keywordIgnoresCaseAndAccents() {
        assertThat(childListService.listParentAccounts(principal, null, "  PHU HUYNH binh "))
                .extracting(ParentAccountRowResponse::childId)
                .containsExactly(notActivated);
        assertThat(childListService.listParentAccounts(principal, null, fatherPhone))
                .extracting(ParentAccountRowResponse::childId, ParentAccountRowResponse::guardianIndex)
                .containsExactly(tuple(activated, 1));
        assertThat(childListService.listParentAccounts(principal, null, "nguyen an"))
                .extracting(ParentAccountRowResponse::guardianIndex)
                .containsExactlyInAnyOrder(0, 1);
    }

    @Test
    @DisplayName("Số truy vấn không tăng theo số trẻ")
    void queryCountDoesNotGrowWithChildren() {
        activatedChild(classB);
        long oneMore = statements();
        for (int i = 0; i < 5; i++) {
            activatedChild(classB);
        }

        assertThat(statements()).isEqualTo(oneMore);
    }

    // ---------- dữ liệu ----------

    private static ParentAccountRowResponse row(List<ParentAccountRowResponse> rows, long childId, int index) {
        return rows.stream()
                .filter(r -> r.childId() == childId && r.guardianIndex() == index)
                .findFirst()
                .orElseThrow();
    }

    private long statements() {
        entityManager.clear();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        childListService.listParentAccounts(principal, null, null);
        return stats.getPrepareStatementCount();
    }

    private void activatedChild(long classroomId) {
        long child = enrolledChild("Trẻ thêm", classroomId);
        String username = phone();
        long account = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)"
                + " VALUES ('kc-pax" + SEQ.incrementAndGet() + "', '" + username + "', 'Phụ huynh thêm', 'PARENT')"
                + " RETURNING id");
        guardian(child, 0, "Phụ huynh thêm", username, account, true);
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'FATHER')",
                account,
                child);
        entityManager.flush();
    }

    private long enrolledChild(String name, long classroomId) {
        long id = jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on)"
                        + " VALUES (?, ?, '2022-03-01', 'FEMALE', 'ACTIVE', '2026-09-01') RETURNING id",
                Long.class,
                "PA" + SEQ.incrementAndGet(),
                name);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                id,
                classroomId);
        return id;
    }

    private long leftChild(String name, long classroomId) {
        long id = jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, left_on, left_reason)"
                        + " VALUES (?, ?, '2022-03-01', 'FEMALE', 'LEFT', '2026-09-01', '2026-10-01', 'Chuyển trường')"
                        + " RETURNING id",
                Long.class,
                "PA" + SEQ.incrementAndGet(),
                name);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to)"
                        + " VALUES (?, ?, '2026-09-01', '2026-09-30')",
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
                "998" + String.format("%09d", SEQ.incrementAndGet()),
                userId,
                accountRecipient);
    }

    private static String phone() {
        return "07" + String.format("%08d", SEQ.incrementAndGet());
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
