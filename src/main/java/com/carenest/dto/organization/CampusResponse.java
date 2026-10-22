package com.carenest.dto.organization;

import java.util.List;

/** Điểm trường kèm số liệu của năm học đang xem (lớp, trẻ đang học, Phó hiệu trưởng phụ trách). */
public record CampusResponse(
        Long id,
        String code,
        String name,
        String shortName,
        String address,
        String phone,
        boolean isMain,
        long classCount,
        long childCount,
        List<StaffBriefResponse> vicePrincipals) {}
