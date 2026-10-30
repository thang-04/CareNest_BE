package com.carenest.repository.account;

import com.carenest.entity.account.UserAccount;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByKeycloakUserId(String keycloakUserId);

    boolean existsByUsername(String username);

    Optional<UserAccount> findByUsername(String username);

    java.util.List<UserAccount> findByEmailIgnoreCase(String email);

    java.util.List<UserAccount> findAllByPrimaryRoleAndStatusOrderByFullNameAsc(
            CareNestRole primaryRole, UserStatus status);
}
