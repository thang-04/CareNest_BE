package com.carenest.controller.organization;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.enums.CareNestRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SchoolStructureController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SchoolStructureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private SchoolStructureService schoolStructureService;

    @Test
    void teacherCannotReadStructureForAssignmentAndCampusIdIsRequired() throws Exception {
        given(currentUserService.getCurrentUser())
                .willReturn(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));
        given(currentUserService.getCurrentUser())
                .willReturn(TestPermissions.userWithDefaults(2L, CareNestRole.PRINCIPAL));
        mockMvc.perform(get("/api/classrooms").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schoolStructureService);
    }
}
