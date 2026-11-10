package com.carenest.security.authorization;

import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AUTH-23: tổ trưởng là GV (role TEACHER trong token) có phân công tổ trưởng còn hiệu lực hôm nay; khi đó người đó
 * nhận thêm quyền cột TEAM_LEADER của ma trận. Hết phân công thì mất ngay ở request kế tiếp.
 */
@Component
@RequiredArgsConstructor
public class TeamLeaderAssignmentChecker {

    private final StaffAssignmentService staffAssignmentService;
    private final Clock clock;

    public boolean isActiveTeamLeader(Long userId, Set<CareNestRole> loginRoles) {
        return loginRoles.contains(CareNestRole.TEACHER)
                && staffAssignmentService.hasActiveTeamLeaderAssignment(userId, LocalDate.now(clock));
    }
}
