package com.carenest.service.health.impl;

import com.carenest.utils.ImageFiles;
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
        String actual = ImageFiles.sniff(content);
        return declared.equals(actual) ? new HealthImageData(content, actual) : null;
    }
}
