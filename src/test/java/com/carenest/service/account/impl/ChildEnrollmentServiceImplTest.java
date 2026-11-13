package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.ChildEnrollmentResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.EnrollmentPhotos;
import com.carenest.dto.child.GuardianAccountResultResponse;
import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.GuardianRecord;
import com.carenest.dto.child.LinkedChildResponse;
import com.carenest.dto.child.ParentAccountLookupResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AuthStateStore;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildPhotoService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianAccountResult;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SmsStatus;
import com.carenest.utils.enums.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit test service bằng Mockito (không DB, Keycloak giả): tra tài khoản phụ huynh, kiểm trùng danh tính, lưu form tiếp
 * nhận kèm tạo/liên kết tài khoản. Dữ liệu giả.
 */
@ExtendWith(MockitoExtension.class)
class ChildEnrollmentServiceImplTest {

    private static final long CAMPUS_A = 1L;
    private static final long CAMPUS_B = 2L;
    private static final long CHILD_ID = 101L;
    private static final String MOTHER_PHONE = "0900000001";
    private static final String FATHER_PHONE = "0900000002";
    private static final String MOTHER_ID = "001200000001";
    private static final String FATHER_ID = "001200000002";
    private static final String OTHER_ID = "001200000099";
    private static final String TEMP_PASS = "Tmp7kQwxy3";
    private static final CurrentUser PRINCIPAL = new CurrentUser(
            2L,
            "kc-2",
            "ht",
            "HT giả",
            null,
            Set.of(CareNestRole.PRINCIPAL),
            Set.of(Permission.CHILD_ENROLL, Permission.PARENT_ACCOUNT_MANAGE));
    private static final CurrentUser VICE = new CurrentUser(
            3L,
            "kc-3",
            "vp",
            "PHT giả",
            null,
            Set.of(CareNestRole.VICE_PRINCIPAL),
            Set.of(Permission.CHILD_ENROLL, Permission.PARENT_ACCOUNT_MANAGE));
    // PHT có quyền tiếp nhận nhưng HT đã tắt quyền quản lý tài khoản phụ huynh (P-18)
    private static final CurrentUser VICE_WITHOUT_ACCOUNTS = new CurrentUser(
            4L, "kc-4", "vp2", "PHT 2 giả", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of(Permission.CHILD_ENROLL));
    private static final EnrollChildRequest REQUEST = new EnrollChildRequest(null, null, null, CAMPUS_A);
    private static final EnrollmentPhotos PHOTOS = new EnrollmentPhotos(null, Map.of());
    private static final Map<String, List<MultipartFile>> FILES =
            Map.of("childPhoto", List.of(new MockMultipartFile("childPhoto", "be.jpg", "image/jpeg", new byte[] {1})));

    @Mock
    private ChildIntakeService childIntakeService;

    @Mock
    private ChildPhotoService childPhotoService;

    @Mock
    private GuardianChildLinkService guardianChildLinkService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private StaffAccessPolicy staffAccessPolicy;

    @Mock
    private IdentityProviderClient identityProviderClient;

    @Mock
    private AuthStateStore authStateStore;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TemporaryPasswordGenerator passwordGenerator;

    @Mock
    private ParentSmsSender smsSender;

