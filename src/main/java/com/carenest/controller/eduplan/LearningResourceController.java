package com.carenest.controller.eduplan;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PresignedUploadResponse;
import com.carenest.dto.eduplan.LearningResourceBinding;
import com.carenest.dto.eduplan.LearningResourceConfirmRequest;
import com.carenest.dto.eduplan.LearningResourceResponse;
import com.carenest.dto.eduplan.LearningResourceUploadUrlRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.LearningResourceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/education-plans/class-docs/theme/{docKey}/learning-resources")
@PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
@RequiredArgsConstructor
@Slf4j
public class LearningResourceController {
    private static final String LOG_PREFIX = "[LearningResourceController]";
    private final LearningResourceService service;

    @GetMapping
    public ResponseEntity<ResponseJson<List<LearningResourceResponse>>> list(
            @PathVariable String docKey, CurrentUser user) {
        String p = LOG_PREFIX + "|list|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.list(user, docKey);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get learning resources success", result);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<LearningResourceResponse>> upload(
            @PathVariable String docKey,
            @RequestPart MultipartFile file,
            @RequestPart String activityKey,
            @RequestPart String activitySignature,
            @RequestPart String activityLabel,
            @RequestHeader("Idempotency-Key") UUID requestId,
            CurrentUser user) {
        String p = LOG_PREFIX + "|upload|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.upload(
                user,
                docKey,
                file,
                new LearningResourceBinding(activityKey, activitySignature, activityLabel),
                requestId);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Upload learning resource success", result);
    }

    /** Bước 1 tải trực tiếp (tệp lớn): xin liên kết PUT lên kho; client PUT xong thì gọi {@code /confirm}. */
    @PostMapping("/upload-url")
    public ResponseEntity<ResponseJson<PresignedUploadResponse>> uploadUrl(
            @PathVariable String docKey, @Valid @RequestBody LearningResourceUploadUrlRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|uploadUrl|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.uploadUrl(user, docKey, request);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Create learning resource upload url success", result);
    }

    /** Bước 2 tải trực tiếp: kiểm lại tệp đã tải và gắn vào hoạt động. */
    @PostMapping("/confirm")
    public ResponseEntity<ResponseJson<LearningResourceResponse>> confirmUpload(
            @PathVariable String docKey,
            @Valid @RequestBody LearningResourceConfirmRequest request,
            @RequestHeader("Idempotency-Key") UUID requestId,
            CurrentUser user) {
        String p = LOG_PREFIX + "|confirmUpload|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.confirmUpload(user, docKey, request, requestId);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Confirm learning resource upload success", result);
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<ResponseJson<FileUrlResponse>> contentUrl(
            @PathVariable String docKey, @PathVariable Long id, CurrentUser user) {
        String p = LOG_PREFIX + "|contentUrl|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.contentUrl(user, docKey, id);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get learning resource url success", result);
    }

    @PatchMapping("/{id}/activity")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<LearningResourceResponse>> bind(
            @PathVariable String docKey,
            @PathVariable Long id,
            @Valid @RequestBody LearningResourceBinding binding,
            CurrentUser user) {
        String p = LOG_PREFIX + "|bind|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.bind(user, docKey, id, binding);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Bind learning resource success", result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<String>> detach(
            @PathVariable String docKey, @PathVariable Long id, CurrentUser user) {
        String p = LOG_PREFIX + "|detach|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        service.detach(user, docKey, id);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Detach learning resource success", "Detached");
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable String docKey, @PathVariable Long id, CurrentUser user) {
        String p = LOG_PREFIX + "|content|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        var result = service.content(user, docKey, id);
        log.info("{}|END", p);
        boolean preview = result.contentType().startsWith("image/")
                || result.contentType().startsWith("video/")
                || result.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        (preview ? ContentDisposition.inline() : ContentDisposition.attachment())
                                .filename(result.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(result.resource());
    }
}
