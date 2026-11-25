package com.carenest.dto.health;

import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Phiếu khám đã kết luận phụ huynh xem trên App (HLT-11, PAR-03): đủ các phần Phụ lục II trừ hành chính (số định danh,
 * địa chỉ, người đi cùng). Lựa chọn là mã như phiếu Web; không có nháp, lịch sử sửa, người nhập, điểm cần bác sĩ xem
 * lại. Dữ liệu sức khỏe: không log.
 */
public record ParentHealthExamResponse(
        Long resultId,
        Long childId,
        String campaignName,
        String examiner,
        LocalDate date,
        Integer ageMonths,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal headCircumferenceCm,
        HealthFollowUpLevel followUpLevel,
        HealthConclusionCategory conclusionCategory,
        HealthExamForm.HealthExamHistory history,
        HealthExamForm.HealthExamVitals vitals,
        HealthExamForm.HealthExamNutrition nutrition,
        HealthExamForm.HealthExamDevelopment development,
        HealthExamForm.HealthExamImmunization immunization,
        Map<String, String> clinical,
        HealthExamForm.HealthExamConclusion conclusion,
        Instant publishedAt) {
    @Override
    public String toString() {
        return "ParentHealthExamResponse[resultId=" + resultId + ", childId=" + childId + "]";
    }
}
