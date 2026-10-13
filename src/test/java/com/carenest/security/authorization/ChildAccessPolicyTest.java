package com.carenest.security.authorization;

import static com.carenest.security.authorization.CampusAccessPolicyTest.FIXED_CLOCK;
import static com.carenest.security.authorization.CampusAccessPolicyTest.TODAY;
import static com.carenest.security.authorization.CampusAccessPolicyTest.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChildAccessPolicyTest {

    private static final long CLASS_A1 = 11L;
    private static final long CLASS_B1 = 21L;
    private static final long CAMPUS_A = 1L;
    private static final long CAMPUS_B = 2L;
    private static final long CHILD_IN_A1 = 101L;
    private static final long CHILD_IN_B1 = 201L;
    private static final long CHILD_WITHOUT_ENROLLMENT = 999L;

    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final ClassroomService classroomService = mock(ClassroomService.class);
    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final GuardianChildLinkService guardianChildLinkService = mock(GuardianChildLinkService.class);
    private final ChildAccessPolicy policy = new ChildAccessPolicy(
            enrollmentService,
            guardianChildLinkService,
            new ClassroomAccessPolicy(
                    staffAssignmentService,
                    classroomService,
                    new CampusAccessPolicy(staffAssignmentService, FIXED_CLOCK),
                    FIXED_CLOCK),
            FIXED_CLOCK);

    @BeforeEach
    void setUpSchool() {
        given(classroomService.findCampusId(CLASS_A1)).willReturn(Optional.of(CAMPUS_A));
        given(classroomService.findCampusId(CLASS_B1)).willReturn(Optional.of(CAMPUS_B));
        given(enrollmentService.findActiveClassroomIds(CHILD_IN_A1, TODAY)).willReturn(List.of(CLASS_A1));
        given(enrollmentService.findActiveClassroomIds(CHILD_IN_B1, TODAY)).willReturn(List.of(CLASS_B1));
        given(enrollmentService.findActiveClassroomIds(CHILD_WITHOUT_ENROLLMENT, TODAY))
                .willReturn(List.of());
    }

    @Test
    void teacherCanViewChildInAssignedClass() {
        CurrentUser teacher = teacherOf(CLASS_A1);

        assertThat(policy.canViewChild(teacher, CHILD_IN_A1)).isTrue();
    }

    @Test
    void teacherCannotViewChildInAnotherClass() {
        CurrentUser teacher = teacherOf(CLASS_A1);

        assertThat(policy.canViewChild(teacher, CHILD_IN_B1)).isFalse();
    }

    @Test
    void parentCanViewOwnLinkedChild() {
        CurrentUser parent = user(CareNestRole.PARENT);
        given(guardianChildLinkService.isGuardianOf(parent.userId(), CHILD_IN_A1))
                .willReturn(true);

        assertThat(policy.canViewChild(parent, CHILD_IN_A1)).isTrue();
    }

    @Test
    void parentCannotViewAnotherParentsChildAndNoStaffLookupIsMade() {
        CurrentUser parent = user(CareNestRole.PARENT);
        given(guardianChildLinkService.isGuardianOf(parent.userId(), CHILD_IN_A1))
                .willReturn(true);

        assertThat(policy.canViewChild(parent, CHILD_IN_B1)).isFalse();
        verifyNoInteractions(staffAssignmentService);
    }

    @Test
    void vicePrincipalCanViewChildrenOnlyInAssignedCampus() {
        CurrentUser vicePrincipal = user(CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.hasActiveCampusAssignment(
                        vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, CAMPUS_A, TODAY))
                .willReturn(true);

        assertThat(policy.canViewChild(vicePrincipal, CHILD_IN_A1)).isTrue();
        assertThat(policy.canViewChild(vicePrincipal, CHILD_IN_B1)).isFalse();
    }

    @Test
    void principalWithActiveAssignmentCanViewEveryEnrolledChild() {
        CurrentUser principal = user(CareNestRole.PRINCIPAL);
        given(staffAssignmentService.hasActivePrincipalAssignment(principal.userId(), TODAY))
                .willReturn(true);

        assertThat(policy.canViewChild(principal, CHILD_IN_A1)).isTrue();
        assertThat(policy.canViewChild(principal, CHILD_IN_B1)).isTrue();
    }

    @Test
    void kitchenStaffCannotViewChildRecords() {
        CurrentUser kitchen = user(CareNestRole.KITCHEN_STAFF);

        assertThat(policy.canViewChild(kitchen, CHILD_IN_A1)).isFalse();
        verifyNoInteractions(enrollmentService, staffAssignmentService);
    }

    @Test
    void adminDoesNotBypassChildScope() {
        CurrentUser admin = user(CareNestRole.ADMIN);

        assertThat(policy.canViewChild(admin, CHILD_IN_A1)).isFalse();
        verifyNoInteractions(enrollmentService, staffAssignmentService);
    }

    @Test
    void teacherRoleWithoutAssignmentIsDenied() {
        CurrentUser teacherWithoutClass = user(CareNestRole.TEACHER);

        assertThat(policy.canViewChild(teacherWithoutClass, CHILD_IN_A1)).isFalse();
    }

    @Test
    void userWhoIsBothTeacherAndParentGetsUnionOfBothScopes() {
        CurrentUser teacherParent = new CurrentUser(
                7L, "kc-7", "teacher.parent", "Demo", null, Set.of(CareNestRole.TEACHER, CareNestRole.PARENT));
        given(staffAssignmentService.hasActiveTeacherAssignment(7L, CLASS_A1, TODAY))
                .willReturn(true);
        given(guardianChildLinkService.isGuardianOf(7L, CHILD_IN_B1)).willReturn(true);

        assertThat(policy.canViewChild(teacherParent, CHILD_IN_A1)).isTrue();
        assertThat(policy.canViewChild(teacherParent, CHILD_IN_B1)).isTrue();
        assertThat(policy.canViewChild(teacherParent, CHILD_WITHOUT_ENROLLMENT)).isFalse();
    }

    @Test
    void childWithoutCurrentEnrollmentAndUnknownIdGiveSameForbiddenResult() {
        CurrentUser principal = user(CareNestRole.PRINCIPAL);
        given(staffAssignmentService.hasActivePrincipalAssignment(anyLong(), any()))
                .willReturn(true);

        assertThatThrownBy(() -> policy.checkCanViewChild(principal, CHILD_WITHOUT_ENROLLMENT))
                .isInstanceOf(GlobalException.class)
                .hasMessage("You do not have access to this child")
                .extracting("apiCode")
                .isEqualTo(ApiCode.FORBIDDEN);
    }

    private CurrentUser teacherOf(long classroomId) {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeacherAssignment(teacher.userId(), classroomId, TODAY))
                .willReturn(true);
        return teacher;
    }
}