    private ChildEnrollmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChildEnrollmentServiceImpl(
                childIntakeService,
                childPhotoService,
                guardianChildLinkService,
                userAccountRepository,
                staffAccessPolicy,
                new AccountLifecycleOperations(
                        userAccountRepository,
                        identityProviderClient,
                        authStateStore,
                        new TransactionTemplate(transactionManager)),
                passwordGenerator,
                smsSender);
    }

    @Nested
    @DisplayName("findParentAccount")
    class FindParentAccount {

        @Test
        @DisplayName("G94-180/AUTH-18: CCCD không đủ 12 số ⇒ null, không tra DB")
        void invalidCitizenIdReturnsNull() {
            assertThat(service.findParentAccount(PRINCIPAL, "12345")).isNull();

            verifyNoInteractions(userAccountRepository, guardianChildLinkService, childIntakeService);
        }

        @Test
        @DisplayName("CCCD chưa có tài khoản ⇒ null")
        void unknownCitizenIdReturnsNull() {
            given(userAccountRepository.findByCitizenId(MOTHER_ID)).willReturn(Optional.empty());

            assertThat(service.findParentAccount(PRINCIPAL, MOTHER_ID)).isNull();
            verifyNoInteractions(guardianChildLinkService);
        }

        @Test
        @DisplayName("CCCD thuộc tài khoản không phải phụ huynh ⇒ null (không lộ tài khoản nhân sự)")
        void staffAccountIsNotReturned() {
            UserAccount teacher = UserAccount.create("kc-63", "gv.gia", null, "GV giả", CareNestRole.TEACHER);
            ReflectionTestUtils.setField(teacher, "id", 63L);
            ReflectionTestUtils.setField(teacher, "citizenId", MOTHER_ID);
            given(userAccountRepository.findByCitizenId(MOTHER_ID)).willReturn(Optional.of(teacher));

            assertThat(service.findParentAccount(PRINCIPAL, MOTHER_ID)).isNull();
            verifyNoInteractions(guardianChildLinkService);
        }

        @Test
        @DisplayName("HT: CCCD có khoảng trắng ⇒ trả tài khoản, mọi trẻ chưa nghỉ, gợi ý quan hệ đã ghi")
        void principalSeesAllChildrenNotLeft() {
            UserAccount parent = parent(60L, MOTHER_PHONE, MOTHER_ID, "Mẹ giả");
            ReflectionTestUtils.setField(parent, "email", "me.gia@example.test");
            given(userAccountRepository.findByCitizenId(MOTHER_ID)).willReturn(Optional.of(parent));
            given(guardianChildLinkService.findChildIdsOfGuardian(60L)).willReturn(List.of(201L, 202L, 203L));
            given(childIntakeService.findChildSummaries(List.of(201L, 202L, 203L)))
                    .willReturn(List.of(
                            summary(201L, ChildStatus.ACTIVE, CAMPUS_A),
                            summary(202L, ChildStatus.PENDING_PLACEMENT, CAMPUS_B),
                            summary(203L, ChildStatus.LEFT, CAMPUS_A)));
            given(staffAccessPolicy.managesWholeSchool(PRINCIPAL)).willReturn(true);
            given(childIntakeService.findRelationshipOfAccount(60L, MOTHER_PHONE))
                    .willReturn(Optional.of(GuardianRelationship.MOTHER));

            ParentAccountLookupResponse response = service.findParentAccount(PRINCIPAL, "0012 0000 0001");

            assertThat(response)
                    .isEqualTo(new ParentAccountLookupResponse(
                            60L,
                            "Mẹ giả",
                            MOTHER_PHONE,
                            MOTHER_ID,
                            "me.gia@example.test",
                            UserStatus.ACTIVE,
                            List.of(
                                    new LinkedChildResponse(201L, "TR201", "Bé giả 201", "Lớp A"),
                                    new LinkedChildResponse(202L, "TR202", "Bé giả 202", "Lớp A")),
                            0,
                            GuardianRelationship.MOTHER));
            verify(staffAccessPolicy, never()).managedCampusIds(any());
        }

        @Test
        @DisplayName(
                "G94-180/AUTH-18: PHT chỉ thấy trẻ điểm trường mình, trẻ ngoài phạm vi chỉ đếm, trẻ đã nghỉ bỏ qua")
        void viceSeesOnlyChildrenOfOwnCampus() {
            given(userAccountRepository.findByCitizenId(MOTHER_ID))
                    .willReturn(Optional.of(parent(60L, MOTHER_PHONE, MOTHER_ID, "Mẹ giả")));
            given(guardianChildLinkService.findChildIdsOfGuardian(60L)).willReturn(List.of(201L, 202L, 203L, 204L));
            given(childIntakeService.findChildSummaries(List.of(201L, 202L, 203L, 204L)))
                    .willReturn(List.of(
                            summary(201L, ChildStatus.ACTIVE, CAMPUS_A),
                            summary(202L, ChildStatus.ACTIVE, CAMPUS_B),
                            summary(203L, ChildStatus.PENDING_PLACEMENT, null),
                            summary(204L, ChildStatus.LEFT, CAMPUS_A)));
            given(staffAccessPolicy.managesWholeSchool(VICE)).willReturn(false);
            given(staffAccessPolicy.managedCampusIds(VICE)).willReturn(Set.of(CAMPUS_A));
            given(childIntakeService.findRelationshipOfAccount(60L, MOTHER_PHONE))
                    .willReturn(Optional.empty());

            ParentAccountLookupResponse response = service.findParentAccount(VICE, MOTHER_ID);

            assertThat(response.children()).extracting(LinkedChildResponse::id).containsExactly(201L);
            assertThat(response.otherChildrenCount()).isEqualTo(2);
            assertThat(response.relation()).isNull();
        }
    }

    @Nested
    @DisplayName("checkGuardians")
    class CheckGuardians {

        @Test
        @DisplayName("G94-180/AUTH-18: quá 3 phụ huynh ⇒ INVALID_REQUEST_DATA ô guardians, không tra tài khoản")
        void moreThanThreeGuardiansIsRejected() {
            GuardianInput g = input(MOTHER_PHONE, MOTHER_ID, null);
            GuardianCheckRequest request = new GuardianCheckRequest(List.of(g, g, g, g));

            assertThatThrownBy(() -> service.checkGuardians(VICE, request))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                        assertThat(((GlobalException) ex).getFieldErrors())
                                .containsExactly(new FieldErrorResponse("guardians", "Mỗi trẻ tối đa 3 phụ huynh"));
                    });
            verifyNoInteractions(userAccountRepository, childIntakeService);
        }

        @Test
        @DisplayName("Request rỗng ⇒ không lỗi, không tra tài khoản")
        void emptyRequestPasses() {
            service.checkGuardians(VICE, null);
            service.checkGuardians(VICE, new GuardianCheckRequest(null));

            verifyNoInteractions(userAccountRepository);
        }

        @Test
        @DisplayName("Người mới: tra theo SĐT, CCCD đã chuẩn hóa (+84, khoảng trắng) ⇒ không lỗi")
        void newPersonPassesWithNormalizedLookup() {
            service.checkGuardians(
                    VICE, new GuardianCheckRequest(List.of(input("+84 900 000 001", "0012 0000 0001", null))));

            verify(userAccountRepository).findAllByCitizenIdIn(Set.of(MOTHER_ID));
            verify(userAccountRepository).findAllByUsernameIn(Set.of(MOTHER_PHONE));
            verify(childIntakeService).findGuardianRecords(Set.of(MOTHER_PHONE), Set.of(MOTHER_ID));
        }

        @Test
        @DisplayName("Ô sai định dạng bỏ qua (form tự báo lỗi định dạng) ⇒ không lỗi trùng")
        void malformedFieldsAreSkipped() {
            service.checkGuardians(VICE, new GuardianCheckRequest(List.of(input("123", MOTHER_ID, null))));

            verify(userAccountRepository, never()).findAllByUsernameIn(any());
        }

        @Test
        @DisplayName("AUTH-18: CCCD đã ghi cho phụ huynh có SĐT khác (trên hồ sơ trẻ khác) ⇒ lỗi ô idNumber")
        void citizenIdOfAnotherPhoneConflicts() {
            given(childIntakeService.findGuardianRecords(Set.of(MOTHER_PHONE), Set.of(MOTHER_ID)))
                    .willReturn(List.of(new GuardianRecord(201L, "0900000003", MOTHER_ID, null)));

            assertConflict(
                    () -> service.checkGuardians(
                            VICE, new GuardianCheckRequest(List.of(input(MOTHER_PHONE, MOTHER_ID, null)))),
                    new FieldErrorResponse("guardians.0.idNumber", ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN));
        }

        @Test
        @DisplayName("AUTH-18: SĐT đã là tài khoản phụ huynh có CCCD khác ⇒ lỗi ô phone của đúng phụ huynh")
        void phoneOfAnotherCitizenIdConflicts() {
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE, FATHER_PHONE)))
                    .willReturn(List.of(parent(62L, FATHER_PHONE, OTHER_ID, "Người khác giả")));

            assertConflict(
                    () -> service.checkGuardians(
                            VICE,
                            new GuardianCheckRequest(List.of(
                                    input(MOTHER_PHONE, MOTHER_ID, true), input(FATHER_PHONE, FATHER_ID, false)))),
                    new FieldErrorResponse("guardians.1.phone", ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID));
        }

        @Test
        @DisplayName("AUTH-18: cùng người trên hồ sơ anh/chị (đã có tài khoản) ⇒ không lỗi")
        void samePersonOnSiblingProfilePasses() {
            given(userAccountRepository.findAllByCitizenIdIn(Set.of(MOTHER_ID)))
                    .willReturn(List.of(parent(60L, MOTHER_PHONE, MOTHER_ID, "Mẹ giả")));
            given(childIntakeService.findGuardianRecords(Set.of(MOTHER_PHONE), Set.of(MOTHER_ID)))
                    .willReturn(List.of(new GuardianRecord(201L, MOTHER_PHONE, MOTHER_ID, 60L)));

            service.checkGuardians(VICE, new GuardianCheckRequest(List.of(input(MOTHER_PHONE, MOTHER_ID, null))));
        }

        @Test
        @DisplayName("G94-180/AUTH-18: người nhận tài khoản dùng SĐT là tên đăng nhập nhân sự ⇒ lỗi ô phone (1130)")
        void staffUsernameConflictsWhenCreatingAccount() {
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE)))
                    .willReturn(List.of(staff(63L, MOTHER_PHONE)));

            assertConflict(
                    () -> service.checkGuardians(
                            VICE, new GuardianCheckRequest(List.of(input(MOTHER_PHONE, MOTHER_ID, null)))),
                    new FieldErrorResponse("guardians.0.phone", ApiCode.PHONE_OF_STAFF_ACCOUNT.getDefaultDesc()));
        }

        @Test
        @DisplayName("P-18: không có quyền quản lý tài khoản ⇒ không tạo tài khoản nên SĐT nhân sự không chặn")
        void staffUsernameAllowedWithoutAccountPermission() {
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE)))
                    .willReturn(List.of(staff(63L, MOTHER_PHONE)));

            service.checkGuardians(
                    VICE_WITHOUT_ACCOUNTS, new GuardianCheckRequest(List.of(input(MOTHER_PHONE, MOTHER_ID, null))));
        }

        @Test
        @DisplayName("SĐT nhân sự ở phụ huynh không nhận tài khoản ⇒ không chặn")
        void staffUsernameAllowedForNonRecipient() {
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE, FATHER_PHONE)))
                    .willReturn(List.of(staff(63L, FATHER_PHONE)));

            service.checkGuardians(
                    VICE,
                    new GuardianCheckRequest(
                            List.of(input(MOTHER_PHONE, MOTHER_ID, true), input(FATHER_PHONE, FATHER_ID, false))));
        }
    }

    @Nested
    @DisplayName("enroll / enrollWithPhotos")
    class Enroll {

        @Test
        @DisplayName(
                "G94-180/AUTH-18, AUTH-20: người nhận chưa có tài khoản ⇒ tạo PARENT, ghi hồ sơ + liên kết, sau đó SMS")
        void createsAccountThenSendsSms() {
            GuardianDraft mother = recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả");
            EnrollmentDraft draft = givenDraft(VICE, FILES, mother);
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any())).willReturn("kc-new");
            givenSaveAssignsId(70L);
            given(childIntakeService.createEnrolledChild(VICE, draft, Map.of(0, 70L)))
                    .willReturn(CHILD_ID);
            given(smsSender.sendActivation(any(), eq(TEMP_PASS))).willReturn(SmsStatus.SENT);
            ChildViewResponse view = givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE, REQUEST, FILES);

            ArgumentCaptor<NewIdentityUser> identity = ArgumentCaptor.forClass(NewIdentityUser.class);
            ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
            InOrder order = inOrder(
                    identityProviderClient, userAccountRepository, childIntakeService, childPhotoService, smsSender);
            order.verify(identityProviderClient).createUser(identity.capture());
            order.verify(identityProviderClient).assignRealmRole("kc-new", "PARENT");
            order.verify(userAccountRepository).saveAndFlush(saved.capture());
            order.verify(childIntakeService).createEnrolledChild(VICE, draft, Map.of(0, 70L));
            order.verify(childPhotoService).attachToEnrollment(CHILD_ID, PHOTOS);
            order.verify(smsSender).sendActivation(saved.getValue(), TEMP_PASS);
            order.verify(childIntakeService).getChildView(CHILD_ID);
            assertThat(identity.getValue()).isEqualTo(new NewIdentityUser(MOTHER_PHONE, null, "Mẹ giả", TEMP_PASS));
            assertThat(saved.getValue().getUsername()).isEqualTo(MOTHER_PHONE);
            assertThat(saved.getValue().getCitizenId()).isEqualTo(MOTHER_ID);
            assertThat(saved.getValue().getEmail()).isEqualTo("me.gia@example.test");
            assertThat(saved.getValue().getPrimaryRole()).isEqualTo(CareNestRole.PARENT);
            assertThat(response.child()).isSameAs(view);
            assertThat(response.guardianAccounts())
                    .containsExactly(new GuardianAccountResultResponse(
                            "Mẹ giả", MOTHER_PHONE, GuardianAccountResult.CREATED, false, SmsStatus.SENT));
            assertThat(response.toString()).doesNotContain(TEMP_PASS);
        }

        @Test
        @DisplayName("enroll không kèm ảnh ⇒ kiểm ảnh với danh sách tệp rỗng")
        void enrollWithoutPhotosUsesEmptyFiles() {
            EnrollmentDraft draft =
                    givenDraft(VICE_WITHOUT_ACCOUNTS, Map.of(), recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(childIntakeService.createEnrolledChild(VICE_WITHOUT_ACCOUNTS, draft, Map.of()))
                    .willReturn(CHILD_ID);
            givenView();

            ChildEnrollmentResponse response = service.enroll(VICE_WITHOUT_ACCOUNTS, REQUEST);

            verify(childPhotoService).validatePhotos(1, Map.of());
            assertThat(response.guardianAccounts()).hasSize(1);
        }

        @Test
        @DisplayName("L-15: SMS gửi lỗi ⇒ hồ sơ và tài khoản vẫn lưu, kết quả CREATED kèm FAILED")
        void smsFailureKeepsEnrollment() {
            EnrollmentDraft draft = givenDraft(VICE, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any())).willReturn("kc-new");
            givenSaveAssignsId(70L);
            given(childIntakeService.createEnrolledChild(VICE, draft, Map.of(0, 70L)))
                    .willReturn(CHILD_ID);
            given(smsSender.sendActivation(any(), eq(TEMP_PASS))).willReturn(SmsStatus.FAILED);
            givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE, REQUEST, FILES);

            assertThat(response.guardianAccounts().getFirst().result()).isEqualTo(GuardianAccountResult.CREATED);
            assertThat(response.guardianAccounts().getFirst().smsStatus()).isEqualTo(SmsStatus.FAILED);
            verify(identityProviderClient, never()).deleteUser(anyString());
        }

        @Test
        @DisplayName(
                "G94-180/AUTH-20 (C-2): CCCD đã có tài khoản ⇒ chỉ liên kết, hồ sơ ghi họ tên/SĐT/email theo tài khoản")
        void existingParentByCitizenIdIsLinkedWithAccountInfo() {
            GuardianDraft mother = recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả (form)");
            givenDraft(VICE, FILES, mother);
            UserAccount account = parent(60L, "0900000009", MOTHER_ID, "Mẹ giả (tài khoản)");
            ReflectionTestUtils.setField(account, "email", "tk.gia@example.test");
            given(userAccountRepository.findAllByCitizenIdIn(Set.of(MOTHER_ID))).willReturn(List.of(account));
            given(userAccountRepository.claimCitizenId(60L, MOTHER_ID)).willReturn(1);
            given(childIntakeService.createEnrolledChild(eq(VICE), any(), eq(Map.of(0, 60L))))
                    .willReturn(CHILD_ID);
            givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE, REQUEST, FILES);

            ArgumentCaptor<EnrollmentDraft> resolved = ArgumentCaptor.forClass(EnrollmentDraft.class);
            verify(childIntakeService).createEnrolledChild(eq(VICE), resolved.capture(), eq(Map.of(0, 60L)));
            GuardianDraft written = resolved.getValue().guardians().getFirst();
            assertThat(written.fullName()).isEqualTo("Mẹ giả (tài khoản)");
            assertThat(written.phone()).isEqualTo("0900000009");
            assertThat(written.email()).isEqualTo("tk.gia@example.test");
            assertThat(written.citizenId()).isEqualTo(MOTHER_ID);
            assertThat(written.relationship()).isEqualTo(GuardianRelationship.MOTHER);
            assertThat(response.guardianAccounts())
                    .containsExactly(new GuardianAccountResultResponse(
                            "Mẹ giả (tài khoản)", "0900000009", GuardianAccountResult.LINKED, false, null));
            verifyNoInteractions(identityProviderClient, passwordGenerator, smsSender);
        }

        @Test
        @DisplayName("AUTH-18: tài khoản phụ huynh cũ theo SĐT chưa có CCCD ⇒ liên kết và bổ sung CCCD")
        void legacyParentByPhoneClaimsCitizenId() {
            givenDraft(VICE, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE)))
                    .willReturn(List.of(parent(61L, MOTHER_PHONE, null, "Mẹ giả")));
            given(userAccountRepository.claimCitizenId(61L, MOTHER_ID)).willReturn(1);
            given(childIntakeService.createEnrolledChild(eq(VICE), any(), eq(Map.of(0, 61L))))
                    .willReturn(CHILD_ID);
            givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE, REQUEST, FILES);

            verify(userAccountRepository).claimCitizenId(61L, MOTHER_ID);
            assertThat(response.guardianAccounts().getFirst().result()).isEqualTo(GuardianAccountResult.LINKED);
            verifyNoInteractions(identityProviderClient);
        }

        @Test
        @DisplayName(
                "AUTH-18: trong giao dịch CCCD đã thuộc tài khoản khác (ghi CCCD thất bại) ⇒ GUARDIAN_IDENTITY_CONFLICT")
        void claimFailureAbortsEnrollment() {
            givenDraft(VICE, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE)))
                    .willReturn(List.of(parent(61L, MOTHER_PHONE, null, "Mẹ giả")));
            given(userAccountRepository.claimCitizenId(61L, MOTHER_ID)).willReturn(0);

            assertApiCode(() -> service.enrollWithPhotos(VICE, REQUEST, FILES), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            verify(childIntakeService, never()).createEnrolledChild(any(), any(), anyMap());
            verify(childPhotoService, never()).attachToEnrollment(anyLong(), any());
        }

        @Test
        @DisplayName(
                "G94-180/P-18: không có quyền quản lý tài khoản ⇒ vẫn lưu hồ sơ, người mới chờ kích hoạt, không SMS")
        void withoutAccountPermissionNewParentWaitsForActivation() {
            EnrollmentDraft draft =
                    givenDraft(VICE_WITHOUT_ACCOUNTS, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(childIntakeService.createEnrolledChild(VICE_WITHOUT_ACCOUNTS, draft, Map.of()))
                    .willReturn(CHILD_ID);
            givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE_WITHOUT_ACCOUNTS, REQUEST, FILES);

            assertThat(response.guardianAccounts())
                    .containsExactly(new GuardianAccountResultResponse(
                            "Mẹ giả", MOTHER_PHONE, GuardianAccountResult.NOT_ACTIVATED, false, null));
            verify(childPhotoService).attachToEnrollment(CHILD_ID, PHOTOS);
            verifyNoInteractions(identityProviderClient, passwordGenerator, smsSender);
        }

        @Test
        @DisplayName("G94-180/P-18: không có quyền quản lý tài khoản, CCCD trùng tài khoản ⇒ vẫn liên kết")
        void withoutAccountPermissionExistingParentIsStillLinked() {
            givenDraft(VICE_WITHOUT_ACCOUNTS, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(userAccountRepository.findAllByCitizenIdIn(Set.of(MOTHER_ID)))
                    .willReturn(List.of(parent(60L, MOTHER_PHONE, MOTHER_ID, "Mẹ giả")));
            given(userAccountRepository.claimCitizenId(60L, MOTHER_ID)).willReturn(1);
            given(childIntakeService.createEnrolledChild(eq(VICE_WITHOUT_ACCOUNTS), any(), eq(Map.of(0, 60L))))
                    .willReturn(CHILD_ID);
            givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE_WITHOUT_ACCOUNTS, REQUEST, FILES);

            assertThat(response.guardianAccounts().getFirst().result()).isEqualTo(GuardianAccountResult.LINKED);
            verifyNoInteractions(identityProviderClient, smsSender);
        }

        @Test
        @DisplayName(
                "G94-180/AUTH-20: không có quyền quản lý tài khoản, tài khoản cũ chỉ khớp SĐT ⇒ không liên kết, không ghi CCCD")
        void withoutAccountPermissionLegacyParentByPhoneIsNotTouched() {
            EnrollmentDraft draft =
                    givenDraft(VICE_WITHOUT_ACCOUNTS, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE)))
                    .willReturn(List.of(parent(61L, MOTHER_PHONE, null, "Mẹ giả (tài khoản)")));
            given(childIntakeService.createEnrolledChild(VICE_WITHOUT_ACCOUNTS, draft, Map.of()))
                    .willReturn(CHILD_ID);
            givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE_WITHOUT_ACCOUNTS, REQUEST, FILES);

            // Hồ sơ giữ thông tin trên form, phụ huynh chờ người có quyền kích hoạt
            assertThat(response.guardianAccounts())
                    .containsExactly(new GuardianAccountResultResponse(
                            "Mẹ giả", MOTHER_PHONE, GuardianAccountResult.NOT_ACTIVATED, false, null));
            verify(userAccountRepository, never()).claimCitizenId(anyLong(), any());
            verifyNoInteractions(identityProviderClient, passwordGenerator, smsSender);
        }

        @Test
        @DisplayName("AUTH-18: chỉ người nhận tài khoản được tạo/liên kết và có kết quả; phụ huynh khác giữ như form")
        void onlyRecipientGetsAccount() {
            GuardianDraft mother = recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả");
            GuardianDraft father =
                    new GuardianDraft(1, "Bố giả", GuardianRelationship.FATHER, FATHER_PHONE, FATHER_ID, null, false);
            EnrollmentDraft draft = givenDraft(VICE, FILES, mother, father);
            given(userAccountRepository.findAllByCitizenIdIn(Set.of(MOTHER_ID, FATHER_ID)))
                    .willReturn(List.of(parent(62L, "0900000008", FATHER_ID, "Bố giả (tài khoản)")));
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any())).willReturn("kc-new");
            givenSaveAssignsId(70L);
            given(childIntakeService.createEnrolledChild(VICE, draft, Map.of(0, 70L)))
                    .willReturn(CHILD_ID);
            given(smsSender.sendActivation(any(), eq(TEMP_PASS))).willReturn(SmsStatus.SENT);
            givenView();

            ChildEnrollmentResponse response = service.enrollWithPhotos(VICE, REQUEST, FILES);

            assertThat(response.guardianAccounts())
                    .extracting(GuardianAccountResultResponse::fullName, GuardianAccountResultResponse::result)
                    .containsExactly(tuple("Mẹ giả", GuardianAccountResult.CREATED));
            verify(userAccountRepository, never()).claimCitizenId(anyLong(), anyString());
        }

        @Test
        @DisplayName(
                "AUTH-18: trùng danh tính ⇒ GUARDIAN_IDENTITY_CONFLICT theo ô, chưa tạo user Keycloak, chưa ghi hồ sơ")
        void identityConflictStopsBeforeWriting() {
            givenDraft(VICE, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(childIntakeService.findGuardianRecords(Set.of(MOTHER_PHONE), Set.of(MOTHER_ID)))
                    .willReturn(List.of(new GuardianRecord(201L, "0900000003", MOTHER_ID, null)));

            assertConflict(
                    () -> service.enrollWithPhotos(VICE, REQUEST, FILES),
                    new FieldErrorResponse("guardians.0.idNumber", ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN));
            verifyNoInteractions(identityProviderClient, passwordGenerator, smsSender);
            verify(childIntakeService, never()).createEnrolledChild(any(), any(), anyMap());
        }

        @Test
        @DisplayName("G94-180/AUTH-18: SĐT người nhận là tên đăng nhập nhân sự ⇒ lỗi ô phone, không tạo tài khoản")
        void staffUsernameStopsEnrollment() {
            givenDraft(VICE, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(userAccountRepository.findAllByUsernameIn(Set.of(MOTHER_PHONE)))
                    .willReturn(List.of(staff(63L, MOTHER_PHONE)));

            assertConflict(
                    () -> service.enrollWithPhotos(VICE, REQUEST, FILES),
                    new FieldErrorResponse("guardians.0.phone", ApiCode.PHONE_OF_STAFF_ACCOUNT.getDefaultDesc()));
            verifyNoInteractions(identityProviderClient);
        }

        @Test
        @DisplayName("ADR-0011 (AC-2): ghi hồ sơ lỗi sau khi tạo user Keycloak ⇒ xóa user vừa tạo, không gửi SMS")
        void persistFailureDeletesCreatedIdentity() {
            EnrollmentDraft draft = givenDraft(VICE, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any())).willReturn("kc-new");
            givenSaveAssignsId(70L);
            given(childIntakeService.createEnrolledChild(VICE, draft, Map.of(0, 70L)))
                    .willThrow(GlobalException.of(ApiCode.CHILD_DUPLICATE));

            assertApiCode(() -> service.enrollWithPhotos(VICE, REQUEST, FILES), ApiCode.CHILD_DUPLICATE);
            verify(identityProviderClient).deleteUser("kc-new");
            verify(transactionManager).rollback(any());
            verifyNoInteractions(smsSender);
            verify(childPhotoService, never()).attachToEnrollment(anyLong(), any());
        }

        @Test
        @DisplayName("Keycloak không tạo được user ⇒ lỗi nhà cung cấp danh tính, không ghi DB")
        void identityProviderFailureWritesNothing() {
            givenDraft(VICE, FILES, recipient(0, MOTHER_PHONE, MOTHER_ID, "Mẹ giả"));
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any()))
                    .willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null));

            assertApiCode(() -> service.enrollWithPhotos(VICE, REQUEST, FILES), ApiCode.IDENTITY_PROVIDER_UNAVAILABLE);
            verify(userAccountRepository, never()).saveAndFlush(any());
            verify(childIntakeService, never()).createEnrolledChild(any(), any(), anyMap());
            verifyNoInteractions(smsSender);
        }

        @Test
        @DisplayName("Kiểm form lỗi (phạm vi điểm trường, ô sai) ⇒ ném ra, không kiểm ảnh, không tra tài khoản")
        void prepareErrorStopsEverything() {
            given(childIntakeService.prepareEnrollment(VICE, REQUEST))
                    .willThrow(GlobalException.withFieldErrors(
                            ApiCode.INVALID_REQUEST_DATA,
                            List.of(new FieldErrorResponse("campusId", "Chọn điểm trường"))));

            assertApiCode(() -> service.enrollWithPhotos(VICE, REQUEST, FILES), ApiCode.INVALID_REQUEST_DATA);
            verifyNoInteractions(childPhotoService, userAccountRepository, identityProviderClient, smsSender);
        }
    }

    private EnrollmentDraft givenDraft(
            CurrentUser actor, Map<String, List<MultipartFile>> files, GuardianDraft... guardians) {
        EnrollmentDraft draft = new EnrollmentDraft(
                "Bé giả", LocalDate.of(2022, 3, 15), Gender.MALE, List.of(guardians), null, null, null, CAMPUS_A);
        given(childIntakeService.prepareEnrollment(actor, REQUEST)).willReturn(draft);
        given(childPhotoService.validatePhotos(guardians.length, files)).willReturn(PHOTOS);
        return draft;
    }

    private ChildViewResponse givenView() {
        ChildViewResponse view = mock(ChildViewResponse.class);
        given(childIntakeService.getChildView(CHILD_ID)).willReturn(view);
        return view;
    }

    private void givenSaveAssignsId(long id) {
        given(userAccountRepository.saveAndFlush(any())).willAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", id);
            return account;
        });
    }

    private static GuardianDraft recipient(int position, String phone, String citizenId, String fullName) {
        return new GuardianDraft(
                position, fullName, GuardianRelationship.MOTHER, phone, citizenId, "me.gia@example.test", true);
    }

    private static GuardianInput input(String phone, String idNumber, Boolean accountRecipient) {
        return new GuardianInput("Phụ huynh giả", "MOTHER", phone, idNumber, null, accountRecipient);
    }

    private static GuardianChildSummary summary(long childId, ChildStatus status, Long campusId) {
        return new GuardianChildSummary(childId, "TR" + childId, "Bé giả " + childId, status, "Lớp A", campusId);
    }

    private static UserAccount parent(long id, String username, String citizenId, String fullName) {
        UserAccount account = UserAccount.createParent("kc-" + id, username, null, fullName, citizenId);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static UserAccount staff(long id, String username) {
        UserAccount account = UserAccount.create("kc-" + id, username, null, "GV giả", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static void assertConflict(ThrowingCallable call, FieldErrorResponse... expected) {
        assertThatThrownBy(call).isInstanceOf(GlobalException.class).satisfies(ex -> {
            GlobalException error = (GlobalException) ex;
            assertThat(error.getApiCode()).isEqualTo(ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            assertThat(error.getFieldErrors()).containsExactly(expected);
        });
    }

    private static void assertApiCode(ThrowingCallable call, ApiCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(expected);
    }
}
