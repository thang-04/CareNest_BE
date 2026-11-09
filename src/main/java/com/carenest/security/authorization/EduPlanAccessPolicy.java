package com.carenest.security.authorization;

import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            CareNestRole.PRINCIPAL, Set.of(EduPermission.GOALS_VIEW, EduPermission.PLAN_CONFIG),
            CareNestRole.VICE_PRINCIPAL,
                    Set.of(
                            EduPermission.GOALS_VIEW,
                            EduPermission.GOALS_EDIT,
                            EduPermission.GOALS_APPROVE,
                            EduPermission.PLAN_CONFIG),
            CareNestRole.TEACHER, Set.of(EduPermission.GOALS_VIEW, EduPermission.GOALS_EDIT));

    private final StaffAssignmentService staffAssignmentService;
    private final Clock clock;
    private final com.carenest.service.organization.ClassroomService classroomService;

    /**
     * Phạm vi xem: toàn trường với các trạng thái {@code schoolWideStatuses}; hoặc nhóm tuổi
     * {@code fullAgeGroupIds} (mọi trạng thái) và {@code approvedAgeGroupIds} (chỉ đã ban hành).
     */
    public record ViewScope(
            Set<EduPlanStatus> schoolWideStatuses,
            Set<Long> fullAgeGroupIds,
            Set<Long> approvedAgeGroupIds,
            Set<Long> reviewAgeGroupIds) {
        public ViewScope(Set<EduPlanStatus> statuses, Set<Long> full, Set<Long> approved) {
            this(statuses, full, approved, Set.of());
        }

        public boolean allows(Long ageGroupId, EduPlanStatus status) {
            return schoolWideStatuses.contains(status)
                    || fullAgeGroupIds.contains(ageGroupId)
                    || (status == EduPlanStatus.APPROVED && approvedAgeGroupIds.contains(ageGroupId))
                    || (status == EduPlanStatus.REVIEW && reviewAgeGroupIds.contains(ageGroupId));
        }
    }

    public boolean hasPermission(CurrentUser user, String permission) {
        return user.roles().stream()
                .filter(role -> ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission))
                .anyMatch(role -> roleInEffect(user, role));
    }

    /** HT có phân công toàn trường còn hiệu lực. */
    public boolean isPrincipal(CurrentUser user) {
        return user.hasRole(CareNestRole.PRINCIPAL) && roleInEffect(user, CareNestRole.PRINCIPAL);
    }

    /** Chỉ Hiệu trưởng có phân công hiệu lực được cấu hình quy trình soạn/duyệt (EDU-54). */
    public boolean canConfigureWorkflow(CurrentUser user) {
        return isPrincipal(user);
    }

    // AUTH-20: role HT/PHT chỉ có hiệu lực khi phân công còn hiệu lực (như attendance/meals); GV giới hạn theo nhóm
    // tuổi
    private boolean roleInEffect(CurrentUser user, CareNestRole role) {
        LocalDate today = LocalDate.now(clock);
        return switch (role) {
            case PRINCIPAL -> staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today);
            case VICE_PRINCIPAL ->
                !staffAssignmentService
                        .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                        .isEmpty();
            default -> true;
        };
    }

    public ViewScope viewScope(CurrentUser user) {
        if (!hasPermission(user, EduPermission.GOALS_VIEW)) {
            return new ViewScope(Set.of(), Set.of(), Set.of());
        }
        // EDU-06: PHT toàn trường mọi trạng thái; HT toàn trường trừ nháp và đang sửa (EDU-10)
        Set<EduPlanStatus> schoolWide = EnumSet.noneOf(EduPlanStatus.class);
        if (isVicePrincipal(user)) {
            schoolWide.addAll(EnumSet.allOf(EduPlanStatus.class));
        } else if (isPrincipal(user)) {
            schoolWide.addAll(EnumSet.complementOf(EnumSet.of(EduPlanStatus.DRAFT)));
        }
        Set<Long> full = Set.of();
        Set<Long> approved = Set.of();
        if (user.hasRole(CareNestRole.TEACHER)) {
            LocalDate today = LocalDate.now(clock);
            full = staffAssignmentService.findActiveTeamLeaderAgeGroupIds(user.userId(), today);
            approved = staffAssignmentService.findActiveTeacherAgeGroupIds(user.userId(), today);
            // EDU-64: tổ trưởng chỉ xem kế hoạch tổ của đúng nhóm tuổi đang được phân công.
        }
        return new ViewScope(Set.copyOf(schoolWide), full, approved);
    }

    public ViewScope annualViewScope(
            CurrentUser user,
            EduWorkflowPolicy.RolePolicy annualPolicy,
            EduWorkflowPolicy.RolePolicy teamPolicy,
            Set<Long> ageGroupIds) {
        if (!hasPermission(user, EduPermission.GOALS_VIEW)) return new ViewScope(Set.of(), Set.of(), Set.of());
        Set<Long> full = new java.util.HashSet<>();
        Set<Long> review = new java.util.HashSet<>();
        Set<Long> approved = new java.util.HashSet<>();
        for (Long age : ageGroupIds) {
            if (canAuthorYearPlan(user, annualPolicy, age)) full.add(age);
            boolean reviewer = annualPolicy.reviewers().stream().anyMatch(role -> canReviewYearPlan(user, role, age));
            if (reviewer) {
                review.add(age);
                approved.add(age);
                // PHT giữ quyền đọc bản nháp độc lập, không cấp quyền này từ tư cách lập tổ.
                if (annualPolicy.reviewers().contains(EduWorkflowPolicy.Role.VICE_PRINCIPAL) && isVicePrincipal(user))
                    full.add(age);
            }
            if (canAuthorYearPlan(user, teamPolicy, age)) approved.add(age);
        }
        return new ViewScope(Set.of(), Set.copyOf(full), Set.copyOf(approved), Set.copyOf(review));
    }

    /** Phó HT còn phân công có quyền duyệt; không giới hạn campus vì kế hoạch năm áp dụng toàn trường. */
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

    public boolean isTeacherOfAgeGroup(CurrentUser user, Long ageGroupId) {
        return user.hasRole(CareNestRole.TEACHER)
                && ageGroupId != null
                && staffAssignmentService
                        .findActiveTeacherAgeGroupIds(user.userId(), LocalDate.now(clock))
                        .contains(ageGroupId);
    }

    /** EDU-02: Phó HT lập và cùng soạn cho mọi nhóm tuổi; Tổ trưởng chỉ nhóm tuổi mình. */
    public boolean canCreate(CurrentUser user, Long ageGroupId) {
        return (hasPermission(user, EduPermission.GOALS_EDIT) && isVicePrincipal(user))
                || isTeamLeaderOf(user, ageGroupId);
    }

    /** EDU-54: Tập vai trò nghiệp vụ thực tế có hiệu lực theo phân công tại ngày hiện tại. */
    public Set<EduWorkflowPolicy.Role> effectiveRoles(CurrentUser user) {
        LocalDate today = LocalDate.now(clock);
        Set<EduWorkflowPolicy.Role> roles = EnumSet.noneOf(EduWorkflowPolicy.Role.class);
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            roles.add(EduWorkflowPolicy.Role.PRINCIPAL);
        }
        if (user.hasRole(CareNestRole.VICE_PRINCIPAL)
                && !staffAssignmentService
                        .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                        .isEmpty()) {
            roles.add(EduWorkflowPolicy.Role.VICE_PRINCIPAL);
        }
        if (user.hasRole(CareNestRole.TEACHER)) {
            if (staffAssignmentService.hasActiveTeamLeaderAssignment(user.userId(), today)) {
                roles.add(EduWorkflowPolicy.Role.TEAM_LEADER);
            }
            roles.add(EduWorkflowPolicy.Role.TEACHER);
        }
        return Set.copyOf(roles);
    }

    /** Reviewer-precedence: người có vai trò reviewer hiệu lực theo loại không được soạn cùng loại dù có nhiều role. */
    public boolean hasReviewerPrecedence(CurrentUser user, EduWorkflowPolicy.RolePolicy policy) {
        Set<EduWorkflowPolicy.Role> active = effectiveRoles(user);
        return policy.reviewers().stream().anyMatch(active::contains);
    }

    public record ClassScopeSnapshot(
            Set<EduWorkflowPolicy.Role> roles, Set<Long> viceCampuses, Set<Long> leaderAges, Set<Long> teacherClasses) {
        public boolean canReview(EduWorkflowPolicy.Role role, Long ageGroupId, Long campusId) {
            return switch (role) {
                case PRINCIPAL -> roles.contains(role);
                case VICE_PRINCIPAL -> roles.contains(role) && campusId != null && viceCampuses.contains(campusId);
                case TEAM_LEADER -> roles.contains(role) && ageGroupId != null && leaderAges.contains(ageGroupId);
                case TEACHER -> false;
            };
        }

        public boolean canAuthor(EduWorkflowPolicy.RolePolicy policy, Long ageGroupId, Long campusId, Long classId) {
            if (policy.reviewers().stream().anyMatch(roles::contains)) return false;
            return policy.creators().stream()
                    .anyMatch(role -> roles.contains(role)
                            && switch (role) {
                                case PRINCIPAL -> true;
                                case VICE_PRINCIPAL -> campusId != null && viceCampuses.contains(campusId);
                                case TEAM_LEADER -> ageGroupId != null && leaderAges.contains(ageGroupId);
                                case TEACHER -> classId != null && teacherClasses.contains(classId);
                            });
        }
    }

    public ClassScopeSnapshot classScopeSnapshot(CurrentUser user) {
        List<com.carenest.dto.organization.StaffAssignmentSummaryResponse> assignments =
                staffAssignmentService.findActiveAssignments(user.userId(), LocalDate.now(clock));
        Set<EduWorkflowPolicy.Role> roles = EnumSet.noneOf(EduWorkflowPolicy.Role.class);
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && assignments.stream().anyMatch(a -> a.staffRole() == StaffRole.PRINCIPAL))
            roles.add(EduWorkflowPolicy.Role.PRINCIPAL);
        if (user.hasRole(CareNestRole.VICE_PRINCIPAL)
                && assignments.stream()
                        .anyMatch(a -> a.staffRole() == StaffRole.VICE_PRINCIPAL && a.campusId() != null))
            roles.add(EduWorkflowPolicy.Role.VICE_PRINCIPAL);
        if (user.hasRole(CareNestRole.TEACHER)) {
            roles.add(EduWorkflowPolicy.Role.TEACHER);
            if (assignments.stream().anyMatch(a -> a.staffRole() == StaffRole.AGE_GROUP_TEAM_LEADER))
                roles.add(EduWorkflowPolicy.Role.TEAM_LEADER);
        }
        Set<Long> campuses = assignments.stream()
                .filter(a -> a.staffRole() == StaffRole.VICE_PRINCIPAL)
                .map(a -> a.campusId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> ages = assignments.stream()
                .filter(a -> a.staffRole() == StaffRole.AGE_GROUP_TEAM_LEADER)
                .map(a -> a.ageGroupId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> classes = assignments.stream()
                .filter(a -> a.staffRole() == StaffRole.TEACHER)
                .map(a -> a.classroomId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return new ClassScopeSnapshot(Set.copyOf(roles), Set.copyOf(campuses), Set.copyOf(ages), Set.copyOf(classes));
    }

    public boolean canAuthorYearPlan(CurrentUser user, EduWorkflowPolicy.RolePolicy policy, Long ageGroupId) {
        if (hasReviewerPrecedence(user, policy)) {
            return false;
        }
        Set<EduWorkflowPolicy.Role> active = effectiveRoles(user);
        LocalDate today = LocalDate.now(clock);
        for (EduWorkflowPolicy.Role creatorRole : policy.creators()) {
            if (!active.contains(creatorRole)) {
                continue;
            }
            boolean inScope =
                    switch (creatorRole) {
                        case PRINCIPAL, VICE_PRINCIPAL -> true;
                        case TEAM_LEADER ->
                            ageGroupId != null
                                    && staffAssignmentService
                                            .findActiveTeamLeaderAgeGroupIds(user.userId(), today)
                                            .contains(ageGroupId);
                        case TEACHER ->
                            ageGroupId != null
                                    && staffAssignmentService
                                            .findActiveTeacherAgeGroupIds(user.userId(), today)
                                            .contains(ageGroupId);
                    };
            if (inScope) {
                return true;
            }
        }
        return false;
    }

    public boolean canAuthorClassDoc(
            CurrentUser user, EduWorkflowPolicy.RolePolicy policy, Long ageGroupId, Long classroomId) {
        if (hasReviewerPrecedence(user, policy)) {
            return false;
        }
        Set<EduWorkflowPolicy.Role> active = effectiveRoles(user);
        LocalDate today = LocalDate.now(clock);
        for (EduWorkflowPolicy.Role creatorRole : policy.creators()) {
            if (!active.contains(creatorRole)) {
                continue;
            }
            boolean inScope =
                    switch (creatorRole) {
                        case PRINCIPAL -> true;
                        case VICE_PRINCIPAL ->
                            canReviewClassDoc(user, EduWorkflowPolicy.Role.VICE_PRINCIPAL, ageGroupId, classroomId);
                        case TEAM_LEADER ->
                            ageGroupId != null
                                    && staffAssignmentService
                                            .findActiveTeamLeaderAgeGroupIds(user.userId(), today)
                                            .contains(ageGroupId);
                        case TEACHER ->
                            classroomId != null
                                    && staffAssignmentService.hasActiveTeacherAssignment(
                                            user.userId(), classroomId, today);
                    };
            if (inScope) {
                return true;
            }
        }
        return false;
    }

    public boolean canReviewYearPlan(CurrentUser user, EduWorkflowPolicy.Role roleKey, Long ageGroupId) {
        LocalDate today = LocalDate.now(clock);
        return switch (roleKey) {
            case PRINCIPAL -> isPrincipal(user);
            case VICE_PRINCIPAL -> isVicePrincipal(user);
            case TEAM_LEADER ->
                ageGroupId != null
                        && user.hasRole(CareNestRole.TEACHER)
                        && staffAssignmentService
                                .findActiveTeamLeaderAgeGroupIds(user.userId(), today)
                                .contains(ageGroupId);
            case TEACHER -> false;
        };
    }

    public boolean canReviewClassDoc(
            CurrentUser user, EduWorkflowPolicy.Role roleKey, Long ageGroupId, Long classroomId) {
        LocalDate today = LocalDate.now(clock);
        return switch (roleKey) {
            case PRINCIPAL -> isPrincipal(user);
            case VICE_PRINCIPAL ->
                isVicePrincipal(user)
                        && classroomId != null
                        && classroomService
                                .findCampusId(classroomId)
                                .map(campusId -> staffAssignmentService
                                        .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                                        .contains(campusId))
                                .orElse(false);
            case TEAM_LEADER ->
                ageGroupId != null
                        && user.hasRole(CareNestRole.TEACHER)
                        && staffAssignmentService
                                .findActiveTeamLeaderAgeGroupIds(user.userId(), today)
                                .contains(ageGroupId);
            case TEACHER -> false;
        };
    }
}
