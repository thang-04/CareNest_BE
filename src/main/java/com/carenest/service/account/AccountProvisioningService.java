package com.carenest.service.account;

import com.carenest.dto.account.AccountCandidateResponse;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.UserStatus;

public interface AccountProvisioningService {

    /** Tra SĐT trước khi tạo nhân sự: tài khoản có sẵn hiện họ tên đã che để người tạo xác nhận đúng người. */
    AccountCandidateResponse findAccountCandidate(CurrentUser actor, String phone);

    CreateAccountResponse createAccount(CurrentUser actor, CreateAccountRequest request);

    AccountResponse updateAccountStatus(CurrentUser actor, Long userId, UserStatus status);

    /** Đặt mật khẩu tạm mới và hủy mọi phiên đang mở của tài khoản (AUTH-17). */
    AccountResponse resetTemporaryPassword(CurrentUser actor, Long userId, String temporaryPassword);

    /**
     * Nhân sự nghỉ việc nhưng con vẫn học: gỡ vai trò nhân sự, kết thúc phân công, giữ tài khoản và vai trò phụ huynh,
     * hủy mọi phiên.
     */
    AccountResponse revokeStaffRole(CurrentUser actor, Long userId);
}
