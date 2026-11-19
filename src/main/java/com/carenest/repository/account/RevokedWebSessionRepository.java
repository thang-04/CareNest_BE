package com.carenest.repository.account;

import com.carenest.entity.account.RevokedWebSession;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RevokedWebSessionRepository extends JpaRepository<RevokedWebSession, String> {
    List<RevokedWebSession> findByExpiresAtAfter(Instant now);

    @Modifying
    @Query("delete from RevokedWebSession r where r.expiresAt<:now")
    int deleteExpired(@Param("now") Instant now);
}
