package com.carenest.repository.account;

import com.carenest.entity.account.UserAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByKeycloakUserId(String keycloakUserId);

    boolean existsByUsername(String username);

    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findByEmailIgnoreCase(String email);

    // CCCD phụ huynh là khóa nhận diện (AUTH-18, UNIQUE ở V10)
    Optional<UserAccount> findByCitizenId(String citizenId);

    List<UserAccount> findAllByCitizenIdIn(Collection<String> citizenIds);

    List<UserAccount> findAllByUsernameIn(Collection<String> usernames);
}
