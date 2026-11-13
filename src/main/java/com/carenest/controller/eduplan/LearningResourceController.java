package com.carenest.controller.eduplan;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PresignedUploadResponse;
import com.carenest.dto.eduplan.LearningResourceBinding;
import com.carenest.dto.eduplan.LearningResourceConfirmRequest;
import com.carenest.dto.eduplan.LearningResourceResponse;
import com.carenest.dto.eduplan.LearningResourceUploadUrlRequest;
import com.carenest.dto.eduplan.ResourceAttachRequest;
import com.carenest.dto.eduplan.ResourceLinkRequest;
import com.carenest.dto.eduplan.ResourceOrderRequest;
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
// EDU-81: học liệu chỉ thuộc kế hoạch giáo dục ngày (dd_…), gắn theo ngày + mục hoạt động
@RequestMapping("/education-plans/class-docs/day/{docKey}/learning-resources")
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
            @RequestPart String activityLabel,
            @RequestHeader("Idempotency-Key") UUID requestId,
            CurrentUser user) {
        String p = LOG_PREFIX + "|upload|userId=" + user.userId();
        log.info("{}|START", p);
        var result =
                service.upload(user, docKey, file, new LearningResourceBinding(activityKey, activityLabel), requestId);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Upload learning resource success", result);
    }

    /** Bước 1 tải trực tiếp (tệp lớn): xin liên kết PUT lên kho; client PUT xong thì gọi {@code /confirm}. */
    @PostMapping("/upload-url")
    public ResponseEntity<ResponseJson<PresignedUploadResponse>> uploadUrl(
            @PathVariable String docKey,
            @Valid @RequestBody LearningResourceUploadUrlRequest request,
            CurrentUser user) {
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

    /** EDU-83: dán link YouTube/Google vào một mục hoạt động. */
    @PostMapping("/links")
    public ResponseEntity<ResponseJson<LearningResourceResponse>> addLink(
            @PathVariable String docKey, @Valid @RequestBody ResourceLinkRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|addLink|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.addLink(user, docKey, request);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Add learning resource link success", result);
    }

    /** EDU-82: gắn học liệu có sẵn trong kho vào một mục hoạt động. */
    @PostMapping("/attach")
    public ResponseEntity<ResponseJson<List<LearningResourceResponse>>> attach(
            @PathVariable String docKey, @Valid @RequestBody ResourceAttachRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|attach|userId=" + user.userId();
        log.info("{}|START", p);
        var result = service.attach(user, docKey, request);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Attach learning resources success", result);
    }

    /** EDU-82: đổi thứ tự học liệu trong một mục hoạt động (thứ tự xem/trình chiếu). */
    @PutMapping("/order")
    public ResponseEntity<ResponseJson<List<LearningResourceResponse>>> reorder(
            @PathVariable String docKey, @Valid @RequestBody ResourceOrderRequest request, CurrentUser user) {
        String p = LOG_PREFIX + "|reorder|userId=" + user.userId();
        log.info("{}|START", p);
        service.reorder(user, docKey, request);
        var result = service.list(user, docKey);
        log.info("{}|END", p);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reorder learning resources success", result);
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
