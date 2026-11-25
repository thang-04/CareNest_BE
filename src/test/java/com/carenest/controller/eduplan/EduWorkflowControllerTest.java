package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.eduplan.EduWorkflowPolicyResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/**
 * Controller & Security contract test cho GET /education-plans/workflow-policy (EDU-54). Lưu policy chuyển sang PUT
 * /permissions/edu-workflow (EDU-108).
 */
@WebMvcTest(EduWorkflowController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class EduWorkflowControllerTest {

    private static final String POLICY_URL = "/api/education-plans/workflow-policy";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private EduWorkflowService workflowService;

    private static final Map<String, EduWorkflowPolicyResponse.PolicyEntry> SAMPLE_POLICIES = Map.of(
            "year",
            new EduWorkflowPolicyResponse.PolicyEntry(List.of("TEAM_LEADER"), List.of("VICE_PRINCIPAL")),
            "branch",
            new EduWorkflowPolicyResponse.PolicyEntry(List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")),
            "theme",
            new EduWorkflowPolicyResponse.PolicyEntry(List.of("TEACHER"), List.of("TEAM_LEADER", "VICE_PRINCIPAL")));

    @Test
    @DisplayName("GET workflow-policy: Hiệu trưởng có phân công nhận 200 và canConfigure=true")
    void getWorkflowPolicy_asPrincipalWithActiveAssignment_returns200AndCanConfigureTrue() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);
        EduWorkflowPolicyResponse response =
                new EduWorkflowPolicyResponse(1L, SAMPLE_POLICIES, true, Instant.parse("2026-11-06T00:00:00Z"));
        given(workflowService.getPolicy(any())).willReturn(response);

        mockMvc.perform(get(POLICY_URL).with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.canConfigure").value(true))
                .andExpect(jsonPath("$.data.policies.year.creators[0]").value("TEAM_LEADER"))
                .andExpect(jsonPath("$.data.policies.year.reviewers[0]").value("VICE_PRINCIPAL"));
    }

    @Test
    @DisplayName("GET workflow-policy: Phó Hiệu trưởng nhận 200 và canConfigure=false")
    void getWorkflowPolicy_asVicePrincipal_returns200AndCanConfigureFalse() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);
        EduWorkflowPolicyResponse response =
                new EduWorkflowPolicyResponse(1L, SAMPLE_POLICIES, false, Instant.parse("2026-11-06T00:00:00Z"));
        given(workflowService.getPolicy(any())).willReturn(response);

        mockMvc.perform(get(POLICY_URL).with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.canConfigure").value(false));
    }

    @Test
    @DisplayName("GET workflow-policy: Giáo viên nhận 200 và canConfigure=false")
    void getWorkflowPolicy_asTeacher_returns200AndCanConfigureFalse() throws Exception {
        authenticateAs(CareNestRole.TEACHER);
        EduWorkflowPolicyResponse response =
                new EduWorkflowPolicyResponse(1L, SAMPLE_POLICIES, false, Instant.parse("2026-11-06T00:00:00Z"));
        given(workflowService.getPolicy(any())).willReturn(response);

        mockMvc.perform(get(POLICY_URL).with(token(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.canConfigure").value(false));
    }

    @Test
    @DisplayName("GET workflow-policy: Chưa đăng nhập nhận 401 Unauthorized")
    void getWorkflowPolicy_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(POLICY_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET workflow-policy: Vai trò ngoài giáo dục (PARENT) bị 403 Forbidden")
    void getWorkflowPolicy_asParent_returns403() throws Exception {
        authenticateAs(CareNestRole.PARENT);
        mockMvc.perform(get(POLICY_URL).with(token(CareNestRole.PARENT))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EDU-108: PUT workflow-policy đã bỏ, Hiệu trưởng gọi không lưu được")
    void updateWorkflowPolicy_removed() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);
        mockMvc.perform(put(POLICY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"policies\":{}}")
                        .with(token(CareNestRole.PRINCIPAL)))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getStatus())
                        .isGreaterThanOrEqualTo(400));
    }

    private void authenticateAs(CareNestRole role) {
        long id =
                switch (role) {
                    case PRINCIPAL -> 1L;
                    case VICE_PRINCIPAL -> 2L;
                    case TEACHER -> 3L;
                    case PARENT -> 4L;
                    default -> 99L;
                };
        CurrentUser user =
                new CurrentUser(id, "kc-" + id, role.name().toLowerCase(), "User " + role.name(), null, Set.of(role));
        given(currentUserService.getCurrentUser()).willReturn(user);
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
