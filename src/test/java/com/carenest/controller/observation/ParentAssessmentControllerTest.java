package com.carenest.controller.observation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentEvaluationsResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.observation.ParentAssessmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ParentAssessmentController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ParentAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ParentAssessmentService parentAssessmentService;

    @Test
    @DisplayName("Phụ huynh xem đánh giá đã chốt của con; con người khác ⇒ 4108")
    void parentReadsEvaluationsAndOtherChildIsForbidden() throws Exception {
        CurrentUser parent = authenticate(TestPermissions.userWithDefaults(5L, CareNestRole.PARENT));
        given(parentAssessmentService.getEvaluations(eq(parent), eq(9L), eq(null)))
                .willReturn(new ParentEvaluationsResponse(9L, 1L, List.of(), List.of(), null));
        mockMvc.perform(get("/api/children/9/evaluations").with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schoolYearId").value(1));

        given(parentAssessmentService.getRewards(eq(parent), eq(8L), any(), any()))
                .willThrow(GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN));
        mockMvc.perform(get("/api/children/8/rewards?from=2026-10-01&to=2026-10-21")
                        .with(token(CareNestRole.PARENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(4108));
    }

    @Test
    @DisplayName("Nhân viên không gọi được API phụ huynh")
    void staffCannotUseParentEndpoints() throws Exception {
        authenticate(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));
        mockMvc.perform(get("/api/children/9/daily-assessments?from=2026-10-01&to=2026-10-21")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/9/rewards?from=2026-10-01&to=2026-10-21")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(parentAssessmentService);
    }

    private CurrentUser authenticate(CurrentUser user) {
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
