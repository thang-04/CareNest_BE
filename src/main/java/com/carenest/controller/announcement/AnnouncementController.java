package com.carenest.controller.announcement;

import com.carenest.dto.announcement.AnnouncementListResponse;
import com.carenest.dto.announcement.AnnouncementRequest;
import com.carenest.dto.announcement.AnnouncementResponse;
import com.carenest.dto.announcement.AnnouncementSelectionRequest;
import com.carenest.dto.announcement.MyAnnouncementResponse;
import com.carenest.dto.announcement.RecipientPreviewResponse;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.AnnouncementStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Thông báo của trường (PAR-05, spec 019): HT/PHT soạn, gửi, hẹn, sửa, hủy; người nhận đọc nội dung, tải tệp. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AnnouncementController {
    private static final String LOG_PREFIX = "[AnnouncementController]";
    private static final String BGH = "hasAnyRole('PRINCIPAL','VICE_PRINCIPAL')";

    private final AnnouncementService announcementService;

    @PostMapping("/announcements/recipient-preview")
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<RecipientPreviewResponse>> previewRecipients(
            @RequestBody RecipientTargetRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|previewRecipients|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        RecipientPreviewResponse preview = announcementService.previewRecipients(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Preview recipients success", preview);
    }

    @PostMapping(value = "/announcements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(BGH)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<AnnouncementResponse>> createAnnouncement(
            @RequestPart("data") AnnouncementRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createAnnouncement|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AnnouncementResponse created = announcementService.createAnnouncement(currentUser, request, files);
        log.info("{}|announcementId={}|END", logPrefix, created.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create announcement success", created);
    }

    @PutMapping(value = "/announcements/{announcementId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<AnnouncementResponse>> updateAnnouncement(
            @PathVariable Long announcementId,
            @RequestPart("data") AnnouncementRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|updateAnnouncement|userId=" + currentUser.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        AnnouncementResponse updated =
                announcementService.updateAnnouncement(currentUser, announcementId, request, files);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update announcement success", updated);
    }

    @PostMapping("/announcements/{announcementId}/cancel")
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<AnnouncementResponse>> cancelAnnouncement(
            @PathVariable Long announcementId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|cancelAnnouncement|userId=" + currentUser.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        AnnouncementResponse cancelled = announcementService.cancelAnnouncement(currentUser, announcementId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Cancel announcement success", cancelled);
    }

    @GetMapping("/announcements")
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<AnnouncementListResponse>> listAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AnnouncementStatus status,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listAnnouncements|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AnnouncementListResponse list = announcementService.listAnnouncements(currentUser, q, status, page, size);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get announcements success", list);
    }

    @DeleteMapping("/announcements/cancelled")
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<Integer>> deleteCancelledAnnouncements(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AnnouncementStatus status,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteCancelledAnnouncements|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        int count = announcementService.deleteCancelledAnnouncements(currentUser, q, status);
        log.info("{}|count={}|END", logPrefix, count);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Delete cancelled announcements success", count);
    }

    @PostMapping("/announcements/delete-selection")
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<Integer>> deleteSelectedAnnouncements(
            @RequestBody AnnouncementSelectionRequest selection, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteSelectedAnnouncements|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        int count = announcementService.deleteSelectedAnnouncements(currentUser, selection);
        log.info("{}|count={}|END", logPrefix, count);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Delete selected announcements success", count);
    }

    @DeleteMapping("/announcements/{announcementId}")
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<Void>> deleteAnnouncement(
            @PathVariable Long announcementId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|deleteAnnouncement|userId=" + currentUser.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        announcementService.deleteAnnouncement(currentUser, announcementId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete announcement success");
    }

    @GetMapping("/announcements/{announcementId}")
    @PreAuthorize(BGH)
    public ResponseEntity<ResponseJson<AnnouncementResponse>> getAnnouncement(
            @PathVariable Long announcementId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getAnnouncement|userId=" + currentUser.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        AnnouncementResponse announcement = announcementService.getAnnouncement(currentUser, announcementId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get announcement success", announcement);
    }

    @GetMapping("/me/announcements/{announcementId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<MyAnnouncementResponse>> getMyAnnouncement(
            @PathVariable Long announcementId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getMyAnnouncement|userId=" + currentUser.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        MyAnnouncementResponse announcement = announcementService.getMyAnnouncement(currentUser, announcementId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get announcement success", announcement);
    }

    @GetMapping("/announcement-attachments/{attachmentId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|downloadAttachment|userId=" + currentUser.userId() + "|attachmentId=" + attachmentId;
        log.info("{}|START", logPrefix);
        AnnouncementService.Download file = announcementService.downloadAttachment(currentUser, attachmentId);
        log.info("{}|END", logPrefix);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }
}
