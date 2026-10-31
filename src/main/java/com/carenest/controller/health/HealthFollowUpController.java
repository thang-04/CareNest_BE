package com.carenest.controller.health;

import com.carenest.dto.health.HealthFollowUpCloseRequest;
import com.carenest.dto.health.HealthFollowUpContactRequest;
import com.carenest.dto.health.HealthFollowUpExternalResultRequest;
import com.carenest.dto.health.HealthFollowUpListResponse;
import com.carenest.dto.health.HealthFollowUpResponse;
import com.carenest.dto.health.HealthImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthFollowUpService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.HealthFollowUpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phiếu theo dõi sức khỏe: BGH, GV xem trong phạm vi; GV lớp ghi trao đổi, kết quả chuyên khoa, đóng phiếu. */
@RestController
@RequestMapping("/health-follow-ups")
@RequiredArgsConstructor
@Slf4j
public class HealthFollowUpController {
    private static final String LOG_PREFIX = "[HealthFollowUpController]";

    private final HealthFollowUpService healthFollowUpService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<HealthFollowUpListResponse>> listFollowUps(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) HealthFollowUpStatus status,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) Long schoolYear,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listFollowUps|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        HealthFollowUpListResponse list =
                healthFollowUpService.listFollowUps(currentUser, campusId, status, overdue, schoolYear);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health follow-ups success", list);
    }

    @GetMapping("/{followUpId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<HealthFollowUpResponse>> getFollowUp(
            @PathVariable Long followUpId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getFollowUp|userId=" + currentUser.userId() + "|followUpId=" + followUpId;
        log.info("{}|START", logPrefix);
        HealthFollowUpResponse followUp = healthFollowUpService.getFollowUp(currentUser, followUpId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health follow-up success", followUp);
    }

    @PostMapping("/{followUpId}/parent-contacts")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<HealthFollowUpResponse>> addParentContact(
            @PathVariable Long followUpId, @RequestBody HealthFollowUpContactRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|addParentContact|userId=" + currentUser.userId() + "|followUpId=" + followUpId;
        log.info("{}|START", logPrefix);
        HealthFollowUpResponse followUp = healthFollowUpService.addParentContact(currentUser, followUpId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Record parent contact success", followUp);
    }

    @PostMapping("/{followUpId}/external-results")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<HealthFollowUpResponse>> addExternalResult(
            @PathVariable Long followUpId,
            @RequestBody HealthFollowUpExternalResultRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|addExternalResult|userId=" + currentUser.userId() + "|followUpId=" + followUpId;
        log.info("{}|START", logPrefix);
        HealthFollowUpResponse followUp = healthFollowUpService.addExternalResult(currentUser, followUpId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Record external result success", followUp);
    }

    @PostMapping("/{followUpId}/close")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<HealthFollowUpResponse>> closeFollowUp(
            @PathVariable Long followUpId, @RequestBody HealthFollowUpCloseRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|closeFollowUp|userId=" + currentUser.userId() + "|followUpId=" + followUpId;
        log.info("{}|START", logPrefix);
        HealthFollowUpResponse followUp = healthFollowUpService.closeFollowUp(currentUser, followUpId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Close health follow-up success", followUp);
    }

    /** Ảnh kết quả khám chuyên khoa; không lưu cache ở trình duyệt hay proxy. */
    @GetMapping("/{followUpId}/images/{imageId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<byte[]> getImage(
            @PathVariable Long followUpId, @PathVariable Long imageId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getImage|userId=" + currentUser.userId() + "|followUpId=" + followUpId
                + "|imageId=" + imageId;
        log.info("{}|START", logPrefix);
        HealthImageContent image = healthFollowUpService.getImage(currentUser, followUpId, imageId);
        log.info("{}|END", logPrefix);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
