package com.carenest.controller.meals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.carenest.dto.meals.MenuAiDraftCandidatesResponse;
import com.carenest.dto.meals.MenuAiDraftEvaluationResponse;
import com.carenest.dto.meals.MenuAiDraftGridRequest;
import com.carenest.dto.meals.MenuAiDraftResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.meals.MenuAiDraftService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import com.carenest.utils.enums.MenuAiStrategy;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.Permission;
import java.time.Instant;
import java.time.LocalDate;
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

/** Lớp 1 (mã quyền MENU_MANAGE) + envelope của API nháp gợi ý thực đơn. */
@WebMvcTest(MenuAiDraftController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class MenuAiDraftControllerTest {

    private static final String DRAFTS = "/api/nutrition/menu-ai-drafts";
    private static final String BODY = """
            {"kind":"MENU_WEEKLY","block":"KINDERGARTEN","weekStart":"2026-11-16","season":"Mùa đông","wishes":"nhiều rau"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private MenuAiDraftService menuAiDraftService;

    @Test
    @DisplayName("AUTH-20: thiếu MENU_MANAGE ⇒ 403, không gọi service")
    void withoutMenuManageIsForbidden() throws Exception {
        authenticate(Set.of());

        mockMvc.perform(post(DRAFTS)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post(DRAFTS + "/5/apply").with(token())).andExpect(status().isForbidden());
        verify(menuAiDraftService, never()).createDraft(any(), any());
        verify(menuAiDraftService, never()).applyDraft(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Tạo nháp ⇒ 201 kèm nguồn, lý do dự phòng, điểm")
    void createReturnsCreated() throws Exception {
        CurrentUser user = authenticate(Set.of(Permission.MENU_MANAGE));
        given(menuAiDraftService.createDraft(eq(user), any())).willReturn(draft());

        mockMvc.perform(post(DRAFTS)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.source").value("RULE_BASED"))
                .andExpect(jsonPath("$.data.fallbackReason").value("AI_DISABLED"))
                .andExpect(jsonPath("$.data.score.baseline").value(80))
                .andExpect(jsonPath("$.data.checks[0].code").value("NORM_BALANCE"));
    }

    @Test
    @DisplayName(
            "Ứng viên ⇒ 200; chấm thử ⇒ 200 kèm điểm; chấm thử thiếu lưới ⇒ 400; dùng kèm lưới chuyển lưới cho service")
    void candidatesEvaluateAndEditedApply() throws Exception {
        CurrentUser user = authenticate(Set.of(Permission.MENU_MANAGE));
        given(menuAiDraftService.getCandidates(user, 5L)).willReturn(new MenuAiDraftCandidatesResponse(List.of()));
        given(menuAiDraftService.evaluateDraft(eq(user), eq(5L), any()))
                .willReturn(new MenuAiDraftEvaluationResponse(
                        false,
                        1,
                        new MenuAiDraftEvaluationResponse.Score(71, new MenuAiDraftResponse.Breakdown(30, 20, 14, 7)),
                        List.of(),
                        List.of(),
                        List.of()));
        String grid = "{\"days\":[{\"date\":\"2026-11-16\",\"items\":[{\"slot\":\"LUNCH.2\",\"dishId\":31}]}]}";

        mockMvc.perform(get(DRAFTS + "/5/candidates").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots").isArray());
        mockMvc.perform(post(DRAFTS + "/5/evaluate")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editedCells").value(1))
                .andExpect(jsonPath("$.data.feasible").value(false))
                .andExpect(jsonPath("$.data.score.total").value(71));
        mockMvc.perform(post(DRAFTS + "/5/evaluate")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(DRAFTS + "/5/apply")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grid))
                .andExpect(status().isOk());
        verify(menuAiDraftService)
                .applyDraft(
                        eq(user),
                        eq(5L),
                        eq(new MenuAiDraftGridRequest(List.of(new MenuAiDraftGridRequest.Day(
                                LocalDate.of(2026, 11, 16),
                                List.of(new MenuAiDraftGridRequest.Item("LUNCH.2", 31L)))))),
                        isNull());
    }

    @Test
    @DisplayName("Mong muốn quá 300 ký tự hoặc thiếu loại ⇒ 400, không gọi service")
    void invalidBodyIsRejected() throws Exception {
        authenticate(Set.of(Permission.MENU_MANAGE));

        mockMvc.perform(post(DRAFTS)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("nhiều rau", "a".repeat(301))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(DRAFTS)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"kind\":\"MENU_WEEKLY\",", "")))
                .andExpect(status().isBadRequest());
        verify(menuAiDraftService, never()).createDraft(any(), any());
    }

    @Test
    @DisplayName("Dùng nháp đã bỏ ⇒ 409 mã 6096; nháp không tồn tại ⇒ 404")
    void stateErrorsMapToConflictAndNotFound() throws Exception {
        CurrentUser user = authenticate(Set.of(Permission.MENU_MANAGE));
        given(menuAiDraftService.applyDraft(eq(user), eq(5L), any(), any()))
                .willThrow(GlobalException.of(ApiCode.MENU_AI_DRAFT_NOT_PENDING));
        given(menuAiDraftService.getDraft(user, 9L)).willThrow(GlobalException.of(ApiCode.MENU_AI_DRAFT_NOT_FOUND));

        mockMvc.perform(post(DRAFTS + "/5/apply").with(token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(6096));
        mockMvc.perform(get(DRAFTS + "/9").with(token())).andExpect(status().isNotFound());
    }

    private CurrentUser authenticate(Set<Permission> permissions) {
        CurrentUser user = new CurrentUser(
                7L, "kc-7", "demo.user", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL), permissions);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token() {
        return jwt().authorities(new SimpleGrantedAuthority(CareNestRole.VICE_PRINCIPAL.authority()));
    }

    private static MenuAiDraftResponse draft() {
        return new MenuAiDraftResponse(
                12L,
                MenuAiDraftKind.MENU_WEEKLY,
                MenuBlock.KINDERGARTEN,
                LocalDate.of(2026, 11, 16),
                null,
                null,
                null,
                MenuAiDraftStatus.PENDING_REVIEW,
                true,
                MenuAiDraftSource.RULE_BASED,
                MenuAiFallbackReason.AI_DISABLED,
                null,
                null,
                Instant.parse("2026-11-10T03:00:00Z"),
                new MenuAiDraftResponse.Score(null, 80, new MenuAiDraftResponse.Breakdown(40, 20, 12, 8)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new MenuAiDraftResponse.Check(
                        "NORM_BALANCE", "PASS", "Cân đối", "5/5 ngày đạt", List.of("NUT-24"))),
                0,
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 11, 15),
                MenuAiStrategy.FREE,
                List.of());
    }
}
