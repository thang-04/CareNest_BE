package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.ClassTeachingActivitiesResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** Lịch hoạt động dạy học của lớp giáo viên đang được phân công. */
public interface ClassTeachingActivityService {

    /** weekStart null = tuần hiện tại; ngày giữa tuần đưa về thứ Hai. */
    ClassTeachingActivitiesResponse getWeek(CurrentUser currentUser, Long classId, LocalDate weekStart);
}
