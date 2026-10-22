package com.carenest.repository.account;

import com.carenest.entity.account.RolePermission;
import com.carenest.utils.enums.CareNestRole;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleIn(Collection<CareNestRole> roles);

    List<RolePermission> findByRole(CareNestRole role);
}
