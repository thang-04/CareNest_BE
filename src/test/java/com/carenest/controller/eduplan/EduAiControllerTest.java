package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.eduplan.EduAiService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EduAiController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class EduAiControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrentUserService users;

    @MockitoBean
    private EduAiService ai;

    @MockitoBean
    private com.carenest.service.eduplan.impl.rag.EduRagRetriever rag;

    @Test
    void activitySuggestionFollowsThemePlanEditCodeNotRole() throws Exception {
        // EDU-108, EDU-110 (12): PHT có mã soạn hồ sơ chủ đề gọi được; GV không có mã bị từ chối
        String body = "{\"themeName\":\"Gia đình\",\"goals\":[{\"id\":\"1\",\"no\":1,\"text\":\"MT\",\"lines\":[]}]}";
        given(users.getCurrentUser())
                .willReturn(new CurrentUser(
                        7L,
                        "kc7",
                        "demo",
                        "Demo",
                        null,
                        Set.of(CareNestRole.VICE_PRINCIPAL),
                        Set.of(com.carenest.utils.enums.Permission.EDU_THEME_PLAN_EDIT)));
        given(ai.suggestActivities(any(), any())).willReturn(List.of());
        mvc.perform(post("/api/education-plans/ai/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk());

        given(users.getCurrentUser())
                .willReturn(new CurrentUser(8L, "kc8", "gv", "GV", null, Set.of(CareNestRole.TEACHER), Set.of()));
        mvc.perform(post("/api/education-plans/ai/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    private void user() {
        given(users.getCurrentUser())
                .willReturn(new CurrentUser(7L, "kc7", "demo", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL)));
    }

    @Test
    void annualEndpointReturnsVerifiedCatalogText() throws Exception {
        user();
        given(ai.matchGoalContents(any(), eq(9L)))
                .willReturn(new EduAiResponse.GoalContentMap(
                        List.of(new EduAiResponse.GoalContentMatch(1L, 2L, "Hát theo giai điệu", "hát")), List.of()));
        mvc.perform(post("/api/education-plans/ai/year-plans/9/goal-content-map")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches[0].text").value("Hát theo giai điệu"));
    }

    @Test
    void annualEndpointRelaysServicePermissionFailure() throws Exception {
        user();
        given(ai.matchGoalContents(any(), any())).willThrow(GlobalException.of(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN));
        mvc.perform(post("/api/education-plans/ai/year-plans/9/goal-content-map")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void feedbackAcceptsSchoolYearAndRejectsInvalidKind() throws Exception {
        user();
        for (String kind : List.of("SCHOOL_YEAR", "YEAR"))
            mvc.perform(post("/api/education-plans/ai/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kind\":\"" + kind + "\",\"suggested\":2,\"kept\":1}")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                    .andExpect(kind.equals("SCHOOL_YEAR") ? status().isOk() : status().isBadRequest());
    }
}
