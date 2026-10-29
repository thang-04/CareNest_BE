package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.account.UpdatePermissionOverridesRequest;
import com.carenest.dto.account.UpdatePermissionOverridesRequest.OverrideItem;
import com.carenest.dto.account.UpdateRolePermissionsRequest;
import com.carenest.dto.account.UserPermissionOverridesResponse;
import com.carenest.entity.account.PermissionChangeLog;
import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.mapper.account.PermissionMapper;
import com.carenest.repository.account.PermissionChangeLogRepository;
import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    private static final Long PRINCIPAL_ID = 1L;
    private static final Long TEACHER_ID = 20L;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Mock
    private PermissionChangeLogRepository permissionChangeLogRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private IdentityProviderClient identityProviderClient;

    private PermissionServiceImpl permissionService;

    private final CurrentUser principal = TestPermissions.userWithDefaults(PRINCIPAL_ID, CareNestRole.PRINCIPAL);

    @BeforeEach
    void setUp() {
        permissionService = new PermissionServiceImpl(
                rolePermissionRepository,
                userPermissionOverrideRepository,
                permissionChangeLogRepository,
                userAccountRepository,
                identityProviderClient,
                new PermissionMapper(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    @DisplayName("AUTH-11: không sửa được ma trận của role cố định")
    void fixedRoleMatrixCannotBeChanged() {
        for (CareNestRole fixedRole : List.of(CareNestRole.PRINCIPAL, CareNestRole.PARENT, CareNestRole.ADMIN)) {
            assertThatThrownBy(() -> permissionService.updateRolePermissions(
                            principal, fixedRole, new UpdateRolePermissionsRequest(List.of(), "thử")))
                    .isInstanceOf(GlobalException.class)
                    .extracting("apiCode")
                    .isEqualTo(ApiCode.ROLE_PERMISSIONS_FIXED);
        }
        verifyNoInteractions(rolePermissionRepository, permissionChangeLogRepository);
    }

    @Test
    @DisplayName("AUTH-11: không gán được PERMISSION_MANAGE cho giáo viên")
    void nonConfigurablePermissionIsRejected() {
        assertThatThrownBy(() -> permissionService.updateRolePermissions(
                        principal,
                        CareNestRole.TEACHER,
                        new UpdateRolePermissionsRequest(List.of(Permission.PERMISSION_MANAGE), "leo quyền")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.PERMISSION_NOT_CONFIGURABLE_FOR_ROLE);
        verifyNoInteractions(rolePermissionRepository, permissionChangeLogRepository);
    }

    @Test
    @DisplayName("AUTH-14: tắt quyền của role xóa ô ma trận và ghi lịch sử ON→OFF kèm lý do")
    void turningOffRolePermissionRemovesRowAndWritesHistory() {
        RolePermission teacherChildView = RolePermission.create(CareNestRole.TEACHER, "CHILD_PROFILE_VIEW", null);
        given(rolePermissionRepository.findByRole(CareNestRole.TEACHER))
                .willReturn(List.of(teacherChildView), List.of());

        var response = permissionService.updateRolePermissions(
                principal, CareNestRole.TEACHER, new UpdateRolePermissionsRequest(List.of(), "Giảm quyền thử nghiệm"));

        verify(rolePermissionRepository).deleteAll(List.of(teacherChildView));
        List<PermissionChangeLog> logs = capturedLogs();
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getTargetRole()).isEqualTo(CareNestRole.TEACHER);
            assertThat(log.getPermissionCode()).isEqualTo("CHILD_PROFILE_VIEW");
            assertThat(log.getBeforeState()).isEqualTo(PermissionState.ON);
            assertThat(log.getAfterState()).isEqualTo(PermissionState.OFF);
            assertThat(log.getActorUserId()).isEqualTo(PRINCIPAL_ID);
            assertThat(log.getReason()).isEqualTo("Giảm quyền thử nghiệm");
        });
        assertThat(response.permissions()).isEmpty();
        assertThat(response.editable()).isTrue();
    }

    @Test
    @DisplayName("AUTH-12: không tự sửa ngoại lệ quyền của chính mình")
    void cannotOverrideOwnPermissions() {
        assertThatThrownBy(() -> permissionService.updatePermissionOverrides(principal, PRINCIPAL_ID, revoke()))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CANNOT_CHANGE_OWN_PERMISSIONS);
        verifyNoInteractions(userAccountRepository, identityProviderClient, userPermissionOverrideRepository);
    }

    @Test
    @DisplayName("AUTH-11: tài khoản PRINCIPAL hoặc chỉ là phụ huynh không có ngoại lệ")
    void privilegedOrNonConfigurableTargetIsRejected() {
        givenTarget(TEACHER_ID, "PRINCIPAL", "default-roles-carenest");
        assertThatThrownBy(() -> permissionService.updatePermissionOverrides(principal, TEACHER_ID, revoke()))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PERMISSIONS_NOT_OVERRIDABLE);

        givenTarget(TEACHER_ID, "PARENT");
        assertThatThrownBy(() -> permissionService.updatePermissionOverrides(principal, TEACHER_ID, revoke()))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PERMISSIONS_NOT_OVERRIDABLE);
        verify(userPermissionOverrideRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AUTH-10: thu hồi quyền của một giáo viên lưu ngoại lệ, ghi lịch sử INHERIT→REVOKE")
    void revokingTeacherPermissionSavesOverrideAndHistory() {
        givenTarget(TEACHER_ID, "TEACHER");
        given(userPermissionOverrideRepository.findByUserId(TEACHER_ID))
                .willReturn(
                        List.of(),
                        List.of(UserPermissionOverride.create(
                                TEACHER_ID, "CHILD_PROFILE_VIEW", PermissionEffect.REVOKE, "x", 1L)));
        given(rolePermissionRepository.findByRoleIn(any()))
                .willReturn(List.of(RolePermission.create(CareNestRole.TEACHER, "CHILD_PROFILE_VIEW", null)));

        UserPermissionOverridesResponse response =
                permissionService.updatePermissionOverrides(principal, TEACHER_ID, revoke());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserPermissionOverride>> saved = ArgumentCaptor.forClass(List.class);
        verify(userPermissionOverrideRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(override -> {
            assertThat(override.getEffect()).isEqualTo(PermissionEffect.REVOKE);
            assertThat(override.getCreatedBy()).isEqualTo(PRINCIPAL_ID);
        });
        assertThat(capturedLogs()).singleElement().satisfies(log -> {
            assertThat(log.getTargetUserId()).isEqualTo(TEACHER_ID);
            assertThat(log.getBeforeState()).isEqualTo(PermissionState.INHERIT);
            assertThat(log.getAfterState()).isEqualTo(PermissionState.REVOKE);
        });
        assertThat(response.effectivePermissions()).isEmpty();
        assertThat(response.overrides()).hasSize(1);
    }

    @Test
    void duplicatePermissionInOverridesIsRejected() {
        givenTarget(TEACHER_ID, "TEACHER");
        var request = new UpdatePermissionOverridesRequest(
                List.of(
                        new OverrideItem(Permission.CHILD_PROFILE_VIEW, PermissionEffect.GRANT),
                        new OverrideItem(Permission.CHILD_PROFILE_VIEW, PermissionEffect.REVOKE)),
                "trùng");

        assertThatThrownBy(() -> permissionService.updatePermissionOverrides(principal, TEACHER_ID, request))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PERMISSION_DUPLICATED);
    }

    @Test
    void identityProviderDownReturnsServiceUnavailable() {
        given(userAccountRepository.findById(TEACHER_ID)).willReturn(Optional.of(account(TEACHER_ID)));
        given(identityProviderClient.getRealmRoleNames("kc-" + TEACHER_ID))
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null));

        assertThatThrownBy(() -> permissionService.getPermissionOverrides(TEACHER_ID))
                .extracting("apiCode")
                .isEqualTo(ApiCode.IDENTITY_PROVIDER_UNAVAILABLE);
    }

    @Test
    void unknownTargetReturnsNotFound() {
        given(userAccountRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.updatePermissionOverrides(principal, 99L, revoke()))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void changeLogFilterAcceptsOnlyOneTarget() {
        assertThatThrownBy(() -> permissionService.getPermissionChangeLogs(CareNestRole.TEACHER, 5L, 0, 20))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PERMISSION_LOG_FILTER_INVALID);
    }

    private void givenTarget(Long userId, String... realmRoles) {
        given(userAccountRepository.findById(userId)).willReturn(Optional.of(account(userId)));
        given(identityProviderClient.getRealmRoleNames("kc-" + userId)).willReturn(List.of(realmRoles));
    }

    private static UserAccount account(Long id) {
        UserAccount account = UserAccount.create("kc-" + id, "user." + id, null, "Người dùng");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static UpdatePermissionOverridesRequest revoke() {
        return new UpdatePermissionOverridesRequest(
                List.of(new OverrideItem(Permission.CHILD_PROFILE_VIEW, PermissionEffect.REVOKE)), "Tạm dừng");
    }

    private List<PermissionChangeLog> capturedLogs() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PermissionChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(permissionChangeLogRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
