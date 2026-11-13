package com.carenest.service.account.impl;

import com.carenest.dto.account.ParentActivationRequest;
import com.carenest.dto.account.ParentActivationResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.service.account.ParentActivationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.PhoneNumbers;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentActivationServiceImpl implements ParentActivationService {
    private static final String LOG_PREFIX = "[ParentActivationServiceImpl]";

    private final UserAccountRepository userAccountRepository;
    private final ParentActivationCodes activationCodes;
    private final IdentityProviderClient identityProviderClient;

    // Không @Transactional: kiểm mã (có đếm lần sai) và đánh dấu đã dùng là hai transaction riêng quanh lời gọi
    // Keycloak
    @Override
    public ParentActivationResponse activate(ParentActivationRequest request) {
        String logPrefix = LOG_PREFIX + "|activate";
        log.info("{}|START", logPrefix);
        try {
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw GlobalException.of(ApiCode.MALFORMED_REQUEST);
            }
            // ADR-0024: SĐT không có tài khoản, khóa, mã sai/hết hạn/hết lượt ⇒ cùng một lỗi
            UserAccount account = PhoneNumbers.normalize(request.phone())
                    .flatMap(userAccountRepository::findByUsername)
                    .filter(a -> a.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> GlobalException.of(ApiCode.OTP_INVALID_OR_EXPIRED));
            if (!activationCodes.verify(account.getId(), request.code())) {
                throw GlobalException.of(ApiCode.OTP_INVALID_OR_EXPIRED);
            }
            try {
                identityProviderClient.setPassword(account.getKeycloakUserId(), request.newPassword());
            } catch (IdentityProviderException ex) {
                // Policy từ chối hoặc Keycloak lỗi: mã còn nguyên để nhập lại mật khẩu khác
                if (ex.getReason() == IdentityProviderErrorReason.REJECTED) {
                    throw GlobalException.withCause(ApiCode.PASSWORD_POLICY_VIOLATION, ex);
                }
                throw AccountLifecycleOperations.toGlobalException(ex);
            }
            activationCodes.markActivated(account.getId());
            log.info("{}|userId={}|END", logPrefix, account.getId());
            return new ParentActivationResponse(account.getUsername());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
