package com.carenest.utils.enums;

import java.time.LocalDate;

/**
 * Khoảng thời gian phân tích xu hướng (HLT-21), cùng bộ lọc biểu đồ màn Xu hướng: tính lùi từ hôm nay; {@code ALL} lấy
 * mọi lần đo đã công bố.
 */
public enum HealthTrendPeriod {
    MONTHS_6(6, "6 tháng qua"),
    MONTHS_12(12, "12 tháng qua"),
    ALL(null, "toàn bộ quá trình theo dõi");

    private final Integer months;
    private final String phrase;

    HealthTrendPeriod(Integer months, String phrase) {
        this.months = months;
        this.phrase = phrase;
    }

    /** Ngày đầu khoảng (gồm cả ngày đó); null khi lấy toàn bộ. */
    public LocalDate since(LocalDate today) {
        return months == null ? null : today.minusMonths(months);
    }

    /** Cụm dùng trong prompt và câu mở đầu tổng quan ("Trong 6 tháng qua, …"). */
    public String phrase() {
        return phrase;
    }
}
