package com.carenest.service.account;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.UserStatus;

public interface AccountProvisioningService {

    CreateAccountResponse createAccount(CurrentUser actor, CreateAccountRequest request);

    AccountResponse updateAccountStatus(CurrentUser actor, Long userId, UserStatus status);

    /** Đặt mật khẩu tạm mới và hủy mọi phiên đang mở của tài khoản (AUTH-17). */
    AccountResponse resetTemporaryPassword(CurrentUser actor, Long userId, String temporaryPassword);
}
