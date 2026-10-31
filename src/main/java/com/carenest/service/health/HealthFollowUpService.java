package com.carenest.service.health;

import com.carenest.dto.health.HealthFollowUpCloseRequest;
import com.carenest.dto.health.HealthFollowUpContactRequest;
import com.carenest.dto.health.HealthFollowUpExternalResultRequest;
import com.carenest.dto.health.HealthFollowUpListResponse;
import com.carenest.dto.health.HealthFollowUpResponse;
import com.carenest.dto.health.HealthImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.HealthFollowUpStatus;

/**
 * Phiếu theo dõi sức khỏe (user chốt 2026-10-31): BGH, GV xem trong phạm vi lớp trẻ đang học; GV lớp ghi trao đổi phụ
 * huynh, kết quả chuyên khoa (tối đa 3 ảnh), đóng phiếu theo lời bác sĩ. Hạn, quá hạn tính khi đọc, không nhắc tự động.
 */
public interface HealthFollowUpService {

    HealthFollowUpListResponse listFollowUps(
            CurrentUser currentUser, Long campusId, HealthFollowUpStatus status, boolean overdue, Long schoolYearId);

    HealthFollowUpResponse getFollowUp(CurrentUser currentUser, Long followUpId);

    HealthFollowUpResponse addParentContact(
            CurrentUser currentUser, Long followUpId, HealthFollowUpContactRequest request);

    HealthFollowUpResponse addExternalResult(
            CurrentUser currentUser, Long followUpId, HealthFollowUpExternalResultRequest request);

    HealthFollowUpResponse closeFollowUp(CurrentUser currentUser, Long followUpId, HealthFollowUpCloseRequest request);

    HealthImageContent getImage(CurrentUser currentUser, Long followUpId, Long imageId);
}
