package com.carenest.security.authorization;

import static com.carenest.security.authorization.CampusAccessPolicyTest.FIXED_CLOCK;
import static com.carenest.security.authorization.CampusAccessPolicyTest.TODAY;
import static com.carenest.security.authorization.CampusAccessPolicyTest.user;
import static com.carenest.security.authorization.CampusAccessPolicyTest.userWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClassroomAccessPolicyTest {

    private static final long CLASS_A1 = 11L;
    private static final long CLASS_B1 = 21L;
    private static final long CAMPUS_A = 1L;
    private static final long CAMPUS_B = 2L;

    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final ClassroomService classroomService = mock(ClassroomService.class);
    private final ClassroomAccessPolicy policy = new ClassroomAccessPolicy(
            staffAssignmentService,
            classroomService,
            new CampusAccessPolicy(staffAssignmentService, FIXED_CLOCK),
            FIXED_CLOCK);

    @BeforeEach
    void setUpClassrooms() {
        given(classroomService.findCampusId(CLASS_A1)).willReturn(Optional.of(CAMPUS_A));
        given(classroomService.findCampusId(CLASS_B1)).willReturn(Optional.of(CAMPUS_B));
    }

    @Test
    void teacherCanViewAssignedClassroom() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeacherAssignment(teacher.userId(), CLASS_A1, TODAY))
                .willReturn(true);

        assertThat(policy.canViewClassroom(teacher, CLASS_A1)).isTrue();
        // ngày xét là "hôm nay" theo Clock (múi giờ trường), không phải tham số client gửi
        verify(staffAssignmentService).hasActiveTeacherAssignment(teacher.userId(), CLASS_A1, TODAY);
    }

    @Test
    void teacherCannotViewUnassignedClassroom() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeacherAssignment(teacher.userId(), CLASS_A1, TODAY))
                .willReturn(true);

        assertThat(policy.canViewClassroom(teacher, CLASS_B1)).isFalse();
    }

    @Test
    void teacherWithEndedAssignmentIsDenied() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeacherAssignment(teacher.userId(), CLASS_A1, TODAY))
                .willReturn(false);

        assertThat(policy.canViewClassroom(teacher, CLASS_A1)).isFalse();
    }

    @Test
    void vicePrincipalCanViewClassroomsOfAssignedCampusOnly() {
        CurrentUser vicePrincipal = user(CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.hasActiveCampusAssignment(
                        vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, CAMPUS_A, TODAY))
                .willReturn(true);

        assertThat(policy.canViewClassroom(vicePrincipal, CLASS_A1)).isTrue();
        assertThat(policy.canViewClassroom(vicePrincipal, CLASS_B1)).isFalse();
    }

    @Test
    void unknownClassroomIsDeniedLikeOutOfScope() {
        CurrentUser principal = user(CareNestRole.PRINCIPAL);
        given(staffAssignmentService.hasActivePrincipalAssignment(principal.userId(), TODAY))
                .willReturn(true);
        given(classroomService.findCampusId(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> policy.checkCanViewClassroom(principal, 999L))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.CLASS_ACCESS_DENIED.getDefaultDesc());
        assertThatThrownBy(() -> policy.checkCanReadClassroom(principal, 999L))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.CLASS_ACCESS_DENIED.getDefaultDesc());
    }

    @Test
    void assignedTeacherCanReadOwnClassroomOnly() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveTeacherAssignment(teacher.userId(), CLASS_A1, TODAY))
                .willReturn(true);

        assertThat(policy.canReadClassroom(teacher, CLASS_A1)).isTrue();
        assertThat(policy.canReadClassroom(teacher, CLASS_B1)).isFalse();
    }

    @Test
    @DisplayName("AUTH-20: PHT có xem liên cơ sở đọc được lớp campus khác nhưng không thao tác được")
    void allCampusViewOpensReadButNotActionOnOtherCampusClassroom() {
        CurrentUser vicePrincipal = userWith(Set.of(Permission.ALL_CAMPUS_VIEW), CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.hasActiveCampusAssignment(
                        vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, CAMPUS_A, TODAY))
                .willReturn(true);
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(CAMPUS_A));

        assertThat(policy.canReadClassroom(vicePrincipal, CLASS_B1)).isTrue();
        assertThat(policy.canViewClassroom(vicePrincipal, CLASS_B1)).isFalse();
    }
}
