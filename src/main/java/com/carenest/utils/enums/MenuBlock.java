package com.carenest.utils.enums;

/** NUT-13: thực đơn lập theo khối — nhà trẻ khác mẫu giáo, các lứa mẫu giáo cùng món khác định lượng. */
public enum MenuBlock {
    NURSERY,
    KINDERGARTEN;

    private static final int NURSERY_MAX_MONTHS = 36;

    /** Nhà trẻ nhận trẻ đến 36 tháng tuổi; nhóm tuổi chưa khai khoảng tuổi coi là mẫu giáo. */
    public static MenuBlock ofAgeGroup(Integer ageToMonths) {
        return ageToMonths != null && ageToMonths <= NURSERY_MAX_MONTHS ? NURSERY : KINDERGARTEN;
    }
}
