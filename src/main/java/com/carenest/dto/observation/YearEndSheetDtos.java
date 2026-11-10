package com.carenest.dto.observation;

import com.carenest.utils.enums.EvaluationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Phiếu đánh giá sự phát triển của trẻ cuối năm (OBS-28): MT kế hoạch năm học theo lĩnh vực, Đạt / Chưa đạt. */
public final class YearEndSheetDtos {
    private YearEndSheetDtos() {}

    /**
     * goalId = dòng gốc MT; met null = chưa có kết quả; auto = kết quả tổng hợp từ cuối chủ đề, source = tên chủ đề lấy
     * kết quả; manual = GV sửa tay.
     */
    public record SheetGoal(
            Long goalId, Short goalNo, String text, Boolean met, boolean manual, Boolean auto, String source) {}

    /** domainId null = MT chưa gắn lĩnh vực; comment = nhận xét của GV dưới lĩnh vực (null = để trống). */
    public record SheetDomain(Long domainId, String name, List<SheetGoal> goals, String comment) {}

    /** Lần cân đo gần nhất tới cuối kỳ (phần sức khỏe); null = chưa cân đo. */
    public record SheetMeasurement(LocalDate date, BigDecimal weightKg, BigDecimal heightCm) {}

    /** 4 lưu ý để điều chỉnh kế hoạch giáo dục năm học / độ tuổi tiếp theo; null = để trống. */
    public record SheetNotes(String forChild, String forTeacher, String forSchool, String forParent) {}

    /** teachers: GV chủ nhiệm (chưa có thì các GV lớp) để in chỗ ký; canEdit: GV lớp, năm đang hoạt động, chưa xác nhận. */
    public record YearEndSheetResponse(
            Long evaluationId,
            Long classId,
            String ageGroupName,
            List<String> teachers,
            SheetMeasurement measurement,
            List<SheetDomain> domains,
            int metCount,
            int notMetCount,
            SheetNotes notes,
            EvaluationStatus status,
            boolean canEdit,
            Instant confirmedAt) {}

    public record SheetGoalInput(@NotNull Long goalId, Boolean met) {}

    /**
     * goals: ô GV gửi (met null hoặc trùng kết quả tổng hợp = bỏ sửa tay); lưu ý tối đa 1000 ký tự. domainComments:
     * nhận xét theo lĩnh vực, khóa = domainId (lĩnh vực chưa gắn mã: 0), chuỗi rỗng = xóa; null = giữ nhận xét đã lưu.
     */
    public record YearEndSheetRequest(
            @Size(max = 500) List<@Valid SheetGoalInput> goals,
            String forChild,
            String forTeacher,
            String forSchool,
            String forParent,
            @Size(max = 20) Map<Long, String> domainComments) {

        public YearEndSheetRequest(
                List<SheetGoalInput> goals, String forChild, String forTeacher, String forSchool, String forParent) {
            this(goals, forChild, forTeacher, forSchool, forParent, null);
        }
    }
}
