package com.carenest.utils;

import com.carenest.exception.GlobalException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LearningResourcePolicy {
    private LearningResourcePolicy() {}

    public static String signature(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static boolean hasActivity(String text, int index) {
        if (index < 0) return false;
        int count = 0;
        for (String raw : text.split("\n")) {
            // Khớp whitespace của JS khi dán từ Word, kể cả NBSP/BOM.
            boolean sub = raw.replaceFirst("^[\\s\\p{Z}\\uFEFF]*", "").startsWith("+");
            String line = raw.replaceFirst("^[\\s\\p{Z}\\uFEFF\\-–•+*]+", "").replaceFirst("[\\s\\p{Z}\\uFEFF]+$", "");
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (!sub && colon >= 0 && line.substring(colon + 1).matches("[\\s\\p{Z}\\uFEFF]*")) continue;
            if (count++ == index) return true;
        }
        return false;
    }

    public static String safeName(String name) {
        String clean = (name == null ? "file" : name).replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .strip();
        if (clean.isBlank() || clean.length() > 255) throw GlobalException.of(ApiCode.FILE_NAME_INVALID);
        return clean;
    }
}
