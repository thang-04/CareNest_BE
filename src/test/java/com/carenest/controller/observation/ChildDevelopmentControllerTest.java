package com.carenest.controller.observation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.observation.ChildEvaluationService;
import com.carenest.utils.enums.CareNestRole;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ChildEvaluationController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChildDevelopmentControllerTest {

    private static final String URL = "/api/assessment/children/9/development?start=2026-10-01&end=2026-10-31";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ChildEvaluationService childEvaluationService;

    @Test
    @DisplayName("Hồ sơ phát triển: GV có CHILD_PROFILE_VIEW mặc định ⇒ vào service (phạm vi lớp kiểm ở service)")
    void teacherWithDefaultPermissionReachesService() throws Exception {
        CurrentUser teacher = authenticate(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));

        mockMvc.perform(get(URL).with(token(CareNestRole.TEACHER))).andExpect(status().isOk());

        verify(childEvaluationService).development(eq(teacher), eq(9L), any(), any());
    }

    @Test
    @DisplayName("Hồ sơ phát triển: HT tắt CHILD_PROFILE_VIEW của GV ⇒ 403 ngay ở lớp 1")
    void teacherWithoutPermissionIsRejected() throws Exception {
        authenticate(new CurrentUser(4L, "kc-4", "gv", "GV", null, Set.of(CareNestRole.TEACHER), Set.of()));

        mockMvc.perform(get(URL).with(token(CareNestRole.TEACHER))).andExpect(status().isForbidden());

        verifyNoInteractions(childEvaluationService);
    }

    private CurrentUser authenticate(CurrentUser user) {
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
