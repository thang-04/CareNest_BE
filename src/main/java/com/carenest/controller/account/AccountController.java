package com.carenest.controller.account;

import com.carenest.dto.account.AccountDetailResponse;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.AccountSummaryResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.account.MyAccountResponse;
import com.carenest.dto.account.ResetTemporaryPasswordRequest;
import com.carenest.dto.account.UpdateAccountStatusRequest;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountProvisioningService;
import com.carenest.service.account.AccountQueryService;
import com.carenest.service.account.AccountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    private static final String LOG_PREFIX = "[AccountController]";

    private final AccountService accountService;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountQueryService accountQueryService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<MyAccountResponse>> getMyAccount(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMyAccount|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        MyAccountResponse profile = accountService.getMyAccount(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get my account success", profile);
    }

    @PostMapping
    @PreAuthorize("@permissionGuard.has('ACCOUNT_MANAGE')")
    // Để OpenAPI ghi 201 khớp ApiCode.CREATED; springdoc mặc định suy ra 200 với ResponseEntity
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<CreateAccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|createAccount|actorUserId=" + currentUser.userId() + "|role=" + request.role();
        log.info("{}|START", logPrefix);
        CreateAccountResponse created = accountProvisioningService.createAccount(currentUser, request);
        log.info("{}|createdUserId={}|END", logPrefix, created.userId());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create account success", created);
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("@permissionGuard.has('ACCOUNT_STATUS_MANAGE')")
    public ResponseEntity<ResponseJson<AccountResponse>> updateAccountStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateAccountStatus|actorUserId=" + currentUser.userId() + "|userId=" + userId
                + "|status=" + request.status();
        log.info("{}|START", logPrefix);
        AccountResponse updated = accountProvisioningService.updateAccountStatus(currentUser, userId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update account status success", updated);
    }

    // Danh sách theo phạm vi người xem (AUTH-08): HT/Admin toàn trường, PHT nhân sự campus mình
    @GetMapping
    @PreAuthorize(
            "@permissionGuard.hasAny('ACCOUNT_MANAGE', 'ACCOUNT_STATUS_MANAGE', 'STAFF_ASSIGNMENT_MANAGE', 'PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<PageResponse<AccountSummaryResponse>>> searchAccounts(
            @RequestParam(required = false) CareNestRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|searchAccounts|actorUserId=" + currentUser.userId() + "|role=" + role
                + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        PageResponse<AccountSummaryResponse> accounts =
                accountQueryService.searchAccounts(currentUser, role, status, campusId, q, page, size);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Search accounts success", accounts);
    }

    @GetMapping("/{userId}")
    @PreAuthorize(
            "@permissionGuard.hasAny('ACCOUNT_MANAGE', 'ACCOUNT_STATUS_MANAGE', 'STAFF_ASSIGNMENT_MANAGE', 'PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<AccountDetailResponse>> getAccountDetail(
            @PathVariable Long userId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getAccountDetail|actorUserId=" + currentUser.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        AccountDetailResponse detail = accountQueryService.getAccountDetail(currentUser, userId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get account success", detail);
    }

    @PostMapping("/{userId}/temporary-password")
    @PreAuthorize("@permissionGuard.has('ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<AccountResponse>> resetTemporaryPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetTemporaryPasswordRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|resetTemporaryPassword|actorUserId=" + currentUser.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        AccountResponse account =
                accountProvisioningService.resetTemporaryPassword(currentUser, userId, request.temporaryPassword());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reset temporary password success", account);
    }
}
