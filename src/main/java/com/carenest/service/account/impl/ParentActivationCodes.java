package com.carenest.service.account.impl;

import com.carenest.entity.account.ParentActivationCode;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.ParentActivationCodeRepository;
import com.carenest.utils.ApiCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mã kích hoạt tài khoản phụ huynh (AUTH-18, ADR-0026): 6 số, hạn 72 giờ, tối đa 5 lần sai, gửi lại cách 60 giây.
 * Chỉ lưu hash; mã gốc chỉ nằm trong bộ nhớ tới khi gửi SMS. Không log mã.
 */
@Component
@RequiredArgsConstructor
class ParentActivationCodes {
    static final Duration TTL = Duration.ofHours(72);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParentActivationCodeRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    record Issued(String code, Instant expiresAt) {}

    String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Lưu mã cho tài khoản vừa tạo; gọi trong transaction tạo tài khoản để mã và tài khoản cùng commit. */
    Instant store(Long userId, String code) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(TTL);
        repository.save(ParentActivationCode.issue(userId, hash(userId, code), now, expiresAt));
        return expiresAt;
    }

    /** Gửi lại: chỉ tài khoản đang chờ kích hoạt; mã cũ mất hiệu lực ngay khi commit. */
    Issued reissue(Long userId) {
        return transactionTemplate.execute(status -> {
            ParentActivationCode row = repository
                    .findForUpdate(userId)
                    .filter(c -> c.getActivatedAt() == null)
                    .orElseThrow(() -> GlobalException.of(ApiCode.ACTIVATION_NOT_PENDING));
            Instant now = clock.instant();
            if (now.isBefore(row.getSentAt().plus(RESEND_COOLDOWN))) {
                throw GlobalException.of(ApiCode.OTP_RESEND_TOO_SOON);
            }
            String code = generate();
            Instant expiresAt = now.plus(TTL);
            row.reissue(hash(userId, code), now, expiresAt);
            return new Issued(code, expiresAt);
        });
    }

    /** Kiểm mã và đếm lần sai trong transaction riêng (lần sai được ghi kể cả khi request báo lỗi). */
    boolean verify(Long userId, String code) {
        Boolean matched = transactionTemplate.execute(status -> {
            ParentActivationCode row = repository.findForUpdate(userId).orElse(null);
            if (row == null || !row.usable(clock.instant())) return false;
            boolean equal = MessageDigest.isEqual(
                    row.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                    hash(userId, code).getBytes(StandardCharsets.US_ASCII));
            if (!equal) row.failAttempt();
            return equal;
        });
        return Boolean.TRUE.equals(matched);
    }

    void markActivated(Long userId) {
        transactionTemplate.executeWithoutResult(status -> repository
                .findForUpdate(userId)
                .filter(c -> c.getActivatedAt() == null)
                .ifPresent(c -> c.markActivated(clock.instant())));
    }

    // Gắn userId để cùng một mã ở hai tài khoản cho hash khác nhau
    static String hash(Long userId, String code) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }
}
