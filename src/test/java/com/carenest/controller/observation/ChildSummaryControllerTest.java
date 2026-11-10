package com.carenest.controller.observation;

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
import com.carenest.dto.observation.ChildSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.observation.ChildSummaryService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SummaryPeriodType;
import com.carenest.utils.enums.SummarySource;
import com.carenest.utils.enums.SummaryStatus;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Lớp 1 (role) cho API nhận xét: chỉ GV tạo/sửa/duyệt; BGH xem; phụ huynh và bếp bị chặn. */
@WebMvcTest(ChildSummaryController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChildSummaryControllerTest {

    private static final String DRAFT_URL = "/api/children/{childId}/summaries/draft";
    private static final String LIST_URL = "/api/children/{childId}/summaries";
    private static final String APPROVE_URL = "/api/summaries/{summaryId}/approve";
    private static final String DRAFT_BODY =
            "{\"periodType\":\"WEEK\",\"periodFrom\":\"2026-10-05\",\"periodTo\":\"2026-10-09\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ChildSummaryService childSummaryService;

    @Test
    void teacherCreatesDraftAndSeesAiErrorCodeWhenFallingBack() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(childSummaryService.createDraft(eq(teacher), eq(101L), any())).willReturn(summary("AI_DISABLED"));

        mockMvc.perform(post(DRAFT_URL, 101)
                        .with(tokenWithRole(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.aiErrorCode").value("AI_DISABLED"));
    }

    @Test
    void vicePrincipalCannotCreateDraft() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(post(DRAFT_URL, 101)
                        .with(tokenWithRole(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_BODY))
                .andExpect(status().isForbidden());
        verify(childSummaryService, never()).createDraft(any(), any(), any());
    }

    @Test
    void missingPeriodTypeIsRejected() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(post(DRAFT_URL, 101)
                        .with(tokenWithRole(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodFrom\":\"2026-10-05\",\"periodTo\":\"2026-10-09\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vicePrincipalCanListSummaries() throws Exception {
        CurrentUser vicePrincipal = authenticateAs(CareNestRole.VICE_PRINCIPAL);
        given(childSummaryService.listSummaries(vicePrincipal, 101L)).willReturn(List.of(summary(null)));

        mockMvc.perform(get(LIST_URL, 101).with(tokenWithRole(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5));
    }

    @Test
    void parentCannotListSummariesUntilBusinessRuleDecided() throws Exception {
        authenticateAs(CareNestRole.PARENT);

        mockMvc.perform(get(LIST_URL, 101).with(tokenWithRole(CareNestRole.PARENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void principalCannotApprove() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(post(APPROVE_URL, 5).with(tokenWithRole(CareNestRole.PRINCIPAL)))
                .andExpect(status().isForbidden());
        verify(childSummaryService, never()).approve(any(), any());
    }

    private static ChildSummaryResponse summary(String aiErrorCode) {
        return new ChildSummaryResponse(
                5L,
                101L,
                SummaryPeriodType.WEEK,
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 9),
                SummarySource.TEMPLATE,
                SummaryStatus.DRAFT,
                "Nội dung",
                null,
                null,
                null,
                Instant.parse("2026-10-14T03:00:00Z"),
                null,
                null,
                aiErrorCode);
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo.user", "Demo", null, Set.of(role));
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor tokenWithRole(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
