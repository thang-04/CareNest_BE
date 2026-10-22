package com.carenest.controller.child;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.child.YearTransitionService;
import com.carenest.utils.enums.CareNestRole;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Lớp 1 (role) + validation cho xếp lớp, readiness, kích hoạt năm học. */
@WebMvcTest(YearTransitionController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class YearTransitionControllerTest {

    private static final String YEAR = "/api/school-years/5";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private YearTransitionService yearTransitionService;

    @Test
    void activateWithoutConfirmNameIs400() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(post(YEAR + "/activate")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(yearTransitionService, never()).activateSchoolYear(any(), any(), any());
    }

    @Test
    void savePlacementsReturnsSavedCount() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);
        given(yearTransitionService.savePlacements(any(), any(), any())).willReturn(1);

        mockMvc.perform(put(YEAR + "/placements")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"childId\":1,\"action\":\"LEAVE\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(1));
        verify(yearTransitionService).savePlacements(any(), any(), any());
    }

    @Test
    void vicePrincipalCannotSeeReadiness() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(get(YEAR + "/readiness").with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotSeePlacements() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get(YEAR + "/placements").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo.user", "Demo", null, Set.of(role));
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
