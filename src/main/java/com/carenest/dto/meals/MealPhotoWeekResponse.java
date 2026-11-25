package com.carenest.dto.meals;

import com.carenest.utils.enums.FoodSampleDisposalStatus;
import com.carenest.utils.enums.FoodSampleStatus;
import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.MealSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ảnh bữa ăn của một điểm trường theo tuần (NUT-16): mỗi ngày thứ 2–6 gồm ba bữa kèm ảnh, người đăng, thực đơn, số suất,
 * sổ lưu mẫu (NUT-21) và biên bản kiểm thực (INS-01, INS-03). {@code upcoming}: ngày chưa tới, không tính thiếu ảnh.
 * {@code status} của bữa: POSTED, MISSING (đáng lẽ có ảnh nhưng đã hết hạn đăng), PENDING (còn hạn đăng),
 * NOT_EXPECTED (ngày nghỉ hoặc thực đơn không có bữa này).
 */
public record MealPhotoWeekResponse(Long campusId, LocalDate weekStart, List<WeekDay> days) {

    public record WeekDay(
            LocalDate date,
            boolean dayOff,
            boolean upcoming,
            List<WeekSession> sessions,
            List<WeekInspection> inspections) {}

    public record WeekSession(
            MealSession session,
            String status,
            WeekPhoto photo,
            List<String> dishes,
            Integer servingsNormal,
            Integer servingsSubstitute,
            WeekSample sample) {}

    /** {@code onTime}: giờ đăng nằm trong khung giờ hiện hành của bữa; null khi bữa chưa có khung giờ. */
    public record WeekPhoto(
            Long id,
            String contentType,
            long sizeBytes,
            Instant uploadedAt,
            Long uploadedBy,
            String uploaderName,
            Boolean onTime) {}

    public record WeekSample(FoodSampleStatus status, FoodSampleDisposalStatus disposalStatus) {}

    /** {@code status}: DRAFT hoặc SIGNED; mẫu chưa có biên bản thì không có dòng. */
    public record WeekInspection(InspectionTab tab, InspectionStatus status, String signedByName, Instant signedAt) {}
}
