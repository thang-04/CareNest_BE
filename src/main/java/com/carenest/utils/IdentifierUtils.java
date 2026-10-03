package com.carenest.utils;

import java.util.Locale;

/**
 * Normalizes login identifiers so the same email/phone is always stored and looked up in one form.
 */
public final class IdentifierUtils {

    private IdentifierUtils() {
    }

    public static boolean isEmail(String identifier) {
        return identifier.contains("@");
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Converts "+84xxxxxxxxx" to "0xxxxxxxxx" and removes spaces, dots and dashes.
     */
    public static String normalizePhone(String phone) {
        String digits = phone.trim().replaceAll("[\\s.-]", "");
        if (digits.startsWith("+84")) {
            return "0" + digits.substring(3);
        }
        return digits;
    }
}
