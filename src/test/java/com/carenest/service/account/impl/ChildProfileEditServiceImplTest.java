package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.ChildEditDraft;
import com.carenest.dto.child.ChildEditDraft.GuardianEdit;
import com.carenest.dto.child.ChildProfileDraft;
import com.carenest.dto.child.ChildUpdateResponse;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.AuthStateStore;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.YesNo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ChildProfileEditServiceImplTest {

    private static final Long CHILD_ID = 1L;
    private static final Long CAMPUS_ID = 2L;
    private static final String OLD_PHONE = "0912345678";
    private static final String NEW_PHONE = "0987654321";
    private static final String CITIZEN = "001234567890";
    private static final CurrentUser PRINCIPAL = TestPermissions.userWithDefaults(9L, CareNestRole.PRINCIPAL);
    private static final UpdateChildRequest REQUEST = new UpdateChildRequest(null, "Sửa");

    private final ChildRecordService records = mock(ChildRecordService.class);
    private final ChildIntakeService intake = mock(ChildIntakeService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final IdentityProviderClient identity = mock(IdentityProviderClient.class);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final ChildProfileEditServiceImpl service = new ChildProfileEditServiceImpl(
            records,
            intake,
            accounts,
            new AccountLifecycleOperations(accounts, identity, mock(AuthStateStore.class), tx),
            tx);

    private UserAccount account;

    @BeforeEach
    void setUp() {
        account = UserAccount.createParent("kc-50", OLD_PHONE, null, "Mẹ An", CITIZEN);
        ReflectionTestUtils.setField(account, "id", 50L);
        given(accounts.findAllById(List.of(50L))).willReturn(List.of(account));
        given(accounts.findById(50L)).willReturn(Optional.of(account));
        given(accounts.findAllByCitizenIdIn(anyCollection())).willReturn(List.of(account));
        given(accounts.findAllByUsernameIn(anyCollection())).willReturn(List.of());
    }

    @Test
    @DisplayName("G94-180/AUTH-21 (sửa 2026-11-11): đổi SĐT phụ huynh có tài khoản ở Sửa hồ sơ ⇒ 400 ô SĐT, không ghi")
    void phoneChangeOfAccountHolderRejected() {
        given(records.prepareEdit(PRINCIPAL, CHILD_ID, REQUEST)).willReturn(draft("Mẹ An", NEW_PHONE, CITIZEN));

        assertThatThrownBy(() -> service.update(PRINCIPAL, CHILD_ID, REQUEST))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                    assertThat(ex.getFieldErrors())
                            .extracting(FieldErrorResponse::field)
                            .containsExactly("guardians.0.phone");
                });
        verify(records, never()).applyEdit(any(), any());
        verify(identity, never()).updateUsername(any(), any());
    }

    @Test
    @DisplayName("G94-180/AUTH-21 (sửa 2026-11-11): phụ huynh chưa có tài khoản cũng không đổi SĐT ở Sửa hồ sơ")
    void phoneChangeOfGuardianWithoutAccountRejected() {
        given(records.prepareEdit(PRINCIPAL, CHILD_ID, REQUEST))
                .willReturn(withoutAccount(guardian("Bố An", NEW_PHONE, CITIZEN)));

        assertThatThrownBy(() -> service.update(PRINCIPAL, CHILD_ID, REQUEST))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("guardians.0.phone"));
        verify(records, never()).applyEdit(any(), any());
    }

    @Test
    @DisplayName("Thay người khác vào vị trí phụ huynh chưa có tài khoản (CCCD khác) ⇒ nhập SĐT mới được")
    void replacingGuardianWithoutAccountKeepsNewPhone() {
        given(records.prepareEdit(PRINCIPAL, CHILD_ID, REQUEST))
                .willReturn(withoutAccount(guardian("Bà An", NEW_PHONE, "001234567899")));

        service.update(PRINCIPAL, CHILD_ID, REQUEST);

        ArgumentCaptor<ChildEditDraft> applied = ArgumentCaptor.forClass(ChildEditDraft.class);
        verify(records).applyEdit(eq(PRINCIPAL), applied.capture());
        assertThat(applied.getValue().guardians().getFirst().draft().phone()).isEqualTo(NEW_PHONE);
    }

    @Test
    @DisplayName(
            "G94-180/AUTH-21: đổi họ tên phụ huynh có tài khoản ⇒ cập nhật tài khoản, mọi hồ sơ, Keycloak sau commit")
    void nameChangeUpdatesAccount() {
        given(records.prepareEdit(PRINCIPAL, CHILD_ID, REQUEST)).willReturn(draft("Mẹ Bình", OLD_PHONE, CITIZEN));

        ChildUpdateResponse response = service.update(PRINCIPAL, CHILD_ID, REQUEST);

        assertThat(account.getFullName()).isEqualTo("Mẹ Bình");
        verify(records).syncGuardianAccountInfo(50L, "Mẹ Bình", null, CITIZEN);
        verify(identity).replaceProfile("kc-50", "Mẹ Bình", null);
        assertThat(response.phoneChangeRequests()).isEmpty();
        assertThat(response.accountChanges())
                .singleElement()
                .satisfies(c -> assertThat(c.phoneChanged()).isFalse());
    }

    @Test
    @DisplayName("G94-180/AUTH-21: không có quyền quản lý tài khoản phụ huynh ⇒ không sửa họ tên của tài khoản")
    void nameChangeNeedsAccountPermission() {
        CurrentUser vice =
                new CurrentUser(3L, "kc-3", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());
        given(records.prepareEdit(vice, CHILD_ID, REQUEST)).willReturn(draft("Mẹ Bình", OLD_PHONE, CITIZEN));

        assertThatThrownBy(() -> service.update(vice, CHILD_ID, REQUEST))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("guardians.0.fullName"));
        verify(records, never()).applyEdit(any(), any());
    }

    @Test
    @DisplayName("Mục 4.2: đổi cùng lúc CCCD và SĐT của phụ huynh có tài khoản ⇒ lỗi ô CCCD")
    void citizenAndPhoneTogetherRejected() {
        given(records.prepareEdit(PRINCIPAL, CHILD_ID, REQUEST)).willReturn(draft("Mẹ An", NEW_PHONE, "001234567899"));

        assertThatThrownBy(() -> service.update(PRINCIPAL, CHILD_ID, REQUEST))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("guardians.0.idNumber"));
    }

    private static ChildEditDraft draft(String guardianName, String phone, String citizenId) {
        return editDraft(new GuardianEdit(guardian(guardianName, phone, citizenId), 31L, 50L, OLD_PHONE, CITIZEN));
    }

    // Dòng phụ huynh có sẵn (id 32) chưa liên kết tài khoản, đang lưu OLD_PHONE + CITIZEN
    private static ChildEditDraft withoutAccount(GuardianDraft edited) {
        return editDraft(new GuardianEdit(edited, 32L, null, OLD_PHONE, CITIZEN));
    }

    private static GuardianDraft guardian(String fullName, String phone, String citizenId) {
        return new GuardianDraft(0, fullName, GuardianRelationship.MOTHER, phone, citizenId, null, true);
    }

    private static ChildEditDraft editDraft(GuardianEdit guardian) {
        ChildProfileDraft profile =
                new ChildProfileDraft(null, YesNo.NO, (short) 39, "Kinh", "Không", null, "Hà Nội", "Phường A", "Số 1");
        return new ChildEditDraft(
                CHILD_ID,
                CAMPUS_ID,
                "Sửa",
                "Bé An",
                LocalDate.of(2022, 3, 1),
                Gender.FEMALE,
                profile,
                List.of(guardian));
    }
}
