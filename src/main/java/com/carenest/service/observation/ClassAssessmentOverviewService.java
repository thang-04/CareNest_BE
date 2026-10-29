package com.carenest.service.observation;

import com.carenest.dto.observation.ClassAssessmentDetailResponse;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse;
import com.carenest.security.CurrentUser;

/**
 * Tổng quan đánh giá các lớp cho PHT (điểm trường mình) và HT (toàn trường): period DAY / WEEK (thứ 2–6) / MONTH (tháng dương
 * lịch) chứa ngày neo, tính tới hôm nay; năm học theo năm chọn ở header (null = năm đang hoạt động).
 */
public interface ClassAssessmentOverviewService {

    ClassAssessmentOverviewResponse getOverview(
            CurrentUser currentUser, String period, Long schoolYearId, Long campusId, Long ageGroupId, Long classId);

    ClassAssessmentDetailResponse getClassDetail(CurrentUser currentUser, Long classId, String period);
}
