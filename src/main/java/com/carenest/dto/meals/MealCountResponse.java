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

    /**
     * confirmedNormal / confirmedSubstitute: số đã chốt trước đó khi sổ đang chờ xác nhận lại (NUT-03, ADR-0005), để
     * người duyệt so với số mới; null khi không có số chờ. substituteAdjusted: suất thay thế đã điều chỉnh tay (NUT-02);
     * attendanceChanged: điểm danh đổi sau lần điều chỉnh đó, cần kiểm tra lại.
     */
    public record Line(
            Long classId,
            String className,
            Long ageGroupId,
            int present,
            int absent,
            int missing,
            int normal,
            int substitute,
            Integer confirmedNormal,
            Integer confirmedSubstitute,
            boolean substituteAdjusted,
            boolean attendanceChanged) {}

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
