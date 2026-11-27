package com.carenest.service.announcement;

import com.carenest.dto.announcement.AnnouncementScheduleRequest;
import com.carenest.dto.announcement.AnnouncementScheduleResponse;
import com.carenest.dto.announcement.ScheduleActivationRequest;
import com.carenest.security.CurrentUser;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Lịch gửi lặp thông báo của trường (PAR-10), chỉ HT. */
public interface AnnouncementScheduleService {

    List<AnnouncementScheduleResponse> listSchedules(CurrentUser user);

    default AnnouncementScheduleResponse createSchedule(CurrentUser user, AnnouncementScheduleRequest request) {
        return createSchedule(user, request, null);
    }

    AnnouncementScheduleResponse createSchedule(
            CurrentUser user, AnnouncementScheduleRequest request, List<MultipartFile> files);

    default AnnouncementScheduleResponse updateSchedule(
            CurrentUser user, Long scheduleId, AnnouncementScheduleRequest request) {
        return updateSchedule(user, scheduleId, request, null);
    }

    AnnouncementScheduleResponse updateSchedule(
            CurrentUser user, Long scheduleId, AnnouncementScheduleRequest request, List<MultipartFile> files);

    AnnouncementScheduleResponse getSchedule(CurrentUser user, Long scheduleId);

    int activateSchedules(CurrentUser user, ScheduleActivationRequest request);

    void deleteSchedule(CurrentUser user, Long scheduleId);

    /** Xóa các lịch đã tick; thiếu/sai một id thì không xóa lịch nào. */
    int deleteSchedules(CurrentUser user, List<Long> scheduleIds);

    /** Worker: chạy một lịch đến hạn (bỏ nếu ngày nghỉ) rồi chuyển sang lần kế tiếp; false nếu máy khác đang giữ. */
    boolean runDue(Long scheduleId);

    List<Long> findDueScheduleIds(int limit);
}
