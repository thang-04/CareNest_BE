package com.carenest.repository.account;

import com.carenest.entity.account.UserPermissionOverride;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {

    List<UserPermissionOverride> findByUserId(Long userId);
}
