package com.carenest.repository.account;

import com.carenest.entity.account.PasswordToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordTokenRepository extends JpaRepository<PasswordToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordToken c where c.tokenHash=:hash")
    Optional<PasswordToken> findLocked(@Param("hash") String hash);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("delete from PasswordToken c where c.expiresAt<:cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
