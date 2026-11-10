package com.carenest.controller.child;

import com.carenest.dto.child.ChildPhotoProfileResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildPhotoService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/children")
@RequiredArgsConstructor
@Slf4j
public class ChildPhotoController {
    private static final String LOG_PREFIX = "[ChildPhotoController]";
    private final ChildPhotoService photoService;

    @GetMapping("/{childId}/photo")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<byte[]> getChildPhoto(@PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChildPhoto|childId=" + childId;
        log.info("{}|START", logPrefix);
        ImageContent photo = photoService.getChildPhoto(currentUser, childId);
        log.info("{}|END", logPrefix);
        return image(photo);
    }

    @GetMapping("/{childId}/guardian-photos/{guardianId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<byte[]> getGuardianPhoto(
            @PathVariable Long childId, @PathVariable Long guardianId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getGuardianPhoto|childId=" + childId + "|guardianId=" + guardianId;
        log.info("{}|START", logPrefix);
        ImageContent photo = photoService.getGuardianPhoto(currentUser, childId, guardianId);
        log.info("{}|END", logPrefix);
        return image(photo);
    }

    @GetMapping("/{childId}/profile")
    @PreAuthorize("@permissionGuard.has('CHILD_PROFILE_VIEW')")
    public ResponseEntity<ResponseJson<ChildPhotoProfileResponse>> getProfile(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getProfile|childId=" + childId;
        log.info("{}|START", logPrefix);
        ChildViewResponse profile = photoService.getProfile(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(
                ApiCode.SUCCESSFUL, "Get child profile success", new ChildPhotoProfileResponse(profile));
    }

    private static ResponseEntity<byte[]> image(ImageContent photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.content());
    }
}
