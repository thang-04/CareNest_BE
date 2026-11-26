package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.eduplan.DayActivity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Đọc giáo án tuần đã duyệt của lớp theo ngày cho module đánh giá trẻ (EDU-19); không ghi, không kiểm quyền người xem. */
public interface EduLessonQueryService {

    /** Kế hoạch ngày đã duyệt của lớp trong khoảng ngày; ngày chưa có giáo án duyệt thì không có khóa. */
    Map<LocalDate, ApprovedDayLessonResponse> findApprovedDayLessons(Long classroomId, LocalDate from, LocalDate to);

    /** EDU-37: mọi dòng có hoạt động của giáo án ngày đã ban hành (kể cả dòng không MT); chưa có giáo án ⇒ rỗng. */
    List<DayActivity> findDayActivities(Long classroomId, LocalDate date);

    Map<Long, Long> findGoalLineages(Collection<Long> goalIds);
}
