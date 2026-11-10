package com.carenest.controller.meals;

import com.carenest.dto.meals.CatalogStatusRequest;
import com.carenest.dto.meals.MenuTemplateRequest;
import com.carenest.dto.meals.MenuTemplateResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MenuTemplateService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.MenuBlock;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Thực đơn & dinh dưỡng › Bộ thực đơn mẫu 4 tuần theo khối (NUT-13). */
@RestController
@RequestMapping("/nutrition/menu-templates")
@RequiredArgsConstructor
@Slf4j
public class MenuTemplateController {
    private static final String LOG_PREFIX = "[MenuTemplateController]";

    private final MenuTemplateService menuTemplateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<MenuTemplateResponse>>> getTemplates(
            @RequestParam(required = false) MenuBlock block,
            @RequestParam(required = false) CatalogStatus status,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getTemplates|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<MenuTemplateResponse> templates = menuTemplateService.getTemplates(currentUser, block, status);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get menu templates success", templates);
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<MenuTemplateResponse>> getTemplate(
            @PathVariable Long templateId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getTemplate|userId=" + currentUser.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        MenuTemplateResponse template = menuTemplateService.getTemplate(currentUser, templateId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get menu template success", template);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuTemplateResponse>> createTemplate(
            @Valid @RequestBody MenuTemplateRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createTemplate|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        MenuTemplateResponse template = menuTemplateService.createTemplate(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create menu template success", template);
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuTemplateResponse>> updateTemplate(
            @PathVariable Long templateId, @Valid @RequestBody MenuTemplateRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateTemplate|userId=" + currentUser.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        MenuTemplateResponse template = menuTemplateService.updateTemplate(currentUser, templateId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update menu template success", template);
    }

    @PatchMapping("/{templateId}/status")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MenuTemplateResponse>> changeTemplateStatus(
            @PathVariable Long templateId, @Valid @RequestBody CatalogStatusRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|changeTemplateStatus|userId=" + currentUser.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        MenuTemplateResponse template =
                menuTemplateService.changeTemplateStatus(currentUser, templateId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Change menu template status success", template);
    }
}
