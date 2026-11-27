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
import com.carenest.utils.enums.YesNo;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
@RecordApplicationEvents
class ChildEnrollmentIntegrationTest {
    // Mã phòng duy nhất trong lần chạy (nanoTime % 100000 trùng khi hai lần gọi sát nhau)
    private static final AtomicInteger ROOM_SEQ = new AtomicInteger();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

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

    // Spy kho thật: giới hạn/định dạng lấy từ cấu hình, chỉ chặn phần gọi S3
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.carenest.integration.storage.FileStorage childPhotoStorage;

    @Autowired
    private com.carenest.service.child.ChildPhotoService childPhotos;

    @Autowired
    private ChildEnrollmentService enrollmentService;

    @Autowired
    private ChildIntakeService intakeService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.carenest.repository.child.EnrollmentRepository enrollmentRepository;

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
    private long activeYear;
    private long plannedYear;
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
        activeYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        plannedYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-06', '2028-05-31') RETURNING id");
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, activeYear);
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, plannedYear);
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
    @DisplayName("CHD-02/PCK-02: lưu ảnh trẻ/phụ huynh chưa kích hoạt, đọc lại đúng scope")
    void enrollsPhotosBeforeAccountActivation() throws Exception {
        Set<Permission> permissions = new java.util.HashSet<>(principal.permissions());
        permissions.remove(Permission.PARENT_ACCOUNT_MANAGE);
        CurrentUser withoutAccounts = user(principal.userId(), CareNestRole.PRINCIPAL, permissions);
        org.mockito.Mockito.doReturn(
                        new com.carenest.integration.storage.FileStorage.StoredFile("child-photo", "image/png", 3),
                        new com.carenest.integration.storage.FileStorage.StoredFile("parent-photo", "image/png", 3))
                .when(childPhotoStorage)
                .store(eq(com.carenest.utils.enums.FileCategory.CHILD_AVATAR), any(), any(byte[].class));
        org.mockito.Mockito.doReturn(new byte[] {7, 8, 9})
                .when(childPhotoStorage)
                .loadBytes(com.carenest.utils.enums.FileCategory.CHILD_AVATAR, "parent-photo");
        var request = request("Trẻ ảnh giả", "2023-03-01", null, campusA, newGuardian("0900000711", "999000000711"));
        var result = enrollmentService.enrollWithPhotos(
                withoutAccounts,
                request,
                Map.of(
                        "childPhoto", List.of(portrait("childPhoto")),
                        "guardianPhoto0", List.of(portrait("guardianPhoto0"))));
        entityManager.flush();
        entityManager.clear();
        var view = intakeService.getChildView(result.child().id());
        assertThat(view.photoUrl()).isEqualTo("/children/" + view.id() + "/photo");
        assertThat(view.guardians().getFirst().photoUrl()).contains("/guardian-photos/");
        assertThat(view.guardians().getFirst().accountStatus()).isEqualTo(GuardianAccountStatus.NOT_ACTIVATED);
        Long guardianId =
                jdbc.queryForObject("select id from child_guardians where child_id = ?", Long.class, view.id());
        assertThat(childPhotos
                        .getGuardianPhoto(principal, view.id(), guardianId)
                        .content())
                .containsExactly(7, 8, 9);
        assertThat(childPhotos
                        .getGuardianPhoto(vicePrincipalOfB, view.id(), guardianId)
                        .content())
                .containsExactly(7, 8, 9);
        CurrentUser otherReader = user(teacher, CareNestRole.TEACHER, TestPermissions.defaultsOf(CareNestRole.TEACHER));
        assertThatThrownBy(() -> childPhotos.getGuardianPhoto(otherReader, view.id(), guardianId))
                .isInstanceOf(GlobalException.class);
        verifyNoInteractions(identityProviderClient, smsGateway);
    }

    @Test
    @DisplayName("CHD-02: ảnh không hợp lệ bị chặn trước tạo Keycloak/SMS/DB")
    void invalidPhotoDoesNotCreateEnrollment() {
        var request =
                request("Trẻ ảnh lỗi giả", "2023-03-01", null, campusA, newGuardian("0900000712", "999000000712"));
        var file = new org.springframework.mock.web.MockMultipartFile(
                "childPhoto", "fake.png", "image/png", "fake".getBytes());
        assertThatThrownBy(() ->
                        enrollmentService.enrollWithPhotos(principal, request, Map.of("childPhoto", List.of(file))))
                .isInstanceOf(GlobalException.class);
        assertThat(jdbc.queryForObject(
                        "select count(*) from children where full_name = ?", Integer.class, "Trẻ ảnh lỗi giả"))
                .isZero();
        verifyNoInteractions(identityProviderClient, smsGateway);
        verify(childPhotoStorage, org.mockito.Mockito.never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("G94-180/CHD-15: tiếp nhận thiếu ảnh của trẻ hoặc phụ huynh bị chặn theo ô, không tạo hồ sơ")
    void enrollmentWithoutPhotosIsRejected() throws Exception {
        var request =
                request("Trẻ thiếu ảnh giả", "2023-03-01", null, campusA, newGuardian("0900000714", "999000000714"));
        assertThatThrownBy(() -> enrollmentService.enrollWithPhotos(principal, request, Map.of()))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(com.carenest.dto.common.FieldErrorResponse::field)
                                .containsExactly("photo", "guardians.0.photo"));
        assertThatThrownBy(() -> enrollmentService.enrollWithPhotos(
                        principal, request, Map.of("childPhoto", List.of(portrait("childPhoto")))))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(com.carenest.dto.common.FieldErrorResponse::field)
                                .containsExactly("guardians.0.photo"));
        assertThat(jdbc.queryForObject(
                        "select count(*) from children where full_name = ?", Integer.class, "Trẻ thiếu ảnh giả"))
                .isZero();
        verifyNoInteractions(identityProviderClient, smsGateway);
    }

    @Test
    @DisplayName("CHD-02: S3 lỗi giữa chừng rollback hồ sơ, dọn object ảnh mới và bù trừ Keycloak")
    void uploadFailureRollsBackEnrollment() throws Exception {
        org.mockito.Mockito.doReturn(
                        new com.carenest.integration.storage.FileStorage.StoredFile("new-object", "image/png", 3))
                .doThrow(GlobalException.of(ApiCode.STORAGE_UNAVAILABLE))
                .when(childPhotoStorage)
                .store(eq(com.carenest.utils.enums.FileCategory.CHILD_AVATAR), any(), any(byte[].class));
        var request =
                request("Trẻ rollback ảnh giả", "2023-03-01", null, campusA, newGuardian("0900000713", "999000000713"));
        var files = Map.<String, List<org.springframework.web.multipart.MultipartFile>>of(
                "childPhoto", List.of(portrait("childPhoto")), "guardianPhoto0", List.of(portrait("guardianPhoto0")));
        assertThatThrownBy(() -> enrollmentService.enrollWithPhotos(principal, request, files))
                .isInstanceOf(GlobalException.class);
        org.springframework.test.context.transaction.TestTransaction.flagForRollback();
        org.springframework.test.context.transaction.TestTransaction.end();
        assertThat(jdbc.queryForObject(
                        "select count(*) from children where full_name = ?", Integer.class, "Trẻ rollback ảnh giả"))
                .isZero();
        verify(childPhotoStorage).removeQuietly(com.carenest.utils.enums.FileCategory.CHILD_AVATAR, "new-object");
        verify(identityProviderClient).deleteUser("kc-new-1");
        verifyNoInteractions(smsGateway);
    }

    private static org.springframework.mock.web.MockMultipartFile portrait(String part) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new org.springframework.mock.web.MockMultipartFile(part, "fake.png", "image/png", bytes.toByteArray());
    }

    @Autowired
    private com.carenest.service.child.ChildListService childListService;

    @Test
    @DisplayName("CHD-13: trẻ tiếp nhận không gửi cờ thì báo ăn")
    void enrolledChildIsMealRegisteredByDefault() {
        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                request("Nguyễn Minh An", "2023-03-01", null, campusA, newGuardian("0912345678", "001234567890")));

        assertThat(response.child().mealRegistered()).isTrue();
        var items = childListService
                .searchChildren(
                        principal, new com.carenest.dto.child.ChildListFilter(null, campusA, null, null, null, 0, 100))
                .items();
        assertThat(items)
                .filteredOn(i -> i.id().equals(response.child().id()))
                .singleElement()
                .satisfies(i -> assertThat(i.mealRegistered()).isTrue());
    }

    @Test
    @DisplayName("CHD-13: tiếp nhận không báo ăn ⇒ lưu cờ, khai báo dị ứng Chưa cung cấp, không có dòng dị ứng")
    void enrollWithoutMealsStoresFlagAndNoAllergies() {
        EnrollChildRequest base =
                request("Trẻ Không Ăn", "2023-03-01", null, campusA, newGuardian("0912345680", "001234567892"));
        HealthDeclarationInput d = base.declaration();
        EnrollChildRequest noMeal = new EnrollChildRequest(
                base.child(),
                new HealthDeclarationInput(d.allergies(), d.diet(), d.otherNotes(), d.medicalHistory(), false),
                null,
                campusA,
                null,
                TODAY);

        ChildEnrollmentResponse response = enrollmentService.enroll(principal, noMeal);
        entityManager.flush();

        Long childId = response.child().id();
        assertThat(response.child().mealRegistered()).isFalse();
        assertThat(jdbc.queryForObject("SELECT meal_registered FROM children WHERE id = ?", Boolean.class, childId))
                .isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT allergies_state FROM child_health_declarations WHERE child_id = ?",
                        String.class,
                        childId))
                .isEqualTo("NOT_PROVIDED");
        assertThat(count("SELECT count(*) FROM child_allergies WHERE child_id = ?", childId))
                .isZero();
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
        // CHD-05 (user chốt 2026-11-04): tuần thai lưu cả khi trẻ không sinh non
        assertThat(child.profile().premature()).isEqualTo(YesNo.NO);
        assertThat(child.profile().gestationWeeks()).isEqualTo((short) 39);
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
                        "SELECT count(*) FROM child_guardians WHERE user_id = ? AND child_id = ?",
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
    @DisplayName("G94-180/CHD-07: tiếp nhận khai Có tiếp xúc lao ⇒ báo HT, PHT điểm trường tiếp nhận (không PHT khác)")
    void intakeWithTbContactNotifiesLeaders() {
        EnrollChildRequest base =
                request("Trần Gia Huy", "2023-05-01", null, campusA, newGuardian("0912345679", "001234567891"));
        HealthDeclarationInput declaration = base.declaration();
        EnrollChildRequest withTbContact = new EnrollChildRequest(
                base.child(),
                new HealthDeclarationInput(
                        declaration.allergies(),
                        declaration.diet(),
                        declaration.otherNotes(),
                        new MedicalHistoryInput(
                                declaration.medicalHistory().self(),
                                declaration.medicalHistory().family(),
                                new TbContactInput("REPORTED", "2026-10-20"))),
                null,
                campusA,
                null,
                TODAY);

        enrollmentService.enroll(principal, withTbContact);
        entityManager.flush();

        assertThat(jdbc.queryForList("SELECT user_id FROM notifications WHERE type = 'CHILD_TB_CONTACT'", Long.class))
                .containsExactlyInAnyOrder(principal.userId(), vicePrincipal.userId());
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
        // Mục 6.2: tiếp nhận có lớp ghi lịch sử xếp lớp (xếp lần đầu) và báo GV lớp
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM child_placements WHERE child_id = ? AND from_classroom_id IS NULL"
                                + " AND to_classroom_id = ?",
                        Long.class,
                        placed.child().id(),
                        classA))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE type = 'CHILD_PLACED' AND user_id = ?",
                        String.class,
                        teacher))
                .isEqualTo("Trẻ Có Lớp đã được xếp vào Lớp A1.");

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
                .containsExactly(GuardianAccountResult.LINKED);
        // C-2: phụ huynh có tài khoản ⇒ hồ sơ ghi theo tài khoản, không sửa tài khoản
        assertThat(response.child().guardians().getFirst()).satisfies(g -> {
            assertThat(g.fullName()).isEqualTo("Lê Thị Có Sẵn");
            assertThat(g.phone()).isEqualTo("0901000001");
            assertThat(g.accountStatus()).isEqualTo(GuardianAccountStatus.ACTIVE);
        });
        assertThat(response.child().guardians().get(1).accountStatus()).isEqualTo(GuardianAccountStatus.NOT_ACTIVATED);
        assertThat(count(
                        "SELECT count(*) FROM child_guardians WHERE user_id = ? AND child_id = ?",
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
                new GuardianInput("Bà", "GRANDMOTHER", "0915000009", "001600000003", null, true)));
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
                        "guardians.1.phone", ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID));
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
    @DisplayName("CHD-04: trẻ đã nghỉ ở lớp Cơ sở B ⇒ duplicate-check trả campusId của trẻ; ngoài phạm vi thì null")
    void duplicateOfLeftChildReturnsCampus() {
        Long childId = enrollmentService
                .enroll(
                        principal,
                        request("Phạm Gia Bảo", "2023-06-01", classB, null, newGuardian("0916000002", "001700000002")))
                .child()
                .id();
        entityManager.flush();
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = ?, left_reason = 'Chuyển nhà' WHERE id = ?",
                TODAY,
                childId);
        entityManager.clear();

        DuplicateCheckResponse left =
                intakeService.checkDuplicate(principal, "Phạm Gia Bảo", "2023-06-01", List.of("001700000002"), null);
        assertThat(left.kind()).isEqualTo(DuplicateChildKind.LEFT);
        assertThat(left.campusId()).isEqualTo(campusB);
        assertThat(intakeService
                        .checkDuplicate(vicePrincipal, "Phạm Gia Bảo", "2023-06-01", List.of("001700000002"), null)
                        .campusId())
                .isNull();
    }

    @Test
    @DisplayName(
            "CHD-04: trẻ chuyển từ Cơ sở A sang B rồi nghỉ thật (đóng lớp) ⇒ duplicate-check trả điểm trường lớp cuối (B)")
    void duplicateOfWithdrawnTransferredChildReturnsLastClassCampus() {
        Long childId = enrollmentService
                .enroll(
                        principal,
                        request("Vũ Minh Khôi", "2023-06-02", classA, null, newGuardian("0916000003", "001700000003")))
                .child()
                .id();
        recordService.place(
                principal, childId, new com.carenest.dto.child.PlaceChildRequest(classB, "Chuyển điểm trường"));
        // Lùi ngày chuyển để nghỉ sau đó đóng lớp B (không bị coi là bỏ lớp vừa vào hôm nay)
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-10-09' WHERE child_id = ? AND classroom_id = ?",
                childId,
                classA);
        jdbc.update(
                "UPDATE enrollments SET valid_from = '2026-10-10' WHERE child_id = ? AND classroom_id = ?",
                childId,
                classB);
        entityManager.clear();

        ChildViewResponse left = recordService.withdraw(
                principal,
                childId,
                new com.carenest.dto.child.WithdrawChildRequest(TODAY.toString(), "Gia đình chuyển nhà"));
        assertThat(left.status()).isEqualTo(ChildStatus.LEFT);
        assertThat(count("SELECT count(*) FROM enrollments WHERE child_id = " + childId
                        + " AND valid_to IS NOT NULL AND classroom_id = " + classB))
                .isEqualTo(1);
        entityManager.clear();

        DuplicateCheckResponse dup =
                intakeService.checkDuplicate(principal, "Vũ Minh Khôi", "2023-06-02", List.of("001700000003"), null);
        assertThat(dup.kind()).isEqualTo(DuplicateChildKind.LEFT);
        assertThat(dup.campusId()).isEqualTo(campusB);
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
    @DisplayName("AUTH-20: gán role Keycloak lỗi ⇒ xóa user được chọn, không lưu hồ sơ")
    void identityFailureCompensatesCreatedUsers() {
        given(identityProviderClient.createUser(any())).willReturn("kc-first");
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identityProviderClient)
                .assignRealmRole("kc-first", "PARENT");
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
                () -> intakeService.readmit(vicePrincipalOfB, left, new ReadmitChildRequest("Quay lại", null, TODAY)),
                ApiCode.CHILD_ACCESS_DENIED);
        expectField(
                () -> intakeService.readmit(principal, left, new ReadmitChildRequest("  ", null, TODAY)),
                ApiCode.INVALID_REQUEST_DATA,
                "reason",
                "Nhập lý do tiếp nhận lại");

        ChildViewResponse view =
                intakeService.readmit(vicePrincipal, left, new ReadmitChildRequest("Gia đình chuyển về", null, TODAY));
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
        // Feature account mở lại tài khoản phụ huynh do hệ thống khóa (sau commit); lần lỗi ở trên không phát
        assertThat(applicationEvents.stream(com.carenest.service.child.ChildIntakeService.ChildReadmittedEvent.class))
                .containsExactly(new com.carenest.service.child.ChildIntakeService.ChildReadmittedEvent(left));

        expectCode(
                () -> intakeService.readmit(principal, left, new ReadmitChildRequest("Lần nữa", null, TODAY)),
                ApiCode.CHILD_NOT_LEFT);
    }

    @Test
    @DisplayName("CHD-03, CHD-11: mở lại hồ sơ cho năm sau ⇒ Chờ xếp lớp, có đăng ký")
    void readmitIntoPlannedYear() {
        long left = child("Trẻ Quay Lại Năm Sau", "2021-02-01", "ACTIVE", null);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01', '2026-10-15')",
                left,
                classA);
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = '2026-10-16', left_reason = 'Chuyển' WHERE id = ?",
                left);
        long plain = child("Trẻ Quay Lại Như Cũ", "2021-02-02", "ACTIVE", null);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01', '2026-10-15')",
                plain,
                classA);
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = '2026-10-16', left_reason = 'Chuyển' WHERE id = ?",
                plain);
        entityManager.clear();

        // Trước ngày mở chuẩn bị ⇒ 2412, hồ sơ giữ nguyên
        expectCode(
                () -> intakeService.readmit(
                        principal,
                        left,
                        new ReadmitChildRequest("Gia đình quay lại", plannedYear, LocalDate.of(2027, 9, 6))),
                ApiCode.PREP_YEAR_NOT_OPEN);
        assertThat(count("SELECT count(*) FROM children WHERE id = ? AND status = 'LEFT'", left))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM planned_admissions WHERE child_id = ?", left))
                .isZero();

        openPrep("2026-10-29");
        entityManager.clear();
        ChildViewResponse view = intakeService.readmit(
                principal, left, new ReadmitChildRequest("Gia đình quay lại", plannedYear, LocalDate.of(2027, 9, 6)));
        assertThat(view.status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(view.campusId()).isEqualTo(campusA);
        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND status = 'PENDING'"
                                + " AND campus_id = ? AND school_year_id = ? AND classroom_id IS NULL"
                                + " AND start_date = '2027-09-06'",
                        left,
                        campusA,
                        plannedYear))
                .isEqualTo(1);

        // Không gửi schoolYearId ⇒ như cũ, không có đăng ký
        intakeService.readmit(principal, plain, new ReadmitChildRequest("Gia đình quay lại", null, TODAY));
        assertThat(count("SELECT count(*) FROM planned_admissions WHERE child_id = ?", plain))
                .isZero();
    }

    @Autowired
    private com.carenest.service.child.ChildRecordService recordService;

    @Test
    @DisplayName(
            "CHD-03, CHD-04: nghỉ ngay ngày vào lớp (bỏ lớp đó) ⇒ hồ sơ đã nghỉ vẫn thuộc điểm trường của lớp; mở lại về Chờ xếp lớp ở đó")
    void readmitChildWithdrawnOnPlacementDay() {
        long childId = enrollmentService
                .enroll(
                        principal,
                        request("Trẻ Nghỉ Ngay", "2023-05-06", classA, null, newGuardian("0918888981", "999900000981")))
                .child()
                .id();

        ChildViewResponse left = recordService.withdraw(
                principal,
                childId,
                new com.carenest.dto.child.WithdrawChildRequest(TODAY.toString(), "Gia đình đổi ý"));
        assertThat(left.status()).isEqualTo(ChildStatus.LEFT);
        assertThat(count("SELECT count(*) FROM enrollments WHERE child_id = " + childId))
                .isZero();
        assertThat(left.campusId()).isEqualTo(campusA);
        entityManager.clear();

        ChildViewResponse view = intakeService.readmit(
                vicePrincipal, childId, new ReadmitChildRequest("Gia đình quay lại", null, TODAY));
        assertThat(view.status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(view.campusId()).isEqualTo(campusA);
    }

    // ---------- dữ liệu ----------

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private com.carenest.service.account.SelectedParentAccountService selectedAccounts;

    @Autowired
    private com.carenest.service.account.EnrollmentImportAccountService imports;

    @Test
    void onlyChosenGuardianReceivesAccountAndAllContactsRemain() {
        long before = count("SELECT count(*) FROM user_accounts");
        EnrollChildRequest base = request(
                "Trẻ Chọn Bố",
                "2023-04-04",
                null,
                campusA,
                newGuardian("0918888001", "999900000571"),
                new GuardianInput("Bố Chọn", "FATHER", "0918888002", "999900000572", null));
        var gs = base.child().guardians();
        EnrollChildRequest chosen = new EnrollChildRequest(
                new EnrollmentChildInput(
                        base.child().fullName(),
                        base.child().dateOfBirth(),
                        base.child().gender(),
                        List.of(
                                new GuardianInput(
                                        gs.get(0).fullName(),
                                        gs.get(0).relation(),
                                        gs.get(0).phone(),
                                        gs.get(0).idNumber(),
                                        null,
                                        false),
                                new GuardianInput(
                                        gs.get(1).fullName(),
                                        gs.get(1).relation(),
                                        gs.get(1).phone(),
                                        gs.get(1).idNumber(),
                                        null,
                                        true)),
                        base.child().profile()),
                base.declaration(),
                null,
                campusA,
                null,
                TODAY);
        var saved = enrollmentService.enroll(principal, chosen);
        assertThat(saved.child().guardians()).hasSize(2);
        assertThat(saved.child().guardians())
                .extracting(g -> g.accountRecipient())
                .containsExactly(false, true);
        assertThat(saved.guardianAccounts())
                .singleElement()
                .extracting(g -> g.phone())
                .isEqualTo("0918888002");
        assertThat(count("SELECT count(*) FROM user_accounts")).isEqualTo(before + 1);
        assertThat(count(
                        "SELECT count(*) FROM child_guardians WHERE child_id = ? AND user_id IS NOT NULL",
                        saved.child().id()))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_accounts WHERE username = '0918888001'"))
                .isZero();
    }

    @Test
    void importDefersActivationAndRejectsStaleRecipient() {
        EnrollChildRequest row = request(
                "Trẻ Nhập Chọn",
                "2023-04-05",
                null,
                campusA,
                newGuardian("0918888011", "999900000581"),
                new GuardianInput("Bố Chọn", "FATHER", "0918888012", "999900000582", null));
        long before = count("SELECT count(*) FROM user_accounts");
        Long childId = imports.importRows(
                        vicePrincipal,
                        new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                                List.of(row), campusA, null, TODAY))
                .ids()
                .getFirst();
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM user_accounts")).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM child_guardians WHERE child_id = ? AND user_id IS NOT NULL", childId))
                .isZero();
        var candidates = selectedAccounts.candidates(vicePrincipal, childId);
        Long old = candidates.get(0).id();
        Long next = candidates.get(1).id();
        selectedAccounts.select(vicePrincipal, childId, next);
        expectCode(() -> selectedAccounts.activate(vicePrincipal, childId, old), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
        verifyNoInteractions(identityProviderClient);
        var activated = selectedAccounts.activate(vicePrincipal, childId, next);
        assertThat(activated).singleElement().satisfies(a -> {
            assertThat(a.username()).isEqualTo("0918888012");
            assertThat(a.newAccount()).isTrue();
        });
        assertThat(selectedAccounts.activate(vicePrincipal, childId, next))
                .singleElement()
                .satisfies(a -> assertThat(a.newAccount()).isFalse());
        assertThat(count("SELECT count(*) FROM child_guardians WHERE child_id = ? AND user_id IS NOT NULL", childId))
                .isEqualTo(1);
        expectCode(() -> selectedAccounts.select(vicePrincipal, childId, old), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
    }

    @Test
    void selectedAccountOperationsDenyOtherCampusIncludingPendingChildren() {
        var row = request("Trẻ Ngoài Phạm Vi", "2023-04-06", null, campusB, newGuardian("0918888021", "999900000591"));
        Long childId = imports.importRows(
                        principal,
                        new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                                List.of(row), campusB, null, TODAY))
                .ids()
                .getFirst();
        expectCode(() -> selectedAccounts.candidates(vicePrincipal, childId), ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        var candidates = selectedAccounts.candidates(principal, childId);
        expectCode(
                () -> selectedAccounts.select(
                        vicePrincipal, childId, candidates.getFirst().id()),
                ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        expectCode(
                () -> selectedAccounts.activate(
                        vicePrincipal, childId, candidates.getFirst().id()),
                ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        verifyNoInteractions(identityProviderClient);
    }

    @Autowired
    private com.carenest.repository.account.UserAccountRepository accountRepository;

    @Test
    void citizenIdClaimDoesNotAcceptConcurrentDifferentIdentity() {
        long accountId = staff("0918888991", "PARENT");
        assertThat(accountRepository.claimCitizenId(accountId, "999900000991")).isEqualTo(1);
        assertThat(accountRepository.claimCitizenId(accountId, "999900000991")).isEqualTo(1);
        assertThat(accountRepository.claimCitizenId(accountId, "999900000992")).isZero();
        assertThat(jdbc.queryForObject("SELECT citizen_id FROM user_accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("999900000991");
    }

    @Test
    void importBatchAllowsDifferentFamiliesAndRejectsOverlappingGuardians() {
        var first = request("Trẻ Trùng Tên", "2023-02-02", null, campusA, newGuardian("0918888911", "999900000911"));
        var other = request("Trẻ Trùng Tên", "2023-02-02", null, campusA, newGuardian("0918888912", "999900000912"));
        var result = imports.importRows(
                principal,
                new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                        List.of(first, other), campusA, null, TODAY));
        assertThat(result.created()).isEqualTo(2);
        var one = request("Trẻ Trùng Gia Đình", "2023-02-03", null, campusA, newGuardian("0918888921", "999900000921"));
        var two = request(
                "Trẻ Trùng Gia Đình",
                "2023-02-03",
                null,
                campusA,
                newGuardian("0918888921", "999900000921"),
                newGuardian("0918888922", "999900000922"));
        long before = count("SELECT count(*) FROM children");
        expectCode(
                () -> imports.importRows(
                        principal,
                        new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                                List.of(one, two), campusA, null, TODAY)),
                ApiCode.INVALID_REQUEST_DATA);
        assertThat(count("SELECT count(*) FROM children")).isEqualTo(before);
    }

    @Test
    @DisplayName("G94-180/CHD-04: xem trước Excel như BE giả FE — trẻ trùng đã nghỉ chỉ ghi chú mở lại hồ sơ cũ,"
            + " trẻ trùng đang học vẫn lỗi, phụ huynh trùng CCCD tài khoản có ghi chú")
    void importPreviewNotesWithdrawnDuplicateAndExistingAccount() {
        parent("0918888931", "Mẹ Đã Có Tài Khoản", null, "999900000931");
        Long leftId = enrollmentService
                .enroll(
                        principal,
                        request(
                                "Trẻ Đã Nghỉ Nhập",
                                "2023-03-03",
                                null,
                                campusA,
                                newGuardian("0918888941", "999900000941")))
                .child()
                .id();
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = ?, left_reason = 'Đã nghỉ (giả)' WHERE id = ?",
                TODAY,
                leftId);
        String leftCode = jdbc.queryForObject("SELECT code FROM children WHERE id = ?", String.class, leftId);
        enrollmentService.enroll(
                principal,
                request("Trẻ Đang Học Nhập", "2023-03-04", null, campusA, newGuardian("0918888951", "999900000951")));
        entityManager.flush();
        entityManager.clear();

        var preview = imports.preview(
                principal,
                importCsv(
                        importRow(
                                "Trẻ Mới Có Mẹ Tài Khoản",
                                "05/03/2023",
                                "Mẹ Đã Có Tài Khoản",
                                "0918888931",
                                "999900000931"),
                        importRow("Trẻ Đã Nghỉ Nhập", "03/03/2023", "Phụ Huynh Mới", "0918888941", "999900000941"),
                        importRow("Trẻ Đang Học Nhập", "04/03/2023", "Phụ Huynh Mới", "0918888951", "999900000951")));

        var rows = preview.rows();
        assertThat(rows.get(0).errors()).isEmpty();
        assertThat(rows.get(0).notes())
                .contains(
                        "Phụ huynh đã có tài khoản (trùng CCCD): Mẹ Đã Có Tài Khoản · 0918888931. Dùng thông tin tài khoản,"
                                + " chỉ liên kết người được chọn khi kích hoạt sau khi nhập.");
        assertThat(rows.get(1).errors())
                .contains("Trẻ từng học ở trường (hồ sơ cũ " + leftCode
                        + ", đã nghỉ học): mở lại hồ sơ cũ ở Tiếp nhận trẻ, không nhập mới.");
        expectCode(
                () -> enrollmentService.enroll(
                        principal,
                        request(
                                "Trẻ Đã Nghỉ Nhập",
                                "2023-03-03",
                                null,
                                campusA,
                                newGuardian("0918888941", "999900000941"))),
                ApiCode.CHILD_DUPLICATE_LEFT);
        assertThat(rows.get(2).errors())
                .contains("Trẻ đã có hồ sơ trong hệ thống (trùng họ tên, ngày sinh và phụ huynh)");
        assertThat(rows.get(2).notes()).noneMatch(n -> n.startsWith("Trẻ từng học ở trường"));
    }

    /** File CSV theo mẫu nhập trẻ (cột bắt buộc của ChildEnrollmentImportServiceImpl), một phụ huynh mỗi dòng. */
    private static org.springframework.mock.web.MockMultipartFile importCsv(List<String>... rows) {
        List<String> header = List.of(
                "Họ tên trẻ",
                "Ngày sinh (dd/mm/yyyy)",
                "Giới tính (Nam/Nữ)",
                "Họ tên phụ huynh",
                "Quan hệ (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ)",
                "Số điện thoại",
                "Số CCCD phụ huynh",
                "Sinh non (Có/Không)",
                "Tuần thai khi sinh (20–42)",
                "Dân tộc",
                "Đối tượng",
                "Nơi ở - Tỉnh/thành",
                "Nơi ở - Phường/xã",
                "Nơi ở - Số nhà/thôn/xóm",
                "Tiền sử bản thân (\"Không\" hoặc tên bệnh)",
                "Tiền sử gia đình (\"Không\" hoặc tên bệnh)",
                "Tiếp xúc người bệnh lao (Có/Không)");
        StringBuilder csv = new StringBuilder();
        for (List<String> line : java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(header), java.util.Arrays.stream(rows))
                .toList()) {
            csv.append(line.stream()
                            .map(v -> "\"" + v.replace("\"", "\"\"") + "\"")
                            .collect(java.util.stream.Collectors.joining(",")))
                    .append("\n");
        }
        return new org.springframework.mock.web.MockMultipartFile(
                "file", "tiep-nhan.csv", "text/csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<String> importRow(
            String fullName, String dateOfBirth, String guardian, String phone, String citizenId) {
        return List.of(
                fullName,
                dateOfBirth,
                "Nữ",
                guardian,
                "Mẹ",
                phone,
                citizenId,
                "Không",
                "39",
                "Kinh",
                "Không",
                "Hà Nội",
                "Cầu Giấy",
                "1 Láng",
                "Không",
                "Không",
                "Không");
    }

    @Autowired
    private com.carenest.service.account.GuardianAccountService legacyAccounts;

    @Test
    void selectedAccountProjectionUsesStoredAccountIdRatherThanAmbiguousIdentity() {
        var row = request(
                "Trẻ Legacy Chiếu Tài Khoản", "2023-02-06", null, campusA, newGuardian("0918888981", "999900000981"));
        var saved = enrollmentService.enroll(principal, row);
        Long childId = saved.child().id();
        Long selectedUser = selectedAccounts.selectedAccountId(principal, childId);
        jdbc.update("UPDATE child_guardians SET phone = '0901000001' WHERE child_id = ?", childId);
        // Một phụ huynh khác của trẻ cũng đã có tài khoản: chiếu vẫn theo người được chọn, không theo SĐT trùng
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id,"
                        + " linked_at) VALUES (?, 1, 'Bố Legacy', 'FATHER', '0918888982', '999900000982', ?, now())",
                childId,
                existingParent);
        entityManager.clear();
        assertThat(legacyAccounts.listGuardians(principal, childId))
                .singleElement()
                .satisfies(g -> assertThat(g.userId()).isEqualTo(selectedUser));
        assertThat(count("SELECT count(*) FROM child_guardians WHERE child_id = ? AND user_id IS NOT NULL", childId))
                .isEqualTo(2);
    }

    @Test
    void pendingScopeDoesNotGrantVicePrincipalAccessToLeavers() {
        var row =
                request("Trẻ Đã Nghỉ Phạm Vi", "2023-02-08", null, campusA, newGuardian("0918888971", "999900000971"));
        var saved = enrollmentService.enroll(principal, row);
        Long childId = saved.child().id();
        Long accountId = selectedAccounts.selectedAccountId(principal, childId);
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = ?, left_reason = 'Đã nghỉ (giả)' WHERE id = ?",
                TODAY,
                childId);
        entityManager.clear();
        expectCode(() -> selectedAccounts.candidates(vicePrincipal, childId), ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        expectCode(() -> selectedAccounts.checkParentScope(vicePrincipal, accountId), ApiCode.ACCOUNT_MANAGE_DENIED);
    }

    @Test
    @DisplayName("CHD-11: năm sau chưa tới ngày mở chuẩn bị ⇒ 2412 ở tiếp nhận và lớp tiếp nhận")
    void plannedYearClosedBeforePrepDate() {
        expectCode(
                () -> enrollmentService.enroll(
                        principal,
                        plannedRequest(
                                "Trẻ Chưa Mở",
                                plannedClass,
                                campusA,
                                "2027-09-06",
                                newGuardian("0915000001", "001500000001"))),
                ApiCode.PREP_YEAR_NOT_OPEN);
        expectCode(() -> intakeService.listPlacementClasses(principal, plannedYear), ApiCode.PREP_YEAR_NOT_OPEN);
        assertThat(count("SELECT count(*) FROM planned_admissions")).isZero();
    }

    @Test
    @DisplayName("CHD-11: đúng ngày mở chuẩn bị ⇒ tiếp nhận vào năm sau, có đăng ký")
    void plannedYearOpensOnPrepDate() {
        openPrep("2026-10-29");
        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                plannedRequest(
                        "Trẻ Năm Sau Mở",
                        plannedClass,
                        campusA,
                        "2027-09-06",
                        newGuardian("0915000002", "001500000002")));
        assertThat(response.child().status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(response.child().classId()).isNull();
        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND school_year_id = ?"
                                + " AND classroom_id = ? AND start_date = '2027-09-06' AND status = 'PLACED'",
                        response.child().id(),
                        plannedYear,
                        plannedClass))
                .isEqualTo(1);
        assertThat(intakeService.listPlacementClasses(principal, plannedYear))
                .filteredOn(c -> c.id().equals(plannedClass))
                .singleElement()
                .satisfies(c -> assertThat(c.enrolledCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("CHD-10: gửi schoolYearId năm đang hoạt động ⇒ như cũ, không đăng ký")
    void activeYearIdKeepsCurrentIntake() {
        EnrollChildRequest base =
                request("Trẻ Năm Nay", "2023-08-01", null, null, newGuardian("0915000003", "001500000003"));
        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                new EnrollChildRequest(base.child(), base.declaration(), classA, campusA, activeYear, TODAY));
        assertThat(response.child().classId()).isEqualTo(classA);
        assertThat(response.child().status()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(count("SELECT count(*) FROM planned_admissions")).isZero();
    }

    @Test
    @DisplayName("CHD-11, AUTH-20: tiếp nhận năm sau tạo, liên kết tài khoản như tiếp nhận thường")
    void plannedIntakeHandlesParentAccountsAsUsual() {
        openPrep("2026-10-29");
        ChildEnrollmentResponse created = enrollmentService.enroll(
                principal,
                plannedRequest("Trẻ Năm Sau Mới", null, campusA, null, newGuardian("0915000004", "001500000004")));
        assertThat(created.guardianAccounts()).singleElement().satisfies(g -> {
            assertThat(g.result()).isEqualTo(GuardianAccountResult.CREATED);
            assertThat(g.smsStatus()).isNotNull();
        });
        verify(identityProviderClient).createUser(any());

        ChildEnrollmentResponse linked = enrollmentService.enroll(
                principal,
                plannedRequest(
                        "Trẻ Năm Sau Có Sẵn",
                        null,
                        campusA,
                        null,
                        new GuardianInput("Lê Thị Có Sẵn", "MOTHER", "0901000001", "001190000001", null)));
        assertThat(linked.guardianAccounts()).extracting(g -> g.result()).containsExactly(GuardianAccountResult.LINKED);
    }

    @Test
    @DisplayName("CHD-11, CHD-03: cho nghỉ học trẻ năm sau ⇒ đăng ký CANCELLED")
    void withdrawCancelsPlannedAdmission() {
        openPrep("2026-10-29");
        long childId = enrollmentService
                .enroll(
                        principal,
                        plannedRequest(
                                "Trẻ Năm Sau Nghỉ",
                                plannedClass,
                                campusA,
                                "2027-09-06",
                                newGuardian("0915000005", "001500000005")))
                .child()
                .id();

        recordService.withdraw(
                principal,
                childId,
                new com.carenest.dto.child.WithdrawChildRequest("2026-10-29", "Gia đình chuyển nhà"));

        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND status = 'CANCELLED'", childId))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ?"
                                + " AND status IN ('PENDING', 'PLACED')",
                        childId))
                .isZero();
    }

    private long withdrawnPlannedChild(String name, String phone, String citizenId) {
        openPrep("2026-10-29");
        long childId = enrollmentService
                .enroll(
                        principal,
                        plannedRequest(name, plannedClass, campusA, "2027-09-06", newGuardian(phone, citizenId)))
                .child()
                .id();
        recordService.withdraw(
                principal,
                childId,
                new com.carenest.dto.child.WithdrawChildRequest("2026-10-29", "Gia đình chuyển nhà"));
        entityManager.clear();
        return childId;
    }

    @Test
    @DisplayName("CHD-03, CHD-11: nghỉ rồi mở lại cùng năm sau ⇒ mở lại đăng ký đã hủy, không trùng dòng")
    void readmitReopensCancelledAdmission() {
        long childId = withdrawnPlannedChild("Trẻ Đổi Ý", "0915000006", "001500000006");

        ChildViewResponse view = intakeService.readmit(
                principal, childId, new ReadmitChildRequest("Gia đình đổi ý", plannedYear, LocalDate.of(2027, 9, 10)));

        assertThat(view.status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND school_year_id = ?",
                        childId,
                        plannedYear))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND school_year_id = ?"
                                + " AND status = 'PENDING' AND classroom_id IS NULL AND start_date = '2027-09-10'",
                        childId,
                        plannedYear))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("CHD-11: mở lại năm sau không gửi ngày bắt đầu ⇒ lấy ngày bắt đầu năm học")
    void readmitDefaultsStartDateToYearStart() {
        long childId = withdrawnPlannedChild("Trẻ Mặc Định Ngày", "0915000007", "001500000007");

        intakeService.readmit(principal, childId, new ReadmitChildRequest("Gia đình đổi ý", plannedYear, null));

        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND school_year_id = ?"
                                + " AND status = 'PENDING' AND start_date = '2027-09-06'",
                        childId,
                        plannedYear))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("CHD-11: mở lại năm sau với ngày bắt đầu ngoài năm học ⇒ lỗi trường, hồ sơ vẫn đã nghỉ")
    void readmitRejectsStartDateOutsideYear() {
        long childId = withdrawnPlannedChild("Trẻ Ngày Sai", "0915000008", "001500000008");

        expectField(
                () -> intakeService.readmit(
                        principal,
                        childId,
                        new ReadmitChildRequest("Gia đình đổi ý", plannedYear, LocalDate.of(2027, 8, 1))),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Ngày bắt đầu phải nằm trong thời gian năm học đã chọn");

        assertThat(count("SELECT count(*) FROM children WHERE id = ? AND status = 'LEFT'", childId))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND status <> 'CANCELLED'",
                        childId))
                .isZero();
    }

    @Test
    @DisplayName("CHD-11: hồ sơ trẻ năm sau có đăng ký; trẻ thường không có")
    void childViewCarriesPlannedAdmission() {
        long childId = enrollPlannedAn();

        var admission = intakeService.getChildView(childId).plannedAdmission();
        assertThat(admission).isNotNull();
        assertThat(admission.schoolYearName()).isEqualTo("2027-2028");
        assertThat(admission.status()).isEqualTo(com.carenest.utils.enums.PlannedAdmissionStatus.PLACED);
        assertThat(intakeService.getChildView(siblingA).plannedAdmission()).isNull();
    }

    @Test
    @DisplayName("CHD-09, CHD-11: trẻ năm sau chỉ ở danh sách năm đó, kèm đăng ký")
    void plannedChildListedUnderPlannedYearOnly() {
        long childId = enrollPlannedAn();

        var underPlanned = childListService
                .searchChildren(
                        principal,
                        new com.carenest.dto.child.ChildListFilter(plannedYear, null, null, null, null, 0, 100))
                .items();
        assertThat(underPlanned)
                .filteredOn(i -> i.id().equals(childId))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.plannedAdmission().classId()).isEqualTo(plannedClass);
                    assertThat(i.plannedAdmission().className()).isEqualTo("Lớp A năm sau");
                    assertThat(i.plannedAdmission().startDate()).isEqualTo(LocalDate.of(2027, 9, 6));
                    assertThat(i.plannedAdmission().status())
                            .isEqualTo(com.carenest.utils.enums.PlannedAdmissionStatus.PLACED);
                });
        var underActive = childListService
                .searchChildren(
                        principal,
                        new com.carenest.dto.child.ChildListFilter(activeYear, null, null, null, null, 0, 100))
                .items();
        assertThat(underActive).extracting(i -> i.id()).doesNotContain(childId);
    }

    @Test
    @DisplayName("CHD-11, AUTH-02: PHT điểm trường B không thấy trẻ năm sau của A")
    void plannedListFollowsCampusScope() {
        long childId = enrollPlannedAn();

        var items = childListService
                .searchChildren(
                        vicePrincipalOfB,
                        new com.carenest.dto.child.ChildListFilter(plannedYear, null, null, null, null, 0, 100))
                .items();
        assertThat(items).extracting(i -> i.id()).doesNotContain(childId);
    }

    @Autowired
    private com.carenest.service.child.ChildEnrollmentImportService importService;

    @Test
    @DisplayName("CHD-11: nhập Excel vào năm sau ⇒ mỗi trẻ một đăng ký chưa có lớp")
    void importIntoPlannedYearCreatesPendingAdmissions() {
        EnrollChildRequest r1 =
                request("Bé Excel Một", "2023-04-10", null, null, newGuardian("0915000021", "001500000021"));
        EnrollChildRequest r2 =
                request("Bé Excel Hai", "2023-05-11", null, null, newGuardian("0915000022", "001500000022"));
        var body = new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                List.of(r1, r2), campusA, plannedYear, LocalDate.of(2027, 9, 6));
        long before = count("SELECT count(*) FROM children");

        expectCode(() -> importService.importRows(principal, body), ApiCode.PREP_YEAR_NOT_OPEN);
        assertThat(count("SELECT count(*) FROM children")).isEqualTo(before);

        openPrep("2026-10-29");
        entityManager.clear(); // năm học đã nạp vào persistence context ở lần gọi 2412 trước
        var result = importService.importRows(principal, body);

        assertThat(result.created()).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM children")).isEqualTo(before + 2);
        for (Long id : result.ids()) {
            assertThat(count(
                            "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND status = 'PENDING'"
                                    + " AND campus_id = ? AND classroom_id IS NULL",
                            id,
                            campusA))
                    .isEqualTo(1);
        }
    }

    // G94-180/CHD-02 (user chốt 2026-11-22): điểm trường tiếp nhận phải có trong danh sách điểm trường của năm (V151)
    private static final String CAMPUS_NOT_IN_YEAR =
            "Điểm trường không có trong năm học đã chọn. Chọn điểm trường khác";

    private void onlyCampusesInYear(long yearId, long... campusIds) {
        jdbc.update("DELETE FROM school_year_campus_members WHERE school_year_id = ?", yearId);
        for (long campusId : campusIds) {
            jdbc.update(
                    "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address)"
                            + " SELECT ?, id, code, name, COALESCE(short_name, ''), COALESCE(address, '')"
                            + " FROM campuses WHERE id = ?",
                    yearId,
                    campusId);
        }
    }

    @Test
    @DisplayName("G94-180/CHD-02: tiếp nhận năm sau vào điểm trường không có trong năm ⇒ 2125 ở ô campusId")
    void plannedIntakeRejectsCampusOutsideYear() {
        openPrep("2026-10-29");
        onlyCampusesInYear(plannedYear, campusA);
        long before = count("SELECT count(*) FROM children");

        expectField(
                () -> enrollmentService.enroll(
                        principal,
                        plannedRequest(
                                "Trẻ Ngoài Năm", null, campusB, null, newGuardian("0915000031", "001500000031"))),
                ApiCode.CAMPUS_NOT_IN_YEAR,
                "campusId",
                CAMPUS_NOT_IN_YEAR);
        assertThat(count("SELECT count(*) FROM children")).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM planned_admissions")).isZero();
    }

    @Test
    @DisplayName("G94-180/CHD-02: nhập Excel vào năm sau ở điểm trường ngoài năm ⇒ cả lô lỗi, không tạo hồ sơ")
    void importRejectsCampusOutsideYear() {
        openPrep("2026-10-29");
        onlyCampusesInYear(plannedYear, campusA);
        EnrollChildRequest row =
                request("Bé Excel Ngoài Năm", "2023-04-10", null, null, newGuardian("0915000032", "001500000032"));
        var body = new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                List.of(row), campusB, plannedYear, LocalDate.of(2027, 9, 6));
        long before = count("SELECT count(*) FROM children");

        expectField(
                () -> importService.importRows(principal, body),
                ApiCode.CAMPUS_NOT_IN_YEAR,
                "campusId",
                CAMPUS_NOT_IN_YEAR);
        assertThat(count("SELECT count(*) FROM children")).isEqualTo(before);
    }

    @Test
    @DisplayName("G94-180/CHD-02: tiếp nhận năm đang hoạt động vào điểm trường không có trong năm ⇒ 2125")
    void activeIntakeRejectsCampusOutsideYear() {
        onlyCampusesInYear(activeYear, campusA);

        expectField(
                () -> enrollmentService.enroll(
                        principal,
                        request(
                                "Trẻ Năm Nay Ngoài",
                                "2023-08-01",
                                null,
                                campusB,
                                newGuardian("0915000033", "001500000033"))),
                ApiCode.CAMPUS_NOT_IN_YEAR,
                "campusId",
                CAMPUS_NOT_IN_YEAR);
    }

    @Test
    @DisplayName("G94-180/CHD-02: năm chưa có danh sách điểm trường riêng ⇒ nhận như cũ")
    void yearWithoutOwnListAcceptsAnyActiveCampus() {
        jdbc.update("DELETE FROM school_year_campus_members WHERE school_year_id = ?", activeYear);

        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                request(
                        "Trẻ Năm Cũ Danh Sách",
                        "2023-08-01",
                        null,
                        campusB,
                        newGuardian("0915000034", "001500000034")));

        assertThat(response.child().status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
    }

    @Test
    @DisplayName("G94-180/CHD-02: PHT của điểm trường bị bỏ khỏi năm sau ⇒ lỗi 2125 ở ô điểm trường, không 403")
    void vicePrincipalOfRemovedCampusGetsYearError() {
        openPrep("2026-10-29");
        onlyCampusesInYear(plannedYear, campusA);

        expectField(
                () -> enrollmentService.enroll(
                        vicePrincipalOfB,
                        plannedRequest("Trẻ PHT B", null, null, null, newGuardian("0915000035", "001500000035"))),
                ApiCode.CAMPUS_NOT_IN_YEAR,
                "campusId",
                CAMPUS_NOT_IN_YEAR);
    }

    @Test
    @DisplayName(
            "G94-180/CHD-11: tiếp nhận lại năm sau, điểm trường cũ không có trong năm, không chọn ⇒ 2125, trẻ vẫn nghỉ")
    void readmitRejectsOldCampusOutsideYear() {
        long childId = withdrawnPlannedChild("Trẻ Về Lại Cũ", "0915000036", "001500000036");
        onlyCampusesInYear(plannedYear, campusB);

        expectField(
                () -> intakeService.readmit(
                        principal, childId, new ReadmitChildRequest("Gia đình đổi ý", plannedYear, null)),
                ApiCode.CAMPUS_NOT_IN_YEAR,
                "campusId",
                CAMPUS_NOT_IN_YEAR);
        assertThat(count("SELECT count(*) FROM children WHERE id = ? AND status = 'LEFT'", childId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("G94-180/CHD-11: tiếp nhận lại chọn điểm trường của năm ⇒ trẻ và đăng ký năm sau ở điểm trường đó")
    void readmitIntoChosenCampus() {
        long childId = withdrawnPlannedChild("Trẻ Về Lại Mới", "0915000037", "001500000037");
        onlyCampusesInYear(plannedYear, campusB);

        ChildViewResponse view = intakeService.readmit(
                principal, childId, new ReadmitChildRequest("Gia đình đổi ý", plannedYear, null, campusB));

        assertThat(view.status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(count("SELECT count(*) FROM children WHERE id = ? AND intake_campus_id = ?", childId, campusB))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND school_year_id = ?"
                                + " AND campus_id = ? AND status = 'PENDING'",
                        childId,
                        plannedYear,
                        campusB))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("G94-180/CHD-11: PHT tiếp nhận lại chọn điểm trường ngoài phạm vi ⇒ lỗi ô campusId")
    void readmitRejectsChosenCampusOutOfScope() {
        long childId = withdrawnPlannedChild("Trẻ Về Lại Phạm Vi", "0915000038", "001500000038");

        expectField(
                () -> intakeService.readmit(
                        vicePrincipal, childId, new ReadmitChildRequest("Gia đình đổi ý", plannedYear, null, campusB)),
                ApiCode.INVALID_REQUEST_DATA,
                "campusId",
                "Chỉ tiếp nhận trẻ vào điểm trường bạn được giao");
    }

    @Test
    @DisplayName(
            "G94-180/CHD-13: nhập Excel trẻ chưa đủ 3 tháng tuổi tại ngày tiếp nhận ⇒ lỗi ngày sinh, không tạo hồ sơ")
    void importRejectsChildUnderThreeMonths() {
        EnrollChildRequest row =
                request("Bé Excel Sơ Sinh", "2026-07-30", null, null, newGuardian("0915000041", "001500000041"));
        var body = new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                List.of(row), campusA, null, TODAY);
        long before = count("SELECT count(*) FROM children");

        expectField(
                () -> importService.importRows(principal, body),
                ApiCode.INVALID_REQUEST_DATA,
                "dateOfBirth",
                "Trẻ phải đủ 3 tháng tuổi tại ngày tiếp nhận");
        assertThat(count("SELECT count(*) FROM children")).isEqualTo(before);
    }

    @Test
    @DisplayName("G94-180/CHD-13: tiếp nhận năm sau vào lớp khác nhóm tuổi của trẻ ⇒ vẫn nhận")
    void plannedClassOfOtherAgeGroupIsAccepted() {
        openPrep("2026-10-29");
        EnrollChildRequest base = request(
                "Trẻ Khác Nhóm Tuổi", "2025-06-01", plannedClass, campusA, newGuardian("0915000042", "001500000042"));
        ChildEnrollmentResponse response = enrollmentService.enroll(
                principal,
                new EnrollChildRequest(
                        base.child(),
                        base.declaration(),
                        plannedClass,
                        campusA,
                        plannedYear,
                        LocalDate.of(2027, 9, 6)));

        assertThat(count(
                        "SELECT count(*) FROM planned_admissions WHERE child_id = ? AND classroom_id = ?",
                        response.child().id(),
                        plannedClass))
                .isEqualTo(1);
    }

    private long enrollPlannedAn() {
        openPrep("2026-10-29");
        return enrollmentService
                .enroll(
                        principal,
                        plannedRequest(
                                "Bé An Năm Sau",
                                plannedClass,
                                campusA,
                                "2027-09-06",
                                newGuardian("0915000010", "001500000010")))
                .child()
                .id();
    }

    @Test
    @DisplayName(
            "G94-180/CHD-14: tiếp nhận năm đang học phải chọn ngày bắt đầu đi học, từ hôm nay tới cuối năm, là ngày học")
    void currentYearIntakeRequiresSchoolDayStartDate() {
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) "
                        + "VALUES (?, '2026-11-20', '2026-11-20', 'Nghỉ lễ')",
                activeYear);
        EnrollChildRequest base =
                request("Bé Chọn Ngày", "2023-05-10", null, campusA, newGuardian("0915000061", "001500000061"));

        expectField(
                () -> intakeService.prepareEnrollment(principal, withStartDate(base, null)),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Chọn ngày bắt đầu đi học");
        expectField(
                () -> intakeService.prepareEnrollment(principal, withStartDate(base, TODAY.minusDays(1))),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Ngày bắt đầu đi học không được trước hôm nay");
        expectField(
                () -> intakeService.prepareEnrollment(principal, withStartDate(base, LocalDate.of(2027, 6, 1))),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Ngày bắt đầu đi học không được sau ngày kết thúc năm học (31/05/2027)");
        // Thứ 7 và ngày nghỉ của trường
        expectField(
                () -> intakeService.prepareEnrollment(principal, withStartDate(base, LocalDate.of(2026, 10, 31))),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Ngày bắt đầu đi học 31/10/2026 là thứ Bảy. Chọn ngày từ thứ 2 tới thứ 6.");
        expectField(
                () -> intakeService.prepareEnrollment(principal, withStartDate(base, LocalDate.of(2026, 11, 20))),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Ngày bắt đầu đi học 20/11/2026 trùng ngày nghỉ của trường: Nghỉ lễ (20/11/2026). Chọn ngày học khác.");
        assertThat(intakeService
                        .prepareEnrollment(principal, withStartDate(base, LocalDate.of(2026, 11, 2)))
                        .startDate())
                .isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    @DisplayName(
            "G94-180/CHD-14: tiếp nhận có lớp, ngày bắt đầu tương lai ⇒ lớp tính từ ngày đó, trước ngày đó lớp chưa có trẻ")
    void intakeWithFutureStartDateJoinsClassOnThatDay() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        long childId = enrollmentService
                .enroll(
                        principal,
                        withStartDate(
                                request(
                                        "Bé Vào Lớp Sau",
                                        "2023-05-10",
                                        classA,
                                        campusA,
                                        newGuardian("0915000062", "001500000062")),
                                monday))
                .child()
                .id();

        assertThat(jdbc.queryForObject("SELECT start_date FROM children WHERE id = ?", LocalDate.class, childId))
                .isEqualTo(monday);
        assertThat(jdbc.queryForObject(
                        "SELECT valid_from FROM enrollments WHERE child_id = ?", LocalDate.class, childId))
                .isEqualTo(monday);
        assertThat(enrollmentRepository.findActiveByClassroomIds(List.of(classA), LocalDate.of(2026, 10, 30)))
                .extracting(com.carenest.entity.child.Enrollment::getChildId)
                .doesNotContain(childId);
        assertThat(enrollmentRepository.findActiveByClassroomIds(List.of(classA), monday))
                .extracting(com.carenest.entity.child.Enrollment::getChildId)
                .contains(childId);
    }

    @Test
    @DisplayName("G94-180/CHD-14: mở lại hồ sơ, nhập Excel vào năm đang học phải chọn ngày bắt đầu đi học")
    void readmitAndImportIntoCurrentYearRequireStartDate() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        long left = child("Trẻ Mở Lại Có Ngày", "2023-05-10", "ACTIVE", null);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01', '2026-10-15')",
                left,
                classA);
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = '2026-10-16', left_reason = 'Chuyển trường' WHERE id = ?",
                left);
        expectField(
                () -> intakeService.readmit(principal, left, new ReadmitChildRequest("Quay lại", null, null)),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Chọn ngày bắt đầu đi học");

        intakeService.readmit(principal, left, new ReadmitChildRequest("Quay lại", activeYear, monday));
        assertThat(jdbc.queryForObject("SELECT start_date FROM children WHERE id = ?", LocalDate.class, left))
                .isEqualTo(monday);
        assertThat(jdbc.queryForObject("SELECT status FROM children WHERE id = ?", String.class, left))
                .isEqualTo("PENDING_PLACEMENT");

        EnrollChildRequest row =
                request("Bé Excel Có Ngày", "2023-05-10", null, null, newGuardian("0915000063", "001500000063"));
        expectField(
                () -> importService.importRows(
                        principal,
                        new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                                List.of(row), campusA, null, null)),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Chọn ngày bắt đầu đi học");
        importService.importRows(
                principal,
                new com.carenest.service.child.ChildEnrollmentImportService.ImportRequest(
                        List.of(row), campusA, activeYear, monday));
        assertThat(count(
                        "SELECT count(*) FROM children WHERE full_name = 'Bé Excel Có Ngày' AND start_date = ?::date",
                        monday.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("G94-180/CHD-14: tiếp nhận năm sau với ngày bắt đầu rơi vào chủ nhật ⇒ lỗi ô startDate")
    void plannedIntakeRejectsNonSchoolStartDate() {
        openPrep("2026-10-29");
        expectField(
                () -> intakeService.prepareEnrollment(
                        principal,
                        plannedRequest(
                                "Trẻ Năm Sau Chủ Nhật",
                                null,
                                campusA,
                                "2027-09-12",
                                newGuardian("0915000064", "001500000064"))),
                ApiCode.INVALID_REQUEST_DATA,
                "startDate",
                "Ngày bắt đầu đi học 12/09/2027 là Chủ nhật. Chọn ngày từ thứ 2 tới thứ 6.");
    }

    @Test
    @DisplayName(
            "G94-180/CHD-13: tiếp nhận năm đang học vào lớp khác nhóm tuổi, kể cả trẻ không thuộc nhóm nào ⇒ vẫn nhận")
    void currentYearIntakeAcceptsClassOfOtherAgeGroup() {
        // 64 tháng tại 01/09/2026 (MG 5–6); Lớp A1 là MG 3–4
        long older = enrollmentService
                .enroll(
                        principal,
                        withStartDate(
                                request(
                                        "Bé Lớp Khác Nhóm",
                                        "2021-05-01",
                                        classA,
                                        campusA,
                                        newGuardian("0915000065", "001500000065")),
                                TODAY))
                .child()
                .id();
        // 74 tháng tại 01/09/2026: không thuộc nhóm nào của năm
        long noGroup = enrollmentService
                .enroll(
                        principal,
                        withStartDate(
                                request(
                                        "Bé Ngoài Nhóm Tuổi",
                                        "2020-06-20",
                                        classB,
                                        campusB,
                                        newGuardian("0915000066", "001500000066")),
                                TODAY))
                .child()
                .id();

        assertThat(jdbc.queryForObject("SELECT classroom_id FROM enrollments WHERE child_id = ?", Long.class, older))
                .isEqualTo(classA);
        assertThat(jdbc.queryForObject("SELECT classroom_id FROM enrollments WHERE child_id = ?", Long.class, noGroup))
                .isEqualTo(classB);
    }

    private static EnrollChildRequest withStartDate(EnrollChildRequest base, LocalDate startDate) {
        return new EnrollChildRequest(
                base.child(), base.declaration(), base.classId(), base.campusId(), null, startDate);
    }

    private void openPrep(String date) {
        jdbc.update("UPDATE school_years SET prep_date = ?::date WHERE id = ?", date, plannedYear);
    }

    private EnrollChildRequest plannedRequest(
            String fullName, Long classId, Long campusId, String startDate, GuardianInput... guardians) {
        // 40 tháng tại ngày bắt đầu năm sau (06/09/2027) ⇒ đúng nhóm của plannedClass (ag-3, 36–48 tháng)
        EnrollChildRequest base = request(fullName, "2024-04-10", classId, campusId, guardians);
        return new EnrollChildRequest(
                base.child(),
                base.declaration(),
                classId,
                campusId,
                plannedYear,
                startDate == null ? null : LocalDate.parse(startDate));
    }

    private static EnrollChildRequest request(
            String fullName, String dateOfBirth, Long classId, Long campusId, GuardianInput... guardians) {
        return new EnrollChildRequest(
                new EnrollmentChildInput(
                        fullName,
                        dateOfBirth,
                        "FEMALE",
                        java.util.stream.IntStream.range(0, guardians.length)
                                .mapToObj(i -> {
                                    GuardianInput g = guardians[i];
                                    return new GuardianInput(
                                            g.fullName(), g.relation(), g.phone(), g.idNumber(), g.email(), i == 0);
                                })
                                .toList(),
                        new ChildProfileInput(
                                null,
                                "NO",
                                39,
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
                campusId,
                null,
                TODAY);
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
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id,"
                        + " linked_at) VALUES (?, 0, ?, ?, ?, ?, ?, CASE WHEN CAST(? AS BIGINT) IS NULL THEN NULL"
                        + " ELSE now() END)",
                childId,
                fullName,
                relationship,
                phone,
                citizenId,
                userId,
                userId);
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId, int capacity) {
        long room = insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campusId
                + ", 'R" + ROOM_SEQ.incrementAndGet() + "', 'Phòng " + name + "', 'CLASS', 30) RETURNING id");
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
