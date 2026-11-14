package com.carenest.dto.observation;

import com.carenest.utils.enums.EvaluationLockKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/** Hạn, khóa và mở lại đánh giá cuối chủ đề / học kỳ / cuối năm theo lớp – kỳ (OBS-06, OBS-27, OBS-28). */
public final class EvaluationDeadlineDtos {

    private EvaluationDeadlineDtos() {}

    /**
     * Một kỳ đánh giá của lớp: mở từ openFrom, khóa sau 23:59:59 ngày deadline; unlockedUntil = đang được mở lại tới lúc
     * đó (null nếu không). periodKey của chủ đề là dòng gốc (giữ qua các bản kế hoạch); themeId = id chủ đề hiện hành.
     */
    public record EvaluationPeriod(
            EvaluationLockKind kind,
            String periodKey,
            String label,
            LocalDate openFrom,
            LocalDate deadline,
            boolean open,
            boolean locked,
            Instant unlockedUntil,
            Long themeId) {

        public boolean writable() {
            return !locked || unlockedUntil != null;
        }

        /** Tham số chọn kỳ trên màn Web: id chủ đề hiện hành, hoặc periodKey. */
        public String pageKey() {
            return themeId != null ? String.valueOf(themeId) : periodKey;
        }
    }

    public record EvaluationUnlockRequest(
            @NotNull EvaluationLockKind kind,
            @NotNull Long classId,
            @NotBlank @Size(max = 40) String periodKey,
            @NotNull Instant validUntil,
            @NotBlank @Size(max = 300) String reason) {}

    /** Một lần mở lại đánh giá; active = còn hiệu lực lúc trả về. */
    public record EvaluationUnlockResponse(
            Long id,
            EvaluationLockKind kind,
            Long classId,
            String className,
            Long campusId,
            String periodKey,
            String periodLabel,
            Instant validUntil,
            String reason,
            Long unlockedBy,
            String unlockedByName,
            Instant unlockedAt,
            Long revokedBy,
            String revokedByName,
            Instant revokedAt,
            boolean active) {}

    /**
     * Lớp quá hạn chưa xong (BGH nhắc): kind WEEKLY (nhận xét tuần) hoặc THEME / SEMESTER / YEAR; done / total = đã
     * gửi hoặc xác nhận / cần làm.
     */
    public record OverdueClassItem(
            String kind,
            Long classId,
            String className,
            Long campusId,
            String periodKey,
            String label,
            LocalDate deadline,
            int done,
            int total) {}
}
