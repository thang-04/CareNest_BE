package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AuthStateStore;
import com.carenest.service.account.SelectedParentAccountService.ActivationResult;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.GuardianAccountStatus;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SmsStatus;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Unit test service bằng Mockito (không DB, Keycloak giả): chọn và kích hoạt tài khoản phụ huynh. Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class SelectedParentAccountServiceImplTest {

    private static final long CHILD_ID = 101L;
    private static final long GUARDIAN_ID = 1L;
    private static final long CAMPUS_A = 1L;
    private static final long CAMPUS_B = 2L;
    private static final String PHONE = "0900000001";
    private static final String CITIZEN_ID = "001200000001";
    private static final String TEMP_PASS = "Tmp7kQwxy3";
    private static final CurrentUser VICE = new CurrentUser(
            3L,
            "kc-3",
            "vp",
            "PHT giả",
            null,
            Set.of(CareNestRole.VICE_PRINCIPAL),
            Set.of(Permission.PARENT_ACCOUNT_MANAGE));
    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-2", "ht", "HT giả", null, Set.of(CareNestRole.PRINCIPAL));

    @Mock
    private ChildIntakeService childIntakeService;

    @Mock
    private StaffAccessPolicy staffAccessPolicy;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private GuardianChildLinkService guardianChildLinkService;

    @Mock
    private IdentityProviderClient identityProviderClient;

    @Mock
    private AuthStateStore authStateStore;

    @Mock
    private TemporaryPasswordGenerator passwordGenerator;

    @Mock
    private ParentSmsSender smsSender;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SelectedParentAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        service = new SelectedParentAccountServiceImpl(
                childIntakeService,
                staffAccessPolicy,
                userAccountRepository,
                guardianChildLinkService,
                new AccountLifecycleOperations(
                        userAccountRepository, identityProviderClient, authStateStore, transactionTemplate),
                passwordGenerator,
                smsSender,
                transactionTemplate);
    }

    @Nested
    @DisplayName("selectedAccountId")
    class SelectedAccountId {

        @Test
        @DisplayName("HT toàn trường ⇒ không tra điểm trường, trả tài khoản người nhận")
        void principalSkipsCampusCheck() {
            given(staffAccessPolicy.managesWholeSchool(PRINCIPAL)).willReturn(true);
            given(childIntakeService.findRecipientAccountId(CHILD_ID)).willReturn(Optional.of(60L));

            assertThat(service.selectedAccountId(PRINCIPAL, CHILD_ID)).isEqualTo(60L);
            verify(childIntakeService, never()).findChildSummaries(any());
        }

        @Test
        @DisplayName("G94-180/CHD-02: PHT điểm trường của trẻ chờ xếp lớp ⇒ được xem; chưa chọn người nhận ⇒ null")
        void viceOfChildCampusWithoutRecipient() {
            givenViceManages(CAMPUS_A);
            givenChild(ChildStatus.PENDING_PLACEMENT, CAMPUS_A);
            given(childIntakeService.findRecipientAccountId(CHILD_ID)).willReturn(Optional.empty());

            assertThat(service.selectedAccountId(VICE, CHILD_ID)).isNull();
        }

        @Test
        @DisplayName("G94-180/AUTH-18: PHT điểm trường khác ⇒ CHILD_GUARDIAN_MANAGE_DENIED")
        void viceOfOtherCampusIsDenied() {
            givenViceManages(CAMPUS_A);
            givenChild(ChildStatus.ACTIVE, CAMPUS_B);

            assertDenied(() -> service.selectedAccountId(VICE, CHILD_ID));
            verify(childIntakeService, never()).findRecipientAccountId(anyLong());
        }
    }

    @Nested
    @DisplayName("Phạm vi theo trẻ (dùng chung mọi thao tác)")
    class ChildScope {

        @Test
        @DisplayName("Không tìm thấy trẻ ⇒ CHILD_GUARDIAN_MANAGE_DENIED (không lộ trẻ có tồn tại)")
        void unknownChildIsDenied() {
            given(staffAccessPolicy.managesWholeSchool(VICE)).willReturn(false);
            given(childIntakeService.findChildSummaries(List.of(CHILD_ID))).willReturn(List.of());

            assertDenied(() -> service.candidates(VICE, CHILD_ID));
        }

        @Test
        @DisplayName("CHD-04: trẻ đã nghỉ học ⇒ PHT không quản lý phụ huynh của trẻ")
        void leftChildIsDenied() {
            given(staffAccessPolicy.managesWholeSchool(VICE)).willReturn(false);
            givenChild(ChildStatus.LEFT, CAMPUS_A);

            assertDenied(() -> service.candidates(VICE, CHILD_ID));
            verify(childIntakeService, never()).accountCandidates(anyLong());
        }

        @Test
        @DisplayName("Trẻ chưa có điểm trường ⇒ PHT bị từ chối")
        void childWithoutCampusIsDenied() {
            given(staffAccessPolicy.managesWholeSchool(VICE)).willReturn(false);
            givenChild(ChildStatus.PENDING_PLACEMENT, null);

            assertDenied(() -> service.candidates(VICE, CHILD_ID));
        }
    }

    @Nested
    @DisplayName("checkParentScope")
    class CheckParentScope {

        @Test
        @DisplayName("HT toàn trường ⇒ không tra trẻ của phụ huynh")
        void principalIsAlwaysAllowed() {
            given(staffAccessPolicy.managesWholeSchool(PRINCIPAL)).willReturn(true);

            service.checkParentScope(PRINCIPAL, 60L);

            verifyNoInteractions(guardianChildLinkService, childIntakeService);
        }

        @Test
        @DisplayName("AUTH-19: PHT phụ trách điểm trường của ít nhất một trẻ đang liên kết ⇒ được làm")
        void viceWithOneChildInCampusIsAllowed() {
            givenViceManages(CAMPUS_A);
            given(guardianChildLinkService.findChildIdsOfGuardian(60L)).willReturn(List.of(101L, 102L));
            given(childIntakeService.findChildSummaries(List.of(101L, 102L)))
                    .willReturn(List.of(
                            summary(101L, ChildStatus.ACTIVE, CAMPUS_B), summary(102L, ChildStatus.ACTIVE, CAMPUS_A)));

            service.checkParentScope(VICE, 60L);
        }

        @Test
        @DisplayName("AUTH-19: con ở điểm trường mình đã nghỉ, con còn lại ở điểm trường khác ⇒ ACCOUNT_MANAGE_DENIED")
        void viceWithOnlyLeftChildInCampusIsDenied() {
            givenViceManages(CAMPUS_A);
            given(guardianChildLinkService.findChildIdsOfGuardian(60L)).willReturn(List.of(101L, 102L));
            given(childIntakeService.findChildSummaries(List.of(101L, 102L)))
                    .willReturn(List.of(
                            summary(101L, ChildStatus.LEFT, CAMPUS_A), summary(102L, ChildStatus.ACTIVE, CAMPUS_B)));

            assertApiCode(() -> service.checkParentScope(VICE, 60L), ApiCode.ACCOUNT_MANAGE_DENIED);
        }

        @Test
        @DisplayName("Phụ huynh chưa liên kết trẻ nào ⇒ PHT bị từ chối")
        void parentWithoutChildrenIsDenied() {
            givenViceManages(CAMPUS_A);
            given(guardianChildLinkService.findChildIdsOfGuardian(60L)).willReturn(List.of());
            given(childIntakeService.findChildSummaries(List.of())).willReturn(List.of());

            assertApiCode(() -> service.checkParentScope(VICE, 60L), ApiCode.ACCOUNT_MANAGE_DENIED);
        }
    }

    @Nested
    @DisplayName("candidates")
    class Candidates {

        @Test
        @DisplayName("Trong phạm vi ⇒ trả danh sách phụ huynh trên hồ sơ")
        void returnsCandidatesInScope() {
            givenViceManages(CAMPUS_A);
            givenChild(ChildStatus.ACTIVE, CAMPUS_A);
            List<ChildGuardianResponse> candidates = List.of(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            given(childIntakeService.accountCandidates(CHILD_ID)).willReturn(candidates);

            assertThat(service.candidates(VICE, CHILD_ID)).isEqualTo(candidates);
        }
    }

    @Nested
    @DisplayName("recordLink")
    class RecordLink {

        @Test
        @DisplayName("Trong phạm vi ⇒ ghi liên kết tài khoản cho phụ huynh được chọn")
        void linksRecipientInScope() {
            given(staffAccessPolicy.managesWholeSchool(PRINCIPAL)).willReturn(true);

            service.recordLink(PRINCIPAL, CHILD_ID, GUARDIAN_ID, 60L);

            verify(childIntakeService).linkAccountRecipient(CHILD_ID, GUARDIAN_ID, 60L);
        }

        @Test
        @DisplayName("Ngoài phạm vi ⇒ từ chối, không ghi liên kết")
        void outOfScopeDoesNotLink() {
            givenViceManages(CAMPUS_A);
            givenChild(ChildStatus.ACTIVE, CAMPUS_B);

            assertDenied(() -> service.recordLink(VICE, CHILD_ID, GUARDIAN_ID, 60L));
            verify(childIntakeService, never()).linkAccountRecipient(anyLong(), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("select")
    class Select {

        @Test
        @DisplayName("AUTH-18: chọn người nhận tài khoản, ghi người chọn")
        void selectsRecipientWithActor() {
            givenViceManages(CAMPUS_A);
            givenChild(ChildStatus.ACTIVE, CAMPUS_A);

            service.select(VICE, CHILD_ID, GUARDIAN_ID);

            verify(childIntakeService).selectAccountRecipient(CHILD_ID, GUARDIAN_ID, VICE.userId());
        }

        @Test
        @DisplayName("Ngoài phạm vi ⇒ từ chối, không đổi người nhận")
        void outOfScopeDoesNotSelect() {
            givenViceManages(CAMPUS_A);
            givenChild(ChildStatus.ACTIVE, CAMPUS_B);

            assertDenied(() -> service.select(VICE, CHILD_ID, GUARDIAN_ID));
            verify(childIntakeService, never()).selectAccountRecipient(anyLong(), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("activate")
    class Activate {

        @Test
        @DisplayName(
                "G94-180/AUTH-18: CCCD, SĐT chưa có tài khoản ⇒ tạo PARENT (tên đăng nhập = SĐT), liên kết, SMS mật khẩu tạm")
        void createsAccountAndSendsSms() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE)).willReturn(Optional.empty());
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any())).willReturn("kc-new");
            givenSaveAssignsId(70L);
            given(smsSender.sendActivation(any(), anyString())).willReturn(SmsStatus.SENT);

            List<ActivationResult> results = service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID);

            ArgumentCaptor<NewIdentityUser> identity = ArgumentCaptor.forClass(NewIdentityUser.class);
            ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
            InOrder order = inOrder(identityProviderClient, userAccountRepository, childIntakeService, smsSender);
            order.verify(identityProviderClient).createUser(identity.capture());
            order.verify(identityProviderClient).assignRealmRole("kc-new", "PARENT");
            order.verify(userAccountRepository).saveAndFlush(saved.capture());
            order.verify(childIntakeService).linkAccountRecipient(CHILD_ID, GUARDIAN_ID, 70L);
            order.verify(smsSender).sendActivation(saved.getValue(), TEMP_PASS);
            assertThat(identity.getValue()).isEqualTo(new NewIdentityUser(PHONE, null, "Mẹ giả", TEMP_PASS));
            assertThat(saved.getValue().getUsername()).isEqualTo(PHONE);
            assertThat(saved.getValue().getCitizenId()).isEqualTo(CITIZEN_ID);
            assertThat(saved.getValue().getEmail()).isEqualTo("me.gia@example.test");
            assertThat(saved.getValue().getPrimaryRole()).isEqualTo(CareNestRole.PARENT);
            assertThat(saved.getValue().getKeycloakUserId()).isEqualTo("kc-new");
            assertThat(results).containsExactly(new ActivationResult(70L, "Mẹ giả", PHONE, true, SmsStatus.SENT));
            assertThat(results.toString()).doesNotContain(TEMP_PASS);
        }

        @Test
        @DisplayName("L-15: SMS gửi lỗi ⇒ vẫn tạo tài khoản, trả FAILED để gửi lại")
        void smsFailureKeepsAccount() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE)).willReturn(Optional.empty());
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any())).willReturn("kc-new");
            givenSaveAssignsId(70L);
            given(smsSender.sendActivation(any(), anyString())).willReturn(SmsStatus.FAILED);

            List<ActivationResult> results = service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID);

            assertThat(results.getFirst().newAccount()).isTrue();
            assertThat(results.getFirst().smsStatus()).isEqualTo(SmsStatus.FAILED);
            verify(identityProviderClient, never()).deleteUser(anyString());
        }

        @Test
        @DisplayName("ADR-0011: ghi DB lỗi sau khi đã tạo user Keycloak ⇒ xóa user Keycloak, không gửi SMS")
        void persistFailureDeletesIdentity() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE)).willReturn(Optional.empty());
            given(passwordGenerator.generate()).willReturn(TEMP_PASS);
            given(identityProviderClient.createUser(any())).willReturn("kc-new");
            givenSaveAssignsId(70L);
            willThrow(GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT))
                    .given(childIntakeService)
                    .linkAccountRecipient(CHILD_ID, GUARDIAN_ID, 70L);

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            verify(identityProviderClient).deleteUser("kc-new");
            verifyNoInteractions(smsSender);
        }

        @Test
        @DisplayName("G94-180/AUTH-18: CCCD đã là phụ huynh ⇒ chỉ liên kết, giữ mật khẩu, không tạo user, không SMS")
        void existingParentByCitizenIdIsLinked() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            UserAccount existing = parent(60L, "0900000009", CITIZEN_ID, "Mẹ giả (tài khoản)");
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.of(existing));
            given(userAccountRepository.findById(60L)).willReturn(Optional.of(existing));
            given(userAccountRepository.claimCitizenId(60L, CITIZEN_ID)).willReturn(1);

            List<ActivationResult> results = service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID);

            verify(childIntakeService).linkAccountRecipient(CHILD_ID, GUARDIAN_ID, 60L);
            verify(userAccountRepository, never()).findByUsername(anyString());
            verifyNoInteractions(identityProviderClient, passwordGenerator, smsSender);
            assertThat(results)
                    .containsExactly(new ActivationResult(60L, "Mẹ giả (tài khoản)", "0900000009", false, null));
        }

        @Test
        @DisplayName("AUTH-18: tài khoản phụ huynh cũ theo SĐT chưa có CCCD ⇒ liên kết và bổ sung CCCD")
        void legacyParentByPhoneClaimsCitizenId() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            UserAccount legacy = parent(61L, PHONE, null, "Mẹ giả");
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE)).willReturn(Optional.of(legacy));
            given(userAccountRepository.findById(61L)).willReturn(Optional.of(legacy));
            given(userAccountRepository.claimCitizenId(61L, CITIZEN_ID)).willReturn(1);

            List<ActivationResult> results = service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID);

            verify(userAccountRepository).claimCitizenId(61L, CITIZEN_ID);
            assertThat(results.getFirst().newAccount()).isFalse();
            assertThat(results.getFirst().userId()).isEqualTo(61L);
        }

        @Test
        @DisplayName("AUTH-18: SĐT thuộc phụ huynh có CCCD khác ⇒ GUARDIAN_IDENTITY_CONFLICT, không liên kết")
        void phoneOfAnotherCitizenIdConflicts() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE))
                    .willReturn(Optional.of(parent(62L, PHONE, "001200000099", "Người khác giả")));

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            verify(childIntakeService, never()).linkAccountRecipient(anyLong(), anyLong(), anyLong());
            verifyNoInteractions(identityProviderClient);
        }

        @Test
        @DisplayName("G94-180/AUTH-18: SĐT là tên đăng nhập nhân sự ⇒ GUARDIAN_IDENTITY_CONFLICT")
        void staffPhoneConflicts() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            UserAccount teacher = UserAccount.create("kc-63", PHONE, null, "GV giả", CareNestRole.TEACHER);
            ReflectionTestUtils.setField(teacher, "id", 63L);
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE)).willReturn(Optional.of(teacher));

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            verify(childIntakeService, never()).linkAccountRecipient(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("AUTH-18: trong giao dịch, tài khoản vừa được ghi CCCD khác ⇒ GUARDIAN_IDENTITY_CONFLICT")
        void concurrentCitizenIdChangeConflicts() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE))
                    .willReturn(Optional.of(parent(61L, PHONE, null, "Mẹ giả")));
            given(userAccountRepository.findById(61L))
                    .willReturn(Optional.of(parent(61L, PHONE, "001200000099", "Mẹ giả")));

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            verify(userAccountRepository, never()).claimCitizenId(anyLong(), anyString());
        }

        @Test
        @DisplayName("AUTH-18: không ghi được CCCD (đã thuộc tài khoản khác) ⇒ GUARDIAN_IDENTITY_CONFLICT")
        void claimFailureConflicts() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            UserAccount legacy = parent(61L, PHONE, null, "Mẹ giả");
            given(userAccountRepository.findByCitizenId(CITIZEN_ID)).willReturn(Optional.empty());
            given(userAccountRepository.findByUsername(PHONE)).willReturn(Optional.of(legacy));
            given(userAccountRepository.findById(61L)).willReturn(Optional.of(legacy));
            given(userAccountRepository.claimCitizenId(61L, CITIZEN_ID)).willReturn(0);

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
        }

        @Test
        @DisplayName("Tài khoản bị xóa giữa chừng ⇒ ACCOUNT_NOT_FOUND")
        void accountRemovedConcurrently() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, "Mẹ giả"));
            given(userAccountRepository.findByCitizenId(CITIZEN_ID))
                    .willReturn(Optional.of(parent(60L, PHONE, CITIZEN_ID, "Mẹ giả")));
            given(userAccountRepository.findById(60L)).willReturn(Optional.empty());

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("AUTH-18: phụ huynh không phải người được chọn nhận tài khoản ⇒ GUARDIAN_IDENTITY_CONFLICT")
        void notSelectedGuardianIsRejected() {
            given(staffAccessPolicy.managesWholeSchool(PRINCIPAL)).willReturn(true);
            given(childIntakeService.accountCandidates(CHILD_ID))
                    .willReturn(List.of(
                            candidate(GUARDIAN_ID, false, PHONE, CITIZEN_ID, "Mẹ giả"),
                            candidate(2L, true, "0900000002", "001200000002", "Bố giả")));

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            verifyNoInteractions(userAccountRepository, identityProviderClient);
        }

        @Test
        @DisplayName("G94-180/CHD-08: người nhận thiếu CCCD hợp lệ ⇒ INVALID_REQUEST_DATA")
        void invalidCitizenIdIsRejected() {
            givenCandidate(chosen(PHONE, "12345", "Mẹ giả"));

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.INVALID_REQUEST_DATA);
            verifyNoInteractions(userAccountRepository, identityProviderClient);
        }

        @Test
        @DisplayName("AUTH-18: SĐT không hợp lệ ⇒ INVALID_REQUEST_DATA")
        void invalidPhoneIsRejected() {
            givenCandidate(chosen("12345", CITIZEN_ID, "Mẹ giả"));

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.INVALID_REQUEST_DATA);
        }

        @Test
        @DisplayName("Họ tên trống ⇒ INVALID_REQUEST_DATA")
        void blankNameIsRejected() {
            givenCandidate(chosen(PHONE, CITIZEN_ID, " "));

            assertApiCode(() -> service.activate(PRINCIPAL, CHILD_ID, GUARDIAN_ID), ApiCode.INVALID_REQUEST_DATA);
        }

        @Test
        @DisplayName("PHT ngoài phạm vi trẻ ⇒ CHILD_GUARDIAN_MANAGE_DENIED, không tạo tài khoản")
        void outOfScopeIsDenied() {
            givenViceManages(CAMPUS_A);
            givenChild(ChildStatus.ACTIVE, CAMPUS_B);

            assertDenied(() -> service.activate(VICE, CHILD_ID, GUARDIAN_ID));
            verifyNoInteractions(userAccountRepository, identityProviderClient, smsSender);
        }
    }

    private void givenViceManages(long campusId) {
        given(staffAccessPolicy.managesWholeSchool(VICE)).willReturn(false);
        given(staffAccessPolicy.managedCampusIds(VICE)).willReturn(Set.of(campusId));
    }

    private void givenChild(ChildStatus status, Long campusId) {
        given(childIntakeService.findChildSummaries(List.of(CHILD_ID)))
                .willReturn(List.of(summary(CHILD_ID, status, campusId)));
    }

    private void givenCandidate(ChildGuardianResponse candidate) {
        given(staffAccessPolicy.managesWholeSchool(PRINCIPAL)).willReturn(true);
        given(childIntakeService.accountCandidates(CHILD_ID)).willReturn(List.of(candidate));
    }

    private void givenSaveAssignsId(long id) {
        given(userAccountRepository.saveAndFlush(any())).willAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", id);
            return account;
        });
    }

    private static GuardianChildSummary summary(long childId, ChildStatus status, Long campusId) {
        return new GuardianChildSummary(childId, "TR" + childId, "Bé giả", status, "Lớp A", campusId);
    }

    private static ChildGuardianResponse chosen(String phone, String idNumber, String fullName) {
        return candidate(GUARDIAN_ID, true, phone, idNumber, fullName);
    }

    private static ChildGuardianResponse candidate(
            long id, boolean recipient, String phone, String idNumber, String fullName) {
        return new ChildGuardianResponse(
                fullName,
                GuardianRelationship.MOTHER,
                phone,
                idNumber,
                "me.gia@example.test",
                GuardianAccountStatus.NOT_ACTIVATED,
                null,
                null,
                null,
                id,
                recipient);
    }

    private static UserAccount parent(long id, String username, String citizenId, String fullName) {
        UserAccount account = UserAccount.createParent("kc-" + id, username, null, fullName, citizenId);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static void assertDenied(ThrowingCallable call) {
        assertApiCode(call, ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
    }

    private static void assertApiCode(ThrowingCallable call, ApiCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(expected);
    }
}
