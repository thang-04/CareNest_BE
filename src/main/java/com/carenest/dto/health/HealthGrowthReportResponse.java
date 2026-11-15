package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthGrowthLevel;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo tổng hợp tăng trưởng của một tháng theo mẫu 5.1 (HLT-20): mỗi điểm trường trong phạm vi một khối (đợt/kỳ của
 * tháng, lớp, tổng MG, NT, điểm trường), khối ngoài cùng cộng các điểm trường. Tỷ lệ % do client tính.
 *
 * @param month tháng {@code yyyy-MM}
 */
public record HealthGrowthReportResponse(
        String month,
        List<HealthGrowthCampus> campuses,
        HealthGrowthCounts preschool,
        HealthGrowthCounts nursery,
        HealthGrowthCounts total) {

    /** Không có đợt/kỳ trong tháng ⇒ {@code campaigns}, {@code classes} rỗng, mọi số 0. */
    public record HealthGrowthCampus(
            Long campusId,
            String campusName,
            List<HealthGrowthCampaign> campaigns,
            List<HealthGrowthClass> classes,
            HealthGrowthCounts preschool,
            HealthGrowthCounts nursery,
            HealthGrowthCounts total) {}

    /** Đợt khám hoặc kỳ cân đo chưa hủy có ngày bắt đầu trong tháng. */
    public record HealthGrowthCampaign(
            Long id,
            String name,
            HealthCheckKind kind,
            HealthCheckStatus status,
            LocalDate startDate,
            LocalDate endDate) {}

    /** {@code level} null khi lớp chưa có nhóm tuổi: chỉ cộng vào tổng điểm trường. */
    public record HealthGrowthClass(
            Long classId, String className, String ageGroupName, HealthGrowthLevel level, HealthGrowthCounts counts) {}

    /**
     * Số trẻ: {@code enrolled} của lớp, {@code measured} được cân đo; cân nặng/tuổi {@code weightNormal},
     * {@code underweight} (< −2SD); chiều cao/tuổi {@code heightNormal}, {@code stunted} (< −2SD); BMI/tuổi
     * {@code bmiNormal} (gồm nguy cơ thừa cân), {@code wasted} (gầy còm), {@code overweight}, {@code obese};
     * {@code unclassified} được cân đo mà có chỉ số không xếp được (ngoài 0–84 tháng, thiếu ngày sinh/giới tính).
     */
    public record HealthGrowthCounts(
            int enrolled,
            int measured,
            int weightNormal,
            int underweight,
            int heightNormal,
            int stunted,
            int bmiNormal,
            int wasted,
            int overweight,
            int obese,
            int unclassified) {}
}
