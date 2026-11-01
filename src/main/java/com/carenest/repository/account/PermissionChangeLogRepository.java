package com.carenest.repository.account;

import com.carenest.entity.account.PermissionChangeLog;
import com.carenest.utils.enums.CareNestRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionChangeLogRepository extends JpaRepository<PermissionChangeLog, Long> {

    Page<PermissionChangeLog> findByTargetRole(CareNestRole targetRole, Pageable pageable);

    Page<PermissionChangeLog> findByTargetUserId(Long targetUserId, Pageable pageable);
}
