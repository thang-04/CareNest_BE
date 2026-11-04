package com.carenest.service.health;

import com.carenest.dto.health.HealthExamRequest;
import com.carenest.dto.health.HealthExamResponse;
import com.carenest.dto.health.HealthExamReviewResponse;
import com.carenest.security.CurrentUser;

/**
 * Phiếu khám Phụ lục II của trẻ trong đợt khám (HLT-08, HLT-09, HLT-11; user chốt 2026-10-31): GV lớp lưu nháp, kết
 * luận (công bố), sửa có lý do; kết luận ghi tiền sử về khai báo, mở/cập nhật phiếu theo dõi. BGH, GV xem trong phạm vi.
 */
public interface HealthExamService {

    HealthExamResponse getExam(CurrentUser currentUser, Long campaignId, Long childId);

    HealthExamResponse saveExam(CurrentUser currentUser, Long campaignId, Long childId, HealthExamRequest request);

    /** Điểm cần bác sĩ xem lại của nội dung đang sửa; không ghi. */
    HealthExamReviewResponse reviewPreview(
            CurrentUser currentUser, Long campaignId, Long childId, HealthExamRequest request);
}
