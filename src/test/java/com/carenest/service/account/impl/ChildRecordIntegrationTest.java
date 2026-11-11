package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.account.GuardianPhoneChangeCreateRequest;
import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.account.PhoneChangeDecisionRequest;
import com.carenest.dto.child.ChildAddressInput;
import com.carenest.dto.child.ChildProfileInput;
import com.carenest.dto.child.ChildUpdateResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.child.WithdrawChildRequest;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.sms.SmsGateway;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.ChildProfileEditService;
import com.carenest.service.account.GuardianPhoneChangeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PhoneChangeStatus;
import com.carenest.utils.enums.SmsStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Cho nghỉ học, Sửa hồ sơ, duyệt đổi SĐT phụ huynh trên PostgreSQL thật (V84 + ràng buộc V63): trạng thái, lớp, lịch sử
 * JSONB, thông báo, yêu cầu đổi SĐT, tên đăng nhập. Keycloak, SMS giả lập. Hôm nay = 10/11/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildRecordIntegrationTest {
    private static final AtomicInteger ROOM_SEQ = new AtomicInteger();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final String OLD_PHONE = "0902000001";
    private static final String NEW_PHONE = "0902000099";
    private static final String CITIZEN = "001190000101";

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

    @MockitoBean
    private IdentityProviderClient identityProviderClient;

    @MockitoBean
    private SmsGateway smsGateway;

    @Autowired
    private ChildRecordService recordService;

    @Autowired
    private ChildProfileEditService editService;

    @Autowired
    private GuardianPhoneChangeService phoneChangeService;

    @Autowired
    private JdbcTemplate jdbc;

    // Test chạy trong một giao dịch không commit: flush trước khi đọc bằng JDBC
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private long teacher;
    private long vicePrincipalId;
    private long principalId;
    private long classA;
    private long parent;
    private long childA;
    private long sibling;

    @BeforeEach
    void setUp() {
        principalId = staff("ht.record", "PRINCIPAL");
        vicePrincipalId = staff("pht.record", "VICE_PRINCIPAL");
        teacher = staff("gv.record", "TEACHER");
        long campusA =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('RC-A', 'Cơ sở A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = classroom(campusA, year, "Lớp RC1", ag3);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vicePrincipalId,
                campusA);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacher,
                classA);

        // Phụ huynh có tài khoản với hai con cùng lớp; con thứ nhất thêm một phụ huynh chưa kích hoạt
        parent = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role, citizen_id) "
                        + "VALUES ('kc-parent-rc', ?, 'Trần Thị Mẹ', 'PARENT', ?) RETURNING id",
                Long.class,
                OLD_PHONE,
                CITIZEN);
        childA = child("Trần Bé An");
        enroll(childA);
        guardian(childA, 0, "Trần Thị Mẹ", "MOTHER", OLD_PHONE, CITIZEN, parent, true);
        guardian(childA, 1, "Trần Văn Bố", "FATHER", "0902000002", "001190000102", null, false);
        sibling = child("Trần Bé Bình");
        enroll(sibling);
        guardian(sibling, 0, "Trần Thị Mẹ", "MOTHER", OLD_PHONE, CITIZEN, parent, true);

        principal = user(principalId, CareNestRole.PRINCIPAL, TestPermissions.defaultsOf(CareNestRole.PRINCIPAL));
        vicePrincipal = user(
                vicePrincipalId, CareNestRole.VICE_PRINCIPAL, TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));
    }

    @Test
    @DisplayName("G94-180/CHD-03: nghỉ học ⇒ LEFT, lớp kết thúc hôm qua, lịch sử, thông báo GV lớp (CHECK V63/V84 qua)")
    void withdrawWritesStatusEnrollmentHistoryAndNotification() {
        ChildViewResponse view =
                recordService.withdraw(vicePrincipal, childA, new WithdrawChildRequest(TODAY.toString(), "Chuyển nhà"));
        entityManager.flush();

        assertThat(view.status()).isEqualTo(ChildStatus.LEFT);
        assertThat(view.leftAt()).isEqualTo(TODAY);
        assertThat(jdbc.queryForObject("SELECT valid_to FROM enrollments WHERE child_id = ?", LocalDate.class, childA))
                .isEqualTo(TODAY.minusDays(1));
        assertThat(count("SELECT count(*) FROM child_history WHERE child_id = ? AND action = 'WITHDRAWN'", childA))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'CHILD_WITHDRAWN' AND child_id = ?",
                        teacher,
                        childA))
                .isEqualTo(1);
        assertThatThrownBy(() ->
                        recordService.withdraw(principal, childA, new WithdrawChildRequest(TODAY.toString(), "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ALREADY_LEFT));
    }

    @Test
    @DisplayName("G94-180/AUTH-21 (sửa 11/11): Sửa hồ sơ không đổi SĐT phụ huynh đã lưu, có hay chưa có tài khoản")
    void editRejectsPhoneChangeOfSavedGuardian() {
        expectField(
                () -> editService.update(principal, childA, edit("Trần Bé An", NEW_PHONE, "0902000002")),
                ApiCode.INVALID_REQUEST_DATA,
                "guardians.0.phone");
        expectField(
                () -> editService.update(principal, childA, edit("Trần Bé An", OLD_PHONE, "0902000003")),
                ApiCode.INVALID_REQUEST_DATA,
                "guardians.1.phone");

        ChildUpdateResponse updated =
                editService.update(principal, childA, edit("Trần Bé An Nhiên", OLD_PHONE, "0902000002"));
        entityManager.flush();

        assertThat(updated.child().fullName()).isEqualTo("Trần Bé An Nhiên");
        assertThat(updated.phoneChangeRequests()).isEmpty();
        assertThat(phones(childA)).containsExactly(OLD_PHONE, "0902000002");
        assertThat(count("SELECT count(*) FROM guardian_phone_change_requests WHERE child_id = ?", childA))
                .isZero();
    }

    @Test
    @DisplayName("G94-180/AUTH-21: HT gửi yêu cầu cho phụ huynh có tài khoản rồi tự duyệt ⇒ tên đăng nhập, SĐT mọi hồ"
            + " sơ đổi, SMS số mới; báo PHT khi gửi, không tự báo mình khi duyệt")
    void principalRequestsAndApprovesOwnRequest() {
        GuardianPhoneChangeResponse requested = phoneChangeService.request(
                principal, childA, guardianId(childA, 0), new GuardianPhoneChangeCreateRequest(NEW_PHONE, "Mẹ đổi số"));
        entityManager.flush();

        assertThat(requested.status()).isEqualTo(PhoneChangeStatus.PENDING);
        assertThat(requested.userId()).isEqualTo(parent);
        assertThat(requested.oldPhone()).isEqualTo(OLD_PHONE);
        assertThat(requested.newPhone()).isEqualTo(NEW_PHONE);
        assertThat(requested.canDecide()).isTrue();
        assertThat(phones(childA).getFirst()).isEqualTo(OLD_PHONE);
        assertThat(notifications(vicePrincipalId, "GUARDIAN_PHONE_CHANGE_REQUESTED"))
                .isEqualTo(1);
        assertThat(notifications(principalId, "GUARDIAN_PHONE_CHANGE_REQUESTED"))
                .isZero();

        GuardianPhoneChangeResponse approved = phoneChangeService.approve(principal, requested.id());
        entityManager.flush();

        assertThat(approved.status()).isEqualTo(PhoneChangeStatus.APPROVED);
        assertThat(approved.smsStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(jdbc.queryForObject("SELECT username FROM user_accounts WHERE id = ?", String.class, parent))
                .isEqualTo(NEW_PHONE);
        assertThat(phones(childA).getFirst()).isEqualTo(NEW_PHONE);
        assertThat(phones(sibling)).containsExactly(NEW_PHONE);
        verify(identityProviderClient).updateUsername("kc-parent-rc", NEW_PHONE);
        verify(identityProviderClient).resetTemporaryPassword(eq("kc-parent-rc"), anyString());
        verify(smsGateway).send(eq(NEW_PHONE), anyString());
        assertThat(notifications(principalId, "GUARDIAN_PHONE_CHANGE_DECIDED")).isZero();
    }

    @Test
    @DisplayName("G94-180/AUTH-21: phụ huynh chưa có tài khoản cũng qua duyệt; duyệt ⇒ chỉ đổi SĐT dòng đó, không SMS")
    void requestForGuardianWithoutAccountChangesOnlyThatRecord() {
        GuardianPhoneChangeResponse requested = phoneChangeService.request(
                vicePrincipal,
                childA,
                guardianId(childA, 1),
                new GuardianPhoneChangeCreateRequest("0902 000 003", "Bố đổi số"));
        entityManager.flush();

        assertThat(requested.userId()).isNull();
        assertThat(requested.guardianName()).isEqualTo("Trần Văn Bố");
        assertThat(requested.oldPhone()).isEqualTo("0902000002");
        assertThat(requested.newPhone()).isEqualTo("0902000003");
        assertThat(phones(childA)).containsExactly(OLD_PHONE, "0902000002");

        GuardianPhoneChangeResponse approved = phoneChangeService.approve(principal, requested.id());
        entityManager.flush();

        assertThat(approved.status()).isEqualTo(PhoneChangeStatus.APPROVED);
        assertThat(approved.smsStatus()).isNull();
        assertThat(phones(childA)).containsExactly(OLD_PHONE, "0902000003");
        verify(identityProviderClient, never()).updateUsername(anyString(), anyString());
        verify(smsGateway, never()).send(anyString(), anyString());
        assertThat(notifications(vicePrincipalId, "GUARDIAN_PHONE_CHANGE_DECIDED"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("G94-180/AUTH-21: gửi lại sang số khác ⇒ yêu cầu cũ CANCELLED; từ chối ⇒ giữ số cũ")
    void newRequestCancelsOldAndRejectKeepsPhone() {
        long mother = guardianId(childA, 0);
        Long first = phoneChangeService
                .request(principal, childA, mother, new GuardianPhoneChangeCreateRequest(NEW_PHONE, "Đổi số"))
                .id();
        Long second = phoneChangeService
                .request(principal, childA, mother, new GuardianPhoneChangeCreateRequest("0902000098", "Gõ nhầm số"))
                .id();
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM guardian_phone_change_requests WHERE id = ?", String.class, first))
                .isEqualTo("CANCELLED");
        phoneChangeService.reject(vicePrincipal, second, new PhoneChangeDecisionRequest("Chưa xác minh số"));
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM guardian_phone_change_requests WHERE id = ?", String.class, second))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT username FROM user_accounts WHERE id = ?", String.class, parent))
                .isEqualTo(OLD_PHONE);
    }

    @Test
    @DisplayName("G94-180/AUTH-21: kiểm yêu cầu — trùng số đang lưu, thiếu lý do, số của phụ huynh khác, phụ huynh lạ")
    void requestValidation() {
        long father = guardianId(childA, 1);
        expectField(
                () -> phoneChangeService.request(
                        principal, childA, father, new GuardianPhoneChangeCreateRequest("0902000002", "Đổi")),
                ApiCode.INVALID_REQUEST_DATA,
                "newPhone");
        expectField(
                () -> phoneChangeService.request(
                        principal, childA, father, new GuardianPhoneChangeCreateRequest("0902000003", "  ")),
                ApiCode.INVALID_REQUEST_DATA,
                "reason");
        expectField(
                () -> phoneChangeService.request(
                        principal, childA, father, new GuardianPhoneChangeCreateRequest(OLD_PHONE, "Đổi")),
                ApiCode.GUARDIAN_IDENTITY_CONFLICT,
                "newPhone");
        assertThatThrownBy(() -> phoneChangeService.request(
                        principal,
                        childA,
                        guardianId(sibling, 0),
                        new GuardianPhoneChangeCreateRequest("0902000003", "Đổi")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_GUARDIAN_NOT_FOUND));
        assertThat(count("SELECT count(*) FROM guardian_phone_change_requests WHERE child_id = ?", childA))
                .isZero();
    }

    @Test
    @DisplayName("G94-180/AUTH-21: bỏ phụ huynh đang có yêu cầu ở Sửa hồ sơ được; duyệt sau đó ⇒ 409 stale")
    void removingGuardianLeavesStaleRequest() {
        Long requestId = phoneChangeService
                .request(
                        principal,
                        childA,
                        guardianId(childA, 1),
                        new GuardianPhoneChangeCreateRequest("0902000003", "Bố đổi số"))
                .id();
        entityManager.flush();

        editService.update(principal, childA, onlyMother());
        entityManager.flush();

        assertThat(phones(childA)).containsExactly(OLD_PHONE);
        assertThatThrownBy(() -> phoneChangeService.approve(principal, requestId))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.PHONE_CHANGE_REQUEST_STALE));
    }

    @Test
    @DisplayName("G94-180/AUTH-21: đổi họ tên phụ huynh có tài khoản ⇒ tài khoản + hồ sơ anh/chị em đổi theo")
    void nameChangeSyncsAccountAndSiblings() {
        UpdateChildRequest request = new UpdateChildRequest(
                new EnrollmentChildInput(
                        "Trần Bé An",
                        "2022-03-01",
                        "MALE",
                        List.of(
                                new GuardianInput("Trần Thị Mẹ Mới", "MOTHER", OLD_PHONE, CITIZEN, null, null, null),
                                new GuardianInput(
                                        "Trần Văn Bố", "FATHER", "0902000002", "001190000102", null, null, null)),
                        profile()),
                "Sửa họ tên mẹ");

        ChildUpdateResponse updated = editService.update(principal, childA, request);
        entityManager.flush();

        assertThat(updated.accountChanges())
                .singleElement()
                .satisfies(c -> assertThat(c.fullName()).isEqualTo("Trần Thị Mẹ Mới"));
        assertThat(jdbc.queryForObject("SELECT full_name FROM user_accounts WHERE id = ?", String.class, parent))
                .isEqualTo("Trần Thị Mẹ Mới");
        assertThat(jdbc.queryForObject(
                        "SELECT full_name FROM child_guardians WHERE child_id = ? AND position = 0",
                        String.class,
                        sibling))
                .isEqualTo("Trần Thị Mẹ Mới");
        verify(identityProviderClient).replaceProfile("kc-parent-rc", "Trần Thị Mẹ Mới", null);
    }

    // ---------- dữ liệu ----------

    private static UpdateChildRequest edit(String childName, String motherPhone, String fatherPhone) {
        return new UpdateChildRequest(
                new EnrollmentChildInput(
                        childName,
                        "2022-03-01",
                        "MALE",
                        List.of(
                                new GuardianInput("Trần Thị Mẹ", "MOTHER", motherPhone, CITIZEN, null, null, null),
                                new GuardianInput(
                                        "Trần Văn Bố", "FATHER", fatherPhone, "001190000102", null, null, null)),
                        profile()),
                "Cập nhật hồ sơ");
    }

    private static UpdateChildRequest onlyMother() {
        return new UpdateChildRequest(
                new EnrollmentChildInput(
                        "Trần Bé An",
                        "2022-03-01",
                        "MALE",
                        List.of(new GuardianInput("Trần Thị Mẹ", "MOTHER", OLD_PHONE, CITIZEN, null, null, null)),
                        profile()),
                "Bỏ phụ huynh");
    }

    private long guardianId(long childId, int position) {
        return jdbc.queryForObject(
                "SELECT id FROM child_guardians WHERE child_id = ? AND position = ?", Long.class, childId, position);
    }

    private long notifications(long userId, String type) {
        entityManager.flush();
        return count("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?", userId, type);
    }

    private static void expectField(Runnable call, ApiCode code, String field) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GlobalException.class, ex -> {
            assertThat(ex.getApiCode()).isEqualTo(code);
            assertThat(ex.getFieldErrors()).extracting(e -> e.field()).contains(field);
        });
    }

    private static ChildProfileInput profile() {
        return new ChildProfileInput(
                null, "NO", 39, "Kinh", "Không", null, new ChildAddressInput("Hà Nội", "Cầu Giấy", "1 Láng"));
    }

    private List<String> phones(long childId) {
        return jdbc.queryForList(
                "SELECT phone FROM child_guardians WHERE child_id = ? ORDER BY position", String.class, childId);
    }

    private long staff(String username, String role) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role) VALUES ('kc-"
                + username + "', '" + username + "', '" + username + "', '" + role + "') RETURNING id");
    }

    private long child(String fullName) {
        long id = jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) "
                        + "VALUES ('RC' || substr(md5(random()::text), 1, 12), ?, '2022-03-01', 'MALE', 'ACTIVE',"
                        + " '2026-09-01') RETURNING id",
                Long.class,
                fullName);
        jdbc.update(
                "INSERT INTO child_profiles (child_id, premature, gestation_weeks, ethnicity, beneficiary, payment_source,"
                        + " address_province, address_ward, address_street)"
                        + " VALUES (?, 'NO', 39, 'Kinh', 'Không', 'BHYT', 'Hà Nội', 'Cầu Giấy', '1 Láng')",
                id);
        return id;
    }

    private void enroll(long childId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classA);
    }

    private void guardian(
            long childId,
            int position,
            String fullName,
            String relationship,
            String phone,
            String citizenId,
            Long userId,
            boolean recipient) {
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id,"
                        + " account_recipient) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                childId,
                position,
                fullName,
                relationship,
                phone,
                citizenId,
                userId,
                recipient);
        if (userId != null) {
            jdbc.update(
                    "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, ?)",
                    userId,
                    childId,
                    relationship);
        }
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId) {
        long room = insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campusId
                + ", 'RC" + ROOM_SEQ.incrementAndGet() + "', 'Phòng " + name + "', 'CLASS', 30) RETURNING id");
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) "
                + "VALUES (" + campusId + ", " + yearId + ", '" + name + "', " + ageGroupId + ", " + room + ", 25)"
                + " RETURNING id");
    }

    private static CurrentUser user(long id, CareNestRole role, Set<Permission> permissions) {
        return new CurrentUser(id, "kc-" + id, "user" + id, "User " + id, null, Set.of(role), permissions);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
