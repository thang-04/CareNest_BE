package com.carenest.controller.account;

import com.carenest.dto.account.AccountPhotoResponse;
import com.carenest.dto.account.AccountPhotoUrlResponse;
import com.carenest.dto.account.AccountPhotoUrlsRequest;
import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountPhotoService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh chân dung tài khoản: mọi tài khoản tự tải/xem ảnh của mình (phụ huynh bắt buộc — PCK-02; nhân sự tự đổi/xóa —
 * AUTH-27); nhân sự trong phạm vi trẻ, phụ huynh của trẻ xem ảnh phụ huynh trên hồ sơ trẻ. Phạm vi kiểm ở service.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AccountPhotoController {
    private static final String LOG_PREFIX = "[AccountPhotoController]";

    private final AccountPhotoService accountPhotoService;

    @PutMapping(value = "/accounts/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<AccountPhotoResponse>> updateMyPhoto(
            @RequestPart MultipartFile file, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateMyPhoto|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AccountPhotoResponse photo = accountPhotoService.updateMyPhoto(currentUser, file);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update photo success", photo);
    }

    @DeleteMapping("/accounts/me/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<AccountPhotoResponse>> deleteMyPhoto(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteMyPhoto|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AccountPhotoResponse photo = accountPhotoService.deleteMyPhoto(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Delete photo success", photo);
    }

    // AUTH-27: một màn gom mọi người đang hiện avatar thành một lần gọi; người không được xem bị bỏ qua
    @PostMapping("/accounts/photos/urls")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<List<AccountPhotoUrlResponse>>> findPhotoUrls(
            @Valid @RequestBody AccountPhotoUrlsRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|findPhotoUrls|userId=" + currentUser.userId() + "|count="
                + request.userIds().size();
        log.info("{}|START", logPrefix);
        List<AccountPhotoUrlResponse> urls = accountPhotoService.findPhotoUrls(currentUser, request.userIds());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get photo urls success", urls);
    }

    @GetMapping("/accounts/me/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getMyPhoto(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMyPhoto|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        ImageContent photo = accountPhotoService.getMyPhoto(currentUser);
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
        ImageContent photo = accountPhotoService.getGuardianPhoto(currentUser, childId, userId);
        log.info("{}|END", logPrefix);
        return image(photo);
    }

    @GetMapping("/accounts/me/photo/url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<FileUrlResponse>> getMyPhotoUrl(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMyPhotoUrl|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        FileUrlResponse url = accountPhotoService.getMyPhotoUrl(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get photo url success", url);
    }

    @GetMapping("/children/{childId}/guardians/{userId}/photo/url")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'PARENT')")
    public ResponseEntity<ResponseJson<FileUrlResponse>> getGuardianPhotoUrl(
            @PathVariable Long childId, @PathVariable Long userId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getGuardianPhotoUrl|userId=" + currentUser.userId() + "|childId=" + childId
                + "|guardianUserId=" + userId;
        log.info("{}|START", logPrefix);
        FileUrlResponse url = accountPhotoService.getGuardianPhotoUrl(currentUser, childId, userId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get guardian photo url success", url);
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
