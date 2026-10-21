package com.carenest.repository.account;

import com.carenest.entity.account.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByKeycloakUserId(String keycloakUserId);

    boolean existsByUsername(String username);

    java.util.List<UserAccount> findByEmailIgnoreCase(String email);
}
