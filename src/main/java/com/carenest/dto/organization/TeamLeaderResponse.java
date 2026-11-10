package com.carenest.dto.organization;

import com.carenest.utils.enums.MealSession;
import java.util.List;
import java.util.Set;

/**
 * Tổ trưởng nhóm tuổi của năm học: 1 người/nhóm tuổi cho cả trường; ứng viên là giáo viên ở mọi điểm trường.
 * leaderGroups = mọi nhóm tuổi (kể cả chưa có lớp) kèm số lớp trong năm.
 */
public record TeamLeaderResponse(
        Long schoolYearId, List<Leader> teamLeaders, List<LeaderGroup> leaderGroups, List<Candidate> leaderCandidates) {

    public record Leader(Long ageGroupId, Long userId, String fullName, Long campusId) {}

    public record LeaderGroup(
            Long id,
            String code,
            String name,
            String shortName,
            Integer ageFromMonths,
            Integer ageToMonths,
            List<MealSession> mealSessions,
            Integer maxClassSize,
            long classCount) {}

    /** leadsAgeGroupId/leadsGroup: đang làm tổ trưởng nhóm khác — chọn người này sẽ chuyển họ sang nhóm mới. */
    public record Candidate(
            Long id, String fullName, Long campusId, Set<Long> campusIds, Long leadsAgeGroupId, String leadsGroup) {}
}
