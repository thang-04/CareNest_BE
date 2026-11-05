package com.carenest.service.account;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

public interface SelectedParentAccountService {
    List<ChildGuardianResponse> candidates(CurrentUser actor, Long childId);

    Long selectedAccountId(CurrentUser actor, Long childId);

    void checkParentScope(CurrentUser actor, Long userId);

    void recordLink(CurrentUser actor, Long childId, Long guardianId, Long userId);

    void select(CurrentUser actor, Long childId, Long guardianId);

    List<ActivationResult> activate(CurrentUser actor, Long childId, Long guardianId);

    record ActivationResult(
            Long userId,
            String fullName,
            String username,
            boolean newAccount,
            com.carenest.utils.enums.SmsStatus smsStatus) {}
}
