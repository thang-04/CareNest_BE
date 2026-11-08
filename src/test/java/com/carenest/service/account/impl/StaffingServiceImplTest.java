package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.account.CreateStaffAssignmentRequest;
import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import com.carenest.utils.enums.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StaffingServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final CurrentUser VICE =
            new CurrentUser(3L, "kc-3", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final StaffAccessPolicy policy = mock(StaffAccessPolicy.class);
    private final StaffingServiceImpl service = new StaffingServiceImpl(
            assignments,
            policy,
            new ManagedAccountLoader(accounts, policy),
            Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName(
            "AUTH-16: phân công GV vào lớp ⇒ role theo tài khoản, kiểm phạm vi theo campus của lớp, ghi người phân công")
    void assignsTeacherToClassroomInScope() {
        givenAccount(40L, CareNestRole.TEACHER);
        given(assignments.resolveCampusId(StaffRole.TEACHER, null, 11L)).willReturn(10L);
        given(assignments.createAssignment(any()))
                .willReturn(new StaffAssignmentResponse(5L, 40L, StaffRole.TEACHER, null, 11L, TODAY, null, false));

        StaffAssignmentResponse created =
                service.assignStaff(VICE, new CreateStaffAssignmentRequest(40L, null, 11L, null, null, null));

        verify(policy).checkCanAssign(VICE, StaffRole.TEACHER, 10L);
        verify(assignments)
                .createAssignment(
                        new NewStaffAssignment(40L, StaffRole.TEACHER, null, 11L, TODAY, null, VICE.userId(), false));
        assertThat(created.id()).isEqualTo(5L);
    }

    @Test
    @DisplayName("AUTH-12: không tự phân công cho chính mình")
    void cannotAssignSelf() {
        assertThatThrownBy(() ->
                        service.assignStaff(VICE, new CreateStaffAssignmentRequest(3L, 10L, null, null, null, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CANNOT_CHANGE_OWN_ASSIGNMENTS);
    }

    @Test
    void parentOrPrincipalAccountCannotBeAssigned() {
        givenAccount(60L, CareNestRole.PARENT);
        givenAccount(61L, CareNestRole.PRINCIPAL);

        for (Long id : new Long[] {60L, 61L}) {
            assertThatThrownBy(() -> service.assignStaff(
                            VICE, new CreateStaffAssignmentRequest(id, 10L, null, null, null, null)))
                    .extracting("apiCode")
                    .isEqualTo(ApiCode.ACCOUNT_NOT_ASSIGNABLE);
        }
        verify(assignments, never()).createAssignment(any());
    }

    @Test
    void outOfScopeAssignmentIsDeniedBeforeWriting() {
        givenAccount(40L, CareNestRole.TEACHER);
        given(assignments.resolveCampusId(StaffRole.TEACHER, null, 99L)).willReturn(20L);
        willThrow(GlobalException.of(ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED))
                .given(policy)
                .checkCanAssign(VICE, StaffRole.TEACHER, 20L);

        assertThatThrownBy(() ->
                        service.assignStaff(VICE, new CreateStaffAssignmentRequest(40L, null, 99L, null, null, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
        verify(assignments, never()).createAssignment(any());
    }

    @Test
    @DisplayName("AUTH-16: PHT không thấy được id phân công không tồn tại (cùng 403)")
    void unknownAssignmentIsForbiddenForCampusScopedActor() {
        given(assignments.findAssignment(77L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.endAssignment(VICE, 77L, TODAY))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
    }

    @Test
    void endsAssignmentInScope() {
        StaffAssignmentResponse existing =
                new StaffAssignmentResponse(5L, 40L, StaffRole.KITCHEN_STAFF, 10L, null, TODAY, null, false);
        given(assignments.findAssignment(5L)).willReturn(Optional.of(existing));
        given(assignments.resolveCampusId(StaffRole.KITCHEN_STAFF, 10L, null)).willReturn(10L);

        service.endAssignment(VICE, 5L, TODAY);

        verify(policy).checkCanAssign(VICE, StaffRole.KITCHEN_STAFF, 10L);
        verify(assignments).endAssignment(5L, TODAY);
    }

    @Test
    void classTeacherIsEndedOnlyThroughTeacherAssignmentScreen() {
        given(assignments.findAssignment(6L))
                .willReturn(Optional.of(
                        new StaffAssignmentResponse(6L, 41L, StaffRole.TEACHER, null, 9L, TODAY, null, false)));
        given(assignments.resolveCampusId(StaffRole.TEACHER, null, 9L)).willReturn(10L);

        assertThatThrownBy(() -> service.endAssignment(VICE, 6L, TODAY))
                .extracting("apiCode")
                .isEqualTo(ApiCode.TEACHER_END_VIA_CLASS);
    }

    @Test
    void assignmentCannotBeEndedBackIntoThePast() {
        given(assignments.findAssignment(5L))
                .willReturn(Optional.of(new StaffAssignmentResponse(
                        5L, 40L, StaffRole.KITCHEN_STAFF, 10L, null, TODAY.minusYears(1), null, false)));
        given(assignments.resolveCampusId(StaffRole.KITCHEN_STAFF, 10L, null)).willReturn(10L);

        assertThatThrownBy(() -> service.endAssignment(VICE, 5L, TODAY.minusDays(10)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ASSIGNMENT_END_IN_PAST);
    }

    @Test
    @DisplayName("AUTH-16: PHT/bếp đang khóa không nhận phân công mới ⇒ 409")
    void lockedKitchenAccountCannotBeAssigned() {
        givenAccount(41L, CareNestRole.KITCHEN_STAFF).changeStatus(UserStatus.INACTIVE);
        given(assignments.resolveCampusId(StaffRole.KITCHEN_STAFF, 10L, null)).willReturn(10L);

        assertThatThrownBy(() ->
                        service.assignStaff(VICE, new CreateStaffAssignmentRequest(41L, 10L, null, null, null, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ACCOUNT_LOCKED);
        verify(assignments, never()).createAssignment(any());
    }

    private UserAccount givenAccount(Long id, CareNestRole role) {
        UserAccount account = UserAccount.create("kc-" + id, "u" + id, null, "U", role);
        ReflectionTestUtils.setField(account, "id", id);
        given(accounts.findById(id)).willReturn(Optional.of(account));
        return account;
    }
}
