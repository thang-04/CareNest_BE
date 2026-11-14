package com.carenest.service.announcement;

import com.carenest.dto.announcement.AnnouncementScheduleRequest;
import com.carenest.dto.announcement.AnnouncementScheduleResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Lịch gửi lặp thông báo của trường (PAR-10), chỉ HT. */
public interface AnnouncementScheduleService {

    List<AnnouncementScheduleResponse> listSchedules(CurrentUser user);

    AnnouncementScheduleResponse createSchedule(CurrentUser user, AnnouncementScheduleRequest request);

    AnnouncementScheduleResponse updateSchedule(CurrentUser user, Long scheduleId, AnnouncementScheduleRequest request);

    void deleteSchedule(CurrentUser user, Long scheduleId);

    /** Worker: chạy một lịch đến hạn (bỏ nếu ngày nghỉ) rồi chuyển sang lần kế tiếp; false nếu máy khác đang giữ. */
    boolean runDue(Long scheduleId);

    List<Long> findDueScheduleIds(int limit);
}
