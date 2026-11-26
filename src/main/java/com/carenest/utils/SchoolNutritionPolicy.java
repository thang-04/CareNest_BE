package com.carenest.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * NUT-33: khoảng tỷ lệ năng lượng đạm/béo/đường và phần nhu cầu cả ngày ăn tại trường theo Thông tư 28/2016/TT-BGDĐT.
 * Chỉ là giá trị nạp cho bản định mức đầu tiên của nhóm tuổi; sau đó khoảng được lưu theo từng bản và mọi nơi đọc từ bản đã lưu.
 */
public final class SchoolNutritionPolicy {

    /** Nhóm có tuổi bắt đầu dưới mốc này (tháng) là nhà trẻ, từ mốc trở lên là mẫu giáo. */
    public static final int NURSERY_BEFORE_MONTHS = 36;

    public record Range(BigDecimal min, BigDecimal max) {}

    public record Policy(Range protein, Range lipid, Range glucid, Range dailyShare) {}

    private static final Policy NURSERY = new Policy(
            range(13, 20), range(30, 40), range(47, 50), new Range(share("0.60"), share("0.70")));
    private static final Policy PRESCHOOL = new Policy(
            range(13, 20), range(25, 35), range(52, 60), new Range(share("0.50"), share("0.55")));

    private SchoolNutritionPolicy() {}

    /** Chính sách theo tuổi bắt đầu của nhóm; null = nhóm chưa có tuổi nên không tự suy ra được. */
    public static Policy forAgeFromMonths(Integer ageFromMonths) {
        if (ageFromMonths == null) {
            return null;
        }
        return ageFromMonths < NURSERY_BEFORE_MONTHS ? NURSERY : PRESCHOOL;
    }

    /** Năng lượng khuyến nghị tại trường = nhu cầu cả ngày × phần ăn tại trường; làm tròn 2 số lẻ. */
    public static BigDecimal atSchool(BigDecimal daily, BigDecimal share) {
        return daily == null ? null : daily.multiply(share).setScale(2, RoundingMode.HALF_UP);
    }

    private static Range range(int min, int max) {
        return new Range(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    private static BigDecimal share(String value) {
        return new BigDecimal(value);
    }
}
