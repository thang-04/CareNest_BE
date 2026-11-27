package com.carenest.dto.organization;

import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import java.util.List;
import java.util.Map;

/**
 * Phòng kèm mức sử dụng trong năm học đang xem: lớp dùng phòng, lý do chặn ngừng dùng/xóa và sức chứa tối thiểu
 * (= sĩ số tối đa lớn nhất của lớp cùng năm học đó). Lịch sử lớp ở năm khác không khóa cấu hình năm đang xem.
 */
public record RoomResponse(
        Long id,
        Long campusId,
        String room,
        String name,
        RoomType type,
        Integer capacity,
        Long managerUserId,
        String managerName,
        RoomStatus status,
        String note,
        List<String> yearClasses,
        Map<String, Long> usage,
        List<String> blockers,
        int minCapacity) {}
