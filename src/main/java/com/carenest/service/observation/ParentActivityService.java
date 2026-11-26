package com.carenest.service.observation;

import com.carenest.dto.observation.ParentActivityDtos.ParentDayActivitiesResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

public interface ParentActivityService {

    /** EDU-37: hoạt động trong ngày của lớp trẻ học ngày đó; {@code date} null ⇒ hôm nay. */
    ParentDayActivitiesResponse getDayActivities(CurrentUser currentUser, Long childId, LocalDate date);
}
