package com.carenest.dto.organization;

import com.carenest.utils.enums.RoomStatus;
import java.util.List;

/** Chi tiết điểm trường cho năm học đang xem: lớp (kèm số trẻ), nhóm tuổi, PHT, GV, bếp (phòng bếp + nhân viên). */
public record CampusDetailResponse(
        CampusResponse campus,
        Long schoolYearId,
        List<ClassBrief> classes,
        List<AgeGroupDetailResponse> ageGroups,
        List<StaffBriefResponse> vicePrincipals,
        List<StaffBriefResponse> teachers,
        Kitchen kitchen) {

    public record ClassBrief(
            Long id,
            String name,
            Long ageGroupId,
            Integer capacity,
            Long locationId,
            long childCount,
            Long homeroomTeacherId,
            String homeroomTeacherName) {}

    public record Kitchen(List<KitchenRoom> locations, List<StaffBriefResponse> staff) {}

    public record KitchenRoom(Long id, String room, String name, Integer capacity, RoomStatus status) {}
}
