package com.carenest.controller.announcement;

import com.carenest.dto.announcement.AnnouncementTemplateRequest;
import com.carenest.dto.announcement.AnnouncementTemplateResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementTemplateService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mẫu soạn sẵn thông báo của trường (FR-017): HT, PHT. */
@RestController
@RequestMapping("/announcement-templates")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('PRINCIPAL','VICE_PRINCIPAL')")
public class AnnouncementTemplateController {
    private static final String LOG_PREFIX = "[AnnouncementTemplateController]";

    private final AnnouncementTemplateService templateService;

    @GetMapping
    public ResponseEntity<ResponseJson<List<AnnouncementTemplateResponse>>> listTemplates(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listTemplates|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AnnouncementTemplateResponse> list = templateService.listTemplates(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get announcement templates success", list);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<AnnouncementTemplateResponse>> createTemplate(
            @RequestBody AnnouncementTemplateRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createTemplate|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AnnouncementTemplateResponse created = templateService.createTemplate(currentUser, request);
        log.info("{}|templateId={}|END", logPrefix, created.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create announcement template success", created);
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<ResponseJson<AnnouncementTemplateResponse>> updateTemplate(
            @PathVariable Long templateId, @RequestBody AnnouncementTemplateRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateTemplate|userId=" + currentUser.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        AnnouncementTemplateResponse updated = templateService.updateTemplate(currentUser, templateId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update announcement template success", updated);
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<ResponseJson<Void>> deleteTemplate(@PathVariable Long templateId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteTemplate|userId=" + currentUser.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        templateService.deleteTemplate(currentUser, templateId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete announcement template success");
    }
}
