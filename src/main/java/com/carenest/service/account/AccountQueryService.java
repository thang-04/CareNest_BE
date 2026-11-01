package com.carenest.service.account;

import com.carenest.dto.account.AccountDetailResponse;
import com.carenest.dto.account.AccountSummaryResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;

/** Danh sách và chi tiết tài khoản cho quản lý nhân sự, lọc theo phạm vi người xem (AUTH-08, AUTH-17). */
public interface AccountQueryService {

    PageResponse<AccountSummaryResponse> searchAccounts(
            CurrentUser actor, CareNestRole role, UserStatus status, Long campusId, String keyword, int page, int size);

    AccountDetailResponse getAccountDetail(CurrentUser actor, Long userId);
}
