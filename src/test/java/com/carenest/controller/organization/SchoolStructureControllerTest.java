package com.carenest.controller.organization;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.organization.CampusOptionResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
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
    void principalListsCampuses() throws Exception {
        CurrentUser principal = TestPermissions.userWithDefaults(2L, CareNestRole.PRINCIPAL);
        given(currentUserService.getCurrentUser()).willReturn(principal);
        given(schoolStructureService.listCampuses(eq(principal)))
                .willReturn(List.of(new CampusOptionResponse(1L, "CS-A", "Cơ sở A (demo)")));

        mockMvc.perform(get("/api/campuses/options")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("CS-A"));
    }

    @Test
    void teacherCannotReadStructureForAssignmentAndCampusIdIsRequired() throws Exception {
        given(currentUserService.getCurrentUser())
                .willReturn(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));
        mockMvc.perform(get("/api/campuses/options")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());

        given(currentUserService.getCurrentUser())
                .willReturn(TestPermissions.userWithDefaults(2L, CareNestRole.PRINCIPAL));
        mockMvc.perform(get("/api/classrooms").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schoolStructureService);
    }
}
