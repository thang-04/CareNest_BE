package com.carenest.controller.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.organization.VicePrincipalAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(VicePrincipalAssignmentController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class VicePrincipalAssignmentControllerTest {

    private static final String URL = "/api/school-years/7/vice-principal-assignments";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private VicePrincipalAssignmentService vicePrincipalAssignmentService;

    @Test
    @DisplayName("AUTH-16: HT có STAFF_ASSIGNMENT_MANAGE ⇒ xem và phân công PHT theo năm học")
    void principalWithPermissionReachesService() throws Exception {
        CurrentUser principal = authenticate(TestPermissions.userWithDefaults(2L, CareNestRole.PRINCIPAL));

        mockMvc.perform(get(URL).with(token(CareNestRole.PRINCIPAL))).andExpect(status().isOk());
        mockMvc.perform(put(URL + "/30")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campusId\":3}"))
                .andExpect(status().isOk());

        verify(vicePrincipalAssignmentService).getAssignments(principal, 7L);
        verify(vicePrincipalAssignmentService).assign(eq(principal), eq(7L), eq(30L), any());
    }

    @Test
    @DisplayName("AUTH-16: không có STAFF_ASSIGNMENT_MANAGE ⇒ 403 ngay ở lớp 1")
    void withoutPermissionIsRejected() throws Exception {
        authenticate(new CurrentUser(3L, "kc-3", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of()));
        mockMvc.perform(get(URL).with(token(CareNestRole.VICE_PRINCIPAL))).andExpect(status().isForbidden());

        authenticate(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));
        mockMvc.perform(put(URL + "/30")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campusId\":3}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vicePrincipalAssignmentService);
    }

    private CurrentUser authenticate(CurrentUser user) {
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
