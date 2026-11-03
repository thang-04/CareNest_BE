package com.carenest.dto.health;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Nội dung phiếu khám Phụ lục II (HLT-08), lưu JSON ở {@code health_measurements.exam}; chiều cao, cân nặng, vòng đầu
 * nằm ở lần đo, không ở đây. Lựa chọn lưu mã ({@code YES}, {@code NORMAL}...), {@code ""} = chưa chọn. Service chuẩn
 * hóa trước khi lưu: đủ mọi khóa, chữ đã cắt khoảng trắng và giới hạn độ dài, số đã làm tròn.
 *
 * @param step bước người nhập đang ở (0–4), chỉ để mở lại đúng bước
 * @param clinical 24 mục khám lâm sàng theo mã mục, cộng {@code behaviorNote}
 */
public record HealthExamForm(
        Integer step,
        HealthExamAdmin admin,
        HealthExamHistory history,
        HealthExamVitals vitals,
        HealthExamNutrition nutrition,
        HealthExamDevelopment development,
        HealthExamImmunization immunization,
        Map<String, String> clinical,
        HealthExamConclusion conclusion) {

    /**
     * Hành chính: lấy từ hồ sơ trẻ (người nhập không sửa), trừ {@code hasCompanion} và {@code companion.guardianIndex}.
     */
    public record HealthExamAdmin(
            String childIdNumber,
            String premature,
            Integer gestationWeeks,
            String ethnicity,
            String beneficiary,
            String paymentSource,
            String bloodType,
            HealthExamAddress address,
            String hasCompanion,
            HealthExamCompanion companion) {}

    public record HealthExamAddress(String province, String ward, String street) {}

    /** Người đi cùng trẻ, chọn trong danh sách phụ huynh của hồ sơ trẻ theo {@code guardianIndex}. */
    public record HealthExamCompanion(
            Integer guardianIndex, String fullName, String relation, String phone, String idNumber) {}

    /** Tiền sử bản thân, gia đình, tiếp xúc người bệnh lao: {@code YES} / {@code NO} / {@code ""}. */
    public record HealthExamHistory(
            String self,
            String selfText,
            String family,
            String familyText,
            String tbContact,
            LocalDate tbContactDate) {}

    public record HealthExamVitals(
            BigDecimal temperatureC,
            String temperatureStatus,
            BigDecimal pulse,
            String pulseStatus,
            BigDecimal respiratoryRate,
            String respiratoryStatus) {}

    public record HealthExamNutrition(
            BigDecimal lengthForAgeSd,
            BigDecimal weightForAgeSd,
            String headStatus,
            BigDecimal armCircumferenceMm,
            List<String> statuses) {}

    public record HealthExamDevelopment(String mental, String motor, String autismRisk) {}

    public record HealthExamImmunization(String bcg, String hepB1, String fullSchedule) {}

    /**
     * Kết luận của bác sĩ. Mẫu STANDARD dùng các cờ, {@code issueKind}, {@code action}; mẫu FOLLOW_UP dùng
     * {@code followUpFinding}.
     */
    public record HealthExamConclusion(
            boolean normal,
            boolean tbRisk,
            boolean healthIssue,
            String issueKind,
            String followUpFinding,
            String action,
            String issueText,
            String adviceText,
            String specialty,
            String doctorName,
            String parentOpinion) {}
}
