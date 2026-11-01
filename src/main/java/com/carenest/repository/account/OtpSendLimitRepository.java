package com.carenest.repository.account;

import com.carenest.entity.account.OtpSendLimit;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OtpSendLimitRepository extends JpaRepository<OtpSendLimit, String> {
    @Modifying
    @Query(value = """
        insert into otp_send_limits(bucket_hash,used,expires_at) values (:hash,1,:expiry)
        on conflict(bucket_hash) do update set used=otp_send_limits.used+1 where otp_send_limits.used<:budget
        """, nativeQuery = true)
    int take(@Param("hash") String hash, @Param("expiry") Instant expiry, @Param("budget") int budget);

    @Modifying
    @Query("delete from OtpSendLimit r where r.expiresAt<:now")
    int deleteExpired(@Param("now") Instant now);
}
