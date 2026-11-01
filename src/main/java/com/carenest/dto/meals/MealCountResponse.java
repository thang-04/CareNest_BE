package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.StaffRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sổ báo ăn một bữa của một điểm trường. id null + status OPEN = tạm tính trước giờ chốt. Người duyệt thấy số chờ xác
 * nhận lại (needsReconfirmation); bếp chỉ thấy số đã xác nhận. confirmedByRole: HT hay PHT xác nhận.
 */
public record MealCountResponse(
        Long id,
        Long campusId,
        Long schoolYearId,
        LocalDate date,
        MealSession session,
        String status,
        boolean needsReconfirmation,
        Instant aggregatedAt,
        Long confirmedBy,
        String confirmedByName,
        StaffRole confirmedByRole,
        Instant confirmedAt,
        List<Line> classes,
        Totals totals,
        List<Adjustment> adjustments,
        boolean canConfirm,
        boolean canAdjust) {

    public record Line(
            Long classId,
            String className,
            Long ageGroupId,
            int present,
            int absent,
            int missing,
            int normal,
            int substitute) {}

    public record Totals(int present, int absent, int missing, int normal, int substitute) {}

    /** Điều chỉnh của PHT điểm trường: chênh lệch + lý do. */
    public record Adjustment(
            Instant at,
            String action,
            Long userId,
            String userName,
            Long classId,
            int normal,
            int substitute,
            String note) {}
}
