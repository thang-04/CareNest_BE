package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.PreviousYearTicksResponse;
import com.carenest.security.CurrentUser;

/** EDU-104: đọc tick kế hoạch tổ đã ban hành năm học liền trước để người soạn kế hoạch tổ lấy lại. */
public interface EduPreviousYearTickService {

    PreviousYearTicksResponse getPreviousYearTicks(CurrentUser currentUser, Long planId);

    /** Nội dung mục tiêu của kế hoạch năm học năm liền trước (EDU-106 nguồn "năm trước"). */
    record PreviousContent(Long domainId, String goalText, String text) {}

    /**
     * EDU-106: nội dung mục tiêu của kế hoạch năm học đã ban hành bản mới nhất, năm liền trước, cùng nhóm tuổi (hoặc cùng
     * khoảng tháng tuổi). Không kiểm quyền: người gọi đã kiểm quyền sửa kế hoạch đang soạn. Không có ⇒ rỗng.
     */
    java.util.List<PreviousContent> previousYearContents(Long schoolYearId, Long ageGroupId);
}
