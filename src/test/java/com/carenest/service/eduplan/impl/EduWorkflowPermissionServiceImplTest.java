package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.account.RolePermissionResponse;
import com.carenest.dto.account.UpdateRolePermissionsRequest;
import com.carenest.dto.eduplan.EduWorkflowPermissionRequest;
import com.carenest.dto.eduplan.EduWorkflowPermissionRequest.Document;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.PermissionService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.RolePolicy;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EduWorkflowPermissionServiceImplTest {

    private final EduWorkflowService workflow = mock(EduWorkflowService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final EduWorkflowPermissionServiceImpl service =
            new EduWorkflowPermissionServiceImpl(workflow, permissions);
    private final CurrentUser principal = TestPermissions.userWithDefaults(1L, CareNestRole.PRINCIPAL);

    @BeforeEach
    void setUp() {
        given(workflow.loadAllPolicies())
                .willReturn(Map.of(
                        "schoolYear", new RolePolicy(List.of(Role.VICE_PRINCIPAL), List.of(Role.PRINCIPAL)),
                        "year", new RolePolicy(List.of(Role.TEAM_LEADER), List.of(Role.VICE_PRINCIPAL)),
                        "branch", new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER, Role.VICE_PRINCIPAL)),
                        "theme",
                                new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER, Role.VICE_PRINCIPAL))));
        for (CareNestRole role : List.of(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEAM_LEADER, CareNestRole.TEACHER)) {
            given(permissions.getRolePermissions(role))
                    .willReturn(new RolePermissionResponse(
                            role, true, List.of(), new ArrayList<>(TestPermissions.defaultsOf(role))));
        }
    }

    private static List<Document> defaults() {
        return new ArrayList<>(List.of(
                new Document(
                        "SCHOOL_YEAR",
                        List.of("VICE_PRINCIPAL"),
                        List.of("PRINCIPAL"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL")),
                new Document(
                        "TEAM",
                        List.of("TEAM_LEADER"),
                        List.of("VICE_PRINCIPAL"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL")),
                new Document(
                        "SEMESTER_PACKAGE",
                        List.of("TEACHER"),
                        List.of("TEAM_LEADER", "VICE_PRINCIPAL"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER", "TEACHER"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER")),
                new Document(
                        "THEME_PLAN",
                        List.of("TEACHER"),
                        List.of("TEAM_LEADER", "VICE_PRINCIPAL"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER", "TEACHER"),
                        List.of("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER"))));
    }

    @Test
    @DisplayName("FR7.2: GET trả ô tích theo ma trận mặc định")
    void getReflectsMatrix() {
        var response = service.getWorkflowPermissions(principal);
        assertThat(response.documents()).hasSize(4);
        assertThat(response.documents().get(0).editRoles()).containsExactly("PRINCIPAL", "VICE_PRINCIPAL");
        assertThat(response.documents().get(3).approveRoles())
                .containsExactly("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER");
    }

    @Test
    @DisplayName("FR7.5: bỏ tích PHT soạn gói học kỳ ⇒ lưu policy và cập nhật ma trận PHT kèm lý do")
    void untickUpdatesMatrix() {
        List<Document> docs = defaults();
        Document sp = docs.get(2);
        docs.set(
                2,
                new Document(
                        sp.docType(),
                        sp.creators(),
                        sp.reviewers(),
                        List.of("PRINCIPAL", "TEAM_LEADER", "TEACHER"),
                        sp.approveRoles()));

        service.saveWorkflowPermissions(principal, new EduWorkflowPermissionRequest(1L, docs, "PHT dinh dưỡng"));

        verify(workflow).savePolicy(eq(principal), any());
        ArgumentCaptor<UpdateRolePermissionsRequest> captor =
                ArgumentCaptor.forClass(UpdateRolePermissionsRequest.class);
        verify(permissions).updateRolePermissions(eq(principal), eq(CareNestRole.VICE_PRINCIPAL), captor.capture());
        assertThat(captor.getValue().permissions())
                .doesNotContain(Permission.EDU_SEMESTER_PACKAGE_EDIT)
                .contains(Permission.EDU_THEME_PLAN_EDIT);
        verify(permissions, never()).updateRolePermissions(any(), eq(CareNestRole.TEACHER), any());
    }

    @Test
    @DisplayName("FR7.4: soạn trùng duyệt hoặc chuỗi duyệt rỗng ⇒ SIGNERS_INVALID, không lưu")
    void invalidChainRejected() {
        List<Document> docs = defaults();
        Document sy = docs.get(0);
        docs.set(
                0,
                new Document(
                        sy.docType(), sy.creators(), List.of("VICE_PRINCIPAL"), sy.editRoles(), sy.approveRoles()));
        assertThatThrownBy(() ->
                        service.saveWorkflowPermissions(principal, new EduWorkflowPermissionRequest(1L, docs, "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.SIGNERS_INVALID));

        docs.set(0, new Document(sy.docType(), sy.creators(), List.of(), sy.editRoles(), sy.approveRoles()));
        assertThatThrownBy(() ->
                        service.saveWorkflowPermissions(principal, new EduWorkflowPermissionRequest(1L, docs, "x")))
                .isInstanceOf(GlobalException.class);
        verify(workflow, never()).savePolicy(any(), any());
    }
}
