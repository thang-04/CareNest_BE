package com.carenest.dto.observation;

import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationAction;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.EvaluationStatus;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import com.carenest.utils.enums.TicketType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Đánh giá tháng / cuối năm: danh sách, chi tiết kèm số liệu nguồn từ đánh giá hằng ngày. */
public final class EvaluationDtos {

    private EvaluationDtos() {}

    /** Bản nháp hệ thống tạo; chỉ GV lớp nhận được (PHT / HT nhận null). */
    public record Draft(String content, Instant generatedAt) {}

    public record EventRow(Long id, EvaluationAction action, Long userId, String userName, Instant at) {}

    public record EvaluationRow(
            Long id,
            Long childId,
            String childName,
            Long classId,
            String className,
            Long campusId,
            Long schoolYearId,
            EvaluationPeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            EvaluationStatus status,
            Draft aiDraft,
            String finalContent,
            boolean programApplicable,
            Boolean programCompleted,
            Long confirmedBy,
            String confirmedByName,
            Instant confirmedAt,
            Integer flagCount,
            List<EventRow> history) {}

    public record ChildRef(Long id, String fullName, LocalDate dateOfBirth) {}

    /** teaches: người xem là GV lớp (thấy bản nháp); canEdit: GV đang phụ trách lớp của năm đang hoạt động. */
    public record ClassRef(Long id, String name, Long campusId, boolean teaches, boolean canEdit) {}

    public record GoalCount(int dat, int canCoGang, int chuaDat) {}

    public record GoalSummary(Map<String, GoalCount> byGoal) {}

    public record HealthSummary(
            int days,
            int normalDays,
            Map<HealthSymptom, Integer> signDays,
            Map<String, Integer> feverDays,
            Map<String, Integer> actionDays) {}

    /** Số liệu đếm từ ô tích của đánh giá hằng ngày trong kỳ (căn cứ của bản nháp). */
    public record StructuredSummary(
            int observedDays,
            HealthSummary health,
            Map<MealSession, Map<EatingLevel, Integer>> meals,
            Map<NapLevel, Integer> nap,
            Map<Emotion, Integer> emotion,
            Map<ObservedBehavior, Integer> behaviors,
            Map<Participation, Integer> participation,
            GoalSummary goals) {}

    public record GoalRef(Long id, String name, String description) {}

    public record DayHealth(List<HealthSymptom> signs, String note) {}

    public record EvaluationDay(
            LocalDate date,
            DayHealth health,
            Map<MealSession, EatingLevel> meals,
            NapLevel nap,
            Emotion emotion,
            List<ObservedBehavior> behaviors,
            String behaviorNote,
            List<GoalRef> met,
            List<GoalRef> effort,
            List<GoalRef> notMet,
            String comment) {}

    public record SummaryInfo(StructuredSummary structured, int presentDays, int assessedDays, int assessmentCount) {}

    /** days: mỗi ngày có đánh giá trong kỳ (đánh giá cuối năm để trống, dùng monthlyEvaluations). */
    public record EvaluationDetailResponse(
            EvaluationRow evaluation,
            ChildRef child,
            ClassRef cls,
            List<EvaluationDay> days,
            List<MealSession> mealSessions,
            List<EvaluationRow> monthlyEvaluations,
            SummaryInfo summary) {}

    /** completed null trả mã lỗi riêng (7346) nên không dùng @NotNull. */
    public record ProgramCompletionRequest(Boolean completed) {}

    /** Trẻ 5 tuổi hoàn thành chương trình theo lớp (sổ chỉ đạo chuyên môn mục 8); chỉ tính đánh giá cuối năm đã xác nhận. */
    public record ProgramRow(
            Long classId,
            String className,
            Long campusId,
            int children,
            int completed,
            int notCompleted,
            int unmarked,
            int completedRate) {}

    public record ProgramTotals(int children, int completed, int notCompleted, int unmarked, int completedRate) {}

    public record ProgramCompletionResponse(List<Long> campusIds, List<ProgramRow> rows, ProgramTotals totals) {}

    /** content null / rỗng trả mã lỗi riêng (7340) nên không dùng @NotBlank. */
    public record EvaluationContentRequest(String content) {}

    // ---------- Hồ sơ / tiến độ phát triển ----------

    /** Trẻ đang học lớp trong phạm vi (GV lớp mình, PHT điểm trường, HT toàn trường). */
    public record DevelopmentChildRow(
            Long id, String fullName, LocalDate dateOfBirth, Long classId, String className, Long campusId) {}

    public record DateRange(LocalDate start, LocalDate end) {}

    /** Tuần thứ 2–6; criteriaRate = lượt đạt / lượt được đánh giá mục tiêu (null khi chưa có lượt nào). */
    public record DevelopmentWeek(
            LocalDate weekStart,
            LocalDate weekEnd,
            int assessments,
            int presentDays,
            int flags,
            Integer criteriaRate,
            boolean finished) {}

    public record TicketRef(Long id, TicketType type, LocalDate periodStart, LocalDate periodEnd) {}

    /** Chỉ dữ liệu đã ghi nhận và đánh giá đã xác nhận; range.end không quá hôm nay. */
    public record DevelopmentResponse(
            ChildRef child,
            ClassRef cls,
            DateRange range,
            int presentDays,
            int flagCount,
            StructuredSummary summary,
            List<EvaluationDay> days,
            List<MealSession> mealSessions,
            List<DevelopmentWeek> weeks,
            List<EvaluationRow> evaluations,
            List<EvaluationRow> yearEnd,
            List<TicketRef> tickets) {}
}
