package com.carenest.controller.meals;

import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MenuAiDraftCandidatesResponse;
import com.carenest.dto.meals.MenuAiDraftEvaluationResponse;
import com.carenest.dto.meals.MenuAiDraftGridRequest;
import com.carenest.dto.meals.MenuAiDraftRequest;
import com.carenest.dto.meals.MenuAiDraftResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MenuAiDraftService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thực đơn & dinh dưỡng › AI gợi ý thực đơn (AI-07): nháp chờ PHT dùng hoặc bỏ; công bố vẫn qua kế hoạch tuần (NUT-08).
 * Không đặt dưới {@code /meal-plans/} vì trùng {@code /meal-plans/{weekStart}}.
 */
@RestController
@RequestMapping("/nutrition/menu-ai-drafts")
@RequiredArgsConstructor
@Slf4j
public class MenuAiDraftController {
    private static final String LOG_PREFIX = "[MenuAiDraftController]";

    private final MenuAiDraftService menuAiDraftService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuAiDraftResponse>> createDraft(
            @Valid @RequestBody MenuAiDraftRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createDraft|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        MenuAiDraftResponse draft = menuAiDraftService.createDraft(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create menu AI draft success", draft);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuAiDraftResponse>> getDraft(@PathVariable Long id, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDraft|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        MenuAiDraftResponse draft = menuAiDraftService.getDraft(currentUser, id);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get menu AI draft success", draft);
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuAiDraftResponse>> dismissDraft(
            @PathVariable Long id, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|dismissDraft|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        MenuAiDraftResponse draft = menuAiDraftService.dismissDraft(currentUser, id);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Dismiss menu AI draft success", draft);
    }

    @PostMapping("/{id}/mark-used")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuAiDraftResponse>> markDailyUsed(
            @PathVariable Long id, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|markDailyUsed|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        MenuAiDraftResponse draft = menuAiDraftService.markDailyUsed(currentUser, id);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Mark menu AI draft used success", draft);
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MealPlanResponse>> applyDraft(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MenuAiDraftGridRequest edited,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|applyDraft|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        MealPlanResponse plan = menuAiDraftService.applyDraft(currentUser, id, edited);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Apply menu AI draft success", plan);
    }

    @GetMapping("/{id}/candidates")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuAiDraftCandidatesResponse>> getCandidates(
            @PathVariable Long id, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getCandidates|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        MenuAiDraftCandidatesResponse candidates = menuAiDraftService.getCandidates(currentUser, id);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get menu AI draft candidates success", candidates);
    }

    @PostMapping("/{id}/evaluate")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuAiDraftEvaluationResponse>> evaluateDraft(
            @PathVariable Long id, @Valid @RequestBody MenuAiDraftGridRequest grid, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|evaluateDraft|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        MenuAiDraftEvaluationResponse evaluation = menuAiDraftService.evaluateDraft(currentUser, id, grid);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Evaluate menu AI draft success", evaluation);
    }
}
