package com.carenest.mapper.account;

import com.carenest.dto.account.AccountDetailResponse;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.AccountSummaryResponse;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toAccountResponse(UserAccount account) {
        return new AccountResponse(
                account.getId(), account.getUsername(), account.getFullName(), account.getEmail(), account.getStatus());
    }

    public CreateAccountResponse toCreateAccountResponse(
            UserAccount account, CareNestRole role, boolean setupEmailSent, StaffAssignmentResponse assignment) {
        return new CreateAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getEmail(),
                role,
                account.getStatus(),
                setupEmailSent,
                assignment);
    }

    public AccountSummaryResponse toSummaryResponse(
            UserAccount account, List<StaffAssignmentSummaryResponse> activeAssignments) {
        return new AccountSummaryResponse(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getEmail(),
                account.getPrimaryRole(),
                account.getStatus(),
                activeAssignments);
    }

    public AccountDetailResponse toDetailResponse(UserAccount account, List<StaffAssignmentResponse> assignments) {
        return new AccountDetailResponse(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getEmail(),
                account.getPrimaryRole(),
                account.getStatus(),
                assignments);
    }
}
