package com.carenest.dto.child;

import com.carenest.utils.enums.ChildStatus;
import java.util.Set;

/**
 * Bộ lọc danh sách trẻ: năm học (id), điểm trường, lớp, trạng thái (rỗng ⇒ Đang học + Chờ xếp lớp), từ khóa (họ tên hoặc
 * mã trẻ), trang (từ 0) và cỡ trang.
 */
public record ChildListFilter(
        Long schoolYearId,
        Long campusId,
        Long classId,
        Set<ChildStatus> statuses,
        String keyword,
        int page,
        int size) {}
