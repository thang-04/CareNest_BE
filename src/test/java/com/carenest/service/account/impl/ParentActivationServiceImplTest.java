package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.account.ParentActivationRequest;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParentActivationServiceImplTest {
    private static final ParentActivationRequest REQUEST =
            new ParentActivationRequest("0912345678", "123456", "Pass-word-1", "Pass-word-1");

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final ParentActivationCodes codes = mock(ParentActivationCodes.class);
    private final IdentityProviderClient identity = mock(IdentityProviderClient.class);
    private final ParentActivationServiceImpl service = new ParentActivationServiceImpl(accounts, codes, identity);
    private final UserAccount parent = mock(UserAccount.class);

    @BeforeEach
    void setUp() {
        given(parent.getId()).willReturn(7L);
        given(parent.getUsername()).willReturn("0912345678");
        given(parent.getKeycloakUserId()).willReturn("kc-7");
        given(parent.getStatus()).willReturn(UserStatus.ACTIVE);
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(parent));
    }

    @Test
    @DisplayName("AUTH-18: mã đúng ⇒ đặt mật khẩu rồi đánh dấu đã kích hoạt")
    void correctCodeSetsPasswordThenMarksActivated() {
        given(codes.verify(7L, "123456")).willReturn(true);

        var response = service.activate(REQUEST);

        assertThat(response.username()).isEqualTo("0912345678");
        var order = inOrder(identity, codes);
        order.verify(identity).setPassword("kc-7", "Pass-word-1");
        order.verify(codes).markActivated(7L);
    }

    @Test
    @DisplayName("AUTH-18: mã sai ⇒ lỗi chung, không đụng Keycloak")
    void wrongCodeRejected() {
        given(codes.verify(7L, "123456")).willReturn(false);

        assertError(ApiCode.OTP_INVALID_OR_EXPIRED);
        verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("AUTH-18: SĐT không có tài khoản ⇒ cùng lỗi với mã sai")
    void unknownPhoneSameError() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.empty());

        assertError(ApiCode.OTP_INVALID_OR_EXPIRED);
        verifyNoInteractions(codes, identity);
    }

    @Test
    @DisplayName("AUTH-18: tài khoản bị khóa ⇒ không kích hoạt được")
    void inactiveAccountRejected() {
        given(parent.getStatus()).willReturn(UserStatus.INACTIVE);

        assertError(ApiCode.OTP_INVALID_OR_EXPIRED);
        verifyNoInteractions(codes, identity);
    }

    @Test
    @DisplayName("AUTH-18: hai mật khẩu không khớp ⇒ không tính lần sai")
    void mismatchedPasswordsRejectedBeforeCode() {
        assertThatThrownBy(() -> service.activate(
                        new ParentActivationRequest("0912345678", "123456", "Pass-word-1", "Pass-word-2")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MALFORMED_REQUEST);
        verifyNoInteractions(codes, identity);
    }

    @Test
    @DisplayName("AUTH-18: policy Keycloak từ chối ⇒ 1009, mã chưa bị đánh dấu dùng")
    void policyRejectionKeepsCode() {
        given(codes.verify(7L, "123456")).willReturn(true);
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.REJECTED, "policy", null))
                .given(identity)
                .setPassword(anyString(), anyString());

        assertError(ApiCode.PASSWORD_POLICY_VIOLATION);
        verify(codes, never()).markActivated(7L);
    }

    private void assertError(ApiCode code) {
        assertThatThrownBy(() -> service.activate(REQUEST))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(code);
    }
}
