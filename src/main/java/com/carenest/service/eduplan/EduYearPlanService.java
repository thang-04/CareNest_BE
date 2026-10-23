package com.carenest.service.eduplan;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.security.CurrentUser;
import org.springframework.data.domain.Pageable;

/** Kế hoạch giáo dục năm học theo nhóm tuổi (rule EDU, sequence SD-01..SD-08). */
public interface EduYearPlanService {

    PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser currentUser, Long schoolYearId, Long ageGroupId, String keyword, Pageable pageable);

    YearPlanDetailResponse getYearPlan(CurrentUser currentUser, Long planId);

    YearPlanDetailResponse createYearPlan(CurrentUser currentUser, YearPlanRequest request);

    YearPlanDetailResponse updateYearPlan(CurrentUser currentUser, Long planId, YearPlanRequest request);

    YearPlanDetailResponse submitYearPlan(CurrentUser currentUser, Long planId, String note);

    YearPlanDetailResponse issueYearPlan(CurrentUser currentUser, Long planId, String note);

    YearPlanDetailResponse returnYearPlan(CurrentUser currentUser, Long planId, String note);

    void deleteYearPlan(CurrentUser currentUser, Long planId);

    /** EDU-10: vào trang soạn ⇒ khóa "Đang sửa" cho người này (chờ duyệt thì không khóa). */
    YearPlanDetailResponse startEditing(CurrentUser currentUser, Long planId);

    /** EDU-10: thoát trang soạn ⇒ nhả khóa của chính người này, về Nháp / Bị trả lại. */
    YearPlanDetailResponse finishEditing(CurrentUser currentUser, Long planId);

    /** EDU-10: nhả các khóa quá hạn (job định kỳ); trả số kế hoạch đã nhả. */
    int releaseExpiredEditLocks();
}
