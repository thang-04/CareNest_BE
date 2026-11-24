package com.carenest.service.child.impl;

import java.util.Optional;

/** Bù số 0 đầu mà Excel bỏ khi lưu SĐT/CCCD thành ô số (CHD-01); chỉ dùng ở đường nhập file. */
final class ExcelZeroFix {

    private ExcelZeroFix() {}

    /** SĐT 9 chữ số bắt đầu 3/5/7/8/9 ⇒ thêm "0"; còn lại empty (giữ nguyên). */
    static Optional<String> phone(String raw) {
        if (raw != null && raw.matches("[35789]\\d{8}")) return Optional.of("0" + raw);
        return Optional.empty();
    }

    /** CCCD 9–11 chữ số ⇒ đệm 0 bên trái tới 12; còn lại empty (giữ nguyên). */
    static Optional<String> citizenId(String raw) {
        if (raw != null && raw.matches("\\d{9,11}")) return Optional.of("0".repeat(12 - raw.length()) + raw);
        return Optional.empty();
    }
}
