package com.carenest.repository;

import com.carenest.entity.InvalidatedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {

    @Modifying
    @Transactional
    @Query("DELETE FROM InvalidatedToken t WHERE t.expirationTime < :now")
    int deleteExpired(@Param("now") Instant now);
}
