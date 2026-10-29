package com.carenest.dto.observation;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Phiếu đánh giá một lớp trong ngày cho hai view (Hoạt động chung, Kiến thức kỹ năng). rows chỉ gồm trẻ đã điểm danh Có mặt;
 * lớp chưa điểm danh thì rows rỗng và attendanceTaken = false. lesson null = chưa có giáo án duyệt (khóa Kiến thức kỹ năng).
 */
public record DailySheetResponse(
        Long classId,
        String className,
        Long campusId,
        LocalDate date,
        LocalDate today,
        LocalDate yearStart,
        boolean canRecord,
        boolean yearReadOnly,
        boolean locked,
        AssessmentDayUnlockResponse unlock,
        boolean attendanceTaken,
        List<MealSession> mealSessions,
        ApprovedDayLessonResponse lesson,
        List<Row> rows,
        ClassCommentResponse classComment) {

    public record Row(
            Long childId,
            String fullName,
            AttendanceStatus attendanceStatus,
            Health health,
            Map<MealSession, EatingLevel> meals,
            NapLevel nap,
            Emotion emotion,
            Participation participation,
            List<ObservedBehavior> behaviors,
            String behaviorNote,
            Boolean flag,
            String comment,
            boolean recorded,
            List<ActivityResult> activities) {}

    /** signs đang hiện; prefillSigns theo sức khỏe trong ngày (không gồm thân nhiệt); overridden = cô đã chỉnh. */
    public record Health(
            List<HealthSymptom> signs, String note, boolean overridden, List<HealthSymptom> prefillSigns) {}

    public record ActivityResult(
            String key,
            Participation participation,
            List<Long> goalsMet,
            List<Long> goalsEffort,
            List<Long> goalsNotMet,
            Instant updatedAt) {}
}
