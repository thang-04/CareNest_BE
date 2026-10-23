package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.List;
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

/** Lớp 1 (role) + envelope {code, desc, data} + validation của API mục tiêu năm học. */
@WebMvcTest(EduYearPlanController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class EduYearPlanControllerTest {

    private static final String GOALS = "/api/education-plans/goals";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private EduYearPlanService eduYearPlanService;

    @Test
    void kitchenStaffIsRejectedByRole() throws Exception {
        authenticateAs(CareNestRole.KITCHEN_STAFF);

        mockMvc.perform(get(GOALS).with(token(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(eduYearPlanService, never()).getYearPlans(any(), any(), any(), any(), any());
    }

    @Test
    void listReturnsPageEnvelope() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(eduYearPlanService.getYearPlans(eq(teacher), eq(1L), eq(null), eq(null), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(GOALS).param("schoolYearId", "1").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void pageSizeAbove200IsRejected() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(get(GOALS).param("size", "500").with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createReturns201() throws Exception {
        CurrentUser leader = authenticateAs(CareNestRole.TEACHER);
        given(eduYearPlanService.createYearPlan(eq(leader), any())).willReturn(detail(EduPlanStatus.DRAFT));

        mockMvc.perform(post(GOALS)
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolYearId\":1,\"ageGroupId\":2,\"title\":\"Kế hoạch\",\"domains\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createWithoutTitleIs400() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(post(GOALS)
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolYearId\":1,\"ageGroupId\":2,\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(eduYearPlanService, never()).createYearPlan(any(), any());
    }

    @Test
    @DisplayName("EDU-05: trả lại không có lý do bị 400 trước khi tới service")
    void returnWithoutReasonIs400() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(post(GOALS + "/5/return")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(eduYearPlanService, never()).returnYearPlan(any(), any(), any());
    }

    @Test
    void teacherCannotCallIssue() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(post(GOALS + "/5/issue").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        verify(eduYearPlanService, never()).issueYearPlan(any(), any(), any());
    }

    @Test
    void submitWithoutBodyPassesNullNote() throws Exception {
        CurrentUser leader = authenticateAs(CareNestRole.TEACHER);
        given(eduYearPlanService.submitYearPlan(leader, 5L, null)).willReturn(detail(EduPlanStatus.PENDING_VP));

        mockMvc.perform(post(GOALS + "/5/submit").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_VP"));
    }

    @Test
    void serviceConflictKeepsEnvelope() throws Exception {
        CurrentUser leader = authenticateAs(CareNestRole.TEACHER);
        given(eduYearPlanService.submitYearPlan(leader, 5L, null))
                .willThrow(new GlobalException(ApiCode.CONFLICT, "Plan needs at least one goal"));

        mockMvc.perform(post(GOALS + "/5/submit").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.desc").value("Plan needs at least one goal"));
    }

    private static YearPlanDetailResponse detail(EduPlanStatus status) {
        return new YearPlanDetailResponse(
                5L,
                "KHNH-2627-MGB",
                1L,
                "2026-2027",
                2L,
                "Mẫu giáo bé (3–4 tuổi)",
                "Kế hoạch",
                "",
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                new YearPlanDetailResponse.Permissions(true, true, false, false, true));
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
