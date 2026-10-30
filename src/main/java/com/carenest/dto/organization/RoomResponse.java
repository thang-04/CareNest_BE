package com.carenest.dto.organization;

import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import java.util.List;
import java.util.Map;

/**
 * Phòng kèm mức sử dụng: lớp của năm học đang xem, lý do chặn ngừng dùng/xóa, sức chứa tối thiểu
 * (= sĩ số tối đa lớn nhất của lớp năm chưa kết thúc đang dùng phòng).
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
