package com.carenest.dto.announcement;

import com.carenest.dto.common.PageResponse;
import java.util.List;

/** Giữ các field phân trang cũ; cancelledCount đếm tin có thể chọn trên mọi trang trong cùng filter/scope. */
public record AnnouncementListResponse(
        List<AnnouncementResponse> items, int page, int size, long totalElements, int totalPages, long cancelledCount) {
    public static AnnouncementListResponse of(PageResponse<AnnouncementResponse> page, long cancelledCount) {
        return new AnnouncementListResponse(
                page.items(), page.page(), page.size(), page.totalElements(), page.totalPages(), cancelledCount);
    }
}
