package com.carenest.utils;

import java.text.Normalizer;

/**
 * Chuẩn hóa tên do người dùng gõ (tên điểm trường, tên lớp) trước khi lưu và so trùng (Cần chốt 19/11/2026): bỏ khoảng
 * trắng đầu/cuối, gộp nhiều khoảng trắng thành một, đưa dấu tiếng Việt về một kiểu mã hóa (NFC). So trùng không phân
 * biệt hoa thường do truy vấn đảm nhận.
 */
public final class DisplayNames {

    private DisplayNames() {}

    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        return Normalizer.normalize(name, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
    }
}
