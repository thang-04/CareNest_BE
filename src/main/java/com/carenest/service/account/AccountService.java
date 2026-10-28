package com.carenest.service.account;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.security.CurrentUser;
import java.util.Collection;
import java.util.Map;

public interface AccountService {

    MyAccountResponse getMyAccount(CurrentUser currentUser);

    /** Họ tên theo userId, cho màn hình hiển thị người soạn, người gửi, người duyệt. */
    Map<Long, String> findFullNames(Collection<Long> userIds);
}
