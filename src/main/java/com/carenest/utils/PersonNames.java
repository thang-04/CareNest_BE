package com.carenest.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Họ tên hiển thị cho người tra SĐT: đủ để đối chiếu với người thật, không lộ họ tên tài khoản ngoài phạm vi. */
public final class PersonNames {

    private PersonNames() {}

    /** Mỗi từ giữ chữ đầu, còn lại thành {@code *}: "Nguyễn Thị Lan" ⇒ "N***** T** L**". */
    public static String mask(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        return Arrays.stream(fullName.trim().split("\\s+"))
                .map(word -> word.substring(0, word.offsetByCodePoints(0, 1))
                        + "*".repeat(word.codePointCount(0, word.length()) - 1))
                .collect(Collectors.joining(" "));
    }
}
