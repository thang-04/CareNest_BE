package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduTeamPlan;
import java.util.List;

public interface EduTeamPlanService {
    void synchronizeSource(EduTeamPlan target, EduPlan source);
    void validateMappings(EduPlan plan, boolean submitting);
    void resolve(EduPlan plan, Long itemId, Long userId);
    List<YearPlanDetailResponse.ReviewItem> reviewItems(EduPlan plan);
    List<YearPlanDetailResponse.NewItem> newItems(EduPlan plan);
}
