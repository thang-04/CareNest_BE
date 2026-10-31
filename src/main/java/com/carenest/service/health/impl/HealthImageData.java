package com.carenest.service.health.impl;

import java.util.Base64;
import java.util.Map;

/**
 * Ảnh kết quả khám chuyên khoa gửi dạng data URL ({@code data:image/jpeg;base64,...}): chỉ JPG, PNG, WEBP, tối đa 1 MB
 * sau giải mã; loại tệp kiểm theo chữ ký đầu tệp, không tin loại khai báo.
 */
record HealthImageData(byte[] content, String contentType) {

    static final int MAX_BYTES = 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg",
            "image/jpeg",
            "image/jpg",
            "image/jpeg",
            "image/png",
            "image/png",
            "image/webp",
            "image/webp");

    /** Null khi không phải data URL ảnh hợp lệ. */
    static HealthImageData decode(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            return null;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String header = dataUrl.substring(5, comma).toLowerCase();
        if (!header.endsWith(";base64")) {
            return null;
        }
        String declared = TYPES.get(header.substring(0, header.length() - ";base64".length()));
        // Chặn chuỗi quá dài trước khi giải mã (base64 dài hơn ~4/3 kích thước tệp)
        if (declared == null || dataUrl.length() - comma > MAX_BYTES / 3 * 4 + 1024) {
            return null;
        }
        byte[] content;
        try {
            content = Base64.getMimeDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (content.length == 0 || content.length > MAX_BYTES) {
            return null;
        }
        String actual = sniff(content);
        return declared.equals(actual) ? new HealthImageData(content, actual) : null;
    }

    private static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P'
                && b[2] == 'N'
                && b[3] == 'G'
                && b[4] == 0x0D
                && b[5] == 0x0A
                && b[6] == 0x1A
                && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
