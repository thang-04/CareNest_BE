package com.carenest.service;

import com.carenest.repository.InvalidatedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Removes blacklisted tokens that are already expired, so the table does not grow forever.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvalidatedTokenCleanupJob {

    private final InvalidatedTokenRepository invalidatedTokenRepository;

    @Scheduled(cron = "0 0 * * * *")
    public void deleteExpiredTokens() {
        int deleted = invalidatedTokenRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired invalidated tokens", deleted);
        }
    }
}
