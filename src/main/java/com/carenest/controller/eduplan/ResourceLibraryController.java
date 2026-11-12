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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Spec 900: kho học liệu dùng lại cho giáo án ngày (EDU-82…88). */
@RestController
@RequestMapping("/education-plans/resources")
@PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
@RequiredArgsConstructor
@Slf4j
public class ResourceLibraryController {
    private static final String LOG_PREFIX = "[ResourceLibraryController]";
    private final ResourceLibraryService service;

    @GetMapping
    public ResponseEntity<ResponseJson<PageResponse<ResourceItemResponse>>> list(
            @RequestParam(defaultValue = "ALL") String view,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(required = false) Long themeId,
            @RequestParam(required = false) String themeName,
            @RequestParam(required = false) List<Long> goalIds,
            @RequestParam(required = false) String docKey,
            @RequestParam(required = false) String activityKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            CurrentUser user) {
        String p = LOG_PREFIX + "|list|userId=" + user.userId() + "|view=" + view;
        log.info("{}|START", p);
        var result = service.list(
                user,
                new ResourceLibraryService.Query(
                        view, q, kind, ageGroupId, themeId, themeName, goalIds, docKey, activityKey, page, size));
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get resource library success", result);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseJson<ResourceItemResponse>> upload(
            @RequestPart MultipartFile file,
            @RequestPart(required = false) String title,
            @RequestPart(required = false) String ageGroupId,
            @RequestHeader("Idempotency-Key") UUID requestId,
            CurrentUser user) {
        String p = LOG_PREFIX + "|upload|userId=" + user.userId();
        log.info("{}|START", p);
        Long ageGroup = ageGroupId == null || ageGroupId.isBlank() ? null : Long.valueOf(ageGroupId);
        var result = service.upload(user, file, title, ageGroup, requestId);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Upload resource success", result);
    }

    @PostMapping("/links")
    public ResponseEntity<ResponseJson<ResourceItemResponse>> addLink(
            @Valid @RequestBody ResourceLibraryLinkRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|addLink|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.addLink(user, request);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Add resource link success", result);
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

    @PostMapping("/{id}/share")
    public ResponseEntity<ResponseJson<ResourceItemResponse>> share(
            @PathVariable Long id, @Valid @RequestBody ResourceShareRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|share|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.share(user, id, request);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Share resource success", result);
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ResponseJson<ResourceItemResponse>> withdraw(@PathVariable Long id, CurrentUser user) {
        String p = LOG_PREFIX + "|withdraw|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.withdraw(user, id);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Withdraw resource request success", result);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ResponseJson<ResourceItemResponse>> review(
            @PathVariable Long id, @Valid @RequestBody ResourceReviewRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|review|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.review(user, id, request);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Review resource request success", result);
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
    public ResponseEntity<ResponseJson<List<ResourceUsageResponse>>> usages(@PathVariable Long id, CurrentUser user) {
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
