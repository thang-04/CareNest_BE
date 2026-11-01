package com.carenest.service.account;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CareNestRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AccountService {

    MyAccountResponse getMyAccount(CurrentUser currentUser);

    /** Họ tên theo userId, cho màn hình hiển thị người soạn, người gửi, người duyệt. */
    Map<Long, String> findFullNames(Collection<Long> userIds);

    /** Họ tên các tài khoản đang hoạt động có vai trò chính role (in tên người ký trên bản in kế hoạch). */
    List<String> findActiveFullNamesByRole(CareNestRole role);
}
