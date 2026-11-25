package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.RolePolicy;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EDU-108: PHT sửa/duyệt kế hoạch giáo dục theo mã quyền HT giao. */
class EduPlanAccessPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final RolePolicy SCHOOL_YEAR =
            new RolePolicy(List.of(Role.VICE_PRINCIPAL), List.of(Role.PRINCIPAL), "schoolYear");

    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final ClassroomService classrooms = mock(ClassroomService.class);
    private EduPlanAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new EduPlanAccessPolicy(assignments, CLOCK, classrooms);
        given(assignments.findActiveCampusIds(5L, StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(1L));
        given(classrooms.findCampusId(200L)).willReturn(Optional.of(2L));
    }

    private static CurrentUser vp(Set<Permission> permissions) {
        return new CurrentUser(5L, "kc-5", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), permissions);
    }

    @Test
    @DisplayName("FR2.1: PHT có mã mặc định ⇒ soạn kế hoạch năm học, duyệt hồ sơ lớp")
    void vicePrincipalWithCodesCanEditAndApprove() {
        CurrentUser user = vp(TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));

        assertThat(policy.canAuthorYearPlan(user, SCHOOL_YEAR, 3L)).isTrue();
        assertThat(policy.canReviewDoc(user, "theme")).isTrue();
    }

    @Test
    @DisplayName("FR2.3: HT tắt mã soạn của PHT ⇒ không soạn được")
    void vicePrincipalWithoutEditCodeCannotEdit() {
        Set<Permission> perms = TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL);
        perms.remove(Permission.EDU_SCHOOL_YEAR_PLAN_EDIT);

        assertThat(policy.canAuthorYearPlan(vp(perms), SCHOOL_YEAR, 3L)).isFalse();
    }

    @Test
    @DisplayName("FR2.2: PHT hết phân công ⇒ mã không có hiệu lực")
    void expiredAssignmentDisablesCodes() {
        CurrentUser user = new CurrentUser(
                6L,
                "kc-6",
                "vp2",
                "PHT",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));
        given(assignments.findActiveCampusIds(6L, StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of());

        assertThat(policy.hasPermission(user, Permission.EDU_SCHOOL_YEAR_PLAN_EDIT))
                .isFalse();
    }

    @Test
    @DisplayName("FR5.1/FR5.2: PHT không có mã nào chỉ thấy APPROVED; PHT có mã thấy mọi trạng thái")
    void viewOnlyVicePrincipalSeesApprovedOnly() {
        var viewOnly = policy.viewScope(vp(EnumSet.noneOf(Permission.class)));
        assertThat(viewOnly.allows(3L, EduPlanStatus.APPROVED)).isTrue();
        assertThat(viewOnly.allows(3L, EduPlanStatus.DRAFT)).isFalse();
        assertThat(viewOnly.allows(3L, EduPlanStatus.REVIEW)).isFalse();

        var full = policy.viewScope(vp(TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL)));
        assertThat(full.allows(3L, EduPlanStatus.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("FR4.1: PHT có mã duyệt duyệt lớp ở điểm trường khác; thiếu mã thì không")
    void approveCodeAllowsOtherCampus() {
        assertThat(policy.canReviewClassDoc(
                        vp(EnumSet.of(Permission.EDU_THEME_PLAN_APPROVE)), "theme", Role.VICE_PRINCIPAL, 3L, 200L))
                .isTrue();
        assertThat(policy.canReviewClassDoc(
                        vp(EnumSet.of(Permission.EDU_THEME_PLAN_APPROVE)), "branch", Role.VICE_PRINCIPAL, 3L, 200L))
                .isFalse();
    }
}
