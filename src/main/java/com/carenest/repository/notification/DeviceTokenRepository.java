package com.carenest.repository.notification;

import com.carenest.entity.notification.DeviceToken;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdIn(Collection<Long> userIds);

    List<DeviceToken> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT d FROM DeviceToken d WHERE d.id = :id")
    Optional<DeviceToken> lockById(@Param("id") Long id);

    long deleteByUpdatedAtBefore(Instant cutoff);
}
