package com.carenest.controller.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.account.RolePermissionResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.PermissionService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(PermissionController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class PermissionControllerTest {

    private static final String UPDATE_TEACHER_JSON = """
            {"permissions":["CHILD_PROFILE_VIEW"],"reason":"Cho phép xem hồ sơ trẻ"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private PermissionService permissionService;

    @ParameterizedTest
    @EnumSource(
            value = CareNestRole.class,
            names = {"VICE_PRINCIPAL", "TEACHER", "ADMIN", "KITCHEN_STAFF", "PARENT"})
    @DisplayName("AUTH-11: chỉ PRINCIPAL vào được các endpoint quản lý quyền")
    void nonPrincipalRolesAreRejectedOnEveryEndpoint(CareNestRole role) throws Exception {
        authenticateAs(role);

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/permissions"),
                get("/api/role-permissions"),
                get("/api/role-permissions/TEACHER"),
                put("/api/role-permissions/TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_TEACHER_JSON),
                get("/api/accounts/20/permission-overrides"),
                put("/api/accounts/20/permission-overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overrides\":[],\"reason\":\"x\"}"),
                get("/api/permission-change-logs"))) {
            mockMvc.perform(withRole(request, role))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
        }
        verifyNoInteractions(permissionService);
    }

    @Test
    void principalUpdatesTeacherMatrix() throws Exception {
        CurrentUser principal = authenticateAs(CareNestRole.PRINCIPAL);
        given(permissionService.updateRolePermissions(eq(principal), eq(CareNestRole.TEACHER), any()))
                .willReturn(new RolePermissionResponse(
                        CareNestRole.TEACHER,
                        true,
                        List.of(Permission.CHILD_PROFILE_VIEW),
                        List.of(Permission.CHILD_PROFILE_VIEW)));

        mockMvc.perform(withRole(
                        put("/api/role-permissions/TEACHER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(UPDATE_TEACHER_JSON),
                        CareNestRole.PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("CHILD_PROFILE_VIEW"));
    }

    @Test
    void missingReasonOrUnknownCodeIsRejectedWith400() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(withRole(
                        put("/api/role-permissions/TEACHER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"permissions\":[]}"),
                        CareNestRole.PRINCIPAL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withRole(
                        put("/api/role-permissions/TEACHER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"permissions\":[\"child.view\"],\"reason\":\"x\"}"),
                        CareNestRole.PRINCIPAL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withRole(get("/api/role-permissions/HEADMASTER"), CareNestRole.PRINCIPAL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withRole(get("/api/permission-change-logs?size=500"), CareNestRole.PRINCIPAL))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(permissionService);
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/permissions")).andExpect(status().isUnauthorized());
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(1L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestBuilder withRole(MockHttpServletRequestBuilder request, CareNestRole role) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority(role.authority())));
    }
}
