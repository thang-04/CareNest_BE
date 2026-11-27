package com.carenest.service.observation;

import com.carenest.dto.observation.TransferredAssessmentHistoryResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** OBS-35: người đang phụ trách lớp xem (chỉ xem) đánh giá ngày, nhận xét tuần đã gửi của trẻ chuyển đến khi còn ở lớp cũ. */
public interface TransferredAssessmentHistoryService {

    TransferredAssessmentHistoryResponse findHistory(
            CurrentUser currentUser, Long classId, LocalDate from, LocalDate to);
}
