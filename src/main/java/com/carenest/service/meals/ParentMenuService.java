package com.carenest.service.meals;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.meals.ParentMenuResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** Thực đơn và ảnh suất ăn phụ huynh xem về con mình (PAR-01, PAR-14). */
public interface ParentMenuService {

    /** Thứ Hai–Sáu của tuần chứa {@code weekStart}; bỏ trống ⇒ tuần hiện tại. */
    ParentMenuResponse getWeek(CurrentUser currentUser, Long childId, LocalDate weekStart);

    /** Liên kết xem ảnh có hạn; chỉ ảnh đang hiện trong tuần của con, ảnh khác báo không tìm thấy. */
    FileUrlResponse getPhotoUrl(CurrentUser currentUser, Long childId, Long photoId);

    /** Nội dung ảnh, cùng điều kiện với {@link #getPhotoUrl}. */
    MealPhotoService.Content getPhotoContent(CurrentUser currentUser, Long childId, Long photoId);
}
