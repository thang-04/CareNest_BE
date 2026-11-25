package com.carenest.service.eduplan;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;

/** Kế hoạch giáo dục năm học theo nhóm tuổi (rule EDU, sequence SD-01..SD-08). */
public interface EduYearPlanService {

    PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser currentUser, Long schoolYearId, Long ageGroupId, String keyword, Pageable pageable);

    PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser user,
            Long yearId,
            Long ageId,
            String keyword,
            Pageable pageable,
            com.carenest.utils.enums.EduPlanLevel level);

    /** currentOnly: ẩn bản đã ban hành bị thay thế, lọc ngay ở truy vấn (edu-list-paging, Tiền 25/11/2026). */
    PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser user,
            Long yearId,
            Long ageId,
            String keyword,
            Pageable pageable,
            com.carenest.utils.enums.EduPlanLevel level,
            boolean currentOnly);

    /** Số liệu thẻ tổng quan + nhóm tuổi đã có kế hoạch (để biết còn lập mới được) trong phạm vi người xem. */
    record YearPlanStats(long total, long issued, long replaced, java.util.List<Long> ageGroupIdsWithPlan) {}

    YearPlanStats getYearPlanStats(CurrentUser user, Long yearId, com.carenest.utils.enums.EduPlanLevel level);

    YearPlanDetailResponse createVersion(
            CurrentUser user, Long planId, YearPlanEditRequest edit, Long curriculumSourcePlanId);

    YearPlanDetailResponse getYearPlan(CurrentUser currentUser, Long planId);

    PageResponse<YearPlanSummaryResponse> getOverdueYearPlans(
            CurrentUser user,
            Long schoolYearId,
            Long ageGroupId,
            String keyword,
            Pageable pageable,
            com.carenest.utils.enums.EduPlanLevel level);

    YearPlanDetailResponse getOverdueYearPlan(CurrentUser user, Long planId, Long schoolYearId);

    /** Kế hoạch năm/tổ đang chờ đúng người gọi duyệt ở bước hiện tại. */
    java.util.List<YearPlanSummaryResponse> getReviewQueue(CurrentUser currentUser, Long schoolYearId);

    YearPlanDetailResponse createYearPlan(CurrentUser currentUser, YearPlanRequest request, YearPlanEditRequest edit);

    YearPlanDetailResponse updateYearPlan(
            CurrentUser currentUser, Long planId, YearPlanRequest request, YearPlanEditRequest edit);

    YearPlanDetailResponse submitYearPlan(CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit);

    YearPlanDetailResponse issueYearPlan(CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit);

    /** EDU-110 BR6.2/(10): HT soạn, chuỗi duyệt rỗng ⇒ ban hành thẳng từ Nháp (hoặc hồ sơ đang chờ chuỗi cũ). */
    YearPlanDetailResponse publishByPrincipal(
            CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit);

    YearPlanDetailResponse returnYearPlan(CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit);

    /** Spec 004 (Q5): Hiệu trưởng mở khóa kế hoạch năm học / tổ đã khóa theo ngày bắt đầu năm học. */
    YearPlanDetailResponse unlockYearPlan(CurrentUser currentUser, Long planId, String note, LocalDate deadline);

    YearPlanDetailResponse reopenReviewDeadline(CurrentUser currentUser, Long planId, String note, LocalDate deadline);

    void deleteYearPlan(CurrentUser currentUser, Long planId, YearPlanEditRequest edit);

    YearPlanDetailResponse resolveReview(
            CurrentUser user, Long planId, Long reviewItemId, Long expectedSourcePlanId, YearPlanEditRequest edit);

    /** EDU-10: cấp khóa sửa theo phiên khi hồ sơ đang ở trạng thái nháp. */
    YearPlanDetailResponse startEditing(CurrentUser currentUser, Long planId, YearPlanEditRequest edit);

    /** EDU-10: thoát trang soạn ⇒ nhả khóa của chính người này. */
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

    /**
     * EDU-109: nhắc hạn kế hoạch năm học / tổ chưa ban hành (còn 1–7 ngày, mỗi ngày một lần) cho người soạn hoặc người
     * duyệt đúng lượt; trả tên các kế hoạch đã quá hạn để gộp vào báo Hiệu trưởng.
     */
    java.util.List<String> remindPlanDeadlines(java.time.LocalDate today);

    /** EDU-72: đối soát bù khi khởi động — năm học đã ban hành mới nhất mà kế hoạch tổ chưa nhận nguồn. */
    void reconcileTeamUpdates();
}
