package com.carenest.dto.attendance;

/** Lớp người dùng được trả trẻ hôm nay (PCK-01): lớp năm học đang hoạt động, GV đang được phân công. */
public record PickupClassResponse(Long id, String name, Long campusId, Long ageGroupId) {}
