package com.carenest.utils.enums;

import java.math.BigDecimal;

/** Mức thân nhiệt đo nách, cố định cho mọi nhóm tuổi (Cần chốt 14/10/2026); chỉ để hiển thị, không tự chọn "Có dấu hiệu". */
public enum TemperatureLevel {
    LOW,
    NORMAL,
    WATCH,
    FEVER,
    HIGH,
    VERY_HIGH;

    private static final BigDecimal LOW_BELOW = new BigDecimal("35.5");
    private static final BigDecimal WATCH_FROM = new BigDecimal("37.5");
    private static final BigDecimal FEVER_FROM = new BigDecimal("38.0");
    private static final BigDecimal HIGH_FROM = new BigDecimal("38.5");
    private static final BigDecimal VERY_HIGH_FROM = new BigDecimal("39.0");

    /** null khi chưa đo. */
    public static TemperatureLevel of(BigDecimal temperature) {
        if (temperature == null) {
            return null;
        }
        if (temperature.compareTo(VERY_HIGH_FROM) >= 0) {
            return VERY_HIGH;
        }
        if (temperature.compareTo(HIGH_FROM) >= 0) {
            return HIGH;
        }
        if (temperature.compareTo(FEVER_FROM) >= 0) {
            return FEVER;
        }
        if (temperature.compareTo(WATCH_FROM) >= 0) {
            return WATCH;
        }
        return temperature.compareTo(LOW_BELOW) < 0 ? LOW : NORMAL;
    }
}
