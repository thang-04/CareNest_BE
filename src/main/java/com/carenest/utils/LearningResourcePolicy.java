package com.carenest.utils;

import com.carenest.exception.GlobalException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

public final class LearningResourcePolicy {
    private LearningResourcePolicy() {}

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"));

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

    public static String inspect(String name, byte[] head, long size) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(ext);
        if (type == null) throw GlobalException.of(ApiCode.UNSUPPORTED_MEDIA_TYPE);
        long limit = (type.startsWith("image/") ? 5 : type.startsWith("video/") ? 100 : 20) * 1024L * 1024;
        if (size > limit) throw GlobalException.of(ApiCode.FILE_TOO_LARGE);
        if (size <= 0) throw GlobalException.of(ApiCode.FILE_EMPTY);
        String ascii = new String(head, StandardCharsets.ISO_8859_1);
        boolean valid =
                switch (ext) {
                    case "jpg", "jpeg" ->
                        head.length >= 3 && (head[0] & 255) == 255 && (head[1] & 255) == 216 && (head[2] & 255) == 255;
                    case "png" -> ascii.startsWith("\u0089PNG\r\n\u001a\n");
                    case "gif" -> ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a");
                    case "webp" ->
                        ascii.startsWith("RIFF")
                                && ascii.length() >= 12
                                && ascii.substring(8, 12).equals("WEBP");
                    case "mp4" -> ascii.length() >= 12 && ascii.substring(4, 8).equals("ftyp");
                    case "webm" ->
                        head.length >= 4 && HexFormat.of().formatHex(head, 0, 4).equals("1a45dfa3");
                    case "pdf" -> ascii.startsWith("%PDF-");
                    case "pptx", "docx", "xlsx" -> ascii.startsWith("PK\u0003\u0004");
                    default ->
                        !ascii.contains("\u0000")
                                && !ascii.stripLeading().startsWith("<")
                                && ascii.chars().noneMatch(c -> c < 32 && c != 9 && c != 10 && c != 13);
                };
        if (!valid) throw GlobalException.of(ApiCode.FILE_CONTENT_MISMATCH);
        return type;
    }
}
