package com.carenest.controller.account;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.account.MyAccountResponse;
import com.carenest.dto.account.UpdateAccountStatusRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountProvisioningService;
import com.carenest.service.account.AccountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
}
