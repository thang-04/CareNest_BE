package com.carenest.utils;

import java.util.Optional;
import java.util.regex.Pattern;

/** SĐT Việt Nam dùng làm tên đăng nhập phụ huynh (AUTH-18): 10 số, bắt đầu bằng 0. */
public final class PhoneNumbers {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");
    private static final Pattern LOCAL_MOBILE = Pattern.compile("^0\\d{9}$");

    private PhoneNumbers() {}

    /** Bỏ dấu phân cách, đổi tiền tố quốc gia +84/84 thành 0; trả rỗng nếu không phải SĐT hợp lệ. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return LOCAL_MOBILE.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
    }
}
