package com.carenest.security.authorization;

import com.carenest.security.CurrentUser;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Quyền kế hoạch giáo dục năm học. Permission theo role (EDU-08) ∩ phạm vi nhóm tuổi (EDU-06):
 * Tổ trưởng = TEACHER + phân công AGE_GROUP_TEAM_LEADER còn hiệu lực; GV theo nhóm tuổi các lớp đang dạy.
 */
@Component
@RequiredArgsConstructor
public class EduPlanAccessPolicy {

    // EDU-08: gán tạm tại một chỗ; khi BE có phân quyền động thì thay map này bằng dữ liệu cấu hình
    private static final Map<CareNestRole, Set<String>> ROLE_PERMISSIONS = Map.of(
            CareNestRole.PRINCIPAL, Set.of(EduPermission.GOALS_VIEW),
            CareNestRole.VICE_PRINCIPAL,
                    Set.of(EduPermission.GOALS_VIEW, EduPermission.GOALS_EDIT, EduPermission.GOALS_APPROVE),
            CareNestRole.TEACHER, Set.of(EduPermission.GOALS_VIEW, EduPermission.GOALS_EDIT));

    private final StaffAssignmentService staffAssignmentService;
    private final Clock clock;

    /**
     * Phạm vi xem: toàn trường với các trạng thái {@code schoolWideStatuses}; hoặc nhóm tuổi
     * {@code fullAgeGroupIds} (mọi trạng thái) và {@code approvedAgeGroupIds} (chỉ đã ban hành).
     */
    public record ViewScope(
            Set<EduPlanStatus> schoolWideStatuses, Set<Long> fullAgeGroupIds, Set<Long> approvedAgeGroupIds) {

        public boolean allows(Long ageGroupId, EduPlanStatus status) {
            return schoolWideStatuses.contains(status)
                    || fullAgeGroupIds.contains(ageGroupId)
                    || (status == EduPlanStatus.APPROVED && approvedAgeGroupIds.contains(ageGroupId));
        }
    }

    public boolean hasPermission(CurrentUser user, String permission) {
        return user.roles().stream()
                .map(role -> ROLE_PERMISSIONS.getOrDefault(role, Set.of()))
                .anyMatch(permissions -> permissions.contains(permission));
    }

    public ViewScope viewScope(CurrentUser user) {
        if (!hasPermission(user, EduPermission.GOALS_VIEW)) {
            return new ViewScope(Set.of(), Set.of(), Set.of());
        }
        // EDU-06: PHT toàn trường mọi trạng thái; HT toàn trường trừ nháp và đang sửa (EDU-10)
        Set<EduPlanStatus> schoolWide = EnumSet.noneOf(EduPlanStatus.class);
        if (isVicePrincipal(user)) {
            schoolWide.addAll(EnumSet.allOf(EduPlanStatus.class));
        } else if (user.hasRole(CareNestRole.PRINCIPAL)) {
            schoolWide.addAll(EnumSet.complementOf(EnumSet.of(EduPlanStatus.DRAFT, EduPlanStatus.EDITING)));
        }
        Set<Long> full = Set.of();
        Set<Long> approved = Set.of();
        if (user.hasRole(CareNestRole.TEACHER)) {
            LocalDate today = LocalDate.now(clock);
            full = staffAssignmentService.findActiveTeamLeaderAgeGroupIds(user.userId(), today);
            approved = staffAssignmentService.findActiveTeacherAgeGroupIds(user.userId(), today);
        }
        return new ViewScope(Set.copyOf(schoolWide), full, approved);
    }

    /** Phó HT có quyền duyệt; không giới hạn campus vì kế hoạch năm áp dụng toàn trường. */
    public boolean isVicePrincipal(CurrentUser user) {
        return hasPermission(user, EduPermission.GOALS_APPROVE);
    }

    public boolean isTeamLeaderOf(CurrentUser user, Long ageGroupId) {
        return user.hasRole(CareNestRole.TEACHER)
                && hasPermission(user, EduPermission.GOALS_EDIT)
                && staffAssignmentService
                        .findActiveTeamLeaderAgeGroupIds(user.userId(), LocalDate.now(clock))
                        .contains(ageGroupId);
    }

    /** EDU-02: Phó HT lập và cùng soạn cho mọi nhóm tuổi; Tổ trưởng chỉ nhóm tuổi mình. */
    public boolean canCreate(CurrentUser user, Long ageGroupId) {
        return (hasPermission(user, EduPermission.GOALS_EDIT) && isVicePrincipal(user))
                || isTeamLeaderOf(user, ageGroupId);
    }
}
