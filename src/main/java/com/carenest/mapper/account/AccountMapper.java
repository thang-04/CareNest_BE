package com.carenest.mapper.account;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.security.CareNestRole;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toAccountResponse(UserAccount account) {
        return new AccountResponse(
                account.getId(), account.getUsername(), account.getFullName(), account.getEmail(), account.getStatus());
    }

    public CreateAccountResponse toCreateAccountResponse(
            UserAccount account, CareNestRole role, boolean setupEmailSent) {
        return new CreateAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getEmail(),
                role,
                account.getStatus(),
                setupEmailSent);
    }
}
