package com.carenest.service.account;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.security.CurrentUser;

public interface AccountService {

    MyAccountResponse getMyAccount(CurrentUser currentUser);
}
