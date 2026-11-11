package com.carenest.integration.storage;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** Đuôi tệp được hỗ trợ, MIME tương ứng và nhận dạng theo chữ ký đầu tệp (không tin đuôi hay loại client khai báo). */
public final class FileTypes {
    private FileTypes() {}

    /** Số byte đầu tệp cần đọc để nhận dạng. */
    public static final int HEAD_BYTES = 8192;

    private static final Map<String, String> MIME = Map.ofEntries(
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

    public static boolean supported(String ext) {
        return ext != null && MIME.containsKey(ext);
    }

    /** MIME của đuôi; null khi không hỗ trợ. */
    public static String mime(String ext) {
        return ext == null ? null : MIME.get(ext);
    }

    /** {@code image}, {@code video} hoặc {@code other} — nhóm dùng cho giới hạn dung lượng theo nhóm. */
    public static String kind(String ext) {
        String mime = mime(ext);
        if (mime == null) return "other";
        if (mime.startsWith("image/")) return "image";
        return mime.startsWith("video/") ? "video" : "other";
    }

    /** Đuôi chuẩn cho MIME ảnh đã nhận dạng (dùng khi chỉ có nội dung, không có tên tệp). */
    public static String extensionOf(String mime) {
        return switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> null;
        };
    }

    /** Đuôi viết thường của tên tệp; null khi không có đuôi. */
    public static String extension(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? null : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Nội dung đầu tệp có đúng định dạng của đuôi không. */
    public static boolean matches(String ext, byte[] head) {
        if (head == null || !supported(ext)) return false;
        String ascii = new String(head, StandardCharsets.ISO_8859_1);
        return switch (ext) {
            case "jpg", "jpeg" ->
                head.length >= 3 && (head[0] & 255) == 255 && (head[1] & 255) == 216 && (head[2] & 255) == 255;
            case "png" -> ascii.startsWith("\u0089PNG\r\n\u001a\n");
            case "gif" -> ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a");
            case "webp" -> ascii.startsWith("RIFF") && ascii.length() >= 12 && ascii.startsWith("WEBP", 8);
            case "mp4" -> ascii.length() >= 12 && ascii.startsWith("ftyp", 4);
            case "webm" -> head.length >= 4 && HexFormat.of().formatHex(head, 0, 4).equals("1a45dfa3");
            case "pdf" -> ascii.startsWith("%PDF-");
            case "pptx", "docx", "xlsx" -> ascii.startsWith("PK\u0003\u0004");
            // txt/csv: văn bản thuần, không nhị phân, không HTML/XML
            default ->
                !ascii.contains("\u0000")
                        && !ascii.stripLeading().startsWith("<")
                        && ascii.chars().noneMatch(c -> c < 32 && c != 9 && c != 10 && c != 13);
        };
    }
}
