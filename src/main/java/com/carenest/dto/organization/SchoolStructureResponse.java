package com.carenest.dto.organization;

import java.util.List;
import java.util.Map;

/** Màn Nhóm tuổi & lớp của một năm học: nhóm tuổi, lớp trong phạm vi người xem, tổ trưởng, thống kê theo nhóm. */
public record SchoolStructureResponse(
        SchoolYearDetailResponse year,
        List<AgeGroupDetailResponse> ageGroups,
        List<ClassResponse> classes,
        List<TeamLeader> teamLeaders,
        Map<Long, GroupStat> groupStats) {

    /** campusId = điểm trường của giáo viên làm tổ trưởng (để hiển thị); tổ trưởng phụ trách cả trường. */
    public record TeamLeader(Long ageGroupId, Long userId, String fullName, Long campusId) {}

    /** planned = trẻ dự kiến (chỉ năm PLANNED, null với năm khác). */
    public record GroupStat(long classCount, long enrolled, Long planned, long capacity) {}
}
