package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.AllergiesInput;
import com.carenest.dto.child.ChildAddressInput;
import com.carenest.dto.child.ChildEnrollmentResponse;
import com.carenest.dto.child.ChildProfileInput;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.DeclarationAnswerInput;
import com.carenest.dto.child.DuplicateCheckResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.HealthDeclarationInput;
import com.carenest.dto.child.MedicalHistoryInput;
import com.carenest.dto.child.ParentAccountLookupResponse;
import com.carenest.dto.child.PlacementClassResponse;
import com.carenest.dto.child.ReadmitChildRequest;
import com.carenest.dto.child.TbContactInput;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.integration.sms.SmsDeliveryException;
import com.carenest.integration.sms.SmsGateway;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.ChildEnrollmentService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildHistoryAction;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.DuplicateChildKind;
import com.carenest.utils.enums.GuardianAccountResult;
import com.carenest.utils.enums.GuardianAccountStatus;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SmsStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Tiếp nhận trẻ trên PostgreSQL thật (V10 + school-structure): kiểm trùng, lớp tiếp nhận, tra tài khoản theo CCCD,
 * lưu form kèm tạo/liên kết tài khoản (Keycloak, SMS giả lập), mở lại hồ sơ. Hôm nay = 29/10/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildEnrollmentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-29T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @MockitoBean
    private IdentityProviderClient identityProviderClient;

    @MockitoBean
    private SmsGateway smsGateway;

    @Autowired
    private ChildEnrollmentService enrollmentService;

    @Autowired
    private ChildIntakeService intakeService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private CurrentUser vicePrincipalOfB;
    private long campusA;
    private long campusB;
    private long campusClosed;
    private long classA;
    private long classB;
    private long plannedClass;
    private long teacher;
    private long existingParent;
    private long legacyParent;
    private long siblingA;

    @BeforeEach
    void setUp() {
        long principalId = staff("ht.enroll", "PRINCIPAL");
        long vpId = staff("pht.a", "VICE_PRINCIPAL");
        long vpBId = staff("pht.b", "VICE_PRINCIPAL");
        teacher = staff("gv.a", "TEACHER");
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('EN-A', 'Cơ sở A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('EN-B', 'Cơ sở B') RETURNING id");
        campusClosed = insertId(
                "INSERT INTO campuses (code, name, status) VALUES ('EN-C', 'Cơ sở C', 'INACTIVE') RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long activeYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long plannedYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-05', '2028-05-31') RETURNING id");
        classA = classroom(campusA, activeYear, "Lớp A1", ag3, 2);
        classB = classroom(campusB, activeYear, "Lớp B1", ag3, 25);
        plannedClass = classroom(campusA, plannedYear, "Lớp A năm sau", ag3, 25);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01'), (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vpId,
                campusA,
                vpBId,
                campusB);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacher,
                classA);

        // Phụ huynh đã có tài khoản (CCCD) với một trẻ đang học lớp A1 và một trẻ ở cơ sở B
        existingParent = parent("0901000001", "Lê Thị Có Sẵn", "co.san@example.test", "001190000001");
        siblingA = child("Lê Anh Cả", "2021-05-01", "ACTIVE", null);
        enroll(siblingA, classA);
        guardian(siblingA, "Lê Thị Có Sẵn", "MOTHER", "0901000001", "001190000001", existingParent);
        long siblingB = child("Lê Anh Hai", "2022-05-01", "ACTIVE", null);
        enroll(siblingB, classB);
        guardian(siblingB, "Lê Thị Có Sẵn", "MOTHER", "0901000001", "001190000001", existingParent);
        // Tài khoản cũ chưa có CCCD (khớp theo SĐT)
        legacyParent = parent("0901000002", "Phạm Văn Cũ", null, null);

        principal = user(principalId, CareNestRole.PRINCIPAL, TestPermissions.defaultsOf(CareNestRole.PRINCIPAL));
        vicePrincipal =
                user(vpId, CareNestRole.VICE_PRINCIPAL, TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));
        vicePrincipalOfB =
                user(vpBId, CareNestRole.VICE_PRINCIPAL, TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));

        given(identityProviderClient.createUser(any())).willReturn("kc-new-1", "kc-new-2", "kc-new-3");
    }

    @Test
    @DisplayName(
            "CHD-02, AUTH-20: HT tiếp nhận không chọn lớp ⇒ Chờ xếp lớp ở điểm trường tiếp nhận, tạo tài khoản, SMS")
    void principalEnrollsPendingChildAndCreatesAccount() {
        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                request("Nguyễn Minh An", "2023-03-01", null, campusA, newGuardian("0912345678", "001234567890")));

        ChildViewResponse child = response.child();
        assertThat(child.status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(child.campusId()).isEqualTo(campusA);
        assertThat(child.classId()).isNull();
        assertThat(child.code()).startsWith("HS26");
        assertThat(child.enrolledAt()).isEqualTo(TODAY);
        assertThat(child.hasDeclaration()).isTrue();
        assertThat(child.profile().paymentSource()).isEqualTo("BHYT");
        assertThat(child.history()).singleElement().satisfies(h -> {
            assertThat(h.action()).isEqualTo(ChildHistoryAction.ENROLLED);
            assertThat(h.userFullName()).isEqualTo("ht.enroll");
        });
        assertThat(response.guardianAccounts()).singleElement().satisfies(g -> {
            assertThat(g.result()).isEqualTo(GuardianAccountResult.CREATED);
            assertThat(g.smsStatus()).isEqualTo(SmsStatus.SENT);
            assertThat(g.phone()).isEqualTo("0912345678");
        });
        assertThat(child.guardians()).singleElement().satisfies(g -> {
            assertThat(g.accountStatus()).isEqualTo(GuardianAccountStatus.ACTIVE);
            assertThat(g.smsStatus()).isEqualTo(SmsStatus.SENT);
            assertThat(g.idNumber()).isEqualTo("001234567890");
        });

        Map<String, Object> account = jdbc.queryForMap("SELECT * FROM user_accounts WHERE username = '0912345678'");
        assertThat(account).containsEntry("citizen_id", "001234567890").containsEntry("primary_role", "PARENT");
        long accountId = ((Number) account.get("id")).longValue();
        assertThat(count(
                        "SELECT count(*) FROM guardian_child_links WHERE user_id = ? AND child_id = ?",
                        accountId,
                        child.id()))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM parent_sms_logs WHERE user_id = ? AND purpose = 'ACTIVATION' AND status = 'SENT'",
                        accountId))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM enrollments WHERE child_id = ?", child.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT intake_campus_id FROM children WHERE id = ?", Long.class, child.id()))
                .isEqualTo(campusA);
        verify(identityProviderClient)
                .createUser(argThat(
                        u -> u.username().equals("0912345678") && u.email() == null && u.temporaryPassword() != null));
        verify(identityProviderClient).assignRealmRole("kc-new-1", "PARENT");
        verify(smsGateway).send(eq("0912345678"), argThat(text -> text.contains("0912345678")));
    }

    @Test
    @DisplayName(
            "CHD-10: tiếp nhận vào lớp năm đang hoạt động ⇒ Đang học; lớp đủ sĩ số, lớp năm khác, lệch điểm trường ⇒ 400 classId")
    void enrollIntoClassChecksClass() {
        ChildEnrollmentResponse placed = enrollmentService.enroll(
                principal,
                request("Trẻ Có Lớp", "2023-04-01", classA, null, newGuardian("0912000001", "001200000001")));
        assertThat(placed.child().status()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(placed.child().classId()).isEqualTo(classA);
        assertThat(placed.child().className()).isEqualTo("Lớp A1");
        assertThat(placed.child().campusId()).isEqualTo(campusA);
        assertThat(jdbc.queryForObject(
                        "SELECT created_by FROM enrollments WHERE child_id = ? AND valid_from = ? AND valid_to IS NULL",
                        Long.class,
                        placed.child().id(),
                        TODAY))
                .isEqualTo(principal.userId());

        // Lớp A1 sĩ số 2: anh cả + trẻ vừa tiếp nhận
        expectField(
                () -> enrollmentService.enroll(
                        principal,
                        request("Trẻ Thừa", "2023-04-02", classA, null, newGuardian("0912000002", "001200000002"))),
                ApiCode.INVALID_REQUEST_DATA,
                "classId",
                "Lớp A1 đã đủ 2 trẻ. Chọn lớp khác.");
        expectField(
                () -> enrollmentService.enroll(
                        principal,
                        request(
                                "Trẻ Năm Sau",
                                "2023-04-03",
                                plannedClass,
                                null,
                                newGuardian("0912000003", "001200000003"))),
                ApiCode.INVALID_REQUEST_DATA,
                "classId",
                "Chỉ xếp vào lớp của năm học đang hoạt động. Xếp lớp cho năm học sau ở màn Xếp lớp năm học mới.");
        expectField(
                () -> enrollmentService.enroll(
                        principal,
                        request("Trẻ Lệch", "2023-04-04", classB, campusA, newGuardian("0912000004", "001200000004"))),
                ApiCode.INVALID_REQUEST_DATA,
                "classId",
                "Lớp không thuộc điểm trường đã chọn");
    }

    @Test
    @DisplayName(
            "CHD-02, AUTH-02: PHT một điểm trường bỏ trống ⇒ điểm trường của mình; điểm trường khác / ngừng / không chọn ⇒ 400 campusId")
    void intakeCampusRules() {
        ChildEnrollmentResponse own = enrollmentService.enroll(
                vicePrincipal, request("Trẻ PHT", "2023-05-01", null, null, newGuardian("0913000001", "001300000001")));
        assertThat(own.child().campusId()).isEqualTo(campusA);

        expectField(
                () -> enrollmentService.enroll(
                        vicePrincipal,
                        request("Trẻ Cơ Sở B", "2023-05-02", null, campusB, newGuardian("0913000002", "001300000002"))),
                ApiCode.INVALID_REQUEST_DATA,
                "campusId",
                "Chỉ tiếp nhận trẻ vào điểm trường bạn được giao");
        expectField(
                () -> enrollmentService.enroll(
                        principal,
                        request("Trẻ Không Chọn", "2023-05-03", null, null, newGuardian("0913000003", "001300000003"))),
                ApiCode.INVALID_REQUEST_DATA,
                "campusId",
                "Chọn điểm trường tiếp nhận trẻ");
        expectField(
                () -> enrollmentService.enroll(
                        principal,
                        request(
                                "Trẻ Cơ Sở Ngừng",
                                "2023-05-04",
                                null,
                                campusClosed,
                                newGuardian("0913000004", "001300000004"))),
                ApiCode.INVALID_REQUEST_DATA,
                "campusId",
                "Điểm trường không tồn tại hoặc đã ngừng hoạt động");
        expectField(
                () -> enrollmentService.enroll(
                        vicePrincipal,
                        request("Trẻ Lớp B", "2023-05-05", classB, null, newGuardian("0913000005", "001300000005"))),
                ApiCode.INVALID_REQUEST_DATA,
                "classId",
                "Chỉ xếp được vào lớp thuộc điểm trường bạn được giao");
    }

    @Test
    @DisplayName(
            "P-18, AUTH-20: không có quyền quản lý tài khoản phụ huynh ⇒ vẫn lưu; trùng CCCD vẫn liên kết, người mới chờ kích hoạt")
    void enrollerWithoutParentAccountPermissionStillSaves() {
        Set<Permission> withoutAccounts = EnumSet.copyOf(TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));
        withoutAccounts.remove(Permission.PARENT_ACCOUNT_MANAGE);
        CurrentUser enroller = user(vicePrincipal.userId(), CareNestRole.VICE_PRINCIPAL, withoutAccounts);

        ChildEnrollmentResponse response = enrollmentService.enroll(
                enroller,
                request(
                        "Lê Anh Ba",
                        "2023-06-01",
                        null,
                        null,
                        new GuardianInput("Tên gõ khác", "MOTHER", "0999999999", "001190000001", null),
                        new GuardianInput("Lê Văn Bố", "FATHER", "0914000001", "001400000001", null)));

        assertThat(response.guardianAccounts())
                .extracting(g -> g.result())
                .containsExactly(GuardianAccountResult.LINKED, GuardianAccountResult.NOT_ACTIVATED);
        // C-2: phụ huynh có tài khoản ⇒ hồ sơ ghi theo tài khoản, không sửa tài khoản
        assertThat(response.child().guardians().getFirst()).satisfies(g -> {
            assertThat(g.fullName()).isEqualTo("Lê Thị Có Sẵn");
            assertThat(g.phone()).isEqualTo("0901000001");
            assertThat(g.accountStatus()).isEqualTo(GuardianAccountStatus.ACTIVE);
        });
        assertThat(response.child().guardians().get(1).accountStatus()).isEqualTo(GuardianAccountStatus.NOT_ACTIVATED);
        assertThat(count(
                        "SELECT count(*) FROM guardian_child_links WHERE user_id = ? AND child_id = ?",
                        existingParent,
                        response.child().id()))
                .isEqualTo(1);
        verify(identityProviderClient, never()).createUser(any());
        verifyNoInteractions(smsGateway);
    }

    @Test
    @DisplayName("AUTH-18: tài khoản cũ chưa có CCCD khớp theo SĐT, được bổ sung CCCD trên hồ sơ")
    void legacyAccountMatchedByPhoneGetsCitizenId() {
        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                request(
                        "Phạm Minh Út",
                        "2023-07-01",
                        null,
                        campusA,
                        new GuardianInput("Phạm Văn Cũ", "FATHER", "0901 000 002", "001500000001", null)));

        assertThat(response.guardianAccounts().getFirst().result()).isEqualTo(GuardianAccountResult.LINKED);
        assertThat(jdbc.queryForObject("SELECT citizen_id FROM user_accounts WHERE id = ?", String.class, legacyParent))
                .isEqualTo("001500000001");
        verify(identityProviderClient, never()).createUser(any());
    }

    @Test
    @DisplayName("AUTH-18: một người một tài khoản ⇒ kiểm phụ huynh và lưu form báo 409 theo ô CCCD / SĐT")
    void identityConflictsAreFieldErrors() {
        // CCCD đã ghi cho phụ huynh khác (chưa có tài khoản) trên hồ sơ trẻ khác với SĐT khác
        long other = child("Trẻ Khác", "2022-01-01", "PENDING_PLACEMENT", campusA);
        guardian(other, "Người Đã Ghi", "MOTHER", "0915000001", "001600000001", null);
        // SĐT là tên đăng nhập nhân sự cũ
        staff("0915000009", "TEACHER");

        GuardianCheckRequest check = new GuardianCheckRequest(List.of(
                new GuardianInput("Mẹ", "MOTHER", "0915000002", "001600000001", null),
                new GuardianInput("Bố", "FATHER", "0901000001", "001600000002", null),
                new GuardianInput("Bà", "GRANDMOTHER", "0915000009", "001600000003", null)));
        expectFields(
                () -> enrollmentService.checkGuardians(principal, check),
                ApiCode.GUARDIAN_IDENTITY_CONFLICT,
                Map.of(
                        "guardians.0.idNumber", ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN,
                        "guardians.1.phone", ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID,
                        "guardians.2.phone", ApiCode.PHONE_OF_STAFF_ACCOUNT.getDefaultDesc()));
        expectFields(
                () -> enrollmentService.enroll(
                        principal,
                        request(
                                "Trẻ Trùng Người",
                                "2023-08-01",
                                null,
                                campusA,
                                check.guardians().toArray(GuardianInput[]::new))),
                ApiCode.GUARDIAN_IDENTITY_CONFLICT,
                Map.of(
                        "guardians.0.idNumber", ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN,
                        "guardians.1.phone", ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID,
                        "guardians.2.phone", ApiCode.PHONE_OF_STAFF_ACCOUNT.getDefaultDesc()));
        verify(identityProviderClient, never()).createUser(any());

        // Phụ huynh có sẵn theo CCCD (gõ SĐT khác) là chính người đó ⇒ không lệch
        enrollmentService.checkGuardians(
                principal,
                new GuardianCheckRequest(
                        List.of(new GuardianInput("Mẹ", "MOTHER", "0999999998", "001190000001", null))));
    }

    @Test
    @DisplayName(
            "CHD-04: trùng họ tên, ngày sinh, CCCD phụ huynh ⇒ ACTIVE chặn lưu (409); điểm trường khác chỉ báo loại; LEFT gợi ý mở lại")
    void duplicateChildRules() {
        ChildEnrollmentResponse first = enrollmentService.enroll(
                principal,
                request("Hoàng Minh Châu", "2023-09-01", null, campusA, newGuardian("0916000001", "001700000001")));
        Long childId = first.child().id();

        DuplicateCheckResponse same = intakeService.checkDuplicate(
                vicePrincipal, "  hoàng   MINH châu ", "2023-09-01", List.of("0017 0000 0001"), null);
        assertThat(same.kind()).isEqualTo(DuplicateChildKind.ACTIVE);
        assertThat(same.sameCampus()).isTrue();
        assertThat(same.id()).isEqualTo(childId);
        assertThat(same.canReadmit()).isFalse();
        assertThat(intakeService.checkDuplicate(
                        vicePrincipalOfB, "Hoàng Minh Châu", "2023-09-01", List.of("001700000001"), null))
                .isEqualTo(DuplicateCheckResponse.otherCampus(DuplicateChildKind.ACTIVE));
        assertThat(intakeService.checkDuplicate(
                        principal, "Hoàng Minh Châu", "2023-09-01", List.of("001700000001"), childId))
                .isNull();
        assertThat(intakeService.checkDuplicate(
                        principal, "Hoàng Minh Châu", "2023-09-02", List.of("001700000001"), null))
                .isNull();

        expectCode(
                () -> enrollmentService.enroll(
                        principal,
                        request(
                                "Hoàng Minh Châu",
                                "2023-09-01",
                                null,
                                campusA,
                                newGuardian("0916000001", "001700000001"))),
                ApiCode.CHILD_DUPLICATE);

        entityManager.flush();
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = ?, left_reason = 'Chuyển nhà', intake_campus_id = NULL WHERE id = ?",
                TODAY,
                childId);
        entityManager.clear();
        DuplicateCheckResponse left =
                intakeService.checkDuplicate(principal, "Hoàng Minh Châu", "2023-09-01", List.of("001700000001"), null);
        assertThat(left.kind()).isEqualTo(DuplicateChildKind.LEFT);
        assertThat(left.leftAt()).isEqualTo(TODAY);
        assertThat(left.canReadmit()).isTrue();
    }

    @Test
    @DisplayName(
            "CHD-10: lớp tiếp nhận = lớp đang hoạt động của năm đang hoạt động trong phạm vi, kèm sĩ số, chủ nhiệm")
    void placementClassesFollowActiveYearAndScope() {
        List<PlacementClassResponse> all = intakeService.listPlacementClasses(principal);
        assertThat(all).extracting(PlacementClassResponse::id).containsExactlyInAnyOrder(classA, classB);
        assertThat(all).filteredOn(c -> c.id().equals(classA)).singleElement().satisfies(c -> {
            assertThat(c.enrolledCount()).isEqualTo(1);
            assertThat(c.capacity()).isEqualTo(2);
            assertThat(c.homeroomTeacherId()).isEqualTo(teacher);
        });
        assertThat(intakeService.listPlacementClasses(vicePrincipal))
                .extracting(PlacementClassResponse::id)
                .containsExactly(classA);
    }

    @Test
    @DisplayName("AUTH-18: tra tài khoản theo CCCD ⇒ trẻ trong phạm vi hiện lớp, ngoài phạm vi chỉ đếm, quan hệ gợi ý")
    void parentAccountLookupByCitizenId() {
        ParentAccountLookupResponse found = enrollmentService.findParentAccount(vicePrincipal, "0011 9000 0001");
        assertThat(found.id()).isEqualTo(existingParent);
        assertThat(found.phone()).isEqualTo("0901000001");
        assertThat(found.children()).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(siblingA);
            assertThat(c.className()).isEqualTo("Lớp A1");
        });
        assertThat(found.otherChildrenCount()).isEqualTo(1);
        assertThat(found.relation()).isEqualTo(GuardianRelationship.MOTHER);
        assertThat(enrollmentService
                        .findParentAccount(principal, "001190000001")
                        .children())
                .hasSize(2);
        assertThat(enrollmentService.findParentAccount(principal, "009999999999"))
                .isNull();
        assertThat(enrollmentService.findParentAccount(principal, "12345")).isNull();
    }

    @Test
    @DisplayName("AUTH-20: Keycloak lỗi ở phụ huynh thứ hai ⇒ xóa user đã tạo, không lưu hồ sơ")
    void identityFailureCompensatesCreatedUsers() {
        given(identityProviderClient.createUser(any()))
                .willReturn("kc-first")
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null));
        long before = count("SELECT count(*) FROM children");

        expectCode(
                () -> enrollmentService.enroll(
                        principal,
                        request(
                                "Trẻ Lỗi Keycloak",
                                "2023-10-01",
                                null,
                                campusA,
                                newGuardian("0917000001", "001800000001"),
                                new GuardianInput("Bố", "FATHER", "0917000002", "001800000002", null))),
                ApiCode.IDENTITY_PROVIDER_UNAVAILABLE);

        verify(identityProviderClient).deleteUser("kc-first");
        assertThat(count("SELECT count(*) FROM children")).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM user_accounts WHERE username IN ('0917000001', '0917000002')"))
                .isZero();
        verifyNoInteractions(smsGateway);
    }

    @Test
    @DisplayName("L-15: SMS lỗi ⇒ hồ sơ, tài khoản vẫn lưu, smsStatus FAILED để FE cho gửi lại")
    void smsFailureKeepsEnrollment() {
        willThrow(new SmsDeliveryException("provider down")).given(smsGateway).send(anyString(), anyString());

        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                request("Trẻ SMS Lỗi", "2023-11-01", null, campusA, newGuardian("0918000001", "001900000001")));

        assertThat(response.guardianAccounts().getFirst()).satisfies(g -> {
            assertThat(g.result()).isEqualTo(GuardianAccountResult.CREATED);
            assertThat(g.smsStatus()).isEqualTo(SmsStatus.FAILED);
        });
        assertThat(count("SELECT count(*) FROM parent_sms_logs p JOIN user_accounts u ON u.id = p.user_id "
                        + "WHERE u.username = '0918000001' AND p.status = 'FAILED'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "CHD-03: mở lại hồ sơ trẻ đã nghỉ ⇒ Chờ xếp lớp ở điểm trường lớp cuối, giữ mã; chưa nghỉ 409; thiếu lý do 400; PHT khác 403")
    void readmitLeftChild() {
        long left = child("Trẻ Đã Nghỉ", "2021-02-01", "ACTIVE", null);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01', '2026-10-15')",
                left,
                classA);
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = '2026-10-16', left_reason = 'Chuyển trường' WHERE id = ?",
                left);
        String code = jdbc.queryForObject("SELECT code FROM children WHERE id = ?", String.class, left);

        expectCode(
                () -> intakeService.readmit(vicePrincipalOfB, left, new ReadmitChildRequest("Quay lại")),
                ApiCode.CHILD_ACCESS_DENIED);
        expectField(
                () -> intakeService.readmit(principal, left, new ReadmitChildRequest("  ")),
                ApiCode.INVALID_REQUEST_DATA,
                "reason",
                "Nhập lý do tiếp nhận lại");

        ChildViewResponse view =
                intakeService.readmit(vicePrincipal, left, new ReadmitChildRequest("Gia đình chuyển về"));
        assertThat(view.status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(view.code()).isEqualTo(code);
        assertThat(view.campusId()).isEqualTo(campusA);
        assertThat(view.readmittedAt()).isEqualTo(TODAY);
        assertThat(view.leftAt()).isNull();
        assertThat(view.leftReason()).isNull();
        assertThat(view.history()).singleElement().satisfies(h -> {
            assertThat(h.action()).isEqualTo(ChildHistoryAction.READMITTED);
            assertThat(h.note()).isEqualTo("Gia đình chuyển về");
        });

        expectCode(
                () -> intakeService.readmit(principal, left, new ReadmitChildRequest("Lần nữa")),
                ApiCode.CHILD_NOT_LEFT);
    }

    // ---------- dữ liệu ----------

    private static EnrollChildRequest request(
            String fullName, String dateOfBirth, Long classId, Long campusId, GuardianInput... guardians) {
        return new EnrollChildRequest(
                new EnrollmentChildInput(
                        fullName,
                        dateOfBirth,
                        "FEMALE",
                        List.of(guardians),
                        new ChildProfileInput(
                                null,
                                "NO",
                                null,
                                "Kinh",
                                "Không",
                                null,
                                new ChildAddressInput("Hà Nội", "Cầu Giấy", "1 Láng"))),
                new HealthDeclarationInput(
                        new AllergiesInput("REPORTED", List.of("Tôm")),
                        new DeclarationAnswerInput("NONE_REPORTED", null),
                        new DeclarationAnswerInput("NOT_PROVIDED", null),
                        new MedicalHistoryInput(
                                new DeclarationAnswerInput("NONE_REPORTED", null),
                                new DeclarationAnswerInput("NONE_REPORTED", null),
                                new TbContactInput("NONE_REPORTED", null))),
                classId,
                campusId);
    }

    private static GuardianInput newGuardian(String phone, String citizenId) {
        return new GuardianInput("Phụ Huynh Mới", "MOTHER", phone, citizenId, null);
    }

    private long staff(String username, String role) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role) VALUES ('kc-"
                + username + "', '" + username + "', '" + username + "', '" + role + "') RETURNING id");
    }

    private long parent(String phone, String fullName, String email, String citizenId) {
        return jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name, email, primary_role, citizen_id) "
                        + "VALUES (?, ?, ?, ?, 'PARENT', ?) RETURNING id",
                Long.class,
                "kc-" + phone,
                phone,
                fullName,
                email,
                citizenId);
    }

    private long child(String fullName, String dateOfBirth, String status, Long intakeCampusId) {
        return jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on) "
                        + "VALUES ('IT' || substr(md5(random()::text), 1, 12), ?, ?::date, 'MALE', ?, ?, '2026-09-01') RETURNING id",
                Long.class,
                fullName,
                dateOfBirth,
                status,
                intakeCampusId);
    }

    private void enroll(long childId, long classroomId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classroomId);
    }

    private void guardian(
            long childId, String fullName, String relationship, String phone, String citizenId, Long userId) {
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id) "
                        + "VALUES (?, 0, ?, ?, ?, ?, ?)",
                childId,
                fullName,
                relationship,
                phone,
                citizenId,
                userId);
        if (userId != null) {
            jdbc.update(
                    "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, ?)",
                    userId,
                    childId,
                    relationship);
        }
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId, int capacity) {
        long room = insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campusId
                + ", 'R" + System.nanoTime() % 100000 + "', 'Phòng " + name + "', 'CLASS', 30) RETURNING id");
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) "
                + "VALUES (" + campusId + ", " + yearId + ", '" + name + "', " + ageGroupId + ", " + room + ", "
                + capacity + ") RETURNING id");
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

    private static void expectCode(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(code);
    }

    private static void expectField(ThrowingCallable call, ApiCode code, String field, String message) {
        expectFields(call, code, Map.of(field, message));
    }

    private static void expectFields(ThrowingCallable call, ApiCode code, Map<String, String> fields) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlobalException.class, ex -> {
            assertThat(ex.getApiCode()).isEqualTo(code);
            assertThat(ex.getFieldErrors().stream()
                            .collect(Collectors.toMap(FieldErrorResponse::field, FieldErrorResponse::message)))
                    .isEqualTo(fields);
        });
    }
}
