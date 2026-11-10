package com.carenest.controller.account;

import com.carenest.dto.account.AccountPhotoResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ParentPhotoService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh chân dung phụ huynh (PCK-02): phụ huynh tải ảnh của mình (App bắt buộc khi đăng nhập lần đầu); nhân sự trong phạm
 * vi trẻ, phụ huynh của trẻ xem ảnh phụ huynh trên hồ sơ trẻ. Phạm vi kiểm ở service.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AccountPhotoController {
    private static final String LOG_PREFIX = "[AccountPhotoController]";

    private final ParentPhotoService parentPhotoService;

    @PutMapping(value = "/accounts/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<AccountPhotoResponse>> updateMyPhoto(
            @RequestPart MultipartFile file, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateMyPhoto|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AccountPhotoResponse photo = parentPhotoService.updateMyPhoto(currentUser, file);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update photo success", photo);
    }

    @GetMapping("/accounts/me/photo")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<byte[]> getMyPhoto(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMyPhoto|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        ImageContent photo = parentPhotoService.getMyPhoto(currentUser);
        log.info("{}|END", logPrefix);
        return image(photo);
    }

    @GetMapping("/children/{childId}/guardians/{userId}/photo")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<byte[]> getGuardianPhoto(
            @PathVariable Long childId, @PathVariable Long userId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getGuardianPhoto|userId=" + currentUser.userId() + "|childId=" + childId
                + "|guardianUserId=" + userId;
        log.info("{}|START", logPrefix);
        ImageContent photo = parentPhotoService.getGuardianPhoto(currentUser, childId, userId);
        log.info("{}|END", logPrefix);
        return image(photo);
    }

    // Ảnh riêng tư: không lưu cache ở trình duyệt hay proxy
    private static ResponseEntity<byte[]> image(ImageContent photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.content());
    }
}
