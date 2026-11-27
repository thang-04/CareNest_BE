package com.carenest.service.observation;

import com.carenest.dto.observation.ParentAssessmentDtos.ParentDailyAssessment;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentEvaluationsResponse;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentRewardsResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Phụ huynh xem đánh giá và khen thưởng đã chốt của con đã liên kết (AUTH-04, OBS-36); chỉ đọc. */
public interface ParentAssessmentService {

    /** Khoảng tối đa 62 ngày trong một năm học. */
    List<ParentDailyAssessment> getDailyAssessments(
            CurrentUser currentUser, Long childId, LocalDate from, LocalDate to);

    /** schoolYearId null = năm học của lớp gần nhất của trẻ. */
    ParentEvaluationsResponse getEvaluations(CurrentUser currentUser, Long childId, Long schoolYearId);

    /** Khoảng tối đa 62 ngày trong một năm học. */
    ParentRewardsResponse getRewards(CurrentUser currentUser, Long childId, LocalDate from, LocalDate to);
}
