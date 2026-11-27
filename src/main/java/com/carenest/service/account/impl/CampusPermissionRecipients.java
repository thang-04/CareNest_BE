package com.carenest.service.account.impl;

import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EffectivePermissionResolver;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CampusPermissionRecipients {
    private final UserAccountRepository accounts;
    private final RolePermissionRepository matrix;
    private final UserPermissionOverrideRepository overrides;
    private final StaffAssignmentService assignments;
    private final StaffAccessPolicy policy;
    private final Clock clock;

    // GEN-02: người nhận thông báo phải có cùng phạm vi quyền với người được duyệt.
    Set<Long> approverIds(Permission permission, Long campusId) {
        Set<Long> ids = new HashSet<>(assignments.findPrincipalIds(LocalDate.now(clock)));
        if (campusId == null) return ids;
        var roles = Set.of(CareNestRole.VICE_PRINCIPAL);
        var rows = matrix.findByRole(CareNestRole.VICE_PRINCIPAL);
        for (var account : accounts.findAllByPrimaryRoleAndStatusOrderByFullNameAsc(
                CareNestRole.VICE_PRINCIPAL, UserStatus.ACTIVE)) {
            var grants = overrides.findByUserId(account.getId());
            CurrentUser recipient = new CurrentUser(
                    account.getId(),
                    account.getKeycloakUserId(),
                    account.getUsername(),
                    account.getFullName(),
                    account.getEmail(),
                    roles,
                    EffectivePermissionResolver.resolve(roles, rows, grants),
                    EffectivePermissionResolver.schoolWide(roles, grants),
                    EffectivePermissionResolver.pinnedCampuses(roles, grants));
            if (policy.canEditCampus(recipient, permission, campusId)) ids.add(account.getId());
        }
        return ids;
    }
}
