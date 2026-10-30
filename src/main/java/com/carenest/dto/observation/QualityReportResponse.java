package com.carenest.dto.observation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo chất lượng tháng theo Biểu sổ chỉ đạo chuyên môn PHT: mục 4.2 (từng lớp trong tháng) và 4.1 (trẻ ra lớp bình
 * quân từng tháng của năm học). month / months dạng yyyy-MM; tỷ lệ là % làm tròn, null khi không có mẫu số.
 */
public record QualityReportResponse(
        String month,
        LocalDate from,
        LocalDate to,
        List<String> months,
        List<Long> campusIds,
        List<QualityRow> rows,
        List<QualityTotal> totals) {

    /** level: NHA_TRE (nhóm tuổi dưới 36 tháng) hoặc MAU_GIAO. */
    public record QualityRow(
            Long classId,
            String className,
            Long campusId,
            Long ageGroupId,
            String level,
            int children,
            int schoolDays,
            Integer avgPresent,
            int presentDays,
            int possibleDays,
            Integer attendanceRate,
            int mealChildren,
            Integer mealRate,
            int diligentChildren,
            Integer diligentRate,
            int goodChildren,
            Integer goodRate,
            int allRoundChildren,
            Integer allRoundRate,
            Map<String, Integer> avgPresentByMonth) {}

    /** level: NHA_TRE, MAU_GIAO hoặc TOAN_TRUONG. */
    public record QualityTotal(
            String level,
            int classes,
            int children,
            Integer avgPresent,
            Integer attendanceRate,
            int mealChildren,
            Integer mealRate,
            int diligentChildren,
            Integer diligentRate,
            int goodChildren,
            Integer goodRate,
            int allRoundChildren,
            Integer allRoundRate,
            Map<String, Integer> avgPresentByMonth) {}
}
