package com.carenest.service.account.impl;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Mật khẩu tạm cho phụ huynh (AUTH-18): ngẫu nhiên, đủ chữ hoa/thường/số, bỏ ký tự dễ nhầm khi in (0/O, 1/l/I).
 * Chỉ trả cho người cấp đúng một lần; không lưu, không log.
 */
@Component
class TemporaryPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = UPPER + LOWER + DIGITS;
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    String generate() {
        char[] password = new char[LENGTH];
        password[0] = pick(UPPER);
        password[1] = pick(LOWER);
        password[2] = pick(DIGITS);
        for (int i = 3; i < LENGTH; i++) {
            password[i] = pick(ALL);
        }
        // Trộn để 3 ký tự bắt buộc không luôn nằm đầu chuỗi
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = password[i];
            password[i] = password[j];
            password[j] = swap;
        }
        return new String(password);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
