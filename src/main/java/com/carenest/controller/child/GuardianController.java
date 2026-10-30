package com.carenest.controller.child;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.UpdateAccountStatusRequest;
import com.carenest.dto.child.AddGuardianRequest;
import com.carenest.dto.child.AddGuardianResponse;
import com.carenest.dto.child.GuardianResponse;
import com.carenest.dto.child.ParentPasswordResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.GuardianAccountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Tài khoản phụ huynh gắn trẻ (AUTH-18, AUTH-19); phạm vi trẻ/phụ huynh do StaffAccessPolicy kiểm ở service
@RestController
@RequiredArgsConstructor
@Slf4j
public class GuardianController {
    private static final String LOG_PREFIX = "[GuardianController]";

    private final GuardianAccountService guardianAccountService;

    @GetMapping("/children/{childId}/guardians")
    @PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<List<GuardianResponse>>> listGuardians(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listGuardians|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<GuardianResponse> guardians = guardianAccountService.listGuardians(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List guardians success", guardians);
    }

    @PostMapping("/children/{childId}/guardians")
    @PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<AddGuardianResponse>> addGuardian(
            @PathVariable Long childId, @Valid @RequestBody AddGuardianRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|addGuardian|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        AddGuardianResponse added = guardianAccountService.addGuardian(currentUser, childId, request);
        log.info("{}|parentUserId={}|END", logPrefix, added.userId());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Add guardian success", added);
    }

    @DeleteMapping("/children/{childId}/guardians/{userId}")
    @PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<Void>> removeGuardian(
            @PathVariable Long childId, @PathVariable Long userId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|removeGuardian|actorUserId=" + currentUser.userId() + "|childId=" + childId
                + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        guardianAccountService.removeGuardian(currentUser, childId, userId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Remove guardian success");
    }

    @PatchMapping("/parent-accounts/{userId}/status")
    @PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<AccountResponse>> updateParentStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateParentStatus|actorUserId=" + currentUser.userId() + "|userId=" + userId
                + "|status=" + request.status();
        log.info("{}|START", logPrefix);
        AccountResponse updated = guardianAccountService.updateParentStatus(currentUser, userId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update parent status success", updated);
    }

    @PostMapping("/parent-accounts/{userId}/temporary-password")
    @PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<ParentPasswordResponse>> resetParentPassword(
            @PathVariable Long userId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|resetParentPassword|actorUserId=" + currentUser.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        ParentPasswordResponse reset = guardianAccountService.resetParentPassword(currentUser, userId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reset parent password success", reset);
    }
}
