package com.carenest.service.eduplan;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.security.CurrentUser;
import org.springframework.data.domain.Pageable;

/** Kế hoạch giáo dục năm học theo nhóm tuổi (rule EDU, sequence SD-01..SD-08). */
public interface EduYearPlanService {

    PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser currentUser, Long schoolYearId, Long ageGroupId, String keyword, Pageable pageable);

    YearPlanDetailResponse getYearPlan(CurrentUser currentUser, Long planId);

    YearPlanDetailResponse createYearPlan(CurrentUser currentUser, YearPlanRequest request, YearPlanEditRequest edit);

    YearPlanDetailResponse updateYearPlan(
            CurrentUser currentUser, Long planId, YearPlanRequest request, YearPlanEditRequest edit);

    YearPlanDetailResponse submitYearPlan(CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit);

    YearPlanDetailResponse issueYearPlan(CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit);

    YearPlanDetailResponse returnYearPlan(CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit);

    void deleteYearPlan(CurrentUser currentUser, Long planId, YearPlanEditRequest edit);

    /** EDU-10: vào trang soạn ⇒ cấp quyền cho đúng phiên, giữ trạng thái Chờ duyệt khi Phó HT sửa. */
    YearPlanDetailResponse startEditing(CurrentUser currentUser, Long planId, YearPlanEditRequest edit);

    /** EDU-10: thoát trang soạn ⇒ nhả khóa của chính người này, về Nháp / Bị trả lại. */
    YearPlanDetailResponse finishEditing(CurrentUser currentUser, Long planId, YearPlanEditRequest edit);

    YearPlanDetailResponse heartbeat(CurrentUser currentUser, Long planId, YearPlanEditRequest edit);

    /** EDU-25: phiên bản n+1 chép từ phiên bản ban hành mới nhất; người tạo giữ phiên sửa. */
    YearPlanDetailResponse createVersion(CurrentUser currentUser, Long planId, YearPlanEditRequest edit);

    /** EDU-25: so sánh theo lineage; fromPlanId null ⇒ phiên bản nguồn. Quyền xem EDU-06 trên cả hai bản. */
    YearPlanChangesResponse getChanges(CurrentUser currentUser, Long planId, Long fromPlanId);

    /** Dùng trong module (tài liệu lớp, EDU-26): so sánh không theo người xem. */
    YearPlanChangesResponse compareVersions(Long fromPlanId, Long planId);

    /** EDU-10: nhả các khóa quá hạn (job định kỳ); trả số kế hoạch đã nhả. */
    int releaseExpiredEditLocks();
}
