package com.carenest.service.account.impl;

import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tải tài khoản đích của thao tác quản lý nhân sự. Người chỉ có phạm vi campus (PHT) nhận cùng 403 cho id không tồn
 * tại và id ngoài phạm vi để không dò được id; Admin/HT nhận 404 như thường.
 */
@Component
@RequiredArgsConstructor
class ManagedAccountLoader {

    private static final String DENIED = "You cannot manage this account";

    private final UserAccountRepository userAccountRepository;
    private final StaffAccessPolicy staffAccessPolicy;

    UserAccount loadManageable(CurrentUser actor, Long userId) {
        UserAccount account = loadExisting(actor, userId);
        staffAccessPolicy.checkCanManageAccount(actor, account.getId(), account.getPrimaryRole());
        return account;
    }

    UserAccount loadExisting(CurrentUser actor, Long userId) {
        return userAccountRepository
                .findById(userId)
                .orElseThrow(() -> fullScope(actor)
                        ? new GlobalException(ApiCode.NOT_FOUND, "Account not found")
                        : new GlobalException(ApiCode.FORBIDDEN, DENIED));
    }

    private boolean fullScope(CurrentUser actor) {
        return actor.hasRole(CareNestRole.ADMIN) || staffAccessPolicy.managesWholeSchool(actor);
    }
}
