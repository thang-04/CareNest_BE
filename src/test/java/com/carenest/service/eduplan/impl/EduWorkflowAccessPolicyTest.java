package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.RolePolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EduWorkflowAccessPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private StaffAssignmentService staffAssignmentService;

    private EduPlanAccessPolicy accessPolicy;

    private final com.carenest.service.organization.ClassroomService classroomService =
            org.mockito.Mockito.mock(com.carenest.service.organization.ClassroomService.class);

    @BeforeEach
    void setUp() {
        accessPolicy = new EduPlanAccessPolicy(staffAssignmentService, FIXED_CLOCK, classroomService);
    }

    @Test
    @DisplayName("EDU-54: PHT không duyệt lớp ngoài campus được phân công")
    void vicePrincipalCannotReviewOtherCampus() {
        CurrentUser vp = user(25L, CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(25L, StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(1L));
        given(classroomService.findCampusId(200L)).willReturn(java.util.Optional.of(2L));
        given(classroomService.findCampusId(100L)).willReturn(java.util.Optional.of(1L));
        assertThat(accessPolicy.canReviewClassDoc(vp, Role.VICE_PRINCIPAL, 3L, 200L))
                .isFalse();
        assertThat(accessPolicy.canReviewClassDoc(vp, Role.VICE_PRINCIPAL, 3L, 100L))
                .isTrue();
        var policy = new RolePolicy(List.of(Role.VICE_PRINCIPAL), List.of(Role.PRINCIPAL));
        assertThat(accessPolicy.canAuthorClassDoc(vp, policy, 3L, 200L)).isFalse();
        assertThat(accessPolicy.canAuthorClassDoc(vp, policy, 3L, 100L)).isTrue();
    }

    private static CurrentUser user(Long id, CareNestRole... roles) {
        return new CurrentUser(
                id, "kc-" + id, "user" + id, "Người thử " + id, "user" + id + "@school.edu", Set.of(roles));
    }

    @Test
    @DisplayName("EDU-54: reviewer-precedence chặn author nếu có vai trò reviewer hiệu lực theo loại, dù có TEACHER")
    void reviewerPrecedenceDeniesAuthoringEvenWithTeacherRole() {
        CurrentUser multiRoleUser = user(10L, CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeamLeaderAssignment(10L, TODAY)).willReturn(true);
        RolePolicy branchPolicy = new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER, Role.VICE_PRINCIPAL));

        assertThat(accessPolicy.hasReviewerPrecedence(multiRoleUser, branchPolicy))
                .isTrue();
        assertThat(accessPolicy.canAuthorClassDoc(multiRoleUser, branchPolicy, 1L, 101L))
                .isFalse();
    }

    @Test
    @DisplayName("EDU-54: tổ trưởng chỉ review đúng nhóm tuổi phân công, nhóm tuổi khác bị từ chối")
    void teamLeaderReviewScopedToAssignedAgeGroup() {
        CurrentUser tlUser = user(11L, CareNestRole.TEACHER);
        given(staffAssignmentService.findActiveTeamLeaderAgeGroupIds(11L, TODAY))
                .willReturn(Set.of(1L));

        assertThat(accessPolicy.canReviewYearPlan(tlUser, Role.TEAM_LEADER, 1L)).isTrue();
        assertThat(accessPolicy.canReviewYearPlan(tlUser, Role.TEAM_LEADER, 2L)).isFalse();
        assertThat(accessPolicy.canReviewClassDoc(tlUser, Role.TEAM_LEADER, 1L, 101L))
                .isTrue();
        assertThat(accessPolicy.canReviewClassDoc(tlUser, Role.TEAM_LEADER, 2L, 102L))
                .isFalse();
    }

    @Test
    @DisplayName("EDU-64: tổ trưởng không xem kế hoạch tổ đã ban hành của nhóm tuổi khác")
    void teamLeaderViewScopeStaysWithinAssignedAgeGroup() {
        CurrentUser leader = user(11L, CareNestRole.TEACHER);
        given(staffAssignmentService.findActiveTeamLeaderAgeGroupIds(11L, TODAY))
                .willReturn(Set.of(1L));
        given(staffAssignmentService.findActiveTeacherAgeGroupIds(11L, TODAY)).willReturn(Set.of());

        var scope = accessPolicy.viewScope(leader);

        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.APPROVED))
                .isTrue();
        assertThat(scope.allows(2L, com.carenest.utils.enums.EduPlanStatus.APPROVED))
                .isFalse();
    }

    @Test
    @DisplayName("EDU-54: giáo viên chỉ soạn lớp được phân công dạy, lớp khác bị từ chối")
    void teacherAuthorScopedToClassroom() {
        CurrentUser teacher = user(12L, CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeamLeaderAssignment(12L, TODAY)).willReturn(false);
        given(staffAssignmentService.hasActiveTeacherAssignment(12L, 101L, TODAY))
                .willReturn(true);
        given(staffAssignmentService.hasActiveTeacherAssignment(12L, 102L, TODAY))
                .willReturn(false);

        RolePolicy branchPolicy = new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER, Role.VICE_PRINCIPAL));
        assertThat(accessPolicy.canAuthorClassDoc(teacher, branchPolicy, 1L, 101L))
                .isTrue();
        assertThat(accessPolicy.canAuthorClassDoc(teacher, branchPolicy, 1L, 102L))
                .isFalse();
    }

    @Test
    @DisplayName("EDU-54: HT hoặc PHT hết phân công hiệu lực bị từ chối review")
    void expiredAssignmentDeniesReview() {
        CurrentUser htUser = user(13L, CareNestRole.PRINCIPAL);
        given(staffAssignmentService.hasActivePrincipalAssignment(13L, TODAY)).willReturn(false);

        assertThat(accessPolicy.canReviewYearPlan(htUser, Role.PRINCIPAL, 1L)).isFalse();

        CurrentUser vpUser = user(14L, CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(14L, StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of());

        assertThat(accessPolicy.canReviewYearPlan(vpUser, Role.VICE_PRINCIPAL, 1L))
                .isFalse();
    }

    @Test
    @DisplayName("EDU-60: tổ trưởng xem annual ban hành đúng tuổi, thu hồi phân công mất scope")
    void annualLeaderScopeUsesCurrentAssignment() {
        var leader = user(50L, CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeamLeaderAssignment(50L, TODAY)).willReturn(true);
        given(staffAssignmentService.findActiveTeamLeaderAgeGroupIds(50L, TODAY))
                .willReturn(Set.of(1L));
        var annual = new RolePolicy(List.of(Role.VICE_PRINCIPAL), List.of(Role.PRINCIPAL));
        var team = new RolePolicy(List.of(Role.TEAM_LEADER), List.of(Role.VICE_PRINCIPAL));
        var scope = accessPolicy.annualViewScope(leader, annual, team, Set.of(1L, 2L));
        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.APPROVED))
                .isTrue();
        assertThat(scope.allows(2L, com.carenest.utils.enums.EduPlanStatus.APPROVED))
                .isFalse();
        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.DRAFT))
                .isFalse();
        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.REVIEW))
                .isFalse();
        given(staffAssignmentService.hasActiveTeamLeaderAssignment(50L, TODAY)).willReturn(false);
        assertThat(accessPolicy
                        .annualViewScope(leader, annual, team, Set.of(1L, 2L))
                        .approvedAgeGroupIds())
                .isEmpty();
    }

    @Test
    @DisplayName("EDU-60: PHT chỉ tạo TEAM xem mọi tuổi đã ban hành, không bypass policy năm")
    void managementTeamCreatorSeesAllApprovedAgesOnly() {
        var vp = user(51L, CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(51L, StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(1L));
        var annual = new RolePolicy(List.of(Role.TEAM_LEADER), List.of(Role.PRINCIPAL));
        var team = new RolePolicy(List.of(Role.VICE_PRINCIPAL), List.of(Role.PRINCIPAL));
        var scope = accessPolicy.annualViewScope(vp, annual, team, Set.of(1L, 2L));
        assertThat(scope.approvedAgeGroupIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.DRAFT))
                .isFalse();
        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.REVIEW))
                .isFalse();
        var independent = new RolePolicy(List.of(Role.VICE_PRINCIPAL), List.of(Role.PRINCIPAL));
        assertThat(accessPolicy
                        .annualViewScope(vp, independent, team, Set.of(1L, 2L))
                        .fullAgeGroupIds())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("EDU-60: reviewer tổ trưởng đúng tuổi không nhận quyền DRAFT từ TEAM")
    void annualReviewerHasStatusScopedVisibility() {
        var leader = user(52L, CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeamLeaderAssignment(52L, TODAY)).willReturn(true);
        given(staffAssignmentService.findActiveTeamLeaderAgeGroupIds(52L, TODAY))
                .willReturn(Set.of(1L));
        var annual = new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER));
        var team = new RolePolicy(List.of(Role.TEAM_LEADER), List.of(Role.VICE_PRINCIPAL));
        var scope = accessPolicy.annualViewScope(leader, annual, team, Set.of(1L, 2L));
        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.REVIEW))
                .isTrue();
        assertThat(scope.allows(1L, com.carenest.utils.enums.EduPlanStatus.DRAFT))
                .isFalse();
        assertThat(scope.allows(2L, com.carenest.utils.enums.EduPlanStatus.REVIEW))
                .isFalse();
    }
}
