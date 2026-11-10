package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** Đọc giáo án tuần đã duyệt của lớp theo ngày cho module đánh giá trẻ (EDU-19); không ghi, không kiểm quyền người xem. */
public interface EduLessonQueryService {

    /** Kế hoạch ngày đã duyệt của lớp trong khoảng ngày; ngày chưa có giáo án duyệt thì không có khóa. */
    Map<LocalDate, ApprovedDayLessonResponse> findApprovedDayLessons(Long classroomId, LocalDate from, LocalDate to);

    Map<Long, Long> findGoalLineages(Collection<Long> goalIds);
}
