package com.carenest.dto.observation;

import com.carenest.dto.eduplan.DayActivity;
import java.time.LocalDate;
import java.util.List;

/** App phụ huynh: hoạt động trong ngày của lớp con (EDU-37) — chỉ tên hoạt động của giáo án ngày đã ban hành. */
public final class ParentActivityDtos {
    private ParentActivityDtos() {}

    public record ParentDayActivitiesResponse(LocalDate date, List<DayActivity> activities) {}
}
