package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Đọc giáo án tuần đã duyệt của lớp theo ngày cho module đánh giá trẻ (EDU-19); không ghi, không kiểm quyền người xem. */
public interface EduLessonQueryService {

    /** Kế hoạch ngày đã duyệt của lớp trong khoảng ngày; ngày chưa có giáo án duyệt thì không có khóa. */
    Map<LocalDate, ApprovedDayLessonResponse> findApprovedDayLessons(Long classroomId, LocalDate from, LocalDate to);

    /**
     * Như {@link #findApprovedDayLessons} nhưng giữ cả hoạt động gõ tay không gắn mục tiêu, để xem lịch dạy của lớp.
     */
    Map<LocalDate, ApprovedDayLessonResponse> findReleasedDayActivities(Long classroomId, LocalDate from, LocalDate to);

    Map<Long, Long> findGoalLineages(Collection<Long> goalIds);

    record ThemeGoal(Long id, Long lineage, Short goalNo, String text) {}

    /** MT của chủ đề theo kế hoạch chủ đề đã ban hành của lớp (gồm MT lớp tự thêm, bớt); rỗng = lớp chưa ban hành. */
    Optional<List<ThemeGoal>> findClassThemeGoals(Long classroomId, Long themeId);
}
