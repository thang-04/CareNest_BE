package com.carenest.controller.eduplan;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.*;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.ResourceLibraryService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Kho học liệu cá nhân của giáo viên (spec 900/901, EDU-82…88); học liệu chỉ vào kho từ giáo án ngày. */
@RestController
@RequestMapping("/education-plans/resources")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
@Slf4j
public class ResourceLibraryController {
    private static final String LOG_PREFIX = "[ResourceLibraryController]";
    private final ResourceLibraryService service;

    @GetMapping
    public ResponseEntity<ResponseJson<PageResponse<ResourceItemResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(required = false) Long themeId,
            @RequestParam(required = false) String themeName,
            @RequestParam(required = false) List<Long> goalIds,
            @RequestParam(required = false) String docKey,
            @RequestParam(required = false) String activityKey,
            @RequestParam(required = false) String teachThemeName,
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            CurrentUser user) {
        String p = LOG_PREFIX + "|list|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.list(
                user,
                new ResourceLibraryService.Query(
                        q,
                        kind,
                        ageGroupId,
                        themeId,
                        themeName,
                        goalIds,
                        docKey,
                        activityKey,
                        teachThemeName,
                        schoolYearId,
                        page,
                        size));
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get resource library success", result);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ResponseJson<ResourceItemResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ResourceItemUpdateRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|update|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.update(user, id, request);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update resource success", result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseJson<String>> delete(@PathVariable Long id, CurrentUser user) {
        String p = LOG_PREFIX + "|delete|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        service.delete(user, id);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Delete resource success", "Deleted");
    }

    @GetMapping("/{id}/usages")
    public ResponseEntity<ResponseJson<ResourceUsagesResponse>> usages(@PathVariable Long id, CurrentUser user) {
        String p = LOG_PREFIX + "|usages|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.usages(user, id);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get resource usages success", result);
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<ResponseJson<FileUrlResponse>> contentUrl(@PathVariable Long id, CurrentUser user) {
        String p = LOG_PREFIX + "|contentUrl|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.contentUrl(user, id);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get resource url success", result);
    }
}
