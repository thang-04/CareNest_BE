package com.carenest.utils;

import java.util.Optional;
import java.util.regex.Pattern;

/** Số CCCD của phụ huynh, khóa nhận diện tài khoản phụ huynh (AUTH-18): đúng 12 chữ số, nhập có khoảng trắng vẫn nhận. */
public final class CitizenIds {

    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern CITIZEN_ID = Pattern.compile("^\\d{12}$");

    private CitizenIds() {}

    /** Bỏ khoảng trắng; trả rỗng nếu không phải 12 chữ số. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = WHITESPACE.matcher(raw).replaceAll("");
        return CITIZEN_ID.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
    }
}
