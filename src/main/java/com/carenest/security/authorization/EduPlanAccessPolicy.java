package com.carenest.security.authorization;

import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    /** EDU-108: loại hồ sơ (khóa workflow_policy) và mã soạn/duyệt tương ứng. */
    public enum DocType {
        SCHOOL_YEAR("schoolYear", Permission.EDU_SCHOOL_YEAR_PLAN_EDIT, Permission.EDU_SCHOOL_YEAR_PLAN_APPROVE),
        TEAM("year", Permission.EDU_TEAM_PLAN_EDIT, Permission.EDU_TEAM_PLAN_APPROVE),
        SEMESTER_PACKAGE("branch", Permission.EDU_SEMESTER_PACKAGE_EDIT, Permission.EDU_SEMESTER_PACKAGE_APPROVE),
        THEME_PLAN("theme", Permission.EDU_THEME_PLAN_EDIT, Permission.EDU_THEME_PLAN_APPROVE);

        private final String key;
        private final Permission edit;
        private final Permission approve;

        DocType(String key, Permission edit, Permission approve) {
            this.key = key;
            this.edit = edit;
            this.approve = approve;
        }

        public String key() {
            return key;
        }

        public Permission edit() {
            return edit;
        }

        public Permission approve() {
            return approve;
        }

        public static Optional<DocType> fromKey(String key) {
            return Arrays.stream(values()).filter(t -> t.key.equals(key)).findFirst();
        }
    }

    private static final Set<CareNestRole> EDU_ROLES =
            EnumSet.of(CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER);

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

    /** Xem module: HT/PHT còn phân công hoặc GV (EDU-06). */
    public boolean canView(CurrentUser user) {
        return user.roles().stream().filter(EDU_ROLES::contains).anyMatch(role -> roleInEffect(user, role));
    }

    /** EDU-108: mã trong tập quyền hiệu lực (ma trận + ngoại lệ) và role mang mã đó còn phân công (AUTH-20). */
    public boolean hasPermission(CurrentUser user, Permission permission) {
        return user.hasPermission(permission)
                && user.roles().stream()
                        .filter(role -> carries(role, permission))
                        .anyMatch(role -> roleInEffect(user, role));
    }

    private static boolean carries(CareNestRole role, Permission permission) {
        if (permission.defaultRoles().contains(role) || permission.isConfigurableFor(role)) return true;
        // Cột Tổ trưởng tính theo GV đang là tổ trưởng (AUTH-23)
        return role == CareNestRole.TEACHER
                && (permission.defaultRoles().contains(CareNestRole.TEAM_LEADER)
                        || permission.isConfigurableFor(CareNestRole.TEAM_LEADER));
    }

    /** Khóa loại hồ sơ không thuộc EDU-108 (null, dữ liệu cũ) ⇒ chỉ xét vai trò như trước. */
    public boolean canAuthorDoc(CurrentUser user, String docKey) {
        return DocType.fromKey(docKey).map(t -> hasPermission(user, t.edit())).orElse(true);
    }

    public boolean canReviewDoc(CurrentUser user, String docKey) {
        return DocType.fromKey(docKey)
                .map(t -> hasPermission(user, t.approve()))
                .orElse(true);
    }

    /** PHT chỉ xem: không có mã soạn/duyệt nào (FR5.1). */
    public boolean isEduVicePrincipal(CurrentUser user) {
        return isVicePrincipal(user)
                && Arrays.stream(DocType.values())
                        .anyMatch(t -> hasPermission(user, t.edit()) || hasPermission(user, t.approve()));
    }

    /** Cấu hình lĩnh vực / dòng hoạt động: HT cố định, PHT khi được bật EDU_PLAN_CONFIG. */
    public boolean canEditConfig(CurrentUser user) {
        return hasPermission(user, Permission.EDU_PLAN_CONFIG);
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
        if (!canView(user)) {
            return new ViewScope(Set.of(), Set.of(), Set.of());
        }
        // EDU-06: PHT toàn trường mọi trạng thái; HT toàn trường trừ nháp và đang sửa (EDU-10)
        Set<EduPlanStatus> schoolWide = EnumSet.noneOf(EduPlanStatus.class);
        if (isEduVicePrincipal(user)) {
            schoolWide.addAll(EnumSet.allOf(EduPlanStatus.class));
        } else if (isVicePrincipal(user)) {
            // FR5.1: PHT không có mã soạn/duyệt chỉ xem bản đã ban hành
            schoolWide.add(EduPlanStatus.APPROVED);
        } else if (isPrincipal(user)) {
            schoolWide.addAll(EnumSet.complementOf(EnumSet.of(EduPlanStatus.DRAFT)));
        }
        Set<Long> full = Set.of();
        Set<Long> approved = Set.of();
        if (user.hasRole(CareNestRole.TEACHER)) {
            LocalDate today = LocalDate.now(clock);
            full = ledAgeGroups(user, null, today);
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
        if (!canView(user)) return new ViewScope(Set.of(), Set.of(), Set.of());
        boolean approver = canReviewDoc(user, annualPolicy.docType());
        Set<Long> full = new java.util.HashSet<>();
        Set<Long> review = new java.util.HashSet<>();
        Set<Long> approved = new java.util.HashSet<>();
        for (Long age : ageGroupIds) {
            if (canAuthorYearPlan(user, annualPolicy, age)) full.add(age);
            boolean reviewer =
                    approver && annualPolicy.reviewers().stream().anyMatch(role -> canReviewYearPlan(user, role, age));
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
        return user.hasRole(CareNestRole.VICE_PRINCIPAL) && roleInEffect(user, CareNestRole.VICE_PRINCIPAL);
    }

    public boolean isTeamLeaderOf(CurrentUser user, Long ageGroupId) {
        return isTeamLeaderOf(user, null, ageGroupId);
    }

    /** Tổ trưởng nhóm tuổi trong đúng năm học; schoolYearId null ⇒ năm đang hoạt động. */
    public boolean isTeamLeaderOf(CurrentUser user, Long schoolYearId, Long ageGroupId) {
        return user.hasRole(CareNestRole.TEACHER)
                && ledAgeGroups(user, schoolYearId, LocalDate.now(clock)).contains(ageGroupId);
    }

    // Phân công Tổ trưởng theo năm học (null = năm đang hoạt động): năm sau có Tổ trưởng riêng, không gộp kể cả khi
    // hai năm cùng hiệu lực trong giai đoạn chuẩn bị
    private Set<Long> ledAgeGroups(CurrentUser user, Long schoolYearId, LocalDate date) {
        return staffAssignmentService.findScopedTeamLeaderAgeGroupIds(user.userId(), schoolYearId, date);
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
        return (isVicePrincipal(user) && hasPermission(user, Permission.EDU_TEAM_PLAN_EDIT))
                || (isTeamLeaderOf(user, ageGroupId) && hasPermission(user, Permission.EDU_TEAM_PLAN_EDIT));
    }

    /** EDU-54: Tập vai trò nghiệp vụ thực tế có hiệu lực theo phân công tại ngày hiện tại. */
    public Set<EduWorkflowPolicy.Role> effectiveRoles(CurrentUser user) {
        return effectiveRoles(user, null);
    }

    /** Như trên; schoolYearId khác null ⇒ vai trò Tổ trưởng chỉ tính phân công của năm học đó. */
    public Set<EduWorkflowPolicy.Role> effectiveRoles(CurrentUser user, Long schoolYearId) {
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
            if (!ledAgeGroups(user, schoolYearId, today).isEmpty()) {
                roles.add(EduWorkflowPolicy.Role.TEAM_LEADER);
            }
            roles.add(EduWorkflowPolicy.Role.TEACHER);
        }
        return Set.copyOf(roles);
    }

    /** Reviewer-precedence: người có vai trò reviewer hiệu lực theo loại không được soạn cùng loại dù có nhiều role. */
    public boolean hasReviewerPrecedence(CurrentUser user, EduWorkflowPolicy.RolePolicy policy) {
        return hasReviewerPrecedence(user, policy, null);
    }

    public boolean hasReviewerPrecedence(CurrentUser user, EduWorkflowPolicy.RolePolicy policy, Long schoolYearId) {
        Set<EduWorkflowPolicy.Role> active = effectiveRoles(user, schoolYearId);
        return policy.reviewers().stream().anyMatch(active::contains);
    }

    public record ClassScopeSnapshot(
            Set<EduWorkflowPolicy.Role> roles,
            Set<Long> viceCampuses,
            Set<Long> leaderAges,
            Set<Long> homeroomClasses,
            Set<String> approveKeys) {
        public ClassScopeSnapshot(
                Set<EduWorkflowPolicy.Role> roles,
                Set<Long> viceCampuses,
                Set<Long> leaderAges,
                Set<Long> homeroomClasses) {
            this(roles, viceCampuses, leaderAges, homeroomClasses, Set.of("branch", "theme"));
        }

        /** docKey: branch/theme; người không có mã duyệt EDU-108 của loại đó không duyệt. */
        public boolean canReview(String docKey, EduWorkflowPolicy.Role role, Long ageGroupId, Long campusId) {
            if (!approveKeys.contains(docKey)) return false;
            return switch (role) {
                case PRINCIPAL -> roles.contains(role);
                // FR4.1: không giới hạn điểm trường phân công
                case VICE_PRINCIPAL -> roles.contains(role);
                case TEAM_LEADER -> roles.contains(role) && ageGroupId != null && leaderAges.contains(ageGroupId);
                case TEACHER -> false;
            };
        }
    }

    /** Tổ trưởng chỉ tính phân công của năm học schoolYearId (null = năm đang hoạt động). */
    public ClassScopeSnapshot classScopeSnapshot(CurrentUser user, Long schoolYearId) {
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
        Set<Long> ages = Set.of();
        if (user.hasRole(CareNestRole.TEACHER)) {
            roles.add(EduWorkflowPolicy.Role.TEACHER);
            ages = ledAgeGroups(user, schoolYearId, LocalDate.now(clock));
            if (!ages.isEmpty()) roles.add(EduWorkflowPolicy.Role.TEAM_LEADER);
        }
        Set<Long> campuses = assignments.stream()
                .filter(a -> a.staffRole() == StaffRole.VICE_PRINCIPAL)
                .map(a -> a.campusId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> teacherClasses = assignments.stream()
                .filter(a -> a.staffRole() == StaffRole.TEACHER)
                .map(a -> a.classroomId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> homeroomClasses =
                staffAssignmentService
                        .findHomeroomTeacherIds(List.copyOf(teacherClasses), LocalDate.now(clock))
                        .entrySet()
                        .stream()
                        .filter(entry -> Objects.equals(entry.getValue(), user.userId()))
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet());
        Set<String> approveKeys = java.util.stream.Stream.of(DocType.SEMESTER_PACKAGE, DocType.THEME_PLAN)
                // Vai trò trong snapshot đã xét phân công hiệu lực; chỉ cần mã trong tập quyền
                .filter(t -> user.hasPermission(t.approve()))
                .map(DocType::key)
                .collect(java.util.stream.Collectors.toSet());
        return new ClassScopeSnapshot(
                Set.copyOf(roles), Set.copyOf(campuses), Set.copyOf(ages), Set.copyOf(homeroomClasses), approveKeys);
    }

    /**
     * EDU-90: vai trò cao nhất của người tạo tại ngày tạo (HT/PHT/GV theo phân công hiệu lực ngày đó; Tổ trưởng gồm cả
     * phân công năm học của tài liệu chưa bắt đầu - giai đoạn chuẩn bị). Không có phân công nào ⇒ null (không rõ).
     */
    public EduWorkflowPolicy.Role creatorRole(Long creatorId, Long schoolYearId, java.time.Instant createdAt) {
        if (creatorId == null || createdAt == null) return null;
        LocalDate createdOn = LocalDate.ofInstant(createdAt, clock.getZone());
        return com.carenest.utils.RequestMemo.get(
                "eduCreatorRole:" + creatorId + ":" + schoolYearId + ":" + createdOn, () -> {
                    EduWorkflowPolicy.Role best = null;
                    for (var a : staffAssignmentService.findActiveAssignments(creatorId, createdOn)) {
                        EduWorkflowPolicy.Role role =
                                switch (a.staffRole()) {
                                    case PRINCIPAL -> EduWorkflowPolicy.Role.PRINCIPAL;
                                    case VICE_PRINCIPAL -> EduWorkflowPolicy.Role.VICE_PRINCIPAL;
                                    case AGE_GROUP_TEAM_LEADER -> EduWorkflowPolicy.Role.TEAM_LEADER;
                                    case TEACHER -> EduWorkflowPolicy.Role.TEACHER;
                                    default -> null;
                                };
                        if (role != null && (best == null || role.compareTo(best) > 0)) best = role;
                    }
                    if ((best == null || best.compareTo(EduWorkflowPolicy.Role.TEAM_LEADER) < 0)
                            && schoolYearId != null
                            && !staffAssignmentService
                                    .findYearTeamLeaderAgeGroupIds(creatorId, schoolYearId, createdOn)
                                    .isEmpty()) best = EduWorkflowPolicy.Role.TEAM_LEADER;
                    return best;
                });
    }

    /**
     * EDU-90: người tạo và vai trò cao hơn (HT > PHT > TT > GV) được xuất file; GV tạo ⇒ ai xem được đều xuất.
     * Không rõ vai trò người tạo (dữ liệu cũ) ⇒ ai xem được đều xuất. Chỉ gọi khi người dùng đã xem được tài liệu.
     */
    public static boolean canExport(
            Long viewerId,
            Set<EduWorkflowPolicy.Role> viewerRoles,
            Long creatorId,
            EduWorkflowPolicy.Role creatorRole) {
        if (creatorRole == null || creatorRole == EduWorkflowPolicy.Role.TEACHER) return true;
        if (Objects.equals(viewerId, creatorId)) return true;
        return viewerRoles.stream().anyMatch(role -> role.compareTo(creatorRole) >= 0);
    }

    public boolean canAuthorYearPlan(CurrentUser user, EduWorkflowPolicy.RolePolicy policy, Long ageGroupId) {
        return canAuthorYearPlan(user, policy, null, ageGroupId);
    }

    /** schoolYearId: năm học của kế hoạch (null ⇒ Tổ trưởng của mọi năm, dùng khi chưa gắn năm cụ thể). */
    public boolean canAuthorYearPlan(
            CurrentUser user, EduWorkflowPolicy.RolePolicy policy, Long schoolYearId, Long ageGroupId) {
        if (!canAuthorDoc(user, policy.docType()) || hasReviewerPrecedence(user, policy, schoolYearId)) {
            return false;
        }
        Set<EduWorkflowPolicy.Role> active = effectiveRoles(user, schoolYearId);
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
                                    && ledAgeGroups(user, schoolYearId, today).contains(ageGroupId);
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
        return canAuthorClassDoc(user, policy, null, ageGroupId, classroomId);
    }

    public boolean canAuthorClassDoc(
            CurrentUser user,
            EduWorkflowPolicy.RolePolicy policy,
            Long schoolYearId,
            Long ageGroupId,
            Long classroomId) {
        if (!canAuthorDoc(user, policy.docType()) || hasReviewerPrecedence(user, policy, schoolYearId)) {
            return false;
        }
        Set<EduWorkflowPolicy.Role> active = effectiveRoles(user, schoolYearId);
        LocalDate today = LocalDate.now(clock);
        for (EduWorkflowPolicy.Role creatorRole : policy.creators()) {
            if (!active.contains(creatorRole)) {
                continue;
            }
            boolean inScope =
                    switch (creatorRole) {
                        case PRINCIPAL -> true;
                        case VICE_PRINCIPAL -> isVicePrincipal(user) && inViceCampus(user, classroomId, today);
                        case TEAM_LEADER ->
                            ageGroupId != null
                                    && ledAgeGroups(user, schoolYearId, today).contains(ageGroupId);
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
        return canReviewYearPlan(user, roleKey, null, ageGroupId);
    }

    public boolean canReviewYearPlan(
            CurrentUser user, EduWorkflowPolicy.Role roleKey, Long schoolYearId, Long ageGroupId) {
        LocalDate today = LocalDate.now(clock);
        return switch (roleKey) {
            case PRINCIPAL -> isPrincipal(user);
            case VICE_PRINCIPAL -> isVicePrincipal(user);
            case TEAM_LEADER ->
                ageGroupId != null
                        && user.hasRole(CareNestRole.TEACHER)
                        && ledAgeGroups(user, schoolYearId, today).contains(ageGroupId);
            case TEACHER -> false;
        };
    }

    private boolean inViceCampus(CurrentUser user, Long classroomId, LocalDate today) {
        return classroomId != null
                && classroomService
                        .findCampusId(classroomId)
                        .map(campusId -> staffAssignmentService
                                .findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today)
                                .contains(campusId))
                        .orElse(false);
    }

    public boolean canReviewClassDoc(
            CurrentUser user, String docKey, EduWorkflowPolicy.Role roleKey, Long ageGroupId, Long classroomId) {
        return canReviewClassDoc(user, docKey, roleKey, null, ageGroupId, classroomId);
    }

    /** docKey: branch/theme; thiếu mã duyệt EDU-108 ⇒ không duyệt. */
    public boolean canReviewClassDoc(
            CurrentUser user,
            String docKey,
            EduWorkflowPolicy.Role roleKey,
            Long schoolYearId,
            Long ageGroupId,
            Long classroomId) {
        if (!canReviewDoc(user, docKey)) return false;
        LocalDate today = LocalDate.now(clock);
        return switch (roleKey) {
            case PRINCIPAL -> isPrincipal(user);
            // FR4.1: PHT có mã duyệt hồ sơ lớp duyệt ở mọi điểm trường
            case VICE_PRINCIPAL -> isVicePrincipal(user) && classroomId != null;
            case TEAM_LEADER ->
                ageGroupId != null
                        && user.hasRole(CareNestRole.TEACHER)
                        && ledAgeGroups(user, schoolYearId, today).contains(ageGroupId);
            case TEACHER -> false;
        };
    }
}
