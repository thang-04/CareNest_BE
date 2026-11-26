package com.carenest.service.attendance;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.DayUnlockResponse;
import com.carenest.security.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mở lại điểm danh một lớp/ngày đã khóa: HT mọi lớp, PHT lớp điểm trường mình. */
public interface AttendanceDayUnlockService {

    /**
     * Một lớp/ngày vừa được mở lại điểm danh. Module đánh giá nghe để mở lại đánh giá cùng lớp–ngày nếu ngày đó đã
     * khóa, để trẻ sửa Vắng → Có mặt vẫn được đánh giá (không phụ thuộc ngược vào module đánh giá).
     */
    record AttendanceDayUnlockedEvent(
            Long classroomId, Long campusId, LocalDate date, Instant validUntil, String reason, Long unlockedBy) {}

    List<DayUnlockResponse> getUnlocks(CurrentUser currentUser);

    DayUnlockResponse unlockDay(CurrentUser currentUser, DayUnlockRequest request);

    DayUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId);
}
