package com.carenest.dto.observation;

import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
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

/**
 * App phụ huynh xem đánh giá, khen thưởng của con (AUTH-04, OBS-36). Chỉ dữ liệu đã chốt: không nháp, không lịch sử
 * sửa, không nhận xét AI chưa được giáo viên xác nhận; mỗi dòng mang lớp của thời điểm đó.
 */
public final class ParentAssessmentDtos {

    private ParentAssessmentDtos() {}

    /** Kết quả một hoạt động của giáo án trong ngày; mục tiêu là câu chữ của kế hoạch, không phải mã. */
    public record ParentActivity(
            String key,
            String label,
            Participation participation,
            List<String> goalsMet,
            List<String> goalsEffort,
            List<String> goalsNotMet) {}

    /** status: ASSESSED (đã đánh giá) / ABSENT (vắng) / NOT_RECORDED (cô chưa ghi). */
    public record ParentDailyAssessment(
            LocalDate date,
            String status,
            Long classId,
            String className,
            String teacherName,
            Instant savedAt,
            List<HealthSymptom> symptoms,
            String healthNote,
            Map<MealSession, EatingLevel> meals,
            NapLevel nap,
            Emotion emotion,
            Participation participation,
            List<ObservedBehavior> behaviors,
            String behaviorNote,
            List<ParentActivity> activities,
            Boolean flag,
            String comment) {}

    public record ParentGoal(Short goalNo, String text, Boolean met) {}

    public record ParentDomain(String name, List<ParentGoal> goals, String comment) {}

    /** Đánh giá cuối chủ đề đã xác nhận: kết quả của riêng trẻ. */
    public record ParentThemeResult(
            Long classId,
            String className,
            String themeName,
            LocalDate startDate,
            LocalDate endDate,
            int met,
            int notMet,
            List<ParentGoal> goals,
            Instant confirmedAt) {}

    /** Đánh giá học kỳ đã xác nhận; comment = nhận xét giáo viên đã xác nhận (null nếu chưa có). */
    public record ParentSemesterResult(
            Long classId,
            String className,
            int semester,
            LocalDate startDate,
            LocalDate endDate,
            List<ParentDomain> domains,
            String comment,
            Instant confirmedAt) {}

    /** Phiếu đánh giá cuối năm đã xác nhận. */
    public record ParentYearEndResult(
            Long classId,
            String className,
            List<ParentDomain> domains,
            int met,
            int notMet,
            Boolean programCompleted,
            String comment,
            String noteForParent,
            Instant confirmedAt) {}

    /** Không có đánh giá tháng (chỉ cuối chủ đề, học kỳ, cuối năm). */
    public record ParentEvaluationsResponse(
            Long childId,
            Long schoolYearId,
            List<ParentThemeResult> themes,
            List<ParentSemesterResult> semesters,
            ParentYearEndResult yearEnd) {}

    /** Cờ của ngày (đạt / chưa đạt) trong khoảng. */
    public record ParentFlag(LocalDate date, boolean flag, Long classId, String className) {}

    /** Phiếu bé ngoan đã phát: tuần (Bé ngoan) / tháng (Bé ngoan toàn diện). */
    public record ParentTicket(
            Long id,
            TicketType type,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer presentDays,
            Integer flagCount,
            Integer weeklyTickets,
            String issuedByName,
            Instant issuedAt,
            Long classId,
            String className) {}

    /** Danh hiệu cuối năm của danh sách đã được Hiệu trưởng phê duyệt; teacherNote = lời cô. */
    public record ParentYearEndTitle(
            Long schoolYearId, Long classId, String className, List<String> titles, String teacherNote) {}

    public record ParentRewardsResponse(
            Long childId,
            LocalDate from,
            LocalDate to,
            List<ParentFlag> flags,
            List<ParentTicket> tickets,
            List<ParentYearEndTitle> yearEndTitles) {}
}
