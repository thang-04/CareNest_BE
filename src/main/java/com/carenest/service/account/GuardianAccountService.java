package com.carenest.service.account;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.child.AddGuardianRequest;
import com.carenest.dto.child.AddGuardianResponse;
import com.carenest.dto.child.GuardianResponse;
import com.carenest.dto.child.ParentPasswordResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.UserStatus;
import java.util.List;

/** Tài khoản phụ huynh gắn trẻ (AUTH-18, AUTH-19): username = SĐT, mật khẩu tạm hệ thống sinh, phạm vi theo trẻ. */
public interface GuardianAccountService {

    List<GuardianResponse> listGuardians(CurrentUser actor, Long childId);

    AddGuardianResponse addGuardian(CurrentUser actor, Long childId, AddGuardianRequest request);

    void removeGuardian(CurrentUser actor, Long childId, Long userId);

    AccountResponse updateParentStatus(CurrentUser actor, Long userId, UserStatus status);

    ParentPasswordResponse resetParentPassword(CurrentUser actor, Long userId);
}
