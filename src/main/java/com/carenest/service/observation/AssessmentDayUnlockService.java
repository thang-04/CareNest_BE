package com.carenest.service.observation;

import com.carenest.dto.observation.AssessmentDayUnlockRequest;
import com.carenest.dto.observation.AssessmentDayUnlockResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Mở lại ngày đánh giá đã khóa (HT mọi lớp, PHT lớp điểm trường mình); lịch sử mới nhất trước, kết thúc sớm không xóa. */
public interface AssessmentDayUnlockService {

    /** Lọc tùy chọn: điểm trường, lớp, active (true còn hiệu lực / false đã kết thúc), khoảng ngày được mở. */
    List<AssessmentDayUnlockResponse> getUnlocks(
            CurrentUser currentUser, Long campusId, Long classId, Boolean active, LocalDate from, LocalDate to);

    AssessmentDayUnlockResponse unlockDay(CurrentUser currentUser, AssessmentDayUnlockRequest request);

    AssessmentDayUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId);
}
