package com.carenest.service.meals;

import com.carenest.dto.meals.ClassMenuResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** Thực đơn tuần của lớp giáo viên đang được phân công. */
public interface ClassMenuService {

    /** weekStart null = tuần hiện tại; ngày giữa tuần đưa về thứ Hai. */
    ClassMenuResponse getWeek(CurrentUser currentUser, Long classId, LocalDate weekStart);
}
