package com.carenest.service.attendance;

import java.time.LocalDate;
import java.util.Map;

/**
 * Số trẻ ăn suất thay thế của một lớp theo ngày, lấy từ sổ báo ăn đã lập (Cần chốt 10/11/2026: sau giờ chốt sổ không tự
 * đổi theo hồ sơ dị ứng, HT/PHT chỉnh tay). Module bán trú cài đặt để tổng hợp điểm danh không phụ thuộc ngược.
 */
public interface MealSubstituteLookup {

    /** date → số suất thay thế của lớp (lớn nhất giữa các bữa); ngày chưa có sổ thì không có. */
    Map<LocalDate, Integer> substitutes(Long campusId, Long classroomId, LocalDate from, LocalDate to);
}
