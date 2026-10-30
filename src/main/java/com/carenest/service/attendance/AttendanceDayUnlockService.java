package com.carenest.service.attendance;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.DayUnlockResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Mở lại điểm danh một lớp/ngày đã khóa: HT mọi lớp, PHT lớp điểm trường mình. */
public interface AttendanceDayUnlockService {
    List<DayUnlockResponse> getUnlocks(CurrentUser currentUser);

    DayUnlockResponse unlockDay(CurrentUser currentUser, DayUnlockRequest request);

    DayUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId);
}
