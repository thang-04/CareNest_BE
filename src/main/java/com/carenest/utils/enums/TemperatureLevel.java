package com.carenest.utils.enums;

import java.math.BigDecimal;

/**
 * Mức thân nhiệt đo nách của trẻ mầm non theo hướng dẫn nhi khoa Bộ Y tế: sốt khi ≥ 37,5 °C; nhẹ / vừa / cao / rất cao
 * tách ở 38 / 39 / 40 °C (Cần chốt 31/10/2026). Chỉ để hiển thị, không tự chọn "Có dấu hiệu".
 */
public enum TemperatureLevel {
    /** < 35,5 °C: thân nhiệt thấp. */
    LOW,
    /** 35,5 – 37,4 °C. */
    NORMAL,
    /** 37,5 – 37,9 °C: sốt nhẹ. */
    MILD,
    /** 38,0 – 38,9 °C: sốt vừa. */
    MODERATE,
    /** 39,0 – 39,9 °C: sốt cao. */
    HIGH,
    /** ≥ 40,0 °C: sốt rất cao. */
    VERY_HIGH;

    private static final BigDecimal LOW_BELOW = new BigDecimal("35.5");
    private static final BigDecimal MILD_FROM = new BigDecimal("37.5");
    private static final BigDecimal MODERATE_FROM = new BigDecimal("38.0");
    private static final BigDecimal HIGH_FROM = new BigDecimal("39.0");
    private static final BigDecimal VERY_HIGH_FROM = new BigDecimal("40.0");

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
        if (temperature.compareTo(MODERATE_FROM) >= 0) {
            return MODERATE;
        }
        if (temperature.compareTo(MILD_FROM) >= 0) {
            return MILD;
        }
        return temperature.compareTo(LOW_BELOW) < 0 ? LOW : NORMAL;
    }
}
