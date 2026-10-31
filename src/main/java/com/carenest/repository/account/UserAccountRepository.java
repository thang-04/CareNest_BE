package com.carenest.repository.account;

import com.carenest.entity.account.UserAccount;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByKeycloakUserId(String keycloakUserId);

    boolean existsByUsername(String username);

    Optional<UserAccount> findByUsername(String username);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "UPDATE user_accounts SET primary_role = :role, updated_at = now() WHERE id = :id",
            nativeQuery = true)
    int updatePrimaryRole(@Param("id") Long id, @Param("role") String role);

    java.util.List<UserAccount> findByEmailIgnoreCase(String email);

    java.util.List<UserAccount> findAllByPrimaryRoleAndStatusOrderByFullNameAsc(
            CareNestRole primaryRole, UserStatus status);
}
