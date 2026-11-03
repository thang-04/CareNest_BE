package com.carenest.dto.observation;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

/** Nhận xét chung cả lớp theo tháng (OBS-26). */
public final class ClassMonthlyCommentDtos {
    private ClassMonthlyCommentDtos() {}

    /**
     * status: NOT_READY (tháng chưa kết thúc), AWAITING_REVIEW (GV chưa xác nhận), CONFIRMED. content null khi người xem
     * không thấy bản chưa xác nhận (BGH). canEdit: GV đang dạy lớp, năm đang hoạt động, chưa xác nhận.
     */
    public record ClassMonthlyCommentResponse(
            Long classId,
            String className,
            LocalDate month,
            String status,
            String draft,
            String content,
            String confirmedByName,
            Instant confirmedAt,
            Instant updatedAt,
            boolean canEdit) {}

    public record ClassMonthlyCommentRequest(
            @NotNull Long classId, @NotNull LocalDate month, String content) {}
}
