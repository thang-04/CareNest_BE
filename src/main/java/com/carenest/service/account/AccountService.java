package com.carenest.service.account;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccountService {

    public MyAccountResponse getMyAccount(CurrentUser currentUser) {
        log.info("[getMyAccount]|userId={}", currentUser.userId());
        return new MyAccountResponse(currentUser.userId(), currentUser.username(), currentUser.fullName(),
                currentUser.email(), currentUser.roles().stream().sorted().toList());
    }
}
