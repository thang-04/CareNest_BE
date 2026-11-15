package com.carenest.controller.account;

import com.carenest.dto.account.UserSignatureResponse;
import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.UserSignatureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Chữ ký ảnh của nhân sự Web (SIG-01): tải lên, xem, đặt mặc định, xóa. Chỉ chủ tài khoản dùng các đường này; ảnh trên
 * biên bản đã ký có đường riêng (INS-03).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class UserSignatureController {
    private static final String LOG_PREFIX = "[UserSignatureController]";
    private static final String STAFF = "hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')";

    private final UserSignatureService signatureService;

    @GetMapping("/signatures/me")
    @PreAuthorize(STAFF)
    public ResponseEntity<ResponseJson<List<UserSignatureResponse>>> listSignatures(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listSignatures|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<UserSignatureResponse> signatures = signatureService.listMine(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get signatures success", signatures);
    }

    @PostMapping(value = "/signatures/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STAFF)
    public ResponseEntity<ResponseJson<UserSignatureResponse>> uploadSignature(
            @RequestPart MultipartFile file,
            @RequestParam(defaultValue = "false") boolean isDefault,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|uploadSignature|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        UserSignatureResponse signature = signatureService.upload(currentUser, file, isDefault);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Upload signature success", signature);
    }

    @PatchMapping("/signatures/me/{signatureId}/default")
    @PreAuthorize(STAFF)
    public ResponseEntity<ResponseJson<UserSignatureResponse>> setDefaultSignature(
            @PathVariable Long signatureId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|setDefaultSignature|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        UserSignatureResponse signature = signatureService.setDefault(currentUser, signatureId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Set default signature success", signature);
    }

    @DeleteMapping("/signatures/me/{signatureId}")
    @PreAuthorize(STAFF)
    public ResponseEntity<ResponseJson<Void>> deleteSignature(@PathVariable Long signatureId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|deleteSignature|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        signatureService.delete(currentUser, signatureId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete signature success");
    }

    @GetMapping("/signatures/me/{signatureId}/url")
    @PreAuthorize(STAFF)
    public ResponseEntity<ResponseJson<FileUrlResponse>> getSignatureUrl(
            @PathVariable Long signatureId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getSignatureUrl|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        FileUrlResponse url = signatureService.getContentUrl(currentUser, signatureId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get signature url success", url);
    }

    @GetMapping("/signatures/me/{signatureId}/content")
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> getSignatureContent(@PathVariable Long signatureId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getSignatureContent|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        ImageContent image = signatureService.getContent(currentUser, signatureId);
        log.info("{}|END", logPrefix);
        // Ảnh riêng tư: không lưu cache ở trình duyệt hay proxy
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
