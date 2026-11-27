package com.carenest.service.eduplan;

import java.util.List;

/** Nội dung kế hoạch năm học năm liền trước, làm nguồn gợi ý của AI (EDU-106/115). */
public interface EduPreviousYearContentService {

    record PreviousContent(Long domainId, String goalText, String text) {}

    /**
     * Nội dung mục tiêu của kế hoạch năm học đã ban hành bản mới nhất, năm liền trước, cùng nhóm tuổi (hoặc cùng khoảng
     * tháng tuổi). Không kiểm quyền: người gọi đã kiểm quyền sửa kế hoạch đang soạn. Không có thì trả rỗng.
     */
    List<PreviousContent> previousYearContents(Long schoolYearId, Long ageGroupId);
}
